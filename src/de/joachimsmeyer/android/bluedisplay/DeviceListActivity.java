/*
 * Copyright (C) 2009 The Android Open Source Project
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

/**
 * This Activity appears as a dialog. It lists any paired devices and devices detected in the area after discovery. When a device is
 * chosen by the user, the MAC address of the device is sent back to the parent Activity in the result Intent.
 */
public class DeviceListActivity extends Activity {
    // Debugging
    private static final String LOG_TAG = "BlueDeviceListActivity";

    // Return Intent extra
    public static String EXTRA_DEVICE_ADDRESS = "device_address";

    // Member fields
    private BluetoothAdapter mBtAdapter;
    private ArrayAdapter<String> mPairedDevicesArrayAdapter;
    Set<BluetoothDevice> mPairedDevices;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (MyLog.isINFO()) {
            Log.i(LOG_TAG, "+++ ON CREATE +++");
        }

        // Setup the window
        setContentView(R.layout.device_list);

        // Set result CANCELED in case the user backs out
        setResult(Activity.RESULT_CANCELED);

        // Initialize the button to perform device discovery
        Button tConnectButton = (Button) findViewById(R.id.connect_last_device);
        /*
         * Change caption of top connect button to include name of last device
         */
        String tDeviceNameLastConnected = this.getIntent().getStringExtra(BlueDisplay.BT_DEVICE_NAME);
        if (tDeviceNameLastConnected.equalsIgnoreCase("null")) {
            tConnectButton.setVisibility(View.INVISIBLE);
        } else {
            tConnectButton.setVisibility(View.VISIBLE);
            String tOldCaption = (String) tConnectButton.getText();
            int tIndex = tOldCaption.indexOf(BlueDisplayPreferences.VALUE_SEPARATOR);
            String tNewCaption;
            if (tIndex >= 0) {
                tNewCaption = (String) tOldCaption.subSequence(0, tIndex);
            }
            tNewCaption = tOldCaption + BlueDisplayPreferences.VALUE_SEPARATOR + tDeviceNameLastConnected;
            tConnectButton.setText(tNewCaption);
            if (MyLog.isVERBOSE()) {
                Log.v(LOG_TAG, "Changing title of button from \"" + tOldCaption + "\" to \"" + tNewCaption + "\"");
            }
        }

        tConnectButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent();
                // signal connecting to last active device
                intent.putExtra(EXTRA_DEVICE_ADDRESS, "");
                // Set result and finish this Activity
                setResult(Activity.RESULT_OK, intent);
                finish();
            }
        });

        // Initialize array adapters for already paired devices
        mPairedDevicesArrayAdapter = new ArrayAdapter<String>(this, R.layout.device_name);

        // Find and set up the ListView for paired devices
        ListView pairedListView = (ListView) findViewById(R.id.paired_devices);
        pairedListView.setAdapter(mPairedDevicesArrayAdapter);
        pairedListView.setOnItemClickListener(mDeviceClickListener);

        // Get the local Bluetooth adapter
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();

        // Get a set of currently paired devices
        mPairedDevices = mBtAdapter.getBondedDevices();

        long tCurrentTimestampMillis = System.currentTimeMillis();
        if (mPairedDevices.size() == 1
                && BluetoothSerialSocket.sLastFailOrDisconnectTimestampMillis < (tCurrentTimestampMillis - 10000)) {
            /*
             * If only one device found, do auto connect (send device address directly) But not within 10 seconds after last fail or
             * disconnect.
             */
            Iterator<BluetoothDevice> tIter = mPairedDevices.iterator();
            // Get the BLuetoothDevice object
            BluetoothDevice tDeviceToConnect = tIter.next();
            // Create the result Intent and include the MAC address
            Intent intent = new Intent();
            intent.putExtra(EXTRA_DEVICE_ADDRESS, tDeviceToConnect.getAddress());
            // Set result and finish this Activity
            setResult(Activity.RESULT_OK, intent);
            finish();

        } else if (mPairedDevices.size() > 0) {
            // If there are paired devices, add each one to the ArrayAdapter
            findViewById(R.id.title_paired_devices).setVisibility(View.VISIBLE);
            /*
             * Sort devices by name
             */
            List<String> tDeviceNameList = new ArrayList<String>(mPairedDevices.size());
            for (BluetoothDevice device : mPairedDevices) {
                tDeviceNameList.add(device.getName());
            }
            Collections.sort(tDeviceNameList);
            for (String tDeviceName : tDeviceNameList) {
                mPairedDevicesArrayAdapter.add(tDeviceName);
            }
        } else {
            String noDevices = getResources().getText(R.string.none_paired).toString();
            mPairedDevicesArrayAdapter.add(noDevices);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(LOG_TAG, "--- ON Destroy ---");

        RPCView.mDeviceListActivityLaunched = false;
    }

    // The on-click listener for all devices in the ListViews
    private OnItemClickListener mDeviceClickListener = new OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
            String noDevicesPaired = getResources().getText(R.string.none_paired).toString();
            String noDevicesFound = getResources().getText(R.string.none_found).toString();

            // Cancel discovery because it's costly and we're about to connect
            if (mBtAdapter.isDiscovering()) {
                mBtAdapter.cancelDiscovery();
            }
            String info = ((TextView) v).getText().toString();

            if ((info != noDevicesPaired) && (info != noDevicesFound)) {
                /*
                 * Try to find device string an extract address
                 */
                for (BluetoothDevice device : mPairedDevices) {
                    if (info.equalsIgnoreCase(device.getName())) {
                        String tMacAddress = device.getAddress();
                        // Create the result Intent and include the MAC address
                        Intent intent = new Intent();
                        intent.putExtra(EXTRA_DEVICE_ADDRESS, tMacAddress);

                        // Set result and finish this Activity
                        setResult(Activity.RESULT_OK, intent);
                        finish();
                    }
                }
                setResult(Activity.RESULT_CANCELED);
                finish();
            }
        }
    };

}
