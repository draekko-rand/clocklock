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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateFormat;

import com.draekko.clocklock.ClockWidgetPlusProvider;
import com.draekko.clocklock.ClockWidgetProvider;
import com.draekko.clocklock.R;
import com.draekko.clocklock.misc.Constants;
import com.draekko.traypreferences.TrayListPreference;
import com.draekko.traypreferences.TrayPreference;
import com.draekko.traypreferences.TrayPreferenceFragment;
import com.draekko.traypreferences.TraySharedPreferences;
import com.draekko.traypreferences.TraySwitchPreference;

public class ClockPreferences extends TrayPreferenceFragment implements
        TraySharedPreferences.OnSharedPreferenceChangeListener {

    private Context mContext;
    private TrayListPreference mClockFontColor;
    private TrayListPreference mAlarmFontColor;
    private TraySwitchPreference mAmPmToggle;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences_clock);

        mContext = getActivity();
        mClockFontColor = (TrayListPreference) findPreference(Constants.CLOCK_FONT_COLOR);
        mAlarmFontColor = (TrayListPreference) findPreference(Constants.CLOCK_ALARM_FONT_COLOR);
        mAmPmToggle = (TraySwitchPreference) findPreference(Constants.CLOCK_AM_PM_INDICATOR);

        updateFontColorsSummary();
        updateAmPmToggle();
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(TraySharedPreferences prefs, String key) {
        TrayPreference pref = findPreference(key);
        if (pref instanceof TrayListPreference) {
            TrayListPreference listPref = (TrayListPreference) pref;
            pref.setSummary(listPref.getEntry());
        }

        Intent updateIntent = new Intent(mContext, ClockWidgetProvider.class);
        mContext.sendBroadcast(updateIntent);

        Intent updatePlusIntent = new Intent(mContext, ClockWidgetPlusProvider.class);
        mContext.sendBroadcast(updatePlusIntent);
    }

    private void updateFontColorsSummary() {
        if (mClockFontColor != null) {
            mClockFontColor.setSummary(mClockFontColor.getEntry());
        }
        if (mAlarmFontColor != null) {
            mAlarmFontColor.setSummary(mAlarmFontColor.getEntry());
        }
    }

    private void updateAmPmToggle() {
        if (DateFormat.is24HourFormat(mContext)) {
            mAmPmToggle.setEnabled(false);
        } else {
            mAmPmToggle.setEnabled(true);
        }
    }
}
