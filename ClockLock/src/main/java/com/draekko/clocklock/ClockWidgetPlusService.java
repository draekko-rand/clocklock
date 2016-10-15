/*
 * Portions Copyright (C) 2012 The CyanogenMod Project (DvTonder)
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

package com.draekko.clocklock;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import com.draekko.clocklock.calendar.CalendarViewsService;
import com.draekko.clocklock.misc.Constants;
import com.draekko.clocklock.misc.Debug;
import com.draekko.clocklock.misc.IconUtils;
import com.draekko.clocklock.misc.Preferences;
import com.draekko.clocklock.misc.WidgetUtils;
import com.draekko.clocklock.weather.WeatherInfo;
import com.draekko.clocklock.weather.WeatherProvider;
import com.draekko.clocklock.weather.WeatherUpdateService;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import static com.draekko.clocklock.misc.WidgetUtils.formattedDate;

public class ClockWidgetPlusService extends IntentService {
    private static final String TAG = "ClockWidgetPlusService";

    public static final String ACTION_REFRESH = "com.draekko.clocklock.action.REFRESH_WIDGET";
    public static final String ACTION_REFRESH_CALENDAR = "com.draekko.clocklock.action.REFRESH_CALENDAR";
    public static final String ACTION_HIDE_CALENDAR = "com.draekko.clocklock.action.HIDE_CALENDAR";

    // This needs to be static to persist between refreshes until explicitly changed by an intent
    private static boolean mHideCalendar = false;

    private int[] mWidgetIds;
    private AppWidgetManager mAppWidgetManager;
    private static Context mContext;

    public ClockWidgetPlusService() {
        super("ClockWidgetPlusService");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (android.os.Debug.isDebuggerConnected()) {
            android.os.Debug.waitForDebugger();
        }

        mContext = getApplicationContext();
        ComponentName thisWidget = new ComponentName(mContext, ClockWidgetPlusProvider.class);
        mAppWidgetManager = AppWidgetManager.getInstance(mContext);
        mWidgetIds = mAppWidgetManager.getAppWidgetIds(thisWidget);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (mContext == null) {
            mContext = getApplicationContext();
        }

        if (Debug.doDebug(mContext)) Log.d(TAG, "Got intent " + intent);

        if (mWidgetIds != null && mWidgetIds.length != 0) {
            // Check passed in intents
            if (intent != null) {
                if (ACTION_HIDE_CALENDAR.equals(intent.getAction())) {
                    if (Debug.doDebug(mContext)) Log.v(TAG, "Force hiding the calendar panel");
                    // Explicitly hide the panel since we received a broadcast indicating no events
                    mHideCalendar = true;
                } else if (ACTION_REFRESH_CALENDAR.equals(intent.getAction())) {
                    if (Debug.doDebug(mContext)) Log.v(TAG, "Forcing a calendar refresh");
                    // Start with the panel not explicitly hidden
                    // If there are no events, a broadcast to the service will hide the panel
                    mHideCalendar = false;
                    mAppWidgetManager.notifyAppWidgetViewDataChanged(mWidgetIds, R.id.calendar_list);
                }
            }
            refreshWidget();
        }
    }

    /**
     * Reload the widget including the Weather forecast, Alarm, Clock font and Calendar
     */
    private void refreshWidget() {
        if (mContext == null) {
            mContext = getApplicationContext();
        }

        if (Debug.doDebug(mContext)) Log.d(TAG, "\n\nRefreshWidget\n\n");

        // Get things ready
        RemoteViews remoteViews;

        boolean digitalClock = Preferences.showDigitalClock(mContext);
        boolean showWeather = Preferences.showWeather(mContext);
        boolean showWeatherWhenMinimized = Preferences.showWeatherWhenMinimized(mContext);

        // Update the widgets
        for (int id : mWidgetIds) {
            boolean showCalendar = false;

            // Determine if its a home or a lock screen widget
            Bundle myOptions = mAppWidgetManager.getAppWidgetOptions (id);
            boolean isKeyguard = false;
            if (WidgetUtils.isTextClockAvailable()) {
                // This is only available on API 17+, make sure we are not calling it on API16
                // This generates an API level Lint warning, ignore it
                int category = myOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, -1);
                isKeyguard = category == AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD;
            }
            if (Debug.doDebug(mContext)) Log.d(TAG, "For Widget id " + id + " isKeyguard is set to " + isKeyguard);

            // Determine which layout to use
            boolean smallWidget = showWeather && showWeatherWhenMinimized
                    && WidgetUtils.showSmallWidget(mContext, id, digitalClock, isKeyguard);
            if (smallWidget) {
                // The small widget is only shown if weather needs to be shown
                // and there is not enough space for the full weather widget and
                // the user had selected to show the weather when minimized (default ON)
                remoteViews = new RemoteViews(getPackageName(), R.layout.appwidget_plus_small);
                showCalendar = false;
            } else {
                remoteViews = new RemoteViews(getPackageName(), R.layout.appwidget_plus);
                // show calendar if enabled and events available and enough space available
                showCalendar = Preferences.showCalendar(mContext) && !mHideCalendar
                        && WidgetUtils.canFitCalendar(mContext, id, digitalClock);
            }

            // Hide the Loading indicator
            remoteViews.setViewVisibility(R.id.loading_indicator, View.GONE);

            // Always Refresh the Clock widget
            refreshClock(remoteViews, smallWidget, digitalClock);
            refreshAlarmStatus(remoteViews, smallWidget);

            // Refresh the time if using TextView Clock (API 16)
            if(!WidgetUtils.isTextClockAvailable()) {
                refreshTime(remoteViews, smallWidget);
            }

            // Don't bother with Calendar if its not visible
            if (showCalendar) {
                refreshCalendar(remoteViews, id);
            }
            // Hide the calendar panel if not visible
            remoteViews.setViewVisibility(R.id.calendar_panel_plus,
                    showCalendar ? View.VISIBLE : View.GONE);

            boolean canFitWeather = smallWidget
                    || WidgetUtils.canFitWeather(mContext, id, digitalClock, isKeyguard);
            boolean canFitTimestamp = smallWidget
                    || WidgetUtils.canFitTimestamp(mContext, id, digitalClock);
            // Now, if we need to show the actual weather, do so
            if (showWeather && canFitWeather) {
                WeatherInfo weatherInfo = Preferences.getCachedWeatherInfo(mContext);

                if (weatherInfo != null) {
                    setWeatherData(remoteViews, smallWidget, weatherInfo);
                    if (Debug.doDebug(mContext)) Log.i(TAG, "Display or update weather!");
                } else {
                    setNoWeatherData(remoteViews, smallWidget);
                    if (Debug.doDebug(mContext)) Log.i(TAG, "No weather!");
                }
            } else {
                if (Debug.doDebug(mContext)) Log.i(TAG, "Do not display or update weather!");
            }

            int vis1 = (showWeather && canFitWeather && canFitTimestamp) ? View.VISIBLE : View.GONE;
            remoteViews.setViewVisibility(R.id.update_time, vis1);
            int vis2 = (showWeather && canFitWeather) ? View.VISIBLE : View.GONE;
            remoteViews.setViewVisibility(R.id.weather_panel_plus, vis2);

            // Resize the clock font if needed
            if (digitalClock) {
                float ratio = WidgetUtils.getScaleRatio(mContext, id);
                //setClockSize(remoteViews, ratio * 1.5f);
                setClockSize(remoteViews, ratio);
            }

            // Set the widget background color/transparency
            int backColor = Preferences.clockBackgroundColor(mContext);
            int backTrans = Preferences.clockBackgroundTransparency(mContext);
            backColor = (backTrans << 24) | (backColor & 0xFFFFFF);
            remoteViews.setInt(R.id.clock_panel_plus, "setBackgroundColor", backColor);
            remoteViews.setInt(R.id.calendar_panel_plus, "setBackgroundColor", backColor);
            remoteViews.setInt(R.id.weather_panel_plus, "setBackgroundColor", backColor);

            // Do the update
            mAppWidgetManager.updateAppWidget(id, remoteViews);
        }
    }

    //===============================================================================================
    // Clock related functionality
    //===============================================================================================
    private void refreshClock(RemoteViews clockViews, boolean smallWidget, boolean digitalClock) {
        if (mContext == null) {
            mContext = getApplicationContext();
        }

        // Analog or Digital clock
        if (digitalClock) {
            // Hours/Minutes is specific to Digital, set it's size
            refreshClockFont(clockViews, smallWidget);
            clockViews.setViewVisibility(R.id.digital_clock, View.VISIBLE);
            clockViews.setViewVisibility(R.id.analog_clock, View.GONE);
        } else {
            clockViews.setViewVisibility(R.id.analog_clock, View.VISIBLE);
            clockViews.setViewVisibility(R.id.digital_clock, View.GONE);
        }

        // Date/Alarm is common to both clocks, set it's size
        refreshDateAlarmFont(clockViews, smallWidget);

        // Register an onClickListener on Clock, starting DeskClock
        Intent i = WidgetUtils.getDefaultClockIntent(mContext);
        if (i != null) {
            PendingIntent pi = PendingIntent.getActivity(mContext, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
            clockViews.setOnClickPendingIntent(R.id.clock_panel_plus, pi);
        }
    }

    // API 16 TextView Clock support
    private void refreshTime(RemoteViews clockViews, boolean smallWidget) {
        if (mContext == null) {
            mContext = getApplicationContext();
        }

        Locale locale = Locale.getDefault();
        Date now = new Date();
        String dateFormat = getString(R.string.abbrev_wday_month_day_no_year);
        CharSequence date = DateFormat.format(dateFormat, now);
        String hours = new SimpleDateFormat(getHourFormat(), locale).format(now);
        String minutes = new SimpleDateFormat(getString(R.string.widget_12_hours_format_no_ampm_m),
                locale).format(now);

        // Hours
        if (Preferences.useBoldFontForHours(mContext)) {
            clockViews.setTextViewText(R.id.clock1_bold, String.valueOf(Integer.valueOf(hours)));
        } else {
            clockViews.setTextViewText(R.id.clock1_regular, String.valueOf(Integer.valueOf(hours)));
        }

        // Minutes
        if (Preferences.useBoldFontForMinutes(mContext)) {
            clockViews.setTextViewText(R.id.clock2_bold, minutes);
        } else {
            clockViews.setTextViewText(R.id.clock2_regular, minutes);
        }

        // Date and Alarm font
        if (!smallWidget) {
            if (Preferences.useBoldFontForDateAndAlarms(mContext)) {
                clockViews.setTextViewText(R.id.date_bold, date);
            } else {
                clockViews.setTextViewText(R.id.date_regular, date);
            }
        } else {
            clockViews.setTextViewText(R.id.date, date);
        }
    }

    private void refreshClockFont(RemoteViews clockViews, boolean smallWidget) {
        if (mContext == null) {
            mContext = getApplicationContext();
        }

        int color = Preferences.clockFontColor(mContext);
        String amPM = new SimpleDateFormat("a", Locale.getDefault()).format(new Date());

        // Hours
        if (Preferences.useBoldFontForHours(mContext)) {
            clockViews.setViewVisibility(R.id.clock1_bold, View.VISIBLE);
            clockViews.setViewVisibility(R.id.clock1_regular, View.GONE);
            clockViews.setTextColor(R.id.clock1_bold, color);
        } else {
            clockViews.setViewVisibility(R.id.clock1_regular, View.VISIBLE);
            clockViews.setViewVisibility(R.id.clock1_bold, View.GONE);
            clockViews.setTextColor(R.id.clock1_regular, color);
        }

        // Minutes
        if (Preferences.useBoldFontForMinutes(mContext)) {
            clockViews.setViewVisibility(R.id.clock2_bold, View.VISIBLE);
            clockViews.setViewVisibility(R.id.clock2_regular, View.GONE);
            clockViews.setTextColor(R.id.clock2_bold, color);
        } else {
            clockViews.setViewVisibility(R.id.clock2_regular, View.VISIBLE);
            clockViews.setViewVisibility(R.id.clock2_bold, View.GONE);
            clockViews.setTextColor(R.id.clock2_regular, color);
        }

        // Show the AM/PM indicator
        if (!DateFormat.is24HourFormat(mContext) && Preferences.showAmPmIndicator(mContext)) {
            clockViews.setViewVisibility(R.id.clock_ampm, View.VISIBLE);
            clockViews.setTextViewText(R.id.clock_ampm, amPM);
            clockViews.setTextColor(R.id.clock_ampm, color);
        } else {
            clockViews.setViewVisibility(R.id.clock_ampm, View.GONE);
        }
    }

    private void refreshDateAlarmFont(RemoteViews clockViews, boolean smallWidget) {
        int color = Preferences.clockFontColor(mContext);

        // Date and Alarm font
        if (!smallWidget) {
            if (Preferences.useBoldFontForDateAndAlarms(mContext)) {
                clockViews.setViewVisibility(R.id.date_bold, View.VISIBLE);
                clockViews.setViewVisibility(R.id.date_regular, View.GONE);
                clockViews.setTextColor(R.id.date_bold, color);
            } else {
                clockViews.setViewVisibility(R.id.date_regular, View.VISIBLE);
                clockViews.setViewVisibility(R.id.date_bold, View.GONE);
                clockViews.setTextColor(R.id.date_regular, color);
            }
        } else {
            clockViews.setViewVisibility(R.id.date, View.VISIBLE);
            clockViews.setTextColor(R.id.date, color);
        }

        // Show the panel
        clockViews.setViewVisibility(R.id.date_alarm, View.VISIBLE);
    }

    private void setClockSize(RemoteViews clockViews, float scale) {
        float fontSize = getResources().getDimension(R.dimen.widget_big_font_size);
        clockViews.setTextViewTextSize(R.id.clock1_bold, TypedValue.COMPLEX_UNIT_PX, fontSize * scale);
        clockViews.setTextViewTextSize(R.id.clock1_regular, TypedValue.COMPLEX_UNIT_PX, fontSize * scale);
        clockViews.setTextViewTextSize(R.id.clock2_bold, TypedValue.COMPLEX_UNIT_PX, fontSize * scale);
        clockViews.setTextViewTextSize(R.id.clock2_regular, TypedValue.COMPLEX_UNIT_PX, fontSize * scale);
    }

    private String getHourFormat() {
        if (mContext == null) {
            mContext = getApplicationContext();
        }

        String format;
        if (DateFormat.is24HourFormat(mContext)) {
            format = getString(R.string.widget_24_hours_format_h_api_16);
        } else {
            format = getString(R.string.widget_12_hours_format_h);
        }
        return format;
    }

    //===============================================================================================
    // Alarm related functionality
    //===============================================================================================
    private void refreshAlarmStatus(RemoteViews alarmViews, boolean smallWidget) {
        if (mContext == null) {
            mContext = getApplicationContext();
        }

        if (Preferences.showAlarm(mContext)) {
            String nextAlarm = getNextAlarm();
            if (!TextUtils.isEmpty(nextAlarm)) {
                // An alarm is set, deal with displaying it
                int color = Preferences.clockAlarmFontColor(mContext);
                final Resources res = getResources();

                // Overlay the selected color on the alarm icon and set the imageview
                alarmViews.setImageViewBitmap(R.id.alarm_icon,
                        IconUtils.getOverlaidBitmap(mContext, res, R.drawable.ic_alarm_small, color));
                alarmViews.setViewVisibility(R.id.alarm_icon, View.VISIBLE);

                if (!smallWidget) {
                    if (Preferences.useBoldFontForDateAndAlarms(mContext)) {
                        alarmViews.setTextViewText(R.id.nextAlarm_bold, nextAlarm);
                        alarmViews.setViewVisibility(R.id.nextAlarm_bold, View.VISIBLE);
                        alarmViews.setViewVisibility(R.id.nextAlarm_regular, View.GONE);
                        alarmViews.setTextColor(R.id.nextAlarm_bold, color);
                    } else {
                        alarmViews.setTextViewText(R.id.nextAlarm_regular, nextAlarm);
                        alarmViews.setViewVisibility(R.id.nextAlarm_regular, View.VISIBLE);
                        alarmViews.setViewVisibility(R.id.nextAlarm_bold, View.GONE);
                        alarmViews.setTextColor(R.id.nextAlarm_regular, color);
                    }
                } else {
                    alarmViews.setTextViewText(R.id.nextAlarm, nextAlarm);
                    alarmViews.setViewVisibility(R.id.nextAlarm, View.VISIBLE);
                    alarmViews.setTextColor(R.id.nextAlarm, color);
                }
                return;
            }
        }

        // No alarm set or Alarm display is hidden, hide the views
        alarmViews.setViewVisibility(R.id.alarm_icon, View.GONE);
        if (!smallWidget) {
            alarmViews.setViewVisibility(R.id.nextAlarm_bold, View.GONE);
            alarmViews.setViewVisibility(R.id.nextAlarm_regular, View.GONE);
        } else {
            alarmViews.setViewVisibility(R.id.nextAlarm, View.GONE);
        }
    }

    /**
     * @return A formatted string of the next alarm or null if there is no next alarm.
     */
    private String getNextAlarm() {
        if (mContext == null) {
            mContext = getApplicationContext();
        }

        String nextAlarm = null;

        AlarmManager am =(AlarmManager) getSystemService(Context.ALARM_SERVICE);
        AlarmManager.AlarmClockInfo alarmClock = am.getNextAlarmClock();
        if (alarmClock != null) {
            nextAlarm = getNextAlarmFormattedTime(mContext, alarmClock.getTriggerTime());
        }

        return nextAlarm;
    }

    private static String getNextAlarmFormattedTime(Context context, long time) {
        String skeleton = DateFormat.is24HourFormat(context) ? "EHm" : "Ehma";
        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton);
        return (String) DateFormat.format(pattern, time);
    }

    //===============================================================================================
    // Weather related functionality
    //===============================================================================================

    /**
     * Display the weather information
     */
    private void setWeatherData(RemoteViews weatherViews, boolean smallWidget, WeatherInfo w) {
        if (mContext == null) {
            mContext = getApplicationContext();
        }

        int color = Preferences.weatherFontColor(mContext);
        int timestampColor = Preferences.weatherTimestampFontColor(mContext);
        boolean ampm = Preferences.showAmPmIndicator(mContext);
        String iconsSet = Preferences.getWeatherIconSet(mContext);

        // Reset no weather visibility
        weatherViews.setViewVisibility(R.id.weather_no_data, View.GONE);
        weatherViews.setViewVisibility(R.id.weather_refresh, View.GONE);

        // Weather Image
        int resId = w.getConditionResource(iconsSet);
        weatherViews.setViewVisibility(R.id.weather_image, View.VISIBLE);
        if (resId != 0) {
            weatherViews.setImageViewResource(R.id.weather_image, w.getConditionResource(iconsSet));
        } else {
            weatherViews.setImageViewBitmap(R.id.weather_image, w.getConditionBitmap(iconsSet, color));
        }

        // Weather Condition
        weatherViews.setTextViewText(R.id.weather_condition, w.getCondition());
        weatherViews.setViewVisibility(R.id.weather_condition, View.VISIBLE);
        weatherViews.setTextColor(R.id.weather_condition, color);

        // Weather Temps Panel
        weatherViews.setTextViewText(R.id.weather_temp, w.getFormattedTemperature());
        weatherViews.setViewVisibility(R.id.weather_temps_panel, View.VISIBLE);
        weatherViews.setTextColor(R.id.weather_temp, color);

        if (!smallWidget) {
            // Display the full weather information panel items
            // Load the preferences
            boolean showLocation = Preferences.showWeatherLocation(mContext);
            boolean showTimestamp = Preferences.showWeatherTimestamp(mContext);

            // City
            weatherViews.setTextViewText(R.id.weather_city, w.getCity());
            weatherViews.setViewVisibility(R.id.weather_city, showLocation ? View.VISIBLE : View.GONE);
            weatherViews.setTextColor(R.id.weather_city, color);

            // Weather Update Time
            if (showTimestamp) {
                Date updateTime = w.getTimestamp();
                StringBuilder sb = new StringBuilder();
                sb.append(DateFormat.format("E", updateTime));
                sb.append(" ");
                sb.append(DateFormat.getTimeFormat(mContext).format(updateTime));
                if (Debug.doDebug(mContext)) Log.i(TAG, "Timestamp: " + sb.toString());
                weatherViews.setTextViewText(R.id.update_time, sb.toString());
                weatherViews.setViewVisibility(R.id.update_time, View.VISIBLE);
                weatherViews.setTextColor(R.id.update_time, timestampColor);
            } else {
                weatherViews.setViewVisibility(R.id.update_time, View.GONE);
            }

            // Weather Temps Panel additional items
            boolean invertLowhigh = Preferences.invertLowHighTemperature(mContext);
            final String low = w.getFormattedLow();
            final String high = w.getFormattedHigh();

            weatherViews.setTextViewText(R.id.weather_high, invertLowhigh ? high : low);
            weatherViews.setTextColor(R.id.weather_high, color);

            weatherViews.setTextViewText(R.id.weather_low, invertLowhigh ? low : high);
            weatherViews.setTextColor(R.id.weather_low, color);

            String direction = w.getWindDirection();
            String speed = w.getFormattedWindSpeed();
            weatherViews.setTextViewText(R.id.weather_windspeed, direction + ", " + speed);
            weatherViews.setTextColor(R.id.weather_windspeed, color);

            weatherViews.setTextViewText(R.id.weather_humidity, w.getFormattedHumidity());
            weatherViews.setTextColor(R.id.weather_humidity, color);

            String UV;
            if (w.getSunUV() < 0) {
                UV = "NA";
            } else {
                UV = String.valueOf(w.getSunUV());
            }
            weatherViews.setTextViewText(R.id.weather_uv, UV);
            weatherViews.setTextColor(R.id.weather_uv, color);

            weatherViews.setTextViewText(R.id.weather_pressure, w.getFormattedPressure());
            weatherViews.setTextColor(R.id.weather_pressure, color);

            weatherViews.setViewVisibility(R.id.weather_sun_set_rise, View.VISIBLE);

            String sunrise = formattedDate(mContext, w.getSunrise(), ampm);
            weatherViews.setTextViewText(R.id.weather_sunrise, sunrise);
            weatherViews.setTextColor(R.id.weather_sunrise, color);

            String sunset = formattedDate(mContext, w.getSunset(), ampm);
            weatherViews.setTextViewText(R.id.weather_sunset, sunset);
            weatherViews.setTextColor(R.id.weather_sunset, color);
        }

        // Register an onClickListener on Weather
        setWeatherClickListener(weatherViews, false);
    }

    /**
     * There is no data to display, display 'empty' fields and the 'Tap to reload' message
     */
    private void setNoWeatherData(RemoteViews weatherViews, boolean smallWidget) {
        if (mContext == null) {
            mContext = getApplicationContext();
        }

        if (mContext == null) throw new NullPointerException("Context == null");

        int color = Preferences.weatherFontColor(mContext);
        boolean firstRun = Preferences.isFirstWeatherUpdate(mContext);

        // Hide the normal weather stuff
        WeatherProvider weatherProvider = Preferences.weatherProvider(mContext);
        int providerNameResource;
        String noData;
        if (weatherProvider != null) {
            providerNameResource = weatherProvider.getNameResourceId();
            noData = getString(R.string.weather_cannot_reach_provider, getString(providerNameResource));
        } else {
            noData = getString(
                    R.string.weather_cannot_reach_provider,
                    Preferences.weatherProviderString(mContext));
        }

        weatherViews.setViewVisibility(R.id.weather_image, View.INVISIBLE);
        if (!smallWidget) {
            weatherViews.setViewVisibility(R.id.weather_city, View.GONE);
            weatherViews.setViewVisibility(R.id.update_time, View.GONE);
            weatherViews.setViewVisibility(R.id.weather_temps_panel, View.GONE);
            weatherViews.setViewVisibility(R.id.weather_condition, View.GONE);
            weatherViews.setViewVisibility(R.id.weather_sun_set_rise, View.GONE);

            // Set up the no data and refresh indicators
            weatherViews.setTextViewText(R.id.weather_no_data, noData);
            weatherViews.setTextViewText(R.id.weather_refresh, getString(R.string.weather_tap_to_refresh));
            weatherViews.setTextColor(R.id.weather_no_data, color);
            weatherViews.setTextColor(R.id.weather_refresh, color);

            // For a better OOBE, dont show the no_data message if mContext is the first run
            weatherViews.setViewVisibility(R.id.weather_no_data, firstRun ? View.GONE : View.VISIBLE);
            weatherViews.setViewVisibility(R.id.weather_refresh,  firstRun ? View.GONE : View.VISIBLE);
        } else {
            weatherViews.setTextViewText(R.id.weather_temp, firstRun ? null : noData);
            weatherViews.setTextViewText(R.id.weather_condition, firstRun ? null : getString(R.string.weather_tap_to_refresh));
            weatherViews.setTextColor(R.id.weather_temp, color);
            weatherViews.setTextColor(R.id.weather_condition, color);
        }

        // Register an onClickListener on Weather with the default (Refresh) action
        if (!firstRun) {
            setWeatherClickListener(weatherViews, true);
        }
    }

    private void setWeatherClickListener(RemoteViews weatherViews, boolean forceRefresh) {
        if (mContext == null) {
            mContext = getApplicationContext();
        }

        // Register an onClickListener on the Weather panel, default action is show forecast
        PendingIntent pi = null;
        if (forceRefresh) {
            pi = WeatherUpdateService.getUpdateIntent(mContext, true);
        }

        if (pi == null) {
            Intent i = new Intent(mContext, ClockWidgetPlusProvider.class);
            i.setAction(Constants.ACTION_SHOW_FORECAST);
            pi = PendingIntent.getBroadcast(mContext, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
        }
        weatherViews.setOnClickPendingIntent(R.id.weather_panel_plus, pi);
    }


    //===============================================================================================
    // Calendar related functionality
    //===============================================================================================
    private void refreshCalendar(RemoteViews calendarViews, int widgetId) {
        if (mContext == null) {
            mContext = getApplicationContext();
        }

        final Resources res = getResources();
        // Calendar icon: Overlay the selected color and set the imageview
        int color = Preferences.calendarFontColor(mContext);
        int bg_color = Preferences.calendarBackgroundColor(this);

        calendarViews.setInt(R.id.calendar_panel_extra, "setBackgroundColor", bg_color);

        // Hide the separator if preference set
        if (Preferences.showCalendarSeparator(mContext)) {
            calendarViews.setViewVisibility(R.id.calendar_separator_top, View.VISIBLE);
            calendarViews.setViewVisibility(R.id.calendar_separator_bottom, View.VISIBLE);
            calendarViews.setInt(R.id.calendar_separator_top, "setBackgroundColor", color);
            calendarViews.setInt(R.id.calendar_separator_bottom, "setBackgroundColor", color);
        } else {
            calendarViews.setViewVisibility(R.id.calendar_separator_top, View.GONE);
            calendarViews.setViewVisibility(R.id.calendar_separator_bottom, View.GONE);
        }

        // Hide the icon if preference set
        if (Preferences.showCalendarIcon(mContext)) {
            calendarViews.setImageViewBitmap(R.id.calendar_icon,
                    IconUtils.getOverlaidBitmap(mContext, res, R.drawable.ic_lock_idle_calendar, color));
        } else {
            calendarViews.setImageViewBitmap(R.id.calendar_icon, null);
        }

        // Set up and start the Calendar RemoteViews service
        final Intent remoteAdapterIntent = new Intent(mContext, CalendarViewsService.class);
        remoteAdapterIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        remoteAdapterIntent.setData(Uri.parse(remoteAdapterIntent.toUri(Intent.URI_INTENT_SCHEME)));
        calendarViews.setRemoteAdapter(R.id.calendar_list, remoteAdapterIntent);
        calendarViews.setEmptyView(R.id.calendar_list, R.id.calendar_empty_view);

        // Register an onClickListener on Calendar starting the Calendar app
        final Intent calendarClickIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR);
        final PendingIntent calendarClickPendingIntent = PendingIntent.getActivity(mContext, 0, calendarClickIntent,PendingIntent.FLAG_UPDATE_CURRENT);
        calendarViews.setOnClickPendingIntent(R.id.calendar_icon, calendarClickPendingIntent);

        final Intent eventClickIntent = new Intent(Intent.ACTION_VIEW);
        final PendingIntent eventClickPendingIntent = PendingIntent.getActivity(mContext, 0, eventClickIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        calendarViews.setPendingIntentTemplate(R.id.calendar_list, eventClickPendingIntent);
    }

    public static PendingIntent getRefreshIntent(Context context) {
        Intent i = new Intent(context, ClockWidgetPlusService.class);
        i.setAction(ClockWidgetPlusService.ACTION_REFRESH_CALENDAR);
        return PendingIntent.getService(context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public static void cancelUpdates(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(getRefreshIntent(context));
    }
}
