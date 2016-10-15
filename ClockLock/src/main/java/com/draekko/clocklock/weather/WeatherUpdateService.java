/*
 * Copyright (C) 2012 The CyanogenMod Project
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

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.text.TextUtils;
import android.util.Log;

import com.draekko.clocklock.ClockWidgetPlusProvider;
import com.draekko.clocklock.ClockWidgetProvider;
import com.draekko.clocklock.misc.Constants;
import com.draekko.clocklock.misc.Debug;
import com.draekko.clocklock.misc.Preferences;
import com.draekko.clocklock.misc.WidgetUtils;
import com.draekko.clocklock.preference.WeatherPreferences;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.GooglePlayServicesUtil;

import java.util.Date;

import static com.draekko.clocklock.misc.Constants.LOCATION_REQUEST_TIMEOUT;

public class WeatherUpdateService extends Service {
    private static final String TAG = "WeatherUpdateService";

    public static final String ACTION_FORCE_UPDATE = "com.draekko.clocklock.action.FORCE_WEATHER_UPDATE";
    private static final String ACTION_CANCEL_LOCATION_UPDATE =
            "com.draekko.clocklock.action.CANCEL_LOCATION_UPDATE";

    // Broadcast action for end of update
    public static final String ACTION_UPDATE_FINISHED = "com.draekko.clocklock.action.WEATHER_UPDATE_FINISHED";
    public static final String EXTRA_UPDATE_CANCELLED = "update_cancelled";

    private WeatherUpdateTask mTask;
    private static Context mContext;

    LocationManager lm = null;
    Location location = null;

    private static final Criteria sLocationCriteria;

    static {
        sLocationCriteria = new Criteria();
        sLocationCriteria.setPowerRequirement(Criteria.POWER_LOW);
        sLocationCriteria.setAccuracy(Criteria.ACCURACY_COARSE);
        sLocationCriteria.setCostAllowed(false);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        mContext = getApplicationContext();

        if (Debug.doDebug(mContext)) Log.v(TAG, "Got intent " + intent);

        boolean active = mTask != null && mTask.getStatus() != AsyncTask.Status.FINISHED;

        if (ACTION_CANCEL_LOCATION_UPDATE.equals(intent.getAction())) {
            WeatherLocationListener.cancel(this);
            if (!active) {
                stopSelf();
            }
            return START_NOT_STICKY;
        }

        if (active) {
            if (Debug.doDebug(mContext)) Log.v(TAG, "Weather update is still active, not starting new update");
            return START_REDELIVER_INTENT;
        }

        boolean force = ACTION_FORCE_UPDATE.equals(intent.getAction());
        if (!shouldUpdate(force)) {
            Log.d(TAG, "Service started, but shouldn't update ... stopping");
            stopSelf();
            sendCancelledBroadcast();
            return START_NOT_STICKY;
        }

        mTask = new WeatherUpdateTask();
        mTask.execute();

        return START_REDELIVER_INTENT;
    }

    private void sendCancelledBroadcast() {
        Intent finishedIntent = new Intent(ACTION_UPDATE_FINISHED);
        finishedIntent.putExtra(EXTRA_UPDATE_CANCELLED, true);
        sendBroadcast(finishedIntent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (mTask != null && mTask.getStatus() != AsyncTask.Status.FINISHED) {
            mTask.cancel(true);
            mTask = null;
        }
    }

    private boolean shouldUpdate(boolean force) {
        if (mContext == null) {
            mContext = getApplicationContext();
        }
        long interval = Preferences.weatherRefreshIntervalInMs(this);
        if (interval == 0 && !force) {
            if (Debug.doDebug(mContext)) Log.v(TAG, "Interval set to manual and update not forced, skip update");
            return false;
        }

        if (!WeatherPreferences.hasLocationPermission(this)) {
            if (Debug.doDebug(mContext)) Log.v(TAG, "Application does not have the location permission");
            return false;
        }

        if (force) {
            Preferences.setCachedWeatherInfo(this, 0, null);
        }

        long now = System.currentTimeMillis();
        long lastUpdate = Preferences.lastWeatherUpdateTimestamp(this);
        long due = lastUpdate + interval;

        if (Debug.doDebug(mContext)) Log.d(TAG, "Now " + now + " due " + due + "(" + new Date(due) + ")");

        if (lastUpdate != 0 && now < due) {
            if (Debug.doDebug(mContext)) Log.v(TAG, "Weather update is not due yet");
            return false;
        }

        return WidgetUtils.isNetworkAvailable(this);
    }

    private class WeatherUpdateTask extends AsyncTask<Void, Void, WeatherInfo> {
        private WakeLock mWakeLock;
        private Context mContext;

        public WeatherUpdateTask() {
            if (mContext == null) {
                mContext = getApplicationContext();
            }
            if (Debug.doDebug(mContext)) Log.d(TAG, "Starting weather update task");
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
            mWakeLock.setReferenceCounted(false);
            mContext = WeatherUpdateService.this;
        }

        @Override
        protected void onPreExecute() {
            if (mContext == null) {
                mContext = getApplicationContext();
            }
            if (Debug.doDebug(mContext)) Log.d(TAG, "ACQUIRING WAKELOCK");
            mWakeLock.acquire();
        }

        @Override
        protected WeatherInfo doInBackground(Void... params) {
            if (mContext == null) {
                mContext = getApplicationContext();
            }
            WeatherProvider provider = Preferences.weatherProvider(mContext);
            if (provider == null) {
                return null;
            }

            WeatherProvider.LocationResult locationResult = null;
            boolean metric = Preferences.useMetricUnits(mContext);
            String customLocationId = null, customLocationName = null, customLocationState = null, customLocationCountry = null;

            if (Preferences.useCustomWeatherLocation(mContext)) {
                locationResult = new WeatherProvider.LocationResult();
                locationResult.id = Preferences.customWeatherLocationId(mContext);
                locationResult.city = Preferences.customWeatherLocationCity(mContext);
                locationResult.state = Preferences.customWeatherLocationState(mContext);
                locationResult.countryName = Preferences.customWeatherLocationCountry(mContext);
            }

            if (provider != null && customLocationId != null && locationResult != null) {
                return provider.getWeatherInfo(customLocationId, locationResult, metric);
            }

            location = WidgetUtils.getCurrentLocation(mContext, lm, location);
            if (location != null) {
                WeatherInfo info = provider.getWeatherInfo(location, metric);
                if (info != null) {
                    return info;
                }
            }

            // work with cached location from last request for now
            // a listener to update it is already scheduled if possible
            WeatherInfo cachedInfo = Preferences.getCachedWeatherInfo(mContext);
            if (cachedInfo != null) {
                WeatherProvider.LocationResult localResults = new WeatherProvider.LocationResult();
                localResults.id = cachedInfo.getId();
                localResults.city = cachedInfo.getId();
                localResults.state = cachedInfo.getState();
                localResults.countryName = cachedInfo.getCountry();
                return provider.getWeatherInfo(cachedInfo.getId(), localResults, metric);
            }

            return null;
        }

        @Override
        protected void onPostExecute(WeatherInfo result) {
            finish(result);
        }

        @Override
        protected void onCancelled() {
            finish(null);
        }

        private void finish(WeatherInfo result) {
            if (mContext == null) {
                mContext = getApplicationContext();
            }
            if (result != null) {
                if (Debug.doDebug(mContext)) Log.d(TAG, "Weather update received, caching data and updating widget");
                long now = System.currentTimeMillis();
                Preferences.setCachedWeatherInfo(mContext, now, result);
                scheduleUpdate(mContext, Preferences.weatherRefreshIntervalInMs(mContext), false);

                Intent updateIntent = new Intent(mContext, ClockWidgetProvider.class);
                sendBroadcast(updateIntent);

                Intent updatePlusIntent = new Intent(mContext, ClockWidgetPlusProvider.class);
                sendBroadcast(updatePlusIntent);
            } else if (isCancelled()) {
                // cancelled, likely due to lost network - we'll get restarted
                // when network comes back
            } else {
                // failure, schedule next download in 30 minutes
                if (Debug.doDebug(mContext)) Log.d(TAG, "Weather refresh failed, scheduling update in 30 minutes");
                long interval = 30 * 60 * 1000;
                scheduleUpdate(mContext, interval, false);
            }
            WeatherContentProvider.updateCachedWeatherInfo(mContext, result);

            Intent finishedIntent = new Intent(ACTION_UPDATE_FINISHED);
            finishedIntent.putExtra(EXTRA_UPDATE_CANCELLED, result == null);
            sendBroadcast(finishedIntent);

            if (Debug.doDebug(mContext)) Log.d(TAG, "RELEASING WAKELOCK");
            mWakeLock.release();
            stopSelf();
        }
    }

    public static class WeatherLocationListener implements LocationListener {
        private Context mContext;
        private PendingIntent mTimeoutIntent;
        private static WeatherLocationListener sInstance = null;

        public static void registerIfNeeded(Context context, String provider) {
            synchronized (WeatherLocationListener.class) {
                if (Debug.doDebug(context)) Log.d(TAG, "Registering location listener");
                if (sInstance == null) {
                    final Context appContext = context.getApplicationContext();
                    final LocationManager locationManager =
                            (LocationManager) appContext.getSystemService(Context.LOCATION_SERVICE);

                    // Check location provider after set sInstance, so, if the provider is not
                    // supported, we never enter here again.
                    sInstance = new WeatherLocationListener(appContext);
                    // Check whether the provider is supported.
                    // NOTE!!! Actually only WeatherUpdateService class is calling this function
                    // with the NETWORK_PROVIDER, so setting the instance is safe. We must
                    // change this if this call receive different providers

                    LocationProvider lp = locationManager.getProvider(provider);
                    if (lp != null) {
                        if (Debug.doDebug(context)) Log.d(TAG, "LocationManager - Requesting single update");
                        if (ActivityCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                            ActivityCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                            // do something there
                            locationManager.requestSingleUpdate(provider, sInstance, appContext.getMainLooper());
                        }
                        sInstance.setTimeoutAlarm();
                    }
                }
            }
        }

        static void cancel(Context context) {
            synchronized (WeatherLocationListener.class) {
                if (sInstance != null) {
                    final Context appContext = context.getApplicationContext();
                    final LocationManager locationManager =
                        (LocationManager) appContext.getSystemService(Context.LOCATION_SERVICE);
                    if (Debug.doDebug(context)) Log.d(TAG, "Aborting location request after timeout");
                    if (ActivityCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                        ActivityCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        locationManager.removeUpdates(sInstance);
                    }
                    sInstance.cancelTimeoutAlarm();
                    sInstance = null;
                }
            }
        }

        private WeatherLocationListener(Context context) {
            super();
            mContext = context;
        }

        private void setTimeoutAlarm() {
            Intent intent = new Intent(mContext, WeatherUpdateService.class);
            intent.setAction(ACTION_CANCEL_LOCATION_UPDATE);

            mTimeoutIntent = PendingIntent.getService(mContext, 0, intent,
                    PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_CANCEL_CURRENT);

            AlarmManager am = (AlarmManager) mContext.getSystemService(ALARM_SERVICE);
            long elapseTime = SystemClock.elapsedRealtime() + LOCATION_REQUEST_TIMEOUT / 10;
            am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, elapseTime, mTimeoutIntent);
        }

        private void cancelTimeoutAlarm() {
            if (mTimeoutIntent != null) {
                AlarmManager am = (AlarmManager) mContext.getSystemService(ALARM_SERVICE);
                am.cancel(mTimeoutIntent);
                mTimeoutIntent = null;
            }
        }

        @Override
        public void onLocationChanged(Location location) {
            // Now, we have a location to use. Schedule a weather update right now.
            if (Debug.doDebug(mContext)) Log.d(TAG, "The location has changed, schedule an update ");
            synchronized (WeatherLocationListener.class) {
                WeatherUpdateService.scheduleUpdate(mContext, 0, true);
                cancelTimeoutAlarm();
                sInstance = null;
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            // Now, we have a location to use. Schedule a weather update right now.
            if (Debug.doDebug(mContext)) Log.d(TAG, "The location service has become available, schedule an update ");
            if (status == LocationProvider.AVAILABLE) {
                synchronized (WeatherLocationListener.class) {
                    WeatherUpdateService.scheduleUpdate(mContext, 0, true);
                    cancelTimeoutAlarm();
                    sInstance = null;
                }
            }
        }

        @Override
        public void onProviderEnabled(String provider) {
            // Not used
        }

        @Override
        public void onProviderDisabled(String provider) {
            // Not used
        }
    }

    public static void scheduleUpdate(Context context, long timeFromNow, boolean force) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        long due = System.currentTimeMillis() + timeFromNow;

        if (Debug.doDebug(context)) Log.d(TAG, "Scheduling next update at " + new Date(due));
        am.set(AlarmManager.RTC_WAKEUP, due, getUpdateIntent(context, force));
    }

    public static void scheduleNextUpdate(Context context, boolean force) {
        long lastUpdate = Preferences.lastWeatherUpdateTimestamp(context);
        if (lastUpdate == 0 || force) {
            scheduleUpdate(context, 0, true);
        } else {
            long interval = Preferences.weatherRefreshIntervalInMs(context);
            scheduleUpdate(context, lastUpdate + interval - System.currentTimeMillis(), false);
        }
    }

    public static PendingIntent getUpdateIntent(Context context, boolean force) {
        Intent i = new Intent(context, WeatherUpdateService.class);
        if (force) {
            i.setAction(ACTION_FORCE_UPDATE);
        }
        return PendingIntent.getService(context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public static void cancelUpdates(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(getUpdateIntent(context, true));
        am.cancel(getUpdateIntent(context, false));
        WeatherLocationListener.cancel(context);
    }
}
