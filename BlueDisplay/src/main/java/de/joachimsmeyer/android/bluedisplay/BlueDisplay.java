/*
 *     SUMMARY
 *     Blue Display is an Open Source Android remote Display for Arduino etc.
 *     It receives basic draw requests from Arduino etc. over Bluetooth and renders it.
 *     It also implements basic GUI elements as buttons and sliders.
 *     GUI callback, touch and sensor events are sent back to Arduino.
 *
 *  Copyright (C) 2014-2019  Armin Joachimsmeyer
 *  armin.joachimsmeyer@gmail.com
 *
 *  This file is part of BlueDisplay.
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
 * FEATURES
 * Scale factor to enlarge the picture drawn.
 * Color in RGB 565.
 *
 *  SUPPORTED FUNCTIONS:
 *  Set display size used for drawing commands. The real display size is user definable by just resizing the view.
 *  Set modes for Touch recognition.
 *  Clear display.
 *  Draw Pixel.
 *  Draw and fill Circle, Rectangle and Path.
 *  Draw Character, Text and Multi-line Text transparent or with background color for easy overwriting existent text.
 *  Draw Line.
 *  Draw Chart from byte or short values. Enables clearing of last drawn chart.
 *  Set Codepage and set Mapping for utf16 character to codepage location. I.e. have Omega at 0x81.
 *
 */

/*
 * Startup
 * - OnCreate of BlueDisplay
 * - OnCreate of RPCView
 *   - create initial canvas with white background covering 80% of screen space.
 * - Create Sensors
 * - Try connect USB
 * - Autoconnect Bluetooth
 *   - Connect thread calls connect() and waits. On success it starts connected thread.
 *   - Connected thread calls resetAll() waits 200ms, reads old data from BT input, resets buffer and signals connection to Client.
 *   #### Connected thread forever reads BT input into buffer and calls handleReceived() of SerialService.
 * - OnStart of BlueDisplay
 * - OnResume of BlueDisplay
 * - OnSizeChanged of BlueDisplay with current display size
 * - OnFocusChanged (true)
 *
 * Running
 * - Bluetooth or USB socket receives data
 * - It calls mSerialService.handleReceived(tReadLength), which puts data into big buffer.
 * - If UI is not working (mRequireUpdateViewMessage) it sends a Message which calls invalidate().
 * - invalidate() triggers OnDraw(), which first calls searchCommand().
 *   - searchCommand() searches the buffer for a valid BlueDisplay command and calls interpretCommand().
 *   - interpretCommand() dispatches Button and Slider commands and interprets all others by itself.
 * - The bitmap of the canvas we use to draw is then copied into the canvas parameter provided by OnDraw().
 * - mRequireUpdateViewMessage is set to true.
 *
 *
 * Deactivate
 * - OnPause
 * - OnFocusChanged (false)
 *
 * Activate
 * - OnResume of BlueDisplay
 * - OnFocusChanged (true)
 *
 */
package de.joachimsmeyer.android.bluedisplay;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.hardware.SensorManager;
import android.hardware.usb.UsbManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class BlueDisplay extends Activity {

    // Intent request codes
    public static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
//    static final int REQUEST_PERMISSION_USB = 2;

    static final String LOG_TAG = "BlueDisplay";

    // Message types sent from the BluetoothSerialSocket Handler
    public static final int MESSAGE_TIMEOUT_AFTER_CONNECT = 1;
    //    public static final int MESSAGE_READ = 2;
//    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_BT_CONNECT = 2;
    public static final int MESSAGE_BT_DISCONNECT = 3;
    public static final int MESSAGE_USB_CONNECT = 4;
    public static final int MESSAGE_USB_DISCONNECT = 5;
    public static final int MESSAGE_TOAST = 10;
    public static final int MESSAGE_UPDATE_VIEW = 11; // call invalidate()
    // Message sent by RPCView
    public static final int REQUEST_INPUT_DATA = 20;

    public static final String CALLBACK_ADDRESS = "callback_address";
    public static final String DIALOG_PROMPT = "dialog_prompt";
    public static final String NUMBER_INITIAL_VALUE = "initialValue";
    public static final String NUMBER_FLAG = "doNumber";

    // Key names received from the BluetoothSerialSocket Handler
    public static final String TOAST = "toast";

    private BluetoothAdapter mBluetoothAdapter = null;
    UsbManager mUsbManager = null;

    /*
     * Main view. Displays the remote data.
     */
    protected RPCView mRPCView;

    Toast mMyToast;

    /*
     * Bluetooth and USB socket handler
     */
    public BluetoothSerialSocket mBTSerialSocket = null;
    public USBSerialSocket mUSBSerialSocket = null;
    public SerialService mSerialService = null;

    boolean mUSBDeviceAttached = false;
    boolean mDeviceConnected = false; // Communication with the device is now possible

    // True for 5 seconds after start of connection and reset by FUNCTION_REQUEST_MAX_CANVAS_SIZE and
    // SUBFUNCTION_GLOBAL_SET_FLAGS_AND_SIZE
    boolean mWaitForDataAfterConnect = false;
    public static final int SECONDS_TO_WAIT_FOR_COMMANDS_RECEIVED = 5;

    /*
     * Sensor listener
     */
    public Sensors mSensorEventListener;

    /*
     * Audio manager for getting current user volume setting
     */
    public AudioManager mAudioManager;
    public int mMaxSystemVolume;

    private boolean mInTryToEnableEnableBT; // We try to enable Bluetooth
    private boolean mAutoConnectBT = false; // Auto connect at startup
    private String mAutoConnectMacAddressFromPreferences; // MAC address for auto connect at startup
    // to be displayed in preferences dialog
    private String mAutoConnectDeviceNameFromPreferences; // Device name for auto connect at startup
    private String mMacAddressToConnect; // MAC address for which an connect is tried
    private String mDeviceNameToConnect; // Device name for which an connect is tried
    private String mMacAddressConnected; // MAC address of the current connected device
    // to be displayed in DeviceListActivity
    private String mBluetoothDeviceNameConnected; // Device name of the current connected Bluetooth device
    static final String BT_DEVICE_NAME = "bt_device_name";

    // State variable is declared in BluetoothSerialSocket
    private static final String SHOW_TOUCH_COORDINATES_KEY = "show_touch_mode";
    private static final String ALLOW_INSECURE_CONNECTIONS_KEY = "allowinsecureconnections";
    private static final String AUTO_CONNECT_KEY = "do_autoconnect";
    public static final String AUTO_CONNECT_MAC_ADDRESS_KEY = "autoconnect_mac_address";
    public static final String AUTO_CONNECT_DEVICE_NAME_KEY = "autoconnect_device_name";

    private static final String SCREENORIENTATION_KEY = "screenorientation";
    int mPreferredScreenOrientation;
    protected int mCurrentScreenOrientation;
    // rotation is 1 or 0 for landscape (usb connector right) depending on model
    protected int mCurrentRotation; // = getWindowManager().getDefaultDisplay().getRotation() - is stored here for convenience
    // reason
    protected boolean mOrientationisLockedByClient = false;

    MenuItem mMenuItemConnect;
    // private MenuItem mMenuItemStartStopLogging;

    private Dialog mAboutDialog;

    /******************
     * Event Handler
     ******************/
    @SuppressLint("InlinedApi")
    /*
     * Called when the activity is first created. Creates Audio Manager, RPCView class, bluetooth adapter + BluetoothSerialSocket,
     * try to connect and start listener to sensors.
     * After onCreate the RPCView gets an onSizeChanged event.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (MyLog.isINFO()) {
            Log.i(LOG_TAG, "+++ ON CREATE +++");
        }

        /*
         * Get Audio manager and max volume for beep volume handling in Buttons and playTone() Must be before create RPCView, since
         * RPCView uses this values
         */
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mMaxSystemVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_SYSTEM);

        /*
         * Create RPCView
         */
        mRPCView = new RPCView(this, mHandlerForGUIRequests);
        setContentView(mRPCView);
        mRPCView.setFocusable(true);
        mRPCView.setFocusableInTouchMode(true);
        mRPCView.requestFocus();

        // Set default values only once after installation
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        // read preferences, needed for auto Bluetooth connection
        readPreferences();

        /*
         * create the serial buffer handler / interpreter
         */
        mSerialService = new SerialService(this, mHandlerForGUIRequests);

        /*
         * Start listen to sensors
         */
        mSensorEventListener = new Sensors(this, (SensorManager) getSystemService(Context.SENSOR_SERVICE));

        /*
         * First try to get USB Interface
         */
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        mUSBSerialSocket = new USBSerialSocket(this, mSerialService, mHandlerForGUIRequests, mUsbManager);
        mUSBSerialSocket.connect();
        // if (mUSBSerialSocket.mIsConnected) {
        // setMenuItemConnect(true);
        // }

        if (!mUSBDeviceAttached) {
            MyLog.i(LOG_TAG, "No USB device connected -> switch to Bluetooth");
            initBluetooth();
        }

        if (MyLog.isINFO()) {
            Log.i(LOG_TAG, "+++ DONE IN ON CREATE +++");
        }
        // mRPCView.showTestpage();
    }

    /*
     * Get Bluetooth interface
     */
    void initBluetooth() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            // no Bluetooth available on this device
            finishDialogNoBluetooth();

        } else {
            /*
             * Create BluetoothSerialSocket to get bluetooth data
             */
            mBTSerialSocket = new BluetoothSerialSocket(this, mSerialService, mHandlerForGUIRequests);
            if (mBTSerialSocket.getState() == BluetoothSerialSocket.STATE_NONE && mBluetoothAdapter.isEnabled()) {
                if (mAutoConnectBT) {
                    /*
                     * Preference is "auto connect", so try it...
                     */
                    if (mAutoConnectMacAddressFromPreferences != null && mAutoConnectMacAddressFromPreferences.length() > 10) {
                        mMacAddressToConnect = mAutoConnectMacAddressFromPreferences;
                        // Get the BluetoothDevice object
                        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(mMacAddressToConnect);
                        // Attempt to connect to the device
                        mBTSerialSocket.connect(device);
//                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
//                                mDeviceNameToConnect = " No connect permission";
//                            } else {
//                                mDeviceNameToConnect = device.getName();
//                            }
//                        } else {
                        mDeviceNameToConnect = device.getName();
//                        }
                    }
                } else {
                    launchDeviceListActivity();
                }
            } else if (mBTSerialSocket.getState() == BluetoothSerialSocket.STATE_CONNECTED) {
                // stop running service if not connected here, to enable new connections later
                mBTSerialSocket.stop();
            }
        }
        mInTryToEnableEnableBT = false;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (MyLog.isINFO()) {
            Log.i(LOG_TAG, "++ ON START ++");
        }
    }

    @Override
    public synchronized void onPause() {
        super.onPause();
        if (MyLog.isINFO()) {
            Log.i(LOG_TAG, "- ON PAUSE -");
        }
        mSensorEventListener.deregisterAllActiveSensorListeners();
        mRPCView.mToneGenerator.stopTone();
    }

    @SuppressLint("InlinedApi")
    @Override
    public synchronized void onResume() {
        super.onResume();
        if (MyLog.isINFO()) {
            Log.i(LOG_TAG, "+ ON RESUME +");
        }

        if (!mInTryToEnableEnableBT) {
            if ((mBluetoothAdapter != null) && (!mBluetoothAdapter.isEnabled())) {
                Log.i(LOG_TAG, "Activate Bluetooth and request permissions");
                // first permission check after booting is here
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1);
                }
                Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//                if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
//                    MyLog.e(LOG_TAG, "Cannot activate Bluetooth, because no BLUETOOTH_CONNECT granted by user");
//                    mInTryToEnableEnableBT = false;
//                    finish();
//                } else {
                startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                mInTryToEnableEnableBT = true;
//                }
            }
        }
        boolean tOldAutoConnectBTValue = mAutoConnectBT; // mAutoConnectBT is overwritten by readPreferences()
        if (mOrientationisLockedByClient) {
            // keep value of mPreferredScreenOrientation, which is overwritten by readPreferences()
            int tOldPreferredScreenOrientation = mPreferredScreenOrientation;
            readPreferences();
            mPreferredScreenOrientation = tOldPreferredScreenOrientation;
        } else {
            readPreferences();
            setScreenOrientation(mPreferredScreenOrientation);
        }
        if (!mUSBDeviceAttached) {
            // Store mMacAddressToConnect and mDeviceNameToConnect in preferences if mAutoConnectBT changed from false to true
            if (mAutoConnectBT && !tOldAutoConnectBTValue && mBTSerialSocket != null
                    && mBTSerialSocket.getState() == BluetoothSerialSocket.STATE_CONNECTED) {
                writeStringPreference(AUTO_CONNECT_MAC_ADDRESS_KEY, mMacAddressToConnect);
                writeStringPreference(AUTO_CONNECT_DEVICE_NAME_KEY, mDeviceNameToConnect);
                mAutoConnectDeviceNameFromPreferences = mDeviceNameToConnect;
            }

            mMacAddressConnected = mMacAddressToConnect;
            mBluetoothDeviceNameConnected = mDeviceNameToConnect;
        }

        mCurrentRotation = getWindowManager().getDefaultDisplay().getRotation();
        mSensorEventListener.registerAllActiveSensorListeners();
        // Reset brightness to user value, it can be set by application to another value
        Window window = getWindow();
        WindowManager.LayoutParams layoutParams = window.getAttributes();
        layoutParams.screenBrightness = -1;
        window.setAttributes(layoutParams);

        mRPCView.invalidate();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (MyLog.isINFO()) {
            Log.i(LOG_TAG, "+ onWindowFocusChanged focus=" + hasFocus);
        }
        // send new max size to client, but only if device is connected (check needed here since we always get an onSizeChanged
        // event at startup)
        if (mRPCView.mSendPendingConnectMessage) {
            mRPCView.mSendPendingConnectMessage = false;
            // Signal connection to Client, now that the mCurrentViewWidth and mCurrentViewHeight are set.
            mSerialService.signalBlueDisplayConnection();
            /*
             * Send the event to the UI Activity, which in turn shows the connected toast and sets window to always on
             */
            mHandlerForGUIRequests.sendEmptyMessage(BlueDisplay.MESSAGE_USB_CONNECT);
            MyLog.i(LOG_TAG, "onWindowFocusChanged: Send delayed connection build up event");
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (MyLog.isINFO()) {
            Log.i(LOG_TAG, "-- ON STOP --");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (MyLog.isINFO()) {
            Log.i(LOG_TAG, "--- ON DESTROY ---");
        }
        if (mBTSerialSocket != null) {
            mBTSerialSocket.stop();
        }
        unregisterReceiver(mUSBSerialSocket.mUSBReceiver);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        int tNewScreenOrientation = getCurrentOrientation(newConfig.orientation);
        if (MyLog.isINFO()) {
            Log.i(LOG_TAG, "+ ON ConfigurationChanged, new orientation is "
                    + getScreenOrientationRotationString(tNewScreenOrientation));
        }
        setCurrentScreenOrientationAndRotationVariables(tNewScreenOrientation);
    }

    /*
     * Get current orientation - 1 for Portrait and 2 for Landscape, 8 for reverse Landscape and 9 for reverse Portrait
     */
    int getCurrentOrientation(int aConfigurationOrientation) {
        int tCurrentScreenOrientation;
        if (aConfigurationOrientation == Configuration.ORIENTATION_PORTRAIT) {
            tCurrentScreenOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        } else {
            tCurrentScreenOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        }
        if (mCurrentRotation == Surface.ROTATION_180 || mCurrentRotation == Surface.ROTATION_270) {
            if (tCurrentScreenOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                tCurrentScreenOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
            } else /*if (tCurrentScreenOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)*/ {
                tCurrentScreenOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
            }
        }
        return tCurrentScreenOrientation;
    }

    void setMenuItemConnect(Boolean aIsConnected) {
        mDeviceConnected = aIsConnected;
        if (aIsConnected) {
            if (mMenuItemConnect != null) {
                // modify menu to show disconnect entry
                mMenuItemConnect.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
                mMenuItemConnect.setTitle(R.string.menu_disconnect);
            }
        } else {
            mWaitForDataAfterConnect = false;
            if (mMenuItemConnect != null) {
                // show connect entry
                mMenuItemConnect.setIcon(android.R.drawable.ic_menu_search);
                mMenuItemConnect.setTitle(R.string.menu_connect);
            }
        }
    }

    void setScreenOrientation(int aNewScreenOrientation) {
        setCurrentScreenOrientationAndRotationVariables(aNewScreenOrientation);
        // really set orientation for device
        setRequestedOrientation(aNewScreenOrientation);
    }

    String getScreenOrientationRotationString(int aNewScreenOrientation) {
        String tOrientationString;
        switch (aNewScreenOrientation) {
            case ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED:
                tOrientationString = "unspecified/auto";
                break;
            case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
                tOrientationString = "landscape";
                break;
            case ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:
                tOrientationString = "both/sensor landscape";
                break;
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
                tOrientationString = "reverse landscape";
                break;
            case ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:
                tOrientationString = "portrait";
                break;
            case ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT:
                tOrientationString = "both/sensor portrait";
                break;
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT:
                tOrientationString = "reverse portrait";
                break;

            default:
                tOrientationString = "unknown";
                break;
        }
        return tOrientationString;
    }

    void setCurrentScreenOrientationAndRotationVariables(int aNewScreenOrientation) {
        mCurrentRotation = getWindowManager().getDefaultDisplay().getRotation();
        mCurrentScreenOrientation = aNewScreenOrientation;
        String tCurrentScreenOrientationRotationString = getScreenOrientationRotationString(aNewScreenOrientation) + "="
                + aNewScreenOrientation + " | " + (90 * mCurrentRotation) + " degrees";
        if (MyLog.isINFO()) {
            Log.i(LOG_TAG, "Orientation is now: " + tCurrentScreenOrientationRotationString);
        }
    }

    /*
     * Standard options menu
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (MyLog.isINFO()) {
            Log.i(LOG_TAG, "+ ON CreateOptionsMenu");
        }
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);
        mMenuItemConnect = menu.getItem(0);
        if (mMenuItemConnect != null) {
            // Set connected to right state
            if (mUSBDeviceAttached) {
                setMenuItemConnect(mUSBSerialSocket.mIsConnected);
            } else {
                if (mBTSerialSocket == null) {
                    initBluetooth();
                }
                setMenuItemConnect(mBTSerialSocket.getState() == BluetoothSerialSocket.STATE_CONNECTED);
            }
        }
        return true;
    }

    /*
     * Handler for Option menu
     */
    @SuppressLint("SimpleDateFormat")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (MyLog.isVERBOSE()) {
            Log.v(LOG_TAG, "Item selected=" + item.getTitle());
        }

        if (item.getItemId() == R.id.menu_connect) {
            /*
             * Connect / disconnect button
             */
            if (mUSBDeviceAttached) {
                if (mUSBSerialSocket.mIsConnected) {
                    // GUI disconnect request here -> send disconnect message to Arduino Client and wait for received
                    // stop running service which reset locked orientation in turn
                    mSerialService.writeTwoIntegerEvent(SerialService.EVENT_DISCONNECT, mRPCView.mCurrentViewWidth,
                            mRPCView.mCurrentViewHeight);
                    // wait for the serial output to be sent
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ignored) {
                    }
                    mUSBSerialSocket.disconnect(); // this sends disconnect message which switches the connect menu item
                    // since it is a manual disconnect the USB device is still attached
                    mUSBDeviceAttached = true;
                } else {
                    // Connect request here -> try to connect and set item according to result
                    mUSBSerialSocket.connect(); // this sends connect message which switches the connect menu item
                    // // USB connect does not affect the GUI, so do it manually
                    // setMenuItemConnect(mUSBSerialSocket.mIsConnected);
                }
            } else {
                if (mBTSerialSocket == null) {
                    initBluetooth();
                }
                if (mBTSerialSocket.getState() == BluetoothSerialSocket.STATE_NONE) {
                    // connect request here
                    launchDeviceListActivity();
                } else if (mBTSerialSocket.getState() == BluetoothSerialSocket.STATE_CONNECTED) {
                    // Disconnect request here -> stop running service + reset locked orientation in turn
                    // send disconnect message to Arduino Client
                    mSerialService.writeTwoIntegerEvent(SerialService.EVENT_DISCONNECT, mRPCView.mCurrentViewWidth,
                            mRPCView.mCurrentViewHeight);
                    // wait for the serial output to be sent
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ignored) {
                    }
                    mBTSerialSocket.stop();
                    BluetoothSerialSocket.sLastFailOrDisconnectTimestampMillis = System.currentTimeMillis();
                }
            }
            return true;

        } else if (item.getItemId() == R.id.menu_show_log) {
            startActivity(new Intent(this, LogViewActivity.class));
            return true;

        } else if (item.getItemId() == R.id.menu_preferences) {

            Intent tPreferencesIntent = new Intent(this, BlueDisplayPreferences.class);
            tPreferencesIntent.putExtra(BT_DEVICE_NAME, mAutoConnectDeviceNameFromPreferences);
            startActivity(tPreferencesIntent);
            return true;

            // case R.id.menu_show_graph_testpage:
            // mRPCView.showGraphTestpage();
            // return true;
        } else if (item.getItemId() == R.id.menu_show_testpage) {
            mRPCView.showTestpage();
            return true;

        } else if (item.getItemId() == R.id.menu_show_statistics) {
            if (MyLog.isINFO()) {
                Log.i(LOG_TAG, mSerialService.getStatisticsString());
            }
            showStatisticsMessage();
            return true;

        } else if (item.getItemId() == R.id.menu_about) {
            showAboutDialog();
            return true;
        }
        return false;
    }

    /*****************
     * OTHER HANDLER
     *****************/
    /*
     * The handler that gets information back from *SerialSocket Do not need a BroadcastReceiver here.
     */
    @SuppressLint("HandlerLeak")
    final Handler mHandlerForGUIRequests = new Handler() {

        void startWaitingForDataAfterConnect() {
            mWaitForDataAfterConnect = true;
            /*
             * Send delayed message to handler, which in turn shows a toast if no data received. Flag is reset and message is
             * deleted on each call to RPCView.interpretCommand().
             */
            if (MyLog.isINFO()) {
                Log.i(LOG_TAG, "Start timeout waiting for commands");
            }
            mHandlerForGUIRequests.sendEmptyMessageAtTime(MESSAGE_TIMEOUT_AFTER_CONNECT, SystemClock.uptimeMillis()
                    + (SECONDS_TO_WAIT_FOR_COMMANDS_RECEIVED * 1000));
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_BT_CONNECT:
                    /*
                     * called by BluetoothSerialSocket after connect -> save the connected device's name,set window to always on, reset
                     * button and slider and show toast
                     */
                    if (MyLog.isDEBUG()) {
                        Log.d(LOG_TAG, "MESSAGE_BT_CONNECT: " + mBluetoothDeviceNameConnected);
                    }
                    setMenuItemConnect(true);
                    startWaitingForDataAfterConnect();

                    // set window to always on and reset all structures and flags
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

                    mMyToast = Toast.makeText(getApplicationContext(), getString(R.string.toast_connected_to) + " "
                            + mBluetoothDeviceNameConnected, Toast.LENGTH_SHORT);
                    mMyToast.show();
                    if (mAutoConnectBT) {
                        // Update the preferences with the latest successful connection
                        writeStringPreference(AUTO_CONNECT_MAC_ADDRESS_KEY, mMacAddressToConnect);
                        writeStringPreference(AUTO_CONNECT_DEVICE_NAME_KEY, mDeviceNameToConnect);
                        mAutoConnectDeviceNameFromPreferences = mDeviceNameToConnect;
                    }
                    mMacAddressConnected = mMacAddressToConnect;
                    mBluetoothDeviceNameConnected = mDeviceNameToConnect;
                    break;

                case MESSAGE_USB_CONNECT:
                    /*
                     * called by USBSerialSocket after connect -> set window to always on, reset button and slider and show toast
                     * Seems too late for the initial events, the are not sent because connect flag is not yet true
                     */
                    if (MyLog.isDEBUG()) {
                        Log.d(LOG_TAG, "MESSAGE_USB_CONNECT received");
                    }
                    setMenuItemConnect(true);
                    startWaitingForDataAfterConnect();

                    // set window to always on and reset all structures and flags
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

                    mMyToast = Toast.makeText(getApplicationContext(), getString(R.string.toast_connected_to) + " USB",
                            Toast.LENGTH_SHORT);
                    mMyToast.show();
                    break;

                case MESSAGE_TIMEOUT_AFTER_CONNECT:
                    /*
                     * Show toast, that no data was received after connection was established.
                     */
                    mWaitForDataAfterConnect = false;
                    MyLog.w(LOG_TAG, getString(R.string.toast_connection_data_timeout));
                    // show toast for 3* 3.5 seconds
                    Toast.makeText(getApplicationContext(), getString(R.string.toast_connection_data_timeout), Toast.LENGTH_LONG)
                            .show();
                    Toast.makeText(getApplicationContext(), getString(R.string.toast_connection_data_timeout), Toast.LENGTH_LONG)
                            .show();
                    Toast.makeText(getApplicationContext(), getString(R.string.toast_connection_data_timeout), Toast.LENGTH_LONG)
                            .show();
                    break;

                case MESSAGE_USB_DISCONNECT:
                    /*
                     * show toast, reset locked orientation, set window to normal (not persistent) state, stop tone, unregister sensor
                     * listener
                     */
                    if (MyLog.isDEBUG()) {
                        Log.d(LOG_TAG, "MESSAGE_USB_DISCONNECT -> reset GUI");
                    }
                    setMenuItemConnect(false);

                    Toast.makeText(getApplicationContext(), getString(R.string.toast_connection_lost) + " USB", Toast.LENGTH_SHORT)
                            .show();
                    // reset eventually locked orientation
                    mOrientationisLockedByClient = false;
                    setScreenOrientation(mPreferredScreenOrientation);
                    // set window to normal (not persistent) state
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    mRPCView.mToneGenerator.stopTone();
                    Sensors.disableAllSensors();
                    mSensorEventListener.deregisterAllActiveSensorListeners();
                    break;

                case MESSAGE_BT_DISCONNECT:
                    /*
                     * show toast, reset locked orientation, set window to normal (not persistent) state, stop tone, unregister sensor
                     * listener
                     */
                    if (MyLog.isDEBUG()) {
                        Log.d(LOG_TAG, "MESSAGE_BT_DISCONNECT: " + mBluetoothDeviceNameConnected);
                    }
                    setMenuItemConnect(false);

                    Toast.makeText(getApplicationContext(),
                            getString(R.string.toast_connection_lost) + " " + mBluetoothDeviceNameConnected, Toast.LENGTH_SHORT).show();
                    // reset eventually locked orientation
                    mOrientationisLockedByClient = false;
                    setScreenOrientation(mPreferredScreenOrientation);
                    // set window to normal (not persistent) state
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    mRPCView.mToneGenerator.stopTone();
                    Sensors.disableAllSensors();
                    mSensorEventListener.deregisterAllActiveSensorListeners();
                    break;

                case MESSAGE_TOAST:
                    /*
                     * only used by connectionFailed
                     */
                    if (MyLog.isVERBOSE()) {
                        Log.v(LOG_TAG, "MESSAGE_TOAST");
                    }
                    Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST), Toast.LENGTH_SHORT).show();
                    break;

                case MESSAGE_UPDATE_VIEW:
                    /*
                     * called by SerialService after command processed.
                     */
                    if (MyLog.isVERBOSE()) {
                        Log.v(LOG_TAG, "Received MESSAGE_UPDATE_VIEW -> call mRPCView.invalidate()");
                    }
                    mRPCView.invalidate();
                    break;

                case REQUEST_INPUT_DATA:
                    /*
                     * Shows input data dialog (requested by FUNCTION_GET_NUMBER, FUNCTION_GET_TEXT etc.)
                     */
                    if (MyLog.isVERBOSE()) {
                        Log.v(LOG_TAG, "REQUEST_INPUT_DATA");
                    }
                    showInputDialog(msg.getData().getBoolean(NUMBER_FLAG), msg.getData().getInt(CALLBACK_ADDRESS), msg.getData()
                            .getString(BlueDisplay.DIALOG_PROMPT), msg.getData().getFloat(NUMBER_INITIAL_VALUE));
                    break;
            }

        }
    };

    /*
     * Handles result from started activities
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (MyLog.isDEBUG()) {
            Log.d(LOG_TAG, "onActivityResult " + resultCode);
        }
        switch (requestCode) {

            case REQUEST_CONNECT_DEVICE:
                /*
                 * When DeviceListActivity returns with a device to connect
                 */
                if (resultCode == Activity.RESULT_OK) {
                    // Get the device MAC address
                    String tMacAddress = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    if (tMacAddress.length() < 10) {
                        // MAC Address not specified (no last device) chose a reasonable one
                        if (mMacAddressConnected != null && mMacAddressConnected.length() > 10) {
                            tMacAddress = mMacAddressConnected;
                        } else if (mAutoConnectMacAddressFromPreferences != null && mAutoConnectMacAddressFromPreferences.length() > 10) {
                            tMacAddress = mAutoConnectMacAddressFromPreferences;
                        } else {
                            mMyToast = Toast.makeText(getApplicationContext(), getString(R.string.toast_unable_to_connect)
                                    + "  \"no device\"", Toast.LENGTH_SHORT);
                            mMyToast.show();
                            break;
                        }
                    }
                    // Get the BluetoothDevice object
                    BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(tMacAddress);
                    // Attempt to connect to the device
                    mBTSerialSocket.connect(device);
                    mMacAddressToConnect = tMacAddress;
//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
//                            mDeviceNameToConnect = " No connect permission";
//                        } else {
//                            mDeviceNameToConnect = device.getName();
//                        }
//                    } else {
                    mDeviceNameToConnect = device.getName();
//                    }
                }
                break;

            case REQUEST_ENABLE_BT:
                /*
                 * When the request to enable Bluetooth returns
                 */
                if (resultCode != Activity.RESULT_OK) {
                    if (MyLog.isDEBUG()) {
                        Log.d(LOG_TAG, "BT not enabled");
                    }
                    finishDialogNoBluetooth();
                } else {
                    launchDeviceListActivity();
                }
                break;

        }
    }

    void launchDeviceListActivity() {
        // Launch the DeviceListActivity to see devices
        Intent tDeviceListIntent = new Intent(this, DeviceListActivity.class);
        if (mBluetoothDeviceNameConnected != null && mBluetoothDeviceNameConnected.length() > 1) {
            tDeviceListIntent.putExtra(BT_DEVICE_NAME, mBluetoothDeviceNameConnected);
        } else {
            tDeviceListIntent.putExtra(BT_DEVICE_NAME, mAutoConnectDeviceNameFromPreferences);
        }
        startActivityForResult(tDeviceListIntent, REQUEST_CONNECT_DEVICE);
    }

    /***********************
     * DIALOG + MESSAGES
     ***********************/

    /*
     * Opens an alert, that Bluetooth is not available
     */
    public void finishDialogNoBluetooth() {
        AlertDialog.Builder tBuilder = new AlertDialog.Builder(this);
        tBuilder.setMessage(R.string.alert_dialog_no_bt);
        tBuilder.setIcon(android.R.drawable.ic_dialog_info);
        tBuilder.setTitle(R.string.app_name);
        tBuilder.setCancelable(false);
        tBuilder.setPositiveButton(R.string.alert_dialog_ok, (dialog, id) -> finish());
        AlertDialog tAlertDialog = tBuilder.create();
        tAlertDialog.show();
    }

    private void showAboutDialog() {
        mAboutDialog = new Dialog(BlueDisplay.this);
        mAboutDialog.setContentView(R.layout.about);
        mAboutDialog.setTitle(getString(R.string.app_name) + " " + getString(R.string.app_version));

        Button buttonOK = mAboutDialog.findViewById(R.id.buttonDialog);
        buttonOK.setOnClickListener(v -> mAboutDialog.dismiss());
        mAboutDialog.show();
    }

    /*
     * Show input dialog requested by client
     */
    @SuppressLint("InflateParams")
    public void showInputDialog(boolean aDoNumber, final int aCallbackAddress, String tShortPrompt, float aInitialValue) {
        LayoutInflater tLayoutInflater = LayoutInflater.from(this);
        View tInputView = tLayoutInflater.inflate(R.layout.input_data, null);

        if (tShortPrompt != null && tShortPrompt.length() > 0) {
            final TextView tTitle = tInputView.findViewById(R.id.title_input_data);
            String tPromptLeading = getResources().getString(R.string.title_input_data_prompt_leading);
            if (tPromptLeading.length() == 0 && tShortPrompt.length() > 1) {
                // convert first character to upper case
                tShortPrompt = Character.toUpperCase(tShortPrompt.charAt(0)) + tShortPrompt.substring(1);
            }
            String tPromptTrailing = getResources().getString(R.string.title_input_data_prompt_trailing);
            tTitle.setText(tPromptLeading + " " + tShortPrompt + " " + tPromptTrailing);
        }
        final boolean tDoNumber = aDoNumber;
        final EditText tUserInput = tInputView.findViewById(R.id.editTextDialogUserInput);
        if (aDoNumber) {
            tUserInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                    | InputType.TYPE_NUMBER_FLAG_SIGNED);
        }
        if (aInitialValue != RPCView.NUMBER_INITIAL_VALUE_DO_NOT_SHOW) {
            tUserInput.setText(Float.toString(aInitialValue).replaceAll("\\.?0*$", ""));
        }
        AlertDialog.Builder tBuilder = new AlertDialog.Builder(this);
        tBuilder.setView(tInputView);
        tBuilder.setCancelable(true);
        tBuilder.setPositiveButton(R.string.alert_dialog_ok, (dialog, id) -> {
            if (tDoNumber) {
                Editable tNumber = tUserInput.getText();
                float tValue;
                try {
                    tValue = Float.parseFloat(tNumber.toString());
                    mSerialService.writeNumberCallbackEvent(SerialService.EVENT_NUMBER_CALLBACK, aCallbackAddress, tValue);
                } catch (NumberFormatException e) {
                    MyLog.i(LOG_TAG, "Entered data \"" + tNumber + "\" is no float. No value sent.");
                }
            } else {
                // getText function here - Not yet implemented
            }
        });
        tBuilder.setNegativeButton(R.string.alert_dialog_cancel, (dialog, id) -> dialog.cancel());
        AlertDialog tAlertDialog = tBuilder.create();
        tAlertDialog.show();
    }

    /*
     * Opens an alert and show statistics
     */
    public void showStatisticsMessage() {
        AlertDialog.Builder tBuilder = new AlertDialog.Builder(this);
        tBuilder.setMessage(mSerialService.getStatisticsString());
        tBuilder.setCancelable(false);
        // empty listener
        tBuilder.setPositiveButton(R.string.alert_dialog_ok, (dialog, id) -> {
        });
        // empty listener
        tBuilder.setNegativeButton(R.string.alert_dialog_reset, (dialog, id) -> mSerialService.resetStatistics());
        AlertDialog tAlertDialog = tBuilder.create();
        tAlertDialog.show();
    }

    /****************
     * PREFERENCES
     ****************/
    private void readPreferences() {
        SharedPreferences tSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        /*
         * Load preferences
         */

        // integer preferences do not work on Android. Values of <integer-array>
        // are always stored as Strings in HashMap
        mPreferredScreenOrientation = Integer.parseInt(tSharedPreferences.getString(SCREENORIENTATION_KEY, ""
                + ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED));

        MyLog.setLoglevel(Integer.parseInt(tSharedPreferences.getString(MyLog.LOGLEVEL_KEY, Log.INFO + "")));

        if (mBTSerialSocket != null) {
            mBTSerialSocket.setAllowInsecureConnections(tSharedPreferences.getBoolean(ALLOW_INSECURE_CONNECTIONS_KEY,
                    mBTSerialSocket.getAllowInsecureConnections()));
        }

        if (mRPCView != null) {
            // don't use setter methods since they modify the preference too
            // mRPCView.mTouchMoveEnable = tSharedPreferences.getBoolean(TOUCH_MOVE_KEY, mRPCView.isTouchMoveEnable());
            mRPCView.mShowTouchCoordinates = tSharedPreferences.getBoolean(SHOW_TOUCH_COORDINATES_KEY, false);
        }

        mAutoConnectBT = tSharedPreferences.getBoolean(AUTO_CONNECT_KEY, mAutoConnectBT);
        mAutoConnectMacAddressFromPreferences = tSharedPreferences.getString(AUTO_CONNECT_MAC_ADDRESS_KEY, "");
        mAutoConnectDeviceNameFromPreferences = tSharedPreferences.getString(AUTO_CONNECT_DEVICE_NAME_KEY, "");
    }

    private void writeStringPreference(String aKey, String aValue) {
        SharedPreferences tSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String tOldValue = tSharedPreferences.getString(aKey, "");
        if (!tOldValue.equalsIgnoreCase(aValue)) {
            MyLog.i(LOG_TAG, "Write new preference value=\"" + aValue + "\" for key=" + aKey);
            SharedPreferences.Editor tEdit = tSharedPreferences.edit();
            tEdit.putString(aKey, aValue);
            tEdit.apply();
        }
    }

    @Override
    // Source is from http://stackoverflow.com/questions/9996333/openoptionsmenu-function-not-working-in-ics/17903128#17903128
    public void openOptionsMenu() {
        super.invalidateOptionsMenu(); // This is required at least for Android 15
        Configuration config = getResources().getConfiguration();

        if ((config.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) > Configuration.SCREENLAYOUT_SIZE_LARGE) {

            int originalScreenLayout = config.screenLayout;
            config.screenLayout = Configuration.SCREENLAYOUT_SIZE_LARGE;
            super.openOptionsMenu();
            config.screenLayout = originalScreenLayout;

        } else {
            super.openOptionsMenu();
        }
    }

}
