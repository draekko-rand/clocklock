/*
 * Copyright (C) 2013 The CyanogenMod Project (DvTonder)
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

import com.draekko.clocklock.misc.Constants;
import com.draekko.clocklock.misc.Debug;

import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

public class WidgetApplication extends Application {
    private static final String TAG = "WidgetApplication";
    private static final long INTERVAL_ONE_MINUTE = 60000L;

    private BroadcastReceiver mTickReceiver = null;
    private static Context mContext;

    /**
     * BroadReceiver and supporting functions used for handling clock ticks
     * for the TextView clock support (API 16) by scheduling a repeating
     * alarm event every 60 seconds and triggering a refresh of the widget
     */
    public class TickReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            mContext = context;
            String action = intent.getAction();
            if (Intent.ACTION_TIME_TICK.equals(action)){
                if (Debug.doDebug(context)) Log.d(TAG, "Clock tick event received");

                // Schedule the clock refresh alarm event
                scheduleClockRefresh(context);

                // Refresh the widget
                Intent refreshIntent = new Intent(context, ClockWidgetProvider.class);
                refreshIntent.setAction(ClockWidgetService.ACTION_REFRESH);
                context.sendBroadcast(refreshIntent);

                Intent refreshPlusIntent = new Intent(context, ClockWidgetPlusProvider.class);
                refreshPlusIntent.setAction(ClockWidgetPlusService.ACTION_REFRESH);
                context.sendBroadcast(refreshPlusIntent);

                // We no longer need the tick receiver, its done its job, stop it
                stopTickReceiver();
            }
        }
    }

    public void startTickReceiver() {
        if (mContext == null) {
            mContext = getApplicationContext();
        }

        // Clean up first, just in case
        stopTickReceiver();

        // Start the new receiver
        if (Debug.doDebug(mContext)) Log.d(TAG, "Registering clock tick receiver");
        mTickReceiver = new TickReceiver();
        registerReceiver(mTickReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
    }

    public void stopTickReceiver() {
        if (mContext == null) {
            mContext = getApplicationContext();
        }

        if (mTickReceiver != null) {
            if (Debug.doDebug(mContext)) Log.d(TAG, "Cleaning up: Unregistering clock tick receiver");
            unregisterReceiver(mTickReceiver);
            mTickReceiver = null;
        }
    }

    private static void scheduleClockRefresh(Context context) {
        if (Debug.doDebug(context)) Log.d(TAG, "Starting clock refresh alarm");
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        long due = System.currentTimeMillis() + INTERVAL_ONE_MINUTE;
        am.setRepeating(AlarmManager.RTC, due, INTERVAL_ONE_MINUTE, getClockRefreshIntent(context));
    }

    public static void cancelClockRefresh(Context context) {
        if (Debug.doDebug(context)) Log.d(TAG, "Cleaning up: Stopping clock refresh alarm");
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(getClockRefreshIntent(context));
    }

    private static PendingIntent getClockRefreshIntent(Context context) {
        Intent i = new Intent(context, ClockWidgetService.class);
        i.setAction(ClockWidgetService.ACTION_REFRESH);
        return PendingIntent.getService(context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
