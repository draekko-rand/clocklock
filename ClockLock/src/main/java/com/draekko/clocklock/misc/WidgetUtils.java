/*
 * Copyright (C) 2012 The Android Open Source Project
 * Portions Copyright (C) 2012 The CyanogenMod Project
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

import android.Manifest;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.TypedValue;

import com.draekko.clocklock.R;
import com.draekko.clocklock.weather.WeatherUpdateService;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import java.util.Calendar;

import static com.draekko.clocklock.misc.Constants.OUTDATED_LOCATION_THRESHOLD_MILLIS;

public class WidgetUtils {
    //===============================================================================================
    // Widget display and resizing related functionality
    //===============================================================================================

    private static final String TAG = "WidgetUtils";

    /**
     *  Decide whether to show the small Weather panel
     */
    public static boolean showSmallWidget(Context context, int id, boolean digitalClock, boolean isKeyguard) {
        Bundle options = AppWidgetManager.getInstance(context).getAppWidgetOptions(id);
        if (options == null) {
            // no data to make the calculation, show the list anyway
            return false;
        }
        Resources resources = context.getResources();
        int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
        int minHeightPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, minHeight,
                resources.getDisplayMetrics());
        int neededFullSize = 0;
        if (isKeyguard) {
            neededFullSize = (int) resources.getDimension(
                    digitalClock ? R.dimen.min_digital_weather_height_lock
                                 : R.dimen.min_analog_weather_height_lock);
        } else {
            neededFullSize = (int) resources.getDimension(
                    digitalClock ? R.dimen.min_digital_weather_height
                                 : R.dimen.min_analog_weather_height);
        }
        int neededSmallSize = (int) resources.getDimension(R.dimen.min_digital_widget_height);

        // Check to see if the widget size is big enough, if it is return true.
        Boolean result = minHeightPx < neededFullSize && minHeightPx > neededSmallSize;
        if (Debug.doDebug(context)) {
            Log.d(TAG, "showSmallWidget: digital clock = " + digitalClock + " with minHeightPx = " + minHeightPx
                    + " and neededFullSize = " + neededFullSize + " and neededSmallSize = " + neededSmallSize);
            Log.d(TAG, "showsmallWidget result = " + result);
        }
        return result;
    }

    /**
     * Decide whether to show the timestamp
     */
    public static boolean canFitTimestamp(Context context, int id, boolean digitalClock) {
        Bundle options = AppWidgetManager.getInstance(context).getAppWidgetOptions(id);
        if (options == null) {
            // no data to make the calculation, show the list anyway
            return true;
        }
        Resources resources = context.getResources();
        int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
        int minHeightPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, minHeight,
                resources.getDisplayMetrics());
        int dimens = digitalClock
                ? R.dimen.min_digital_timestamp_height : R.dimen.min_analog_timestamp_height;
        int neededSize = (int) resources.getDimension(dimens);
        if (digitalClock) {
            //neededSize /= 1.5;
        }

        // Check to see if the widget size is big enough, if it is return true.
        Boolean result = minHeightPx > neededSize;
        if (Debug.doDebug(context)) {
            Log.d(TAG, "canFitTimestamp: digital clock = " + digitalClock
                    + " with minHeightPx = " + minHeightPx + "  and neededSize = " + neededSize);
            Log.d(TAG, "canFitTimestamp result = " + result);
        }
        return result;
    }

    /**
     *  Decide whether to show the full Weather panel
     */
    public static boolean canFitWeather(Context context, int id, boolean digitalClock, boolean isKeyguard) {
        Bundle options = AppWidgetManager.getInstance(context).getAppWidgetOptions(id);
        if (options == null) {
            // no data to make the calculation, show the list anyway
            return true;
        }
        Resources resources = context.getResources();
        int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
        int minHeightPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, minHeight,
                resources.getDisplayMetrics());
        int neededSize = 0;
        if (isKeyguard) {
            neededSize = (int) resources.getDimension(
                    digitalClock ? R.dimen.min_digital_weather_height_lock
                                 : R.dimen.min_analog_weather_height_lock);
        } else {
            neededSize = (int) resources.getDimension(
                    digitalClock ? R.dimen.min_digital_weather_height
                                 : R.dimen.min_analog_weather_height);
        }

        // Check to see if the widget size is big enough, if it is return true.
        Boolean result = minHeightPx > neededSize;
        if (Debug.doDebug(context)) {
            Log.d(TAG, "canFitWeather: digital clock = " + digitalClock + " with minHeightPx = "
                    + minHeightPx + "  and neededSize = " + neededSize);
            Log.d(TAG, "canFitWeather result = " + result);
        }
        return result;
    }

    /**
     *  Decide whether to show the Calendar panel
     */
    public static boolean canFitCalendar(Context context, int id, boolean digitalClock) {
        Bundle options = AppWidgetManager.getInstance(context).getAppWidgetOptions(id);
        if (options == null) {
            // no data to make the calculation, show the list anyway
            return true;
        }
        Resources resources = context.getResources();
        int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
        int minHeightPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, minHeight,
                resources.getDisplayMetrics());
        int dimens = digitalClock ?
                R.dimen.min_digital_calendar_height :
                R.dimen.min_analog_calendar_height;
        int neededSize = (int) resources.getDimension(dimens);
        if (digitalClock) {
            //neededSize /= 1.5;
        }

        // Check to see if the widget size is big enough, if it is return true.
        Boolean result = minHeightPx > neededSize;
        if (Debug.doDebug(context)) {
            Log.d(TAG, "canFitCalendar: digital clock = " + digitalClock + " with minHeightPx = "
                    + minHeightPx + "  and neededSize = " + neededSize);
            Log.d(TAG, "canFitCalendar result = " + result);
        }
        return result;
    }

    /**
     *  Calculate the scale factor of the fonts in the widget
     */
    public static float getScaleRatio(Context context, int id) {
        Bundle options = AppWidgetManager.getInstance(context).getAppWidgetOptions(id);
        if (options != null) {
            int minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
            if (minWidth == 0) {
                // No data , do no scaling
                return 1f;
            }
            Resources res = context.getResources();
            float ratio = minWidth / res.getDimension(R.dimen.def_digital_widget_width);
            return (ratio > 1) ? 1f : ratio;
        }
        return 1f;
    }

    /**
     *  The following two methods return the default DeskClock intent depending on which
     *  clock package is installed
     *
     *  Copyright 2013 Google Inc.
     */
    private static final String[] CLOCK_PACKAGES = new String[] {
        "com.google.android.deskclock",
        "com.android.deskclock",
    };

    public static Intent getDefaultClockIntent(Context context) {
        PackageManager pm = context.getPackageManager();
        for (String packageName : CLOCK_PACKAGES) {
            try {
                pm.getPackageInfo(packageName, 0);
                return pm.getLaunchIntentForPackage(packageName);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        return null;
    }

    public static Intent getDefaultAlarmsIntent(Context context) {
        PackageManager pm = context.getPackageManager();
        for (String packageName : CLOCK_PACKAGES) {
            try {
                ComponentName cn = new ComponentName(packageName,
                        "com.android.deskclock.AlarmClock");
                pm.getActivityInfo(cn, 0);
                return Intent.makeMainActivity(cn);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        return getDefaultClockIntent(context);
    }

    /**
     *  API level check to see if the new API 17 TextClock is available
     */
    public static boolean isTextClockAvailable() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1;
    }

    /**
     *  API level check to see if the new API 19 transparencies are available
     */
    public static boolean isTranslucencyAvailable() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
    }

    /**
     *  Networking available check
     */
    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        if (info == null || !info.isConnected() || !info.isAvailable()) {
            if (Debug.doDebug(context)) {
                Log.d(TAG, "No network connection is available for weather update");
            }

            return false;
        }
        return true;
    }

    public static String formattedDate(Context context, long milliseconds, boolean ampm)  {
        Calendar today = Calendar.getInstance();
        today.setTimeInMillis(milliseconds * 1000);
        String time;
        int minute = today.get(Calendar.MINUTE);

        if (DateFormat.is24HourFormat(context)) {
            int hour_of_day = today.get(Calendar.HOUR_OF_DAY);
            time = hour_of_day + ":";
            if (minute < 10) {
                time += "0";
            }
            time += minute;
        } else {
            String am_pm = "";
            if (ampm) {
                int value = today.get(Calendar.AM_PM);
                am_pm = " ";
                if (value == 1) {
                    am_pm += "PM";
                } else {
                    am_pm += "AM";
                }
            }
            int hour = today.get(Calendar.HOUR);
            if (hour == 0) {
                hour = 12;
            }
            time = hour + ":";
            if (minute < 10) {
                time += "0";
            }
            time += minute + am_pm;
        }

        return time;
    }

    public static Location getCurrentLocation(Context context, LocationManager lm, Location location) {

        Criteria sLocationCriteria;

        sLocationCriteria = new Criteria();
        sLocationCriteria.setPowerRequirement(Criteria.POWER_LOW);
        sLocationCriteria.setAccuracy(Criteria.ACCURACY_COARSE);
        sLocationCriteria.setCostAllowed(false);

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        location = null;
        lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        location = lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);

        if (location == null)
            location = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

        if (location == null)
            location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);

        // If lastKnownLocation is not present (because none of the apps in the
        // device has requested the current location to the system yet) or outdated,
        // then try to get the current location use the provider that best matches the criteria.
        boolean needsUpdate = location == null;
        if (location != null) {
            long delta = System.currentTimeMillis() - location.getTime();
            needsUpdate = delta > OUTDATED_LOCATION_THRESHOLD_MILLIS;
        }
        if (needsUpdate) {
            if (Debug.doDebug(context)) Log.d(TAG, "Getting best location provider");
            String locationProvider = lm.getBestProvider(sLocationCriteria, true);
            if (TextUtils.isEmpty(locationProvider)) {
                Log.e(TAG, "No available location providers matching criteria.");
            } else if (isGooglePlayServicesAvailable(context)
                    && locationProvider.equals(LocationManager.GPS_PROVIDER)) {
                // Since Google Play services is available,
                // let's conserve battery power and not depend on the device's GPS.
                Log.i(TAG, "Google Play Services available; Ignoring GPS provider.");
            } else {
                WeatherUpdateService.WeatherLocationListener.registerIfNeeded(context, locationProvider);
            }
        }

        if (location != null)
            if (Debug.doDebug(context)) Log.v(TAG, "Current location is " + location);

        return location;
    }

    public static boolean isGooglePlayServicesAvailable(Context context) {
        int result = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context);
        return result == ConnectionResult.SUCCESS
                || result == ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED;
    }
}
