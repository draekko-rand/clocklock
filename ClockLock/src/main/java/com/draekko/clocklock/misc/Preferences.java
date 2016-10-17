/*
 * Copyright (C) 2012 The CyanogenMod Project
 * Portions Copyright (C) 2016 Benoit Touchette
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

package com.draekko.clocklock.misc;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;

import com.draekko.clocklock.R;
import com.draekko.clocklock.weather.OpenWeatherMapProvider;
import com.draekko.clocklock.weather.WeatherInfo;
import com.draekko.clocklock.weather.WeatherProvider;
import com.draekko.clocklock.weather.WeatherUndergroundProvider;

import net.grandcentrix.tray.AppPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class Preferences {

    private static final String TAG = "Preferences";

    private static AppPreferences preference;

    private Preferences(Context context) {
        preference = new AppPreferences(context.getApplicationContext());
    }

    public static boolean doDebug(Context context) {
        return getPrefs(context.getApplicationContext()).getBoolean(Constants.USE_DEBUGGER, false);
    }

    public static void setDoDebug(Context context, boolean debug) {
        getPrefs(context.getApplicationContext()).put(Constants.USE_DEBUGGER, debug);
    }

    public static boolean isFirstWeatherUpdate(Context context) {
        return getPrefs(context.getApplicationContext()).getBoolean(Constants.WEATHER_FIRST_UPDATE, true);
    }

    public static boolean showDigitalClock(Context context) {
        return getPrefs(context.getApplicationContext()).getBoolean(Constants.CLOCK_DIGITAL, true);
    }

    public static boolean showAlarm(Context context) {
        return getPrefs(context.getApplicationContext()).getBoolean(Constants.CLOCK_SHOW_ALARM, true);
    }

    public static boolean showWeather(Context context) {
        return getPrefs(context.getApplicationContext()).getBoolean(Constants.SHOW_WEATHER, true);
    }

    public static boolean showCalendar(Context context) {
        return getPrefs(context.getApplicationContext()).getBoolean(Constants.SHOW_CALENDAR, false);
    }

    public static boolean useBoldFontForHours(Context context) {
        return getPrefs(context.getApplicationContext()).getBoolean(Constants.CLOCK_FONT, false);
    }

    public static boolean useBoldFontForMinutes(Context context) {
        return getPrefs(context.getApplicationContext()).getBoolean(Constants.CLOCK_FONT_MINUTES, false);
    }

    public static boolean useBoldFontForDateAndAlarms(Context context) {
        return getPrefs(context.getApplicationContext()).getBoolean(Constants.CLOCK_FONT_DATE, true);
    }

    public static boolean showAmPmIndicator(Context context) {
        return getPrefs(context.getApplicationContext()).getBoolean(Constants.CLOCK_AM_PM_INDICATOR, false);
    }

    public static int clockFontColor(Context context) {
        int color = Color.parseColor(getPrefs(context.getApplicationContext()).getString(Constants.CLOCK_FONT_COLOR,
                Constants.DEFAULT_LIGHT_COLOR));
        return color;
    }

    public static int clockAlarmFontColor(Context context) {
        int color = Color.parseColor(getPrefs(context.getApplicationContext()).getString(Constants.CLOCK_ALARM_FONT_COLOR,
                Constants.DEFAULT_DARK_COLOR));
        return color;
    }

    public static int clockBackgroundColor(Context context) {
        int color = Color.parseColor(getPrefs(context.getApplicationContext()).getString(Constants.CLOCK_BACKGROUND_COLOR,
                Constants.DEFAULT_BACKGROUND_COLOR));
        return color;
    }

    public static int clockBackgroundTransparency(Context context) {
        int trans = getPrefs(context.getApplicationContext()).getInt(Constants.CLOCK_BACKGROUND_TRANSPARENCY,
                Constants.DEFAULT_BACKGROUND_TRANSPARENCY);
        return trans;
    }

    public static int weatherFontColor(Context context) {
        int color = Color.parseColor(getPrefs(context.getApplicationContext()).getString(Constants.WEATHER_FONT_COLOR,
                Constants.DEFAULT_LIGHT_COLOR));
        return color;
    }

    public static int weatherTimestampFontColor(Context context) {
        int color = Color.parseColor(getPrefs(context.getApplicationContext()).getString(Constants.WEATHER_TIMESTAMP_FONT_COLOR,
                Constants.DEFAULT_DARK_COLOR));
        return color;
    }

    public static int calendarFontColor(Context context) {
        int color = Color.parseColor(getPrefs(context.getApplicationContext()).getString(Constants.CALENDAR_FONT_COLOR,
                Constants.DEFAULT_LIGHT_COLOR));
        return color;
    }

    public static int calendarBackgroundColor(Context context) {
        int color = Color.parseColor(getPrefs(context.getApplicationContext()).getString(Constants.CALENDAR_BACKGROUND_COLOR,
                Constants.DEFAULT_BACKGROUND_COLOR));
        return color;
    }

    public static int calendarDetailsFontColor(Context context) {
        int color = Color.parseColor(getPrefs(context.getApplicationContext()).getString(Constants.CALENDAR_DETAILS_FONT_COLOR,
                Constants.DEFAULT_DARK_COLOR));
        return color;
    }

    public static boolean calendarHighlightUpcomingEvents(Context context) {
        return getPrefs(context.getApplicationContext()).getBoolean(Constants.CALENDAR_HIGHLIGHT_UPCOMING_EVENTS, false);
    }

    public static boolean calendarUpcomingEventsBold(Context context) {
        return getPrefs(context.getApplicationContext()).getBoolean(Constants.CALENDAR_UPCOMING_EVENTS_BOLD, false);
    }

    public static int calendarUpcomingEventsFontColor(Context context) {
        int color = Color.parseColor(getPrefs(context.getApplicationContext()).getString(Constants.CALENDAR_UPCOMING_EVENTS_FONT_COLOR,
                Constants.DEFAULT_LIGHT_COLOR));
        return color;
    }

    public static int calendarUpcomingEventsDetailsFontColor(Context context) {
        int color = Color.parseColor(getPrefs(context.getApplicationContext()).getString(Constants.CALENDAR_UPCOMING_EVENTS_DETAILS_FONT_COLOR,
                Constants.DEFAULT_DARK_COLOR));
        return color;
    }

    public static boolean showWeatherWhenMinimized(Context context) {
        return getPrefs(context.getApplicationContext()).getBoolean(Constants.WEATHER_SHOW_WHEN_MINIMIZED, true);
    }

    public static boolean showWeatherLocation(Context context) {
        return getPrefs(context.getApplicationContext()).getBoolean(Constants.WEATHER_SHOW_LOCATION, true);
    }

    public static boolean showWeatherTimestamp(Context context) {
        return getPrefs(context.getApplicationContext()).getBoolean(Constants.WEATHER_SHOW_TIMESTAMP, true);
    }

    public static boolean invertLowHighTemperature(Context context) {
        return getPrefs(context.getApplicationContext()).getBoolean(Constants.WEATHER_INVERT_LOWHIGH, false);
    }

    public static boolean useDeviceSensors(Context context) {
        return getPrefs(context.getApplicationContext()).getBoolean(Constants.WEATHER_USE_SENSORS, true);
    }

    public static String getWeatherIconSet(Context context) {
        return getPrefs(context.getApplicationContext()).getString(Constants.WEATHER_ICONS, "color");
    }

    public static boolean useMetricUnits(Context context) {
        Locale locale = context.getResources().getConfiguration().locale;
        boolean defValue = !(locale.equals(Locale.US)
                || locale.toString().equals("ms_MY") // Malaysia
                || locale.toString().equals("si_LK") // Sri Lanka
        );
        return getPrefs(context.getApplicationContext()).getBoolean(Constants.WEATHER_USE_METRIC, defValue);
    }

    public static void setUseMetricUnits(Context context, boolean value) {
        getPrefs(context.getApplicationContext()).put(Constants.WEATHER_USE_METRIC, value);
    }

    public static long weatherRefreshIntervalInMs(Context context) {
        String value = getPrefs(context.getApplicationContext()).getString(Constants.WEATHER_REFRESH_INTERVAL, "60");
        return Long.parseLong(value) * 60 * 1000;
    }

    public static boolean useCustomWeatherLocation(Context context) {
        return getPrefs(context.getApplicationContext()).getBoolean(Constants.WEATHER_USE_CUSTOM_LOCATION, false);
    }

    public static void setUseCustomWeatherLocation(Context context, boolean value) {
        getPrefs(context.getApplicationContext()).put(Constants.WEATHER_USE_CUSTOM_LOCATION, value);
    }

    public static String customWeatherLocationId(Context context) {
        return getPrefs(context.getApplicationContext()).getString(Constants.WEATHER_CUSTOM_LOCATION_ID, null);
    }

    public static void setCustomWeatherLocationId(Context context, String id) {
        getPrefs(context.getApplicationContext()).put(Constants.WEATHER_CUSTOM_LOCATION_ID, id);
    }

    public static String getApiKey(Context context) {
        return getPrefs(context.getApplicationContext()).getString(Constants.WEATHER_API_KEY, null);
    }

    public static void setApiKey(Context context, String value) {
        getPrefs(context.getApplicationContext()).put(Constants.WEATHER_API_KEY, value);
    }

    public static String customWeatherLocationCity(Context context) {
        return getPrefs(context.getApplicationContext()).getString(Constants.WEATHER_CUSTOM_LOCATION_CITY, null);
    }

    public static String customWeatherLocationState(Context context) {
        return getPrefs(context.getApplicationContext()).getString(Constants.WEATHER_CUSTOM_LOCATION_STATE, null);
    }

    public static String customWeatherLocationCountry(Context context) {
        return getPrefs(context.getApplicationContext()).getString(Constants.WEATHER_CUSTOM_LOCATION_COUNTRY, null);
    }

    public static String customWeatherLocationCountryName(Context context) {
        return getPrefs(context.getApplicationContext()).getString(Constants.WEATHER_CUSTOM_LOCATION_COUNTRY_NAME, null);
    }

    public static void setCustomWeatherLocationCity(Context context, String city) {
        getPrefs(context.getApplicationContext()).put(Constants.WEATHER_CUSTOM_LOCATION_CITY, city);
    }

    public static void setCustomWeatherLocationState(Context context, String state) {
        getPrefs(context.getApplicationContext()).put(Constants.WEATHER_CUSTOM_LOCATION_STATE, state);
    }

    public static void setCustomWeatherLocationCountry(Context context, String country) {
        getPrefs(context.getApplicationContext()).put(Constants.WEATHER_CUSTOM_LOCATION_COUNTRY, country);
    }

    public static void setCustomWeatherLocationCountryName(Context context, String countryName) {
        getPrefs(context.getApplicationContext()).put(Constants.WEATHER_CUSTOM_LOCATION_COUNTRY_NAME, countryName);
    }

    public static String weatherProviderString(Context context) {
        String name = getPrefs(context.getApplicationContext()).getString(Constants.WEATHER_SOURCE, "openweathermap");
        if (name.equals("weatherunderground")) {
            return context.getString(R.string.weather_source_weatherunderground);
        }
        if (name.equals("openweathermap")) {
            return context.getString(R.string.weather_source_openweathermap);
        }
        return null;
    }

    public static WeatherProvider weatherProvider(Context context) {
        if (context == null) throw new NullPointerException("Context == null");
        String APIKEY = Preferences.getApiKey(context.getApplicationContext());
        if (APIKEY == null || APIKEY.isEmpty()) {
            if (Debug.doDebug(context.getApplicationContext())) {
                Log.e(TAG, "Cannot fetch weather, no api key specified");
            }
            return null;
        } else {
            String name = getPrefs(context.getApplicationContext()).getString(Constants.WEATHER_SOURCE, "openweathermap");
            if (name.equals("weatherunderground")) {
                return new WeatherUndergroundProvider(context.getApplicationContext());
            }
            if (name.equals("openweathermap")) {
                return new OpenWeatherMapProvider(context.getApplicationContext());
            }
            return null;
        }
    }

    public static void setCachedWeatherInfo(Context context, long timestamp, WeatherInfo data) {
        getPrefs(context.getApplicationContext()).put(Constants.WEATHER_LAST_UPDATE, timestamp);
        if (data != null) {
            // We now have valid weather data to display
            getPrefs(context.getApplicationContext()).put(Constants.WEATHER_FIRST_UPDATE, false);
            String weather_data = data.toSerializedString();
            getPrefs(context.getApplicationContext()).put(Constants.WEATHER_DATA, weather_data);
        }
    }

    public static long lastWeatherUpdateTimestamp(Context context) {
        return getPrefs(context.getApplicationContext()).getLong(Constants.WEATHER_LAST_UPDATE, 0);
    }

    public static WeatherInfo getCachedWeatherInfo(Context context) {
        String weather_data = getPrefs(context.getApplicationContext()).getString(Constants.WEATHER_DATA, null);
        return WeatherInfo.fromSerializedString(context, weather_data);
    }

    public static String getCachedLocationId(Context context) {
        return getPrefs(context.getApplicationContext()).getString(Constants.WEATHER_LOCATION_ID, null);
    }

    public static void setCachedLocationId(Context context, String id) {
        getPrefs(context.getApplicationContext()).put(Constants.WEATHER_LOCATION_ID, id);
    }

    public static Set<String> calendarsToDisplay(Context context) {
        JSONObject jsonObject;
        Set<String> newVal = new HashSet<>();
        String dataval = getPrefs(context.getApplicationContext()).getString(Constants.CALENDAR_LIST, null);

        if (dataval == null) {
            return null;
        }

        try {
            jsonObject = new JSONObject(dataval);
            JSONArray stringset = jsonObject.getJSONArray("stringset");
            for (int loop = 0; loop < stringset.length(); loop++) {
                String data = stringset.getString(loop);
                newVal.add(data);
            }
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }

        return newVal;
    }

    public static boolean showEventsWithRemindersOnly(Context context) {
        return getPrefs(context.getApplicationContext()).getBoolean(Constants.CALENDAR_REMINDERS_ONLY, false);
    }

    public static boolean showAllDayEvents(Context context) {
        return !getPrefs(context.getApplicationContext()).getBoolean(Constants.CALENDAR_HIDE_ALLDAY, false);
    }

    public static boolean showCalendarSeparator(Context context) {
        return getPrefs(context.getApplicationContext()).getBoolean(Constants.CALENDAR_SEPARATOR, true);
    }

    public static boolean showCalendarIcon(Context context) {
        return getPrefs(context.getApplicationContext()).getBoolean(Constants.CALENDAR_ICON, true);
    }

    public static long lookAheadTimeInMs(Context context) {
        long lookAheadTime;
        String preferenceSetting = getPrefs(context.getApplicationContext()).getString(Constants.CALENDAR_LOOKAHEAD, "1209600000");

        if (preferenceSetting.equals("today")) {
            long now = System.currentTimeMillis();

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 23);
            cal.set(Calendar.MINUTE, 59);
            cal.set(Calendar.SECOND, 59);
            cal.set(Calendar.MILLISECOND, 500);
            long endtimeToday = cal.getTimeInMillis();

            lookAheadTime = endtimeToday - now;
        } else {
            lookAheadTime = Long.parseLong(preferenceSetting);
        }
        return lookAheadTime;
    }

    public static final int SHOW_NEVER = 0;
    public static final int SHOW_FIRST_LINE = 1;
    public static final int SHOW_ALWAYS = 2;

    public static int calendarLocationMode(Context context) {
        return Integer.parseInt(getPrefs(context.getApplicationContext()).getString(Constants.CALENDAR_SHOW_LOCATION, "0"));
    }

    public static int calendarDescriptionMode(Context context) {
        return Integer.parseInt(getPrefs(context.getApplicationContext()).getString(Constants.CALENDAR_SHOW_DESCRIPTION, "0"));
    }

    public static AppPreferences getPrefs(Context context) {
        if (preference == null) {
            preference = new AppPreferences(context.getApplicationContext());
        }
        return preference;
    }
}