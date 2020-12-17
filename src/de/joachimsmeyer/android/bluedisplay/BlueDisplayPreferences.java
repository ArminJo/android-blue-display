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

package de.joachimsmeyer.android.bluedisplay;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.util.Log;

@SuppressWarnings("deprecation")
public class BlueDisplayPreferences extends PreferenceActivity implements OnSharedPreferenceChangeListener {

    // Debugging
    private static final String LOG_TAG = "BlueDisplayPreferences";
    public static final String VALUE_SEPARATOR = "  - ";

    private void addValueToPreferenceTitle(ListPreference aPreference) {
        String tTitle = (String) aPreference.getTitle();
        int tIndex = tTitle.indexOf(VALUE_SEPARATOR);
        if (tIndex >= 0) {
            tTitle = (String) tTitle.subSequence(0, tIndex);
        }
        tTitle += VALUE_SEPARATOR + aPreference.getEntry();
        aPreference.setTitle(tTitle);
        if (MyLog.isVERBOSE()) {
            Log.v(LOG_TAG, "Changing title of " + aPreference.getKey() + " to " + tTitle);
        }
    }

    private void addStringToPreferenceTitle(Preference aPreference, String aValueString) {
        if (aValueString != null && aValueString.length() > 1) {
            String tTitle = (String) aPreference.getTitle();
            int tIndex = tTitle.indexOf(VALUE_SEPARATOR);
            if (tIndex >= 0) {
                tTitle = (String) tTitle.subSequence(0, tIndex);
            }
            tTitle += VALUE_SEPARATOR + aValueString;
            aPreference.setTitle(tTitle);
            if (MyLog.isVERBOSE()) {
                Log.v(LOG_TAG, "Changing title of " + aPreference.getKey() + " to " + tTitle);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (MyLog.isINFO()) {
            Log.i(LOG_TAG, " +++ ON CREATE +++");
        }
        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);
        ListPreference tPref = (ListPreference) findPreference("loglevel");
        addValueToPreferenceTitle(tPref);
        tPref = (ListPreference) findPreference("screenorientation");
        addValueToPreferenceTitle(tPref);
        CheckBoxPreference tCBPref = (CheckBoxPreference) findPreference("do_autoconnect");
        String tDeviceName = this.getIntent().getStringExtra(BlueDisplay.BT_DEVICE_NAME);
        addStringToPreferenceTitle(tCBPref, tDeviceName);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Preference tPref = findPreference(key);
        if (tPref instanceof ListPreference) {
            addValueToPreferenceTitle((ListPreference) tPref);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (MyLog.isINFO()) {
            Log.i(LOG_TAG, " + ON RESUME +");
        }
        // Set up a listener whenever a key changes
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (MyLog.isINFO()) {
            Log.i(LOG_TAG, " - ON PAUSE -");
        }
        // Unregister the listener whenever a key changes
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }
}
