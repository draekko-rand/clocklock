/*
 * Copyright (C) 2013 The CyanogenMod Project
 * Copyright (C) 2016 Benoit Touchette
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.draekko.clocklock.weather;

import java.util.*;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.draekko.clocklock.misc.Debug;
import com.draekko.clocklock.misc.Preferences;
import com.draekko.clocklock.weather.WeatherInfo.DayForecast;
import com.draekko.clocklock.R;

import static android.content.Context.SENSOR_SERVICE;

public class OpenWeatherMapProvider implements WeatherProvider {
    private static final String TAG = "OpenWeatherMapProvider";

    private static final int FORECAST_DAYS = 5;
    private static final String SELECTION_LOCATION = "lat=%f&lon=%f";
    private static final String SELECTION_LOCATION_UVI_5F = "%.5f,%.5f";
    private static final String SELECTION_LOCATION_UVI_INT = "%d,%d";
    private static final String SELECTION_ID = "id=%s";

    private static final long LOCATION_REQUEST_TIMEOUT = 5L * 60L * 1000L; // request for at most 5 minutes
    private static final long OUTDATED_LOCATION_THRESHOLD_MILLIS = 10L * 60L * 1000L; // 10 minutes
    private static final float LOCATION_ACCURACY_THRESHOLD_METERS = 50000;

    private static final String URL_UVI_PRE =
            "http://api.openweathermap.org/v3/uvi/%s/current.json?appid=";
    private static final String URL_LOCATION_PRE =
            "http://api.openweathermap.org/data/2.5/find?q=%s&mode=json&lang=%s&appid=";
    private static final String URL_WEATHER_PRE =
            "http://api.openweathermap.org/data/2.5/weather?%s&mode=json&units=%s&lang=%s&appid=";
    private static final String URL_FORECAST_PRE =
            "http://api.openweathermap.org/data/2.5/forecast/daily?" +
                    "%s&mode=json&units=%s&lang=%s&cnt=" + FORECAST_DAYS + "&appid=";

    private Context mContext;
    private String API_KEY;
    private float pressureSensor = -1;
    private float pressure = -1;

    private static final Criteria sLocationCriteria;
    static {
        sLocationCriteria = new Criteria();
        sLocationCriteria.setPowerRequirement(Criteria.POWER_LOW);
        sLocationCriteria.setAccuracy(Criteria.ACCURACY_COARSE);
        sLocationCriteria.setCostAllowed(false);
    }

    public OpenWeatherMapProvider(Context context) {
        mContext = context;
        API_KEY = Preferences.getApiKey(mContext);
    }

    @Override
    public int getNameResourceId() {
        return R.string.weather_source_openweathermap;
    }

    @Override
    public List<LocationResult> getLocations(String input) {
        if (API_KEY == null || API_KEY.isEmpty()) {
            API_KEY = Preferences.getApiKey(mContext);
        }
        if (API_KEY == null || API_KEY.isEmpty()) {
            return null;
        }

        input = input.replaceAll("\\s","");
        String[] splitstr = input.split(",");
        String URL_LOCATION = URL_LOCATION_PRE + API_KEY;
        String url = String.format(URL_LOCATION, splitstr[0].trim(), getLanguageCode());

        //String URL_LOCATION = URL_LOCATION_PRE + API_KEY;
        //String url = String.format(URL_LOCATION, Uri.encode(input), getLanguageCode());
        String response = HttpRetriever.retrieve(url);
        if (response == null) {
            return null;
        }

        if (Debug.doDebug(mContext)) Log.v(TAG, "URL = " + url + " returning a response of " + response);

        try {
            JSONArray jsonResults = new JSONObject(response).getJSONArray("list");
            ArrayList<LocationResult> results = new ArrayList<LocationResult>();
            int count = jsonResults.length();

            for (int i = 0; i < count; i++) {
                JSONObject result = jsonResults.getJSONObject(i);
                LocationResult location = new LocationResult();

                location.id = result.getString("id");
                location.city = result.getString("name");
                location.countryId = result.getJSONObject("sys").getString("country");
                results.add(location);
            }

            return results;
        } catch (JSONException e) {
            if (Debug.doDebug(mContext)) Log.w(TAG, "Received malformed location data (input=" + input + ")", e);
        }

        return null;
    }

    public WeatherInfo getWeatherInfo(String id, LocationResult location, boolean metric) {
        String selection = String.format(Locale.US, SELECTION_ID, id);
        String location_city = null;
        String location_state = null;
        String location_country = null;
        String location_country_name = null;
        if (location == null || id == null) {
            return null;
        }
        if (location.city != null) {
            location_city = location.city.trim();
        } else {
            return null;
        }
        if (location.state != null) {
            location_state = location.state.trim();
        }
        if (location.country != null) {
            location_country = location.country.trim();
        }
        if (location.countryName != null) {
            location_country_name = location.countryName.trim();
        }
        return handleWeatherRequest(
                selection,
                location_city,
                location_state,
                location_country_name,
                metric);
    }

    public WeatherInfo getWeatherInfo(Location location, boolean metric) {
        String selection = String.format(Locale.US, SELECTION_LOCATION,
                location.getLatitude(), location.getLongitude());
        return handleWeatherRequest(selection, null, null, null, metric);
    }

    private WeatherInfo handleWeatherRequest(String selection,
                                             String cityName,
                                             String stateName,
                                             String countryName,
                                             boolean metric) {
        if (API_KEY == null || API_KEY.isEmpty()) {
            API_KEY = Preferences.getApiKey(mContext);
        }
        if (API_KEY == null || API_KEY.isEmpty()) {
            if (Debug.doDebug(mContext)) Log.v(TAG, "KEY = NULL\n");
            return null;
        }

        String units = metric ? "metric" : "imperial";
        String locale = getLanguageCode();

        String URL_WEATHER = URL_WEATHER_PRE + API_KEY;
        String conditionUrl = String.format(Locale.US, URL_WEATHER, selection, units, locale);
        String conditionResponse = HttpRetriever.retrieve(conditionUrl);
        if (conditionResponse == null) {
            return null;
        }

        if (Debug.doDebug(mContext)) Log.v(TAG, "URL = " + conditionUrl + " returning a response of " + conditionResponse);

        String URL_FORECAST = URL_FORECAST_PRE + API_KEY;
        String forecastUrl = String.format(Locale.US, URL_FORECAST, selection, units, locale);
        String forecastResponse = HttpRetriever.retrieve(forecastUrl);
        if (forecastResponse == null) {
            return null;
        }

        String uviResponse = null;
        String selectionUvi = null;
        Location location = getCurrentLocation();
        int sun_uv = -99;

        /* Try full coordinates first but openweathermap fails sometimes
           try with only integer value for coordinates in that case. */
        if (location != null) {
            selectionUvi = String.format(
                    Locale.US, SELECTION_LOCATION_UVI_5F,
                    location.getLatitude(),
                    location.getLongitude());
            String URL_UVI = URL_UVI_PRE + API_KEY;
            String uviUrl = String.format(Locale.US, URL_UVI, selectionUvi);
            uviResponse = HttpRetriever.retrieve(uviUrl);
        }
        if (uviResponse == null || uviResponse.isEmpty() || uviResponse.contains("not found")) {
            if (location != null) {
                selectionUvi = String.format(
                        Locale.US, SELECTION_LOCATION_UVI_INT,
                        (int)location.getLatitude(),
                        (int)location.getLongitude());
                String URL_UVI = URL_UVI_PRE + API_KEY;
                String uviUrl = String.format(Locale.US, URL_UVI, selectionUvi);
                uviResponse = HttpRetriever.retrieve(uviUrl);
            }
        }

        if (Debug.doDebug(mContext)) Log.v(TAG, "URL = " + selectionUvi + " returning a response of " + uviResponse);

        try {
            if (uviResponse != null && !uviResponse.isEmpty() && !uviResponse.contains("not found")) {
                JSONObject uvi = new JSONObject(uviResponse);
                sun_uv = (int) uvi.getDouble("data");
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        try {
            JSONObject conditions = new JSONObject(conditionResponse);
            JSONObject weather = conditions.getJSONArray("weather").getJSONObject(0);
            JSONObject conditionData = conditions.getJSONObject("main");
            JSONObject windData = conditions.getJSONObject("wind");
            JSONObject sunRiseSetData = conditions.getJSONObject("sys");
            ArrayList<DayForecast> forecasts =
                    parseForecasts(new JSONObject(forecastResponse).getJSONArray("list"), metric);
            int speedUnitResId = metric ? R.string.weather_kph : R.string.weather_mph;
            if (cityName == null) {
                cityName = conditions.getString("name");
            }

            long sunrise_unix = sunRiseSetData.getLong("sunrise");
            long sunset_unix = sunRiseSetData.getLong("sunset");

            /* get air pressure if sensor is available */
            if (Preferences.useDeviceSensors(mContext)) {
                SensorManager mSensorManager = (SensorManager) mContext.getSystemService(SENSOR_SERVICE);
                List<Sensor> sensors = mSensorManager.getSensorList(Sensor.TYPE_PRESSURE);
                if (sensors.size() > 0)
                {
                    Sensor sensor = sensors.get(0);
                    SensorEventListener sensorEventListener = new SensorEventListener() {
                        @Override
                        public void onSensorChanged(SensorEvent event) {
                            if (event != null && event.values.length > 0) {
                                pressureSensor = event.values[0];
                            }
                        }

                        @Override
                        public void onAccuracyChanged(Sensor sensor, int accuracy) {
                        }
                    };
                    mSensorManager.registerListener(sensorEventListener, sensor, SensorManager.SENSOR_DELAY_FASTEST);
                    int count = 0;
                    while (pressureSensor == -1 && count < 10) {
                        count++;
                        SystemClock.sleep(100);
                    }
                    if (pressureSensor != -1) {
                        pressure = pressureSensor;
                    } else {
                        pressure = (float) conditionData.getDouble("pressure");
                    }
                    mSensorManager.unregisterListener(sensorEventListener);
                } else {
                    pressure = (float) conditionData.getDouble("pressure");
                }
            } else {
                pressure = (float) conditionData.getDouble("pressure");
            }

            long timestamp = System.currentTimeMillis();
            if (Debug.doDebug(mContext)) Log.i(TAG, "WeatherInfo Timestamp: " + timestamp);

            WeatherInfo weatherInfo = new WeatherInfo(mContext, conditions.getString("id"),
                    cityName,
                    stateName,
                    countryName,
                    /* condition */ weather.getString("main"),
                    /* conditionCode */ mapConditionIconToCode(
                    weather.getString("icon"), weather.getInt("id")),
                    /* temperature */ sanitizeTemperature(conditionData.getDouble("temp"), metric),
                    /* tempUnit */ metric ? "C" : "F",
                    /* humidity */ (float) conditionData.getDouble("humidity"),
                    /* pressure */ pressure,
                    /* wind */ (float) windData.getDouble("speed"),
                    /* windDir */ windData.getInt("deg"),
                    /* speedUnit */ mContext.getString(speedUnitResId),
                    /* sunrise */sunrise_unix,
                    /* sunset */sunset_unix,
                    /* sun_uv */sun_uv,
                    forecasts,
                    timestamp);

            if (Debug.doDebug(mContext)) Log.d(TAG, "Weather updated: " + weatherInfo);
            return weatherInfo;
        } catch (JSONException e) {
            if (Debug.doDebug(mContext)) Log.w(TAG, "Received malformed weather data (selection = " + selection
                              + ", lang = " + locale + ")", e);
        }

        return null;
    }

    private ArrayList<DayForecast> parseForecasts(JSONArray forecasts, boolean metric) throws JSONException {
        ArrayList<DayForecast> result = new ArrayList<DayForecast>();
        int count = forecasts.length();

        if (count == 0) {
            throw new JSONException("Empty forecasts array");
        }
        for (int i = 0; i < count; i++) {
            JSONObject forecast = forecasts.getJSONObject(i);
            JSONObject temperature = forecast.getJSONObject("temp");
            JSONObject data = forecast.getJSONArray("weather").getJSONObject(0);
            DayForecast item = new DayForecast(
                    /* low */ sanitizeTemperature(temperature.getDouble("min"), metric),
                    /* high */ sanitizeTemperature(temperature.getDouble("max"), metric),
                    /* condition */ data.getString("main"),
                    /* conditionCode */ mapConditionIconToCode(
                    data.getString("icon"), data.getInt("id")));
            result.add(item);
        }

        return result;
    }

    // OpenWeatherMap sometimes returns temperatures in Kelvin even if we ask it
    // for deg C or deg F. Detect this and convert accordingly.
    private static float sanitizeTemperature(double value, boolean metric) {
        // threshold chosen to work for both C and F. 170 deg F is hotter
        // than the hottest place on earth.
        if (value > 170) {
            // K -> deg C
            value -= 273.15;
            if (!metric) {
                // deg C -> deg F
                value = (value * 1.8) + 32;
            }
        }
        return (float) value;
    }

    private static final HashMap<String, Integer> ICON_MAPPING = new HashMap<String, Integer>();

    static {
        ICON_MAPPING.put("01d", 32);
        ICON_MAPPING.put("01n", 31);
        ICON_MAPPING.put("02d", 30);
        ICON_MAPPING.put("02n", 29);
        ICON_MAPPING.put("03d", 26);
        ICON_MAPPING.put("03n", 26);
        ICON_MAPPING.put("04d", 28);
        ICON_MAPPING.put("04n", 27);
        ICON_MAPPING.put("09d", 12);
        ICON_MAPPING.put("09n", 11);
        ICON_MAPPING.put("10d", 40);
        ICON_MAPPING.put("10n", 45);
        ICON_MAPPING.put("11d", 4);
        ICON_MAPPING.put("11n", 4);
        ICON_MAPPING.put("13d", 16);
        ICON_MAPPING.put("13n", 16);
        ICON_MAPPING.put("50d", 21);
        ICON_MAPPING.put("50n", 20);
    }

    private int mapConditionIconToCode(String icon, int conditionId) {

        // First, use condition ID for specific cases
        switch (conditionId) {
            // Thunderstorms
            case 202:    // thunderstorm with heavy rain
            case 232:    // thunderstorm with heavy drizzle
            case 211:    // thunderstorm
                return 4;
            case 212:    // heavy thunderstorm
                return 3;
            case 221:    // ragged thunderstorm
            case 231:    // thunderstorm with drizzle
            case 201:    // thunderstorm with rain
                return 38;
            case 230:    // thunderstorm with light drizzle
            case 200:    // thunderstorm with light rain
            case 210:    // light thunderstorm
                return 37;

            // Drizzle
            case 300:    // light intensity drizzle
            case 301:     // drizzle
            case 302:     // heavy intensity drizzle
            case 310:     // light intensity drizzle rain
            case 311:     // drizzle rain
            case 312:     // heavy intensity drizzle rain
            case 313:     // shower rain and drizzle
            case 314:     // heavy shower rain and drizzle
            case 321:    // shower drizzle
                return 9;

            // Rain
            case 500:    // light rain
            case 501:    // moderate rain
            case 520:    // light intensity shower rain
            case 521:    // shower rain
            case 531:    // ragged shower rain
                return 11;
            case 502:    // heavy intensity rain
            case 503:    // very heavy rain
            case 504:    // extreme rain
            case 522:    // heavy intensity shower rain
                return 12;
            case 511:    // freezing rain
                return 10;

            // Snow
            case 600:
            case 620:
                return 14; // light snow
            case 601:
            case 621:
                return 16; // snow
            case 602:
            case 622:
                return 41; // heavy snow
            case 611:
            case 612:
                return 18; // sleet
            case 615:
            case 616:
                return 5;  // rain and snow

            // Atmosphere
            case 741:    // fog
                return 20;
            case 711:    // smoke
            case 762:    // volcanic ash
                return 22;
            case 701:    // mist
            case 721:    // haze
                return 21;
            case 731:    // sand/dust whirls
            case 751:    // sand
            case 761:    // dust
                return 19;
            case 771:    // squalls
                return 23;
            case 781:    // tornado
                return 0;

            // Extreme
            case 900:
                return 0;  // tornado
            case 901:
                return 1;  // tropical storm
            case 902:
                return 2;  // hurricane
            case 903:
                return 25; // cold
            case 904:
                return 36; // hot
            case 905:
                return 24; // windy
            case 906:
                return 17; // hail
        }

        // Not yet handled - Use generic icon mapping
        Integer condition = ICON_MAPPING.get(icon);
        if (condition != null) {
            return condition;
        }

        return -1;
    }

    private static final HashMap<String, String> LANGUAGE_CODE_MAPPING = new HashMap<String, String>();

    static {
        LANGUAGE_CODE_MAPPING.put("bg-", "bg");
        LANGUAGE_CODE_MAPPING.put("de-", "de");
        LANGUAGE_CODE_MAPPING.put("es-", "sp");
        LANGUAGE_CODE_MAPPING.put("fi-", "fi");
        LANGUAGE_CODE_MAPPING.put("fr-", "fr");
        LANGUAGE_CODE_MAPPING.put("it-", "it");
        LANGUAGE_CODE_MAPPING.put("nl-", "nl");
        LANGUAGE_CODE_MAPPING.put("pl-", "pl");
        LANGUAGE_CODE_MAPPING.put("pt-", "pt");
        LANGUAGE_CODE_MAPPING.put("ro-", "ro");
        LANGUAGE_CODE_MAPPING.put("ru-", "ru");
        LANGUAGE_CODE_MAPPING.put("se-", "se");
        LANGUAGE_CODE_MAPPING.put("tr-", "tr");
        LANGUAGE_CODE_MAPPING.put("uk-", "ua");
        LANGUAGE_CODE_MAPPING.put("zh-CN", "zh_cn");
        LANGUAGE_CODE_MAPPING.put("zh-TW", "zh_tw");
    }

    private String getLanguageCode() {
        Locale locale = mContext.getResources().getConfiguration().locale;
        String selector = locale.getLanguage() + "-" + locale.getCountry();

        for (Map.Entry<String, String> entry : LANGUAGE_CODE_MAPPING.entrySet()) {
            if (selector.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }

        return "en";
    }

    private Location getCurrentLocation() {
        LocationManager lm = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(
                mContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(
                        mContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        Location location = lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);

        if (location != null && location.getAccuracy() > LOCATION_ACCURACY_THRESHOLD_METERS) {
            location = null;
        }

        return location;
    }
}
