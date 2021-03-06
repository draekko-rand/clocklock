/*
 * Portions (C) 2012-2015 The CyanogenMod Project (DvTonder)
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

import android.appwidget.AppWidgetProvider;

import android.content.Intent;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.util.Log;

import com.draekko.clocklock.misc.Constants;
import com.draekko.clocklock.misc.Debug;
import com.draekko.clocklock.misc.WidgetUtils;
import com.draekko.clocklock.weather.ForecastActivity;
import com.draekko.clocklock.weather.WeatherUpdateService;

public class ClockWidgetPlusProvider extends AppWidgetProvider {
    private static final String TAG = "ClockWidgetPlusProvider";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // Default handling, triggered via the super class
        if (Debug.doDebug(context)) Log.v(TAG, "Updating widgets, default handling.");
        updateWidgets(context, false, false);
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        // Deal with received broadcasts that force a refresh
        String action = intent.getAction();
        if (Debug.doDebug(context)) Log.v(TAG, "Received intent " + intent);

        // Network connection has changed, make sure the weather update service knows about it
        if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
            boolean hasConnection =
                    !intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);

            if (Debug.doDebug(context)) Log.d(TAG, "Got connectivity change, has connection: " + hasConnection);

            Intent i = new Intent(context, WeatherUpdateService.class);
            if (hasConnection) {
                context.startService(i);
            } else {
                context.stopService(i);
            }

            // Boot completed, schedule next weather update
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            // On first boot lastUpdate will be 0 thus no need to force an update
            // Subsequent boots will use cached data
            WeatherUpdateService.scheduleNextUpdate(context, false);

            // A widget has been deleted, prevent our handling and ask the super class handle it
        } else if (AppWidgetManager.ACTION_APPWIDGET_DELETED.equals(action)
                || AppWidgetManager.ACTION_APPWIDGET_DISABLED.equals(action)) {
            super.onReceive(context, intent);

            // Calendar, Time or a settings change, force a calendar refresh
        } else if (Intent.ACTION_PROVIDER_CHANGED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_DATE_CHANGED.equals(action)
                || Intent.ACTION_LOCALE_CHANGED.equals(action)
                || "android.app.action.NEXT_ALARM_CLOCK_CHANGED".equals(action)
                || ClockWidgetPlusService.ACTION_REFRESH_CALENDAR.equals(action)) {
            updateWidgets(context, true, false);

            // There are no events to show in the Calendar panel, hide it explicitly
        } else if (ClockWidgetPlusService.ACTION_HIDE_CALENDAR.equals(action)) {
            updateWidgets(context, false, true);

            // The intent is to launch the modal pop-up forecast dialog
        } else if (Constants.ACTION_SHOW_FORECAST.equals(action)) {
            Intent i = new Intent(context, ForecastActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);

            // Something we did not handle, let the super class deal with it.
            // This includes the REFRESH_CLOCK intent from Clock settings
        } else {
            if (Debug.doDebug(context)) Log.v(TAG, "We did not handle the intent, trigger normal handling");
            super.onReceive(context, intent);
            updateWidgets(context, false, false);
        }
    }

    /**
     *  Update the widget via the service.
     */
    private void updateWidgets(Context context, boolean refreshCalendar, boolean hideCalendar) {
        // Build the intent and pass on the weather and calendar refresh triggers
        Intent i = new Intent(context.getApplicationContext(), ClockWidgetPlusService.class);
        if (refreshCalendar) {
            i.setAction(ClockWidgetPlusService.ACTION_REFRESH_CALENDAR);
        } else if (hideCalendar) {
            i.setAction(ClockWidgetPlusService.ACTION_HIDE_CALENDAR);
        } else {
            i.setAction(ClockWidgetPlusService.ACTION_REFRESH);
        }

        // Start the service. The service itself will take care of scheduling refreshes if needed
        if (Debug.doDebug(context)) Log.d(TAG, "Starting the service to update the widgets...");
        context.startService(i);
    }

    @Override
    public void onEnabled(Context context) {
        if (Debug.doDebug(context)) Log.d(TAG, "Scheduling next weather update");
        WeatherUpdateService.scheduleNextUpdate(context, true);

        // Start the broadcast receiver (API 16 devices)
        // This will schedule a repeating alarm every minute to handle the clock refresh
        // Once triggered, the receiver will unregister itself when its work is done
        if (!WidgetUtils.isTextClockAvailable()) {
            final WidgetApplication app = (WidgetApplication) context.getApplicationContext();
            app.startTickReceiver();
        }
    }

    @Override
    public void onDisabled(Context context) {
        if (Debug.doDebug(context)) Log.d(TAG, "Cleaning up: Clearing all pending alarms");
        ClockWidgetPlusService.cancelUpdates(context);
        WeatherUpdateService.cancelUpdates(context);

        // Stop the clock update event (API 16 devices)
        if (!WidgetUtils.isTextClockAvailable()) {
            WidgetApplication.cancelClockRefresh(context);
        }
    }
}
