/*
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
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.draekko.clocklock.misc.Debug;
import com.draekko.clocklock.misc.Preferences;
import com.draekko.clocklock.weather.WeatherInfo.DayForecast;
import com.draekko.clocklock.R;

import static android.content.Context.SENSOR_SERVICE;

public class WeatherUndergroundProvider implements WeatherProvider {
    private static final String TAG = "WthrUndergroundProvider";

    private static final String SELECTION_LOCATION_UVI_5F = "%.5f,%.5f";
    private static final String SELECTION_CITY_STATE = "%s/%s";

    private static final long LOCATION_REQUEST_TIMEOUT = 5L * 60L * 1000L; // request for at most 5 minutes
    private static final long OUTDATED_LOCATION_THRESHOLD_MILLIS = 10L * 60L * 1000L; // 10 minutes
    private static final float LOCATION_ACCURACY_THRESHOLD_METERS = 50000;

    private static final String URL_LOCATION_PRE = "http://api.wunderground.com/api/";
    private static final String URL_LOCATION_POST = "/q/%s.json";

    private static final String URL_WEATHER_PRE = "http://api.wunderground.com/api/";
    private static final String URL_WEATHER_POST = "/conditions/q/%s.json";

    private static final String URL_FORECAST_PRE = "http://api.wunderground.com/api/";
    private static final String URL_FORECAST_POST = "/forecast/q/%s.json";

    private static final String URL_ASTRONOMY_PRE = "http://api.wunderground.com/api/";
    private static final String URL_ASTRONOMY_POST = "/astronomy/q/%s.json";

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

    public WeatherUndergroundProvider(Context context) {
        mContext = context;
        API_KEY = Preferences.getApiKey(mContext);
    }

    @Override
    public int getNameResourceId() {
        return R.string.weather_source_weatherunderground;
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
        String URL_LOCATION = URL_LOCATION_PRE + API_KEY + URL_LOCATION_POST;
        String url = String.format(URL_LOCATION, splitstr[0].trim(), getLanguageCode());
        String response = HttpRetriever.retrieve(url);
        if (response == null) {
            return null;
        }

        if (Debug.doDebug(mContext)) Log.v(TAG, "URL = " + url + " returning a response of " + response);

        try {
            JSONArray jsonResults = new JSONObject(response).getJSONObject("response").getJSONArray("results");
            ArrayList<LocationResult> results = new ArrayList<LocationResult>();
            int count = jsonResults.length();

            for (int i = 0; i < count; i++) {
                JSONObject result = jsonResults.getJSONObject(i);
                LocationResult location = new LocationResult();

                location.id = result.getString("zmw");
                location.city = result.getString("name");
                location.state = result.getString("state");
                location.country = result.getString("country");
                location.countryId = result.getString("country");
                location.countryName = result.getString("country_name");
                results.add(location);
            }

            return results;
        } catch (JSONException e) {
            if (Debug.doDebug(mContext)) Log.w(TAG, "Received malformed location data (input=" + input + ")", e);
        }

        return null;
    }

    public WeatherInfo getWeatherInfo(String id, LocationResult location, boolean metric) {
        String location_city = null;
        String location_state = null;
        String location_country = null;
        String location_country_name = null;
        String selection = null;
        if (location == null) {
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
        if (location_country != null && location_country.toLowerCase().equals("us")) {
            selection = location_state + "/" + location_city;
        } else {
            selection = location_country_name + "/" + location_city;
        }
        return handleWeatherRequest(
                selection,
                location_city,
                location_state,
                location_country_name,
                metric,
                false);
    }

    public WeatherInfo getWeatherInfo(Location location, boolean metric) {
        String selection = String.format(
                Locale.US, SELECTION_LOCATION_UVI_5F,
                location.getLatitude(),
                location.getLongitude());
        return handleWeatherRequest(selection, null, null, null, metric, true);
    }

    private WeatherInfo handleWeatherRequest(String selection,
                                             String cityName,
                                             String stateName,
                                             String countryName,
                                             boolean metric,
                                             boolean coords) {
        if (API_KEY == null || API_KEY.isEmpty()) {
            API_KEY = Preferences.getApiKey(mContext);
        }
        if (API_KEY == null || API_KEY.isEmpty()) {
            if (Debug.doDebug(mContext)) Log.v(TAG, "KEY = NULL\n");
            return null;
        }

        if (cityName != null && selection != null) {
            if (cityName.toLowerCase().equals(selection.toLowerCase())) {
                String temp = selection;
                selection = countryName + "/" + temp;
            }
        }

        String units = metric ? "metric" : "imperial";
        String locale = getLanguageCode();
        Location location = getCurrentLocation();

        String URL_WEATHER = URL_WEATHER_PRE + API_KEY + URL_WEATHER_POST;
        String weatherUrl = String.format(Locale.US, URL_WEATHER, selection);
        String weatherResponse = HttpRetriever.retrieve(weatherUrl);
        if (weatherResponse == null) {
            return null;
        }

        if (Debug.doDebug(mContext)) Log.v(TAG, "URL = " + weatherUrl + " returning a response of " + weatherResponse);

        String URL_FORECAST = URL_FORECAST_PRE + API_KEY + URL_FORECAST_POST;
        String forecastUrl = String.format(Locale.US, URL_FORECAST, selection);
        String forecastResponse = HttpRetriever.retrieve(forecastUrl);
        if (forecastResponse == null) {
            return null;
        }

        if (Debug.doDebug(mContext)) Log.v(TAG, "URL = " + forecastUrl + " returning a response of " + forecastResponse);

        String URL_ASTRONOMY = URL_ASTRONOMY_PRE + API_KEY + URL_ASTRONOMY_POST;
        String astronomyUrl = String.format(Locale.US, URL_ASTRONOMY, selection);
        String astronomyResponse = HttpRetriever.retrieve(astronomyUrl);
        if (astronomyResponse == null) {
            return null;
        }

        if (Debug.doDebug(mContext)) Log.v(TAG, "URL = " + astronomyUrl + " returning a response of " + astronomyResponse);

        try {
            JSONObject weather = new JSONObject(weatherResponse);
            JSONObject astronomy = new JSONObject(astronomyResponse);
            JSONObject forecast = new JSONObject(forecastResponse);

            JSONObject current = weather.getJSONObject("current_observation");

            JSONArray simpleforecast = forecast.getJSONObject("forecast").getJSONObject("simpleforecast").getJSONArray("forecastday");

            int speedUnitResId = metric ? R.string.weather_kph : R.string.weather_mph;
            cityName = current.getJSONObject("display_location").getString("city");
            stateName = current.getJSONObject("display_location").getString("state_name");
            countryName = current.getJSONObject("display_location").getString("country");

            int sr_hour = Integer.valueOf(astronomy.getJSONObject("sun_phase").getJSONObject("sunrise").getString("hour"));
            int sr_minute = Integer.valueOf(astronomy.getJSONObject("sun_phase").getJSONObject("sunrise").getString("minute"));
            int ss_hour = Integer.valueOf(astronomy.getJSONObject("sun_phase").getJSONObject("sunset").getString("hour"));
            int ss_minute = Integer.valueOf(astronomy.getJSONObject("sun_phase").getJSONObject("sunset").getString("minute"));

            Calendar cal_sr = Calendar.getInstance();
            cal_sr.set(Calendar.SECOND, 0);
            cal_sr.set(Calendar.HOUR_OF_DAY, sr_hour);
            cal_sr.set(Calendar.MINUTE, sr_minute);

            Calendar cal_ss = Calendar.getInstance();
            cal_ss.set(Calendar.SECOND, 0);
            cal_ss.set(Calendar.HOUR_OF_DAY, ss_hour);
            cal_ss.set(Calendar.MINUTE, ss_minute);

            long sunrise_unix = cal_sr.getTimeInMillis() / 1000;
            long sunset_unix = cal_ss.getTimeInMillis() / 1000;

            ArrayList<DayForecast> forecasts = parseForecasts(
                    simpleforecast,
                    sunrise_unix,
                    sunset_unix,
                    metric);

            String temp = metric ? current.getString("temp_c") : current.getString("temp_f");
            String humid = current.getString("relative_humidity");
            String windage = metric ? current.getString("wind_kph") : current.getString("wind_mph");
            String windir = current.getString("wind_degrees");
            String UVI =  current.getString("UV");
            float temperature = Float.valueOf(temp);
            float humidity = Float.valueOf(humid.substring(0, humid.length() - 1));
            float windspeed = Float.valueOf(windage);
            int winddirection = Integer.valueOf(windir);
            float sun_uv = Float.valueOf(UVI);
            int weathercode = mapConditionIconToCode(
                    current.getString("icon"),
                    sunrise_unix,
                    sunset_unix);

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
                        pressure = Float.valueOf(current.getString("pressure_mb"));
                    }
                    mSensorManager.unregisterListener(sensorEventListener);
                } else {
                    pressure = Float.valueOf(current.getString("pressure_mb"));
                }
            } else {
                pressure = Float.valueOf(current.getString("pressure_mb"));
            }

            long timestamp = System.currentTimeMillis();
            if (Debug.doDebug(mContext)) Log.i(TAG, "WeatherInfo Timestamp: " + timestamp);

            WeatherInfo weatherInfo = new WeatherInfo(
                    mContext,
                    current.getString("station_id"),
                    /* city name */ cityName,
                    /* state name */ stateName,
                    /* country name */ countryName,
                    /* condition */ current.getString("weather"),
                    /* conditionCode */ weathercode,
                    /* temperature */ temperature,
                    /* tempUnit */ metric ? "C" : "F",
                    /* humidity */ humidity,
                    /* pressure */ pressure,
                    /* wind */ windspeed,
                    /* windDir */ winddirection,
                    /* speedUnit */ mContext.getString(speedUnitResId),
                    /* sunrise */sunrise_unix,
                    /* sunset */sunset_unix,
                    /* sun_uv */(int)sun_uv,
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

    private ArrayList<DayForecast> parseForecasts(
            JSONArray forecasts, long sunrise, long sunset, boolean metric) throws JSONException {
        ArrayList<DayForecast> result = new ArrayList<DayForecast>();
        int count = forecasts.length();

        if (count == 0) {
            throw new JSONException("Empty forecasts array");
        }
        for (int i = 0; i < count; i++) {
            String high = metric ?
                    forecasts.getJSONObject(i).getJSONObject("high").getString("celsius") :
                    forecasts.getJSONObject(i).getJSONObject("high").getString("fahrenheit");
            String low = metric ?
                    forecasts.getJSONObject(i).getJSONObject("low").getString("celsius") :
                    forecasts.getJSONObject(i).getJSONObject("low").getString("fahrenheit");
            float lowtemp = Float.valueOf(low);
            float hightemp = Float.valueOf(high);
            String conditions = forecasts.getJSONObject(i).getString("conditions");
            int code = mapConditionIconToCode(forecasts.getJSONObject(i).getString("icon"), sunrise, sunset);
            DayForecast item = new DayForecast(
                    /* low */ hightemp,
                    /* high */ lowtemp,
                    /* condition */ conditions,
                    /* conditionCode */ code);
            result.add(item);
        }

        return result;
    }

    private int mapConditionIconToCode(String icon, long sunrise, long sunset) {
        Calendar current = Calendar.getInstance();
        long time = current.getTimeInMillis() / 1000;
        boolean daytime = false;
        long halfhour = 1800;

        if (time > sunrise - halfhour && time < sunset + halfhour) {
            daytime = true;
        }

        if (icon.toLowerCase().contains("chanceflurries")) {
            return 14;
        } else if (icon.toLowerCase().contains("chancerain")) {
            if (daytime) {
                return 40;
            } else {
                return 45;
            }
        } else if (icon.toLowerCase().contains("chancesleet")) {
            return 18;
        } else if (icon.toLowerCase().contains("chancesnow")) {
            return 16;
        } else if (icon.toLowerCase().contains("chancetstorms")) {
            return 4;
        } else if (icon.toLowerCase().contains("clear")) {
            if (daytime) {
                return 32;
            } else {
                return 31;
            }
        } else if (icon.toLowerCase().contains("cloudy")) {
            return 26;
        } else if (icon.toLowerCase().contains("flurries")) {
            return 14;
        } else if (icon.toLowerCase().contains("fog")) {
            return 21;
        } else if (icon.toLowerCase().contains("hazy")) {
            return 21;
        } else if (icon.toLowerCase().contains("mostlycloudy")) {
            if (daytime) {
                return 30;
            } else {
                return 29;
            }
        } else if (icon.toLowerCase().contains("mostlysunny")) {
            if (daytime) {
                return 30;
            } else {
                return 29;
            }
        } else if (icon.toLowerCase().contains("partlycloudy")) {
            if (daytime) {
                return 28;
            } else {
                return 27;
            }
        } else if (icon.toLowerCase().contains("partlysunny")) {
            if (daytime) {
                return 28;
            } else {
                return 27;
            }
        } else if (icon.toLowerCase().contains("rain")) {
            if (daytime) {
                return 40;
            } else {
                return 45;
            }
        } else if (icon.toLowerCase().contains("sleet")) {
            return 18;
        } else if (icon.toLowerCase().contains("snow")) {
            return 16;
        } else if (icon.toLowerCase().contains("sunny")) {
            if (daytime) {
                return 32;
            } else {
                return 31;
            }
        } else if (icon.toLowerCase().contains("thunderstorms")) {
            return 4;
        } else if (icon.toLowerCase().contains("tstorms")) {
            return 4;
        } else if (icon.toLowerCase().contains("unknown")) {
            return -1;
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
