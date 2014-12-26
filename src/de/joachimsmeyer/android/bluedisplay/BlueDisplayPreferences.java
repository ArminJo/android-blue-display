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
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.util.Log;

@SuppressWarnings("deprecation")
public class BlueDisplayPreferences extends PreferenceActivity implements OnSharedPreferenceChangeListener {

	// Debugging
	private static final String LOG_TAG = "BlueDisplayPreferences";
	private static final String tValueSeparator = "  - ";

	private void addValueToPreferenceTitle(ListPreference aPreference) {
		String tTitle = (String) aPreference.getTitle();
		int tIndex = tTitle.indexOf(tValueSeparator);
		if (tIndex >= 0) {
			tTitle = (String) tTitle.subSequence(0, tIndex);
		}
		tTitle += tValueSeparator + aPreference.getEntry();
		aPreference.setTitle(tTitle);
		if (BlueDisplay.isDEBUG()) {
			Log.d(LOG_TAG, "Changing title to " + tTitle);
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (BlueDisplay.isINFO()) {
			Log.i(LOG_TAG, " +++ ON CREATE +++");
		}
		// Load the preferences from an XML resource
		addPreferencesFromResource(R.xml.preferences);
		ListPreference tPref = (ListPreference) findPreference("loglevel");
		addValueToPreferenceTitle(tPref);
		tPref = (ListPreference) findPreference("screenorientation");
		addValueToPreferenceTitle(tPref);
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
		if (BlueDisplay.isINFO()) {
			Log.i(LOG_TAG, " + ON RESUME +");
		}
		// Set up a listener whenever a key changes
		getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (BlueDisplay.isINFO()) {
			Log.i(LOG_TAG, " - ON PAUSE -");
		}
		// Unregister the listener whenever a key changes
		getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
	}
}
