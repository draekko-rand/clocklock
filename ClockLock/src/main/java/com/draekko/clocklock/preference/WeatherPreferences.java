/*
 * Copyright (C) 2012 The CyanogenMod Project (DvTonder)
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

package com.draekko.clocklock.preference;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.draekko.clocklock.ClockWidgetPlusProvider;
import com.draekko.clocklock.ClockWidgetProvider;
import com.draekko.clocklock.R;
import com.draekko.clocklock.misc.Constants;
import com.draekko.clocklock.misc.Debug;
import com.draekko.clocklock.misc.Preferences;
import com.draekko.clocklock.weather.WeatherUpdateService;

import com.draekko.traypreferences.TrayEditTextPreference;
import com.draekko.traypreferences.TrayListPreference;
import com.draekko.traypreferences.TrayPreference;
import com.draekko.traypreferences.TrayPreferenceFragment;
import com.draekko.traypreferences.TraySharedPreferences;
import com.draekko.traypreferences.TraySwitchPreference;

public class WeatherPreferences extends TrayPreferenceFragment implements
        TraySharedPreferences.OnSharedPreferenceChangeListener, TrayPreference.OnPreferenceChangeListener {
    private static final String TAG = "WeatherPreferences";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    private static final String[] LOCATION_PREF_KEYS = new String[] {
        Constants.WEATHER_USE_CUSTOM_LOCATION,
        Constants.WEATHER_CUSTOM_LOCATION_CITY
    };
    private static final String[] WEATHER_REFRESH_KEYS = new String[] {
        Constants.SHOW_WEATHER,
        Constants.WEATHER_REFRESH_INTERVAL
    };

    private TraySwitchPreference mUseCustomLoc;
    private TrayEditTextPreference mCustomWeatherLoc;
    private TrayListPreference mFontColor;
    private TrayListPreference mTimestampFontColor;
    private TraySwitchPreference mUseMetric;
    private IconSelectionPreference mIconSet;
    private TraySwitchPreference mUseCustomlocation;
    private TraySwitchPreference mShowWeather;
    private Context mContext;
    private ContentResolver mResolver;
    private Runnable mPostResumeRunnable;
    private TrayEditTextPreference mWeatherApiKey;
    private String mApiKey;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences_weather);
        mContext = getActivity();
        mResolver = mContext.getContentResolver();

        // Load items that need custom summaries etc.
        mUseCustomLoc = (TraySwitchPreference) findPreference(Constants.WEATHER_USE_CUSTOM_LOCATION);
        mCustomWeatherLoc = (TrayEditTextPreference) findPreference(Constants.WEATHER_CUSTOM_LOCATION_CITY);
        mFontColor = (TrayListPreference) findPreference(Constants.WEATHER_FONT_COLOR);
        mTimestampFontColor = (TrayListPreference) findPreference(Constants.WEATHER_TIMESTAMP_FONT_COLOR);
        mIconSet = (IconSelectionPreference) findPreference(Constants.WEATHER_ICONS);
        mUseMetric = (TraySwitchPreference) findPreference(Constants.WEATHER_USE_METRIC);
        mUseCustomlocation = (TraySwitchPreference) findPreference(Constants.WEATHER_USE_CUSTOM_LOCATION);
        mWeatherApiKey = (TrayEditTextPreference) findPreference(Constants.WEATHER_API_KEY);

        mApiKey = Preferences.getApiKey(mContext);
        mWeatherApiKey.setText(mApiKey);

        mShowWeather = (TraySwitchPreference) findPreference(Constants.SHOW_WEATHER);
        mShowWeather.setOnPreferenceChangeListener(this);

        // At first placement/start default the use of Metric units based on locale
        // If we had a previously set value already, this will just reset the same value
        Boolean defValue = Preferences.useMetricUnits(mContext);
        Preferences.setUseMetricUnits(mContext, defValue);
        mUseMetric.setChecked(defValue);

        // Show a warning if location manager is disabled and there is no custom location set
        LocationManager lm = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
        if (!lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) && !mUseCustomLoc.isChecked()) {
            showDialog();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

        if (!hasLocationPermission(mContext)) {
            mShowWeather.setChecked(false);
        }

        if (mPostResumeRunnable != null) {
            mPostResumeRunnable.run();
            mPostResumeRunnable = null;
        }

        updateLocationSummary();
        updateFontColorsSummary();
        updateIconSetSummary();
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(TraySharedPreferences sharedPreferences, String key) {
        TrayPreference pref = findPreference(key);
        if (pref instanceof TrayListPreference) {
            TrayListPreference listPref = (TrayListPreference) pref;
            pref.setSummary(listPref.getEntry());
        }

        updateLocationSummary();
        updateFontColorsSummary();
        updateIconSetSummary();

        boolean needWeatherUpdate = false;
        boolean forceWeatherUpdate = false;

        if (pref == mUseCustomLoc || pref == mCustomWeatherLoc) {
            updateLocationSummary();
        }

        if (pref == mIconSet) {
            updateIconSetSummary();
        }

        if (pref == mUseMetric) {
            // The display format of the temperatures have changed
            // Force a weather update to refresh the display
            forceWeatherUpdate = true;
        }

        // If the weather source has changes, invalidate the custom location settings and change
        // back to GeoLocation to force the user to specify a new custom location if needed
        if (TextUtils.equals(key, Constants.WEATHER_SOURCE)) {
            Preferences.setCustomWeatherLocationId(mContext, null);
            Preferences.setCustomWeatherLocationCity(mContext, null);
            Preferences.setUseCustomWeatherLocation(mContext, false);
            mUseCustomlocation.setChecked(false);
            updateLocationSummary();
        }

        if (key.equals(Constants.WEATHER_USE_CUSTOM_LOCATION)
                || key.equals(Constants.WEATHER_CUSTOM_LOCATION_CITY)) {
            forceWeatherUpdate = true;
        }

        if (key.equals(Constants.SHOW_WEATHER) || key.equals(Constants.WEATHER_REFRESH_INTERVAL)) {
            needWeatherUpdate = true;
        }

        if (Debug.doDebug(mContext)) {
            Log.v(TAG, "Preference " + key + " changed, need update " +
                    needWeatherUpdate + " force update "  + forceWeatherUpdate);
        }

        if (Preferences.showWeather(mContext) && (needWeatherUpdate || forceWeatherUpdate)) {
            Intent updateIntent = new Intent(mContext, WeatherUpdateService.class);
            if (forceWeatherUpdate) {
                updateIntent.setAction(WeatherUpdateService.ACTION_FORCE_UPDATE);
            }
            mContext.startService(updateIntent);
        }

        Intent updateIntent = new Intent(mContext, ClockWidgetProvider.class);
        mContext.sendBroadcast(updateIntent);

        Intent updatePlusIntent = new Intent(mContext, ClockWidgetPlusProvider.class);
        mContext.sendBroadcast(updatePlusIntent);
    }

    public static boolean hasLocationPermission(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        return context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    //===============================================================================================
    // Utility classes and supporting methods
    //===============================================================================================

    private void updateLocationSummary() {
        if (mUseCustomLoc.isChecked()) {
            String location = Preferences.customWeatherLocationCity(mContext);
            if (location == null) {
                location = getResources().getString(R.string.unknown);
            }
            mCustomWeatherLoc.setSummary(location);
        } else {
            mCustomWeatherLoc.setSummary(R.string.weather_geolocated);
        }
    }

    private void showDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        final Dialog dialog;

        // Build and show the dialog
        builder.setTitle(R.string.weather_retrieve_location_dialog_title);
        builder.setMessage(R.string.weather_retrieve_location_dialog_message);
        builder.setCancelable(false);
        builder.setPositiveButton(R.string.weather_retrieve_location_dialog_enable_button,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        mContext.startActivity(intent);
                    }
                });
        builder.setNegativeButton(R.string.cancel, null);
        dialog = builder.create();
        dialog.show();
    }

    private void updateFontColorsSummary() {
        if (mFontColor != null) {
            mFontColor.setSummary(mFontColor.getEntry());
        }
        if (mTimestampFontColor != null) {
            mTimestampFontColor.setSummary(mTimestampFontColor.getEntry());
        }
    }

    private void updateIconSetSummary() {
        if (mIconSet != null) {
            mIconSet.setSummary(mIconSet.getEntry());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // We only get here if user tried to enable the preference,
                // hence safe to turn it on after permission is granted
                mPostResumeRunnable = new Runnable() {
                    @Override
                    public void run() {
                        mShowWeather.setChecked(true);
                    }
                };
            }
        }
    }

    @Override
    public boolean onPreferenceChange(TrayPreference preference, Object newValue) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        if (preference == mShowWeather) {
            if (!hasLocationPermission(mContext) ) {
                String[] permissions = new String[]{Manifest.permission.ACCESS_COARSE_LOCATION};
                requestPermissions(permissions, LOCATION_PERMISSION_REQUEST_CODE);
                return false;
            }
        }
        return true;
    }
}
