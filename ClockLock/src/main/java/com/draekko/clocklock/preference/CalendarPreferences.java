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
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.CalendarContract;

import com.draekko.clocklock.ClockWidgetPlusProvider;
import com.draekko.clocklock.ClockWidgetProvider;
import com.draekko.clocklock.ClockWidgetService;
import com.draekko.clocklock.R;
import com.draekko.clocklock.misc.Constants;
import com.draekko.traypreferences.TrayListPreference;
import com.draekko.traypreferences.TrayMultiSelectListPreference;
import com.draekko.traypreferences.TrayPreference;
import com.draekko.traypreferences.TrayPreferenceFragment;
import com.draekko.traypreferences.TraySharedPreferences;
import com.draekko.traypreferences.TraySwitchPreference;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;

public class CalendarPreferences extends TrayPreferenceFragment implements
        TraySharedPreferences.OnSharedPreferenceChangeListener, TrayPreference.OnPreferenceChangeListener {

    private static final int CALENDAR_PERMISSION_REQUEST_CODE = 1;

    private Context mContext;
    private TrayListPreference mFontColor;
    private TrayListPreference mBackgroundColor;
    private TrayListPreference mEventDetailsFontColor;
    private TrayListPreference mHighlightFontColor;
    private TrayListPreference mHighlightDetailsFontColor;
    private TraySwitchPreference mShowCalendar;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences_calendar);
        mContext = getActivity();

        mFontColor = (TrayListPreference) findPreference(Constants.CALENDAR_FONT_COLOR);
        mBackgroundColor = (TrayListPreference) findPreference(Constants.CALENDAR_BACKGROUND_COLOR);
        mEventDetailsFontColor = (TrayListPreference) findPreference(Constants.CALENDAR_DETAILS_FONT_COLOR);
        mHighlightFontColor = (TrayListPreference) findPreference(Constants.CALENDAR_UPCOMING_EVENTS_FONT_COLOR);
        mHighlightDetailsFontColor = (TrayListPreference) findPreference(Constants.CALENDAR_UPCOMING_EVENTS_DETAILS_FONT_COLOR);
        updateFontColorsSummary();
        updateBackgroundColors();

        mShowCalendar = (TraySwitchPreference) findPreference(Constants.SHOW_CALENDAR);
        mShowCalendar.setOnPreferenceChangeListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        if (!hasCalendarPermission()) {
            mShowCalendar.setChecked(false);
        } else {
            updateCalendars();
        }
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
        updateIntent.setAction(ClockWidgetService.ACTION_REFRESH_CALENDAR);
        mContext.sendBroadcast(updateIntent);

        Intent updatePlusIntent = new Intent(mContext, ClockWidgetPlusProvider.class);
        updatePlusIntent.setAction(ClockWidgetService.ACTION_REFRESH_CALENDAR);
        mContext.sendBroadcast(updatePlusIntent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (requestCode == CALENDAR_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // We only get here if user tried to enable the preference,
                // hence safe to turn it on after permission is granted
                mShowCalendar.setChecked(true);
                updateCalendars();
            }
        }
    }

    private boolean hasCalendarPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        return mContext.checkSelfPermission(Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void updateCalendars() {
        if (!hasCalendarPermission()) {
            return;
        }
        // The calendar list entries and values are determined at run time, not in XML
        TrayMultiSelectListPreference calendarList =
                (TrayMultiSelectListPreference) findPreference(Constants.CALENDAR_LIST);
        CalendarEntries calEntries = CalendarEntries.findCalendars(getActivity());

        boolean firstTime = com.draekko.clocklock.misc.Preferences.calendarsToDisplay(mContext) == null;
        calendarList.setEntries(calEntries.getEntries());
        calendarList.setEntryValues(calEntries.getEntryValues());
        if (firstTime) {
            // by default, select all the things
            HashSet defaults = new HashSet();
            for (CharSequence s : calEntries.getEntryValues()) {
                defaults.add((String) s);
            }
            calendarList.setValues(defaults);
        }

        if (calEntries.getEntryValues().length == 0) {
            calendarList.setSummary(R.string.calendars_none_found_summary);
            calendarList.setEnabled(false);
        } else {
            calendarList.setSummary(R.string.calendars_summary);
            calendarList.setEnabled(true);
        }
    }

    @Override
    public boolean onPreferenceChange(TrayPreference preference, Object newValue) {
        if (preference == mShowCalendar) {
            if (hasCalendarPermission()) {
                updateCalendars();
            } else {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    return true;
                }
                Boolean enabled = (Boolean) newValue;
                if (enabled) {
                    String[] permissions = new String[]{Manifest.permission.READ_CALENDAR};
                    requestPermissions(permissions, CALENDAR_PERMISSION_REQUEST_CODE);
                    return false;
                }
            }
        }
        return true;
    }

    //===============================================================================================
    // Utility classes and supporting methods
    //===============================================================================================

    private static class CalendarEntries {
        private final CharSequence[] mEntries;
        private final CharSequence[] mEntryValues;
        private static Uri uri = CalendarContract.Calendars.CONTENT_URI;

        // Calendar projection array
        private static String[] projection = new String[] {
               CalendarContract.Calendars._ID,
               CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
        };

        // The indices for the projection array
        private static final int CALENDAR_ID_INDEX = 0;
        private static final int DISPLAY_NAME_INDEX = 1;

        static CalendarEntries findCalendars(Context context) {
            List<CharSequence> entries = new ArrayList<>();
            List<CharSequence> entryValues = new ArrayList<>();
            ContentResolver cr = context.getContentResolver();

            Cursor calendarCursor = cr.query(uri, projection, null, null, null);
            if (calendarCursor != null) {
                calendarCursor.moveToFirst();
                while (!calendarCursor.isAfterLast()) {
                    entryValues.add(calendarCursor.getString(CALENDAR_ID_INDEX));
                    entries.add(calendarCursor.getString(DISPLAY_NAME_INDEX));
                    calendarCursor.moveToNext();
                }
                calendarCursor.close();
            }

            return new CalendarEntries(entries, entryValues);
        }

        private CalendarEntries(List<CharSequence> mEntries, List<CharSequence> mEntryValues) {
            this.mEntries = mEntries.toArray(new CharSequence[mEntries.size()]);
            this.mEntryValues = mEntryValues.toArray(new CharSequence[mEntryValues.size()]);
        }

        CharSequence[] getEntries() {
            return mEntries;
        }

        CharSequence[] getEntryValues() {
            return mEntryValues;
        }
    }


    private void updateBackgroundColors() {
        if (mBackgroundColor != null) {
            mBackgroundColor.setSummary(mBackgroundColor.getEntry());
        }
    }

    private void updateFontColorsSummary() {
        if (mFontColor != null) {
            mFontColor.setSummary(mFontColor.getEntry());
        }
        if (mEventDetailsFontColor != null) {
            mEventDetailsFontColor.setSummary(mEventDetailsFontColor.getEntry());
        }
        if (mHighlightFontColor != null) {
            mHighlightFontColor.setSummary(mHighlightFontColor.getEntry());
        }
        if (mHighlightDetailsFontColor != null) {
            mHighlightDetailsFontColor.setSummary(mHighlightDetailsFontColor.getEntry());
        }
    }
}
