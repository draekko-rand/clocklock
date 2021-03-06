/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.draekko.traypreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;

import com.draekko.traypreferences.TrayPreference.OnPreferenceChangeInternalListener;


/**
 * An adapter that returns the {@link TrayPreference} contained in this group.
 * In most cases, this adapter should be the base class for any custom
 * adapters from {link Preference#getAdapter()}.
 * <p>
 * This adapter obeys the
 * {@link TrayPreference}'s adapter rule (the
 * {@link Adapter#getView(int, View, ViewGroup)} should be used instead of
 * {link Preference#getView(ViewGroup)} if a {@link TrayPreference} has an
 * adapter via {link Preference#getAdapter()}).
 * <p>
 * This adapter also propagates data change/invalidated notifications upward.
 * <p>
 * This adapter does not include this {@link TrayPreferenceGroup} in the returned
 * adapter, use {link PreferenceCategoryAdapter} instead.
 * 
 * see PreferenceCategoryAdapter
 *
 * @hide
 */
public class TrayPreferenceGroupAdapter extends BaseAdapter
        implements OnPreferenceChangeInternalListener {
    
    private static final String TAG = "PreferenceGroupAdapter";

    /**
     * The group that we are providing data from.
     */
    private TrayPreferenceGroup mPreferenceGroup;
    
    /**
     * Maps a position into this adapter -> {@link TrayPreference}. These
     * {@link TrayPreference}s don't have to be direct children of this
     * {@link TrayPreferenceGroup}, they can be grand children or younger)
     */
    private List<TrayPreference> mPreferenceList;
    
    /**
     * List of unique Preference and its subclasses' names. This is used to find
     * out how many types of views this adapter can return. Once the count is
     * returned, this cannot be modified (since the ListView only checks the
     * count once--when the adapter is being set). We will not recycle views for
     * Preference subclasses seen after the count has been returned.
     */
    private ArrayList<TrayPreferenceLayout> mPreferenceLayouts;

    private TrayPreferenceLayout mTempPreferenceLayout = new TrayPreferenceLayout();

    /**
     * Blocks the mPreferenceClassNames from being changed anymore.
     */
    private boolean mHasReturnedViewTypeCount = false;
    
    private volatile boolean mIsSyncing = false;
    
    private Handler mHandler = new Handler(); 
    
    private Runnable mSyncRunnable = new Runnable() {
        public void run() {
            syncMyPreferences();
        }
    };

    private int mHighlightedPosition = -1;
    private Drawable mHighlightedDrawable;

    private static ViewGroup.LayoutParams sWrapperLayoutParams = new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

    private static class TrayPreferenceLayout implements Comparable<TrayPreferenceLayout> {
        private int resId;
        private int widgetResId;
        private String name;

        public int compareTo(TrayPreferenceLayout other) {
            int compareNames = name.compareTo(other.name);
            if (compareNames == 0) {
                if (resId == other.resId) {
                    if (widgetResId == other.widgetResId) {
                        return 0;
                    } else {
                        return widgetResId - other.widgetResId;
                    }
                } else {
                    return resId - other.resId;
                }
            } else {
                return compareNames;
            }
        }
    }

    public TrayPreferenceGroupAdapter(TrayPreferenceGroup preferenceGroup) {
        mPreferenceGroup = preferenceGroup;
        // If this group gets or loses any children, let us know
        mPreferenceGroup.setOnPreferenceChangeInternalListener(this);

        mPreferenceList = new ArrayList<TrayPreference>();
        mPreferenceLayouts = new ArrayList<TrayPreferenceLayout>();

        syncMyPreferences();
    }

    private void syncMyPreferences() {
        synchronized(this) {
            if (mIsSyncing) {
                return;
            }

            mIsSyncing = true;
        }

        List<TrayPreference> newPreferenceList = new ArrayList<TrayPreference>(mPreferenceList.size());
        flattenPreferenceGroup(newPreferenceList, mPreferenceGroup);
        mPreferenceList = newPreferenceList;
        
        notifyDataSetChanged();

        synchronized(this) {
            mIsSyncing = false;
            notifyAll();
        }
    }
    
    private void flattenPreferenceGroup(List<TrayPreference> preferences, TrayPreferenceGroup group) {
        // TODO: shouldn't always?
        group.sortPreferences();

        final int groupSize = group.getPreferenceCount();
        for (int i = 0; i < groupSize; i++) {
            final TrayPreference preference = group.getPreference(i);
            
            preferences.add(preference);
            
            if (!mHasReturnedViewTypeCount && preference.canRecycleLayout()) {
                addPreferenceClassName(preference);
            }
            
            if (preference instanceof TrayPreferenceGroup) {
                final TrayPreferenceGroup preferenceAsGroup = (TrayPreferenceGroup) preference;
                if (preferenceAsGroup.isOnSameScreenAsChildren()) {
                    flattenPreferenceGroup(preferences, preferenceAsGroup);
                }
            }

            preference.setOnPreferenceChangeInternalListener(this);
        }
    }

    /**
     * Creates a string that includes the preference name, layout id and widget layout id.
     * If a particular preference type uses 2 different resources, they will be treated as
     * different view types.
     */
    private TrayPreferenceLayout createPreferenceLayout(TrayPreference preference, TrayPreferenceLayout in) {
        TrayPreferenceLayout pl = in != null? in : new TrayPreferenceLayout();
        pl.name = preference.getClass().getName();
        pl.resId = preference.getLayoutResource();
        pl.widgetResId = preference.getWidgetLayoutResource();
        return pl;
    }

    private void addPreferenceClassName(TrayPreference preference) {
        final TrayPreferenceLayout pl = createPreferenceLayout(preference, null);
        int insertPos = Collections.binarySearch(mPreferenceLayouts, pl);

        // Only insert if it doesn't exist (when it is negative).
        if (insertPos < 0) {
            // Convert to insert index
            insertPos = insertPos * -1 - 1;
            mPreferenceLayouts.add(insertPos, pl);
        }
    }
    
    public int getCount() {
        return mPreferenceList.size();
    }

    public TrayPreference getItem(int position) {
        if (position < 0 || position >= getCount()) return null;
        return mPreferenceList.get(position);
    }

    public long getItemId(int position) {
        if (position < 0 || position >= getCount()) return ListView.INVALID_ROW_ID;
        return this.getItem(position).getId();
    }

    /**
     * @hide
     */
    public void setHighlighted(int position) {
        mHighlightedPosition = position;
    }

    /**
     * @hide
     */
    public void setHighlightedDrawable(Drawable drawable) {
        mHighlightedDrawable = drawable;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        final TrayPreference preference = this.getItem(position);
        // Build a PreferenceLayout to compare with known ones that are cacheable.
        mTempPreferenceLayout = createPreferenceLayout(preference, mTempPreferenceLayout);

        // If it's not one of the cached ones, set the convertView to null so that 
        // the layout gets re-created by the Preference.
        if (Collections.binarySearch(mPreferenceLayouts, mTempPreferenceLayout) < 0 ||
                (getItemViewType(position) == getHighlightItemViewType())) {
            convertView = null;
        }
        View result = preference.getView(convertView, parent);
        if (position == mHighlightedPosition && mHighlightedDrawable != null) {
            ViewGroup wrapper = new FrameLayout(parent.getContext());
            wrapper.setLayoutParams(sWrapperLayoutParams);
            wrapper.setBackground(mHighlightedDrawable);
            wrapper.addView(result);
            result = wrapper;
        }
        return result;
    }

    @Override
    public boolean isEnabled(int position) {
        if (position < 0 || position >= getCount()) return true;
        return this.getItem(position).isSelectable();
    }

    @Override
    public boolean areAllItemsEnabled() {
        // There should always be a preference group, and these groups are always
        // disabled
        return false;
    }

    public void onPreferenceChange(TrayPreference preference) {
        notifyDataSetChanged();
    }

    public void onPreferenceHierarchyChange(TrayPreference preference) {
        mHandler.removeCallbacks(mSyncRunnable);
        mHandler.post(mSyncRunnable);
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    private int getHighlightItemViewType() {
        return getViewTypeCount() - 1;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == mHighlightedPosition) {
            return getHighlightItemViewType();
        }

        if (!mHasReturnedViewTypeCount) {
            mHasReturnedViewTypeCount = true;
        }
        
        final TrayPreference preference = this.getItem(position);
        if (!preference.canRecycleLayout()) {
            return IGNORE_ITEM_VIEW_TYPE;
        }

        mTempPreferenceLayout = createPreferenceLayout(preference, mTempPreferenceLayout);

        int viewType = Collections.binarySearch(mPreferenceLayouts, mTempPreferenceLayout);
        if (viewType < 0) {
            // This is a class that was seen after we returned the count, so
            // don't recycle it.
            return IGNORE_ITEM_VIEW_TYPE;
        } else {
            return viewType;
        }
    }

    @Override
    public int getViewTypeCount() {
        if (!mHasReturnedViewTypeCount) {
            mHasReturnedViewTypeCount = true;
        }
        
        return Math.max(1, mPreferenceLayouts.size()) + 1;
    }
}
