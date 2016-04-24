/*
 * 	SUMMARY
 * 	Blue Display is an Open Source Android remote Display for Arduino etc.
 * 	It receives basic draw requests from Arduino etc. over Bluetooth and renders it.
 * 	It also implements basic GUI elements as buttons and sliders.
 * 	It sends touch or GUI callback events over Bluetooth back to Arduino.
 * 
 *  Copyright (C) 2015  Armin Joachimsmeyer
 *  armin.joachimsmeyer@gmail.com
 *  
 * 	This file is part of BlueDisplay.
 *  BlueDisplay is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.

 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.

 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/gpl.html>.
 *  
 *  
 * This class implements local view of the BlueDisplay log output to help debugging a client arduino application.
 * Data is provided by the MyLog wrapper class.
 */

package de.joachimsmeyer.android.bluedisplay;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class LogViewActivity extends ListActivity {
	LogViewActivity sInstance;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		sInstance = this;

		final ArrayAdapter<String> tColoredLogAdapter = new ColoredLogAdapter(this, R.layout.logview);
		setListAdapter(tColoredLogAdapter);

		ListView listView = getListView();
		listView.setTextFilterEnabled(true);

		listView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				AlertDialog.Builder tBuilder = new AlertDialog.Builder(parent.getContext());
				tBuilder.setMessage(R.string.message_clear);
				tBuilder.setCancelable(true);
				tBuilder.setPositiveButton(R.string.alert_dialog_ok, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						MyLog.clear();
						tColoredLogAdapter.notifyDataSetChanged();
						// return to main window
						sInstance.onBackPressed();
					}
				});
				tBuilder.setNegativeButton(R.string.alert_dialog_cancel, new DialogInterface.OnClickListener() {
					// empty listener
					public void onClick(DialogInterface dialog, int id) {
					}
				});
				AlertDialog tAlertDialog = tBuilder.create();
				tAlertDialog.show();
			}
		});
	}

	@Override
	public synchronized void onResume() {
		super.onResume();
		// set window to always on
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}

	@Override
	public synchronized void onPause() {
		super.onPause();
		// set window to normal (not persistent) state
		getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}

	private class ColoredLogAdapter extends ArrayAdapter<String> {

		private final Context mContext;
		private final int mTextViewResourceId;

		public ColoredLogAdapter(Context context, int textViewResourceId) {
			super(context, textViewResourceId);
			this.mContext = context;
			mTextViewResourceId = textViewResourceId;
		}

		@Override
		public int getCount() {
			int tCount = MyLog.getCount();
//			Log.i("LW", "MyLog count=" + tCount);
			return tCount;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View rowView;
			LayoutInflater tInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			if (convertView == null) {
				rowView = tInflater.inflate(mTextViewResourceId, parent, false);
			} else {
				rowView = convertView;
			}
			TextView textView = (TextView) rowView;
			String tLogMessage = MyLog.get(position);
			if (tLogMessage != null) {
				if (tLogMessage.charAt(0) == 'E') {
					textView.setBackgroundColor(Color.RED);
				} else if (tLogMessage.charAt(0) == 'W') {
					textView.setBackgroundColor(Color.rgb(0xFF, 0x60, 0x00)); // Orange
				} else if (tLogMessage.charAt(0) == 'I') {
					textView.setBackgroundColor(Color.BLACK);
				} else if (tLogMessage.charAt(0) == 'D') {
					textView.setBackgroundColor(Color.rgb(0x50, 0x50, 0x50));
				} else if (tLogMessage.charAt(0) == 'V') {
					textView.setBackgroundColor(Color.rgb(0x68, 0x68, 0x68));
				}
				textView.setText(tLogMessage);
			}
			return rowView;
		}

	}
}