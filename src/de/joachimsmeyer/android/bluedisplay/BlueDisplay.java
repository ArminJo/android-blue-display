/*
 * 	SUMMARY
 * 	Blue Display is an Open Source Android remote Display for Arduino etc.
 * 	It receives basic draw requests from Arduino etc. over Bluetooth and renders it.
 * 	It also implements basic GUI elements as buttons and sliders.
 * 	It sends touch or GUI callback events over Bluetooth back to Arduino.
 * 
 *  Copyright (C) 2014  Armin Joachimsmeyer
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
 *
 * FEATURES
 * Scale factor to enlarge the picture drawn.
 * Color in RGB 565.
 * Compatibility mode to be compatible with e.g. MI0283QT2 library
 *  - Start position is at upper left (instead of lower right) for pixel bigger than 1x1.
 *  - Start position is at upper left (instead of lower right???) for first pixel of line.
 * 	- End point of line is drawn, so direction of line is irrelevant.
 *  - End position of rectangle is included.
 *  - Start position for character is upper left (of character).
 *  - Character background is filled (enables direct overwrite of character)
 *  - Character integer scale factor supported.
 *  - Use of font size 11, 22, 44 (which is roughly compatible with 7x12 Font)
 *  
 *  SUPPORTED FUNCTIONS
 *  
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

package de.joachimsmeyer.android.bluedisplay;

import java.util.Set;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class BlueDisplay extends Activity {

	// Intent request codes
	private static final int REQUEST_CONNECT_DEVICE = 1;
	private static final int REQUEST_ENABLE_BT = 2;

	// Name of the connected device
	private String mConnectedDeviceName = null;

	// Debugging
	static boolean isDevelopmentTesting = false;
	// static boolean isDevelopmentTesting = true;
	static final String LOG_TAG = "BlueDisplay";
	private static final String LOGLEVEL_KEY = "loglevel";
	private static int mLoglevel = Log.INFO; // 6=ERROR 5=WARN, 4=INFO, 3=DEBUG
												// 2=VERBOSE

	public static void setLogLevel(int aNewLevel) {
		mLoglevel = aNewLevel;
	}

	public static boolean isINFO() {
		return (mLoglevel <= Log.INFO);
	}

	public static boolean isDEBUG() {
		return (mLoglevel <= Log.DEBUG);
	}

	public static boolean isVERBOSE() {
		return (mLoglevel <= Log.VERBOSE);
	}

	public static boolean isDEVELPMENT_TESTING() {
		return (isDevelopmentTesting);
	}

	// Message types sent from the BluetoothSerialService Handler
	public static final int MESSAGE_STATE_CHANGE = 1;
	public static final int MESSAGE_READ = 2;
	public static final int MESSAGE_WRITE = 3;
	public static final int MESSAGE_DEVICE_NAME = 4;
	public static final int MESSAGE_DISCONNECT = 5;
	public static final int MESSAGE_TOAST = 6;
	public static final int MESSAGE_UPDATE_VIEW = 7;

	// Message sent by RPCView
	public static final int REQUEST_INPUT_DATA = 10;
	public static final String CALLBACK_ADDRESS = "callback_address";
	public static final String DIALOG_PROMPT = "dialog_prompt";
	public static final String NUMBER_FLAG = "doNumber";

	// Key names received from the BluetoothSerialService Handler
	public static final String DEVICE_NAME = "device_name";
	public static final String TOAST = "toast";

	public static final String STATE_SCALE_FACTOR = "scale_factor";

	private BluetoothAdapter mBluetoothAdapter = null;

	/*
	 * Main view. Displays the remote data.
	 */
	protected RPCView mRPCView;

	/*
	 * Bluetooth socket handler
	 */
	public BluetoothSerialService mSerialService = null;

	/*
	 * Sensor listener
	 */
	public Sensors mSensorEventListener;

	private boolean mInEnableBT; // We try to enable Bluetooth

	// State variable is declared in BluetoothSerialService
	private static final String SHOW_TOUCH_COORDINATES_KEY = "show_touch_mode";
	private static final String ALLOW_INSECURE_CONNECTIONS_KEY = "allowinsecureconnections";

	private static final String SCREENORIENTATION_KEY = "screenorientation";
	int mPreferredScreenOrientation;
	protected int mActualScreenOrientation;
	protected String mActualScreenOrientationString = "";
	protected int mActualRotation;

	MenuItem mMenuItemConnect;
	// private MenuItem mMenuItemStartStopLogging;

	private Dialog mAboutDialog;

	/******************
	 * Event Handler
	 ******************/
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (BlueDisplay.isINFO()) {
			Log.i(LOG_TAG, "+++ ON CREATE +++");
		}

		/*
		 * Create RPCView
		 */
		mRPCView = new RPCView(this, mHandlerBT);
		setContentView(mRPCView);
		mRPCView.setFocusable(true);
		mRPCView.setFocusableInTouchMode(true);
		mRPCView.requestFocus();

		/*
		 * Bluetooth
		 */
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (mBluetoothAdapter == null) {
			if (!isVERBOSE()) {
				// finishDialogNoBluetooth();
			}
		} else {
			mSerialService = new BluetoothSerialService(this, mHandlerBT);
			if (mSerialService.getState() == BluetoothSerialService.STATE_NONE && mBluetoothAdapter.isEnabled()) {
				Set<BluetoothDevice> tDeviceset = mBluetoothAdapter.getBondedDevices();
				if (tDeviceset.size() == 1) {
					// found one device, try to connect directly
					tDeviceset.iterator().next();
					// Get the BLuetoothDevice object
					BluetoothDevice device = tDeviceset.iterator().next();
					// Attempt to connect to the device
					mSerialService.connect(device);
				} else {
					// Launch the DeviceListActivity to choose device
					Intent serverIntent = new Intent(this, DeviceListActivity.class);
					startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
				}
			} else if (mSerialService.getState() == BluetoothSerialService.STATE_CONNECTED) {
				// stop running service
				mSerialService.stop();
			}
		}
		// Set default values only once (after installation)
		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
		readPreferences();

		setActualScreenOrientation(mPreferredScreenOrientation, true);

		mInEnableBT = false;
		// mRPCView.showTestpage();

		/*
		 * List sensors
		 */
		mSensorEventListener = new Sensors(this, (SensorManager) getSystemService(Context.SENSOR_SERVICE));
		if (BlueDisplay.isINFO()) {
			Log.i(LOG_TAG, "+++ DONE IN ON CREATE +++");
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		if (BlueDisplay.isDEBUG()) {
			Log.i(LOG_TAG, "++ ON START ++");
		}
	}

	@SuppressLint("InlinedApi")
	@Override
	public synchronized void onResume() {
		super.onResume();
		if (BlueDisplay.isDEBUG()) {
			Log.i(LOG_TAG, "+ ON RESUME +");
		}

		if (!mInEnableBT) {
			if ((mBluetoothAdapter != null) && (!mBluetoothAdapter.isEnabled())) {
				Log.i(LOG_TAG, "Activate bluetooth");
				Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
				startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
				mInEnableBT = true;
			}
		}
		int tOldPreferredScreenOrientation = mPreferredScreenOrientation;
		readPreferences();
		if (tOldPreferredScreenOrientation != mPreferredScreenOrientation) {
			setActualScreenOrientation(mPreferredScreenOrientation, true);
		}

		mActualRotation = getWindowManager().getDefaultDisplay().getRotation();
		mSensorEventListener.registerAllActiveSensorListeners();

		mRPCView.invalidate();
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		if (BlueDisplay.isINFO()) {
			Log.i(LOG_TAG, "+ onWindowFocusChanged focus=" + hasFocus);
		}
	}

	@Override
	public synchronized void onPause() {
		super.onPause();
		if (BlueDisplay.isINFO()) {
			Log.i(LOG_TAG, "- ON PAUSE -");
		}
		mSensorEventListener.deregisterAllActiveSensorListeners();
	}

	@Override
	public void onStop() {
		super.onStop();
		if (BlueDisplay.isINFO()) {
			Log.i(LOG_TAG, "-- ON STOP --");
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (BlueDisplay.isINFO()) {
			Log.i(LOG_TAG, "--- ON DESTROY ---");
		}
		if (mSerialService != null) {
			mSerialService.stop();
		}

	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		setActualScreenOrientation(newConfig.orientation, false);
		mActualRotation = getWindowManager().getDefaultDisplay().getRotation();
		if (BlueDisplay.isINFO()) {
			Log.i(LOG_TAG, "--- ON ConfigurationChanged --- " + mActualScreenOrientationString + " rotation=" + mActualRotation);
		}
	}

	/**
	 * 
	 * @param aNewOrientation
	 * @param doRequest
	 *            false if called by onConfigurationChanged, since orientation has already changed because of a former request
	 */
	void setActualScreenOrientation(int aNewOrientation, boolean doRequest) {
		mActualScreenOrientation = aNewOrientation;
		if (doRequest) {
			setRequestedOrientation(aNewOrientation);
		}
		String tOrientation = "portrait";
		if (mActualScreenOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
			tOrientation = "landscape";
		} else if (mActualScreenOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
			tOrientation = "undefined";
		}
		mActualScreenOrientationString = tOrientation;
		if (BlueDisplay.isINFO()) {
			Log.i(LOG_TAG, "Set orientation to " + mActualScreenOrientationString);
		}
	}

	/*
	 * Standard options menu
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		if (BlueDisplay.isINFO()) {
			Log.i(LOG_TAG, "--- ON CreateOptionsMenu ---");
		}
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.option_menu, menu);
		mMenuItemConnect = menu.getItem(0);
		if (mMenuItemConnect != null) {
			if (mSerialService.getState() == BluetoothSerialService.STATE_CONNECTED) {
				// modify menu to show appropriate entry
				mMenuItemConnect.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
				mMenuItemConnect.setTitle(R.string.menu_disconnect);
			} else {
				mMenuItemConnect.setIcon(android.R.drawable.ic_menu_search);
				mMenuItemConnect.setTitle(R.string.menu_connect);
			}
		}
		return true;
	}

	@SuppressLint("SimpleDateFormat")
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (isVERBOSE()) {
			Log.v(LOG_TAG, "Item " + item.getTitle());
		}
		switch (item.getItemId()) {
		case R.id.menu_connect:
			if (mSerialService.getState() == BluetoothSerialService.STATE_NONE) {
				// Launch the DeviceListActivity to see devices and do scan
				Intent serverIntent = new Intent(this, DeviceListActivity.class);
				startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
			} else if (mSerialService.getState() == BluetoothSerialService.STATE_CONNECTED) {
				// disconnect -> stop running service + reset locked orientation in turn
				mSerialService.stop();
			}
			return true;
		case R.id.menu_preferences:
			startActivity(new Intent(this, BlueDisplayPreferences.class));
			return true;
			// case R.id.menu_start_stop_logging:
			// if (mMenuItemStartStopLogging.getTitle() ==
			// getString(R.string.menu_stop_logging)) {
			// mMenuItemStartStopLogging.setTitle(R.string.menu_start_logging);
			// mLoglevel = Log.WARN;
			//
			// // For future use:
			// // String tState = Environment.getExternalStorageState();
			// // if (Environment.MEDIA_MOUNTED.equals(tState)) {
			// // File tLogDirectory =
			// Environment.getExternalStorageDirectory();
			// // SimpleDateFormat format = new
			// SimpleDateFormat("yyyyMMdd_HHmmss");
			// // String currentDateTimeString = format.format(new Date());
			// // String mLogFileName = tLogDirectory.getAbsolutePath() +
			// "/blue_display_" + currentDateTimeString + ".log";
			// // File tLogFile = new File(mLogFileName);
			// // FileOutputStream tFileOutputStream;
			// // try {
			// // tFileOutputStream = new FileOutputStream(tLogFile, true);
			// // PrintWriter tLogFilePrintWriter = new
			// PrintWriter(tFileOutputStream);
			// // } catch (FileNotFoundException e) {
			// // // should not happen since new File(mLogFileName) before
			// // }
			// // }
			// } else {
			// mMenuItemStartStopLogging.setTitle(R.string.menu_stop_logging);
			// mLoglevel = Log.DEBUG;
			// }
			// return true;

		case R.id.menu_show_graph_testpage:
			mRPCView.showGraphTestpage();
			return true;

		case R.id.menu_show_font_testpage:
			mRPCView.showFontTestpage();
			return true;

		case R.id.menu_show_statistics:
			if (BlueDisplay.isINFO()) {
				Log.i(LOG_TAG, mSerialService.getStatisticsString());
			}
			showStatisticsMessage();
			return true;
		case R.id.menu_about:
			showAboutDialog();
			return true;
		}
		return false;
	}

	/*****************
	 * OTHER HANDLER
	 *****************/
	/*
	 * The handler that gets information back from BluetoothSerialService
	 */
	@SuppressLint("HandlerLeak")
	private final Handler mHandlerBT = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MESSAGE_STATE_CHANGE:
				if (isVERBOSE()) {
					Log.v(LOG_TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
				}
				switch (msg.arg1) {
				case BluetoothSerialService.STATE_CONNECTED:
					// modify menu to show disconnect entry
					if (mMenuItemConnect != null) {
						mMenuItemConnect.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
						mMenuItemConnect.setTitle(R.string.menu_disconnect);
					}
					break;

				case BluetoothSerialService.STATE_CONNECTING:
					break;

				case BluetoothSerialService.STATE_LISTEN:
				case BluetoothSerialService.STATE_NONE:
					if (mMenuItemConnect != null) {
						mMenuItemConnect.setIcon(android.R.drawable.ic_menu_search);
						mMenuItemConnect.setTitle(R.string.menu_connect);
					}
					break;
				}
				break;

			case MESSAGE_DEVICE_NAME:
				// save the connected device's name
				mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
				if (isDEBUG()) {
					Log.d(LOG_TAG, "MESSAGE_DEVICE_NAME: " + mConnectedDeviceName);
				}
				Toast.makeText(getApplicationContext(), getString(R.string.toast_connected_to) + " " + mConnectedDeviceName,
						Toast.LENGTH_SHORT).show();
				// set window to always on
				getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
				break;

			case MESSAGE_DISCONNECT:
				if (isDEBUG()) {
					Log.d(LOG_TAG, "MESSAGE_DISCONNECT: " + mConnectedDeviceName);
				}
				Toast.makeText(getApplicationContext(), getString(R.string.toast_connection_lost) + " " + mConnectedDeviceName,
						Toast.LENGTH_SHORT).show();
				// reset locked orientation
				setActualScreenOrientation(mPreferredScreenOrientation, true);
				// set window to normal (not persistent) state
				getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
				break;

			case MESSAGE_TOAST:
				if (isVERBOSE()) {
					Log.v(LOG_TAG, "MESSAGE_TOAST");
				}
				Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST), Toast.LENGTH_SHORT).show();
				break;

			case MESSAGE_UPDATE_VIEW:
				if (isVERBOSE()) {
					Log.v(LOG_TAG, "MESSAGE_UPDATE_VIEW");
				}
				mRPCView.invalidate();
				break;

			case REQUEST_INPUT_DATA:
				if (isVERBOSE()) {
					Log.v(LOG_TAG, "REQUEST_INPUT_DATA");
				}
				showInputDialog(msg.getData().getBoolean(NUMBER_FLAG), msg.getData().getInt(CALLBACK_ADDRESS), msg.getData()
						.getString(BlueDisplay.DIALOG_PROMPT));
				break;
			}

		}
	};

	/*
	 * Handles result from started activities
	 */
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (isDEBUG()) {
			Log.d(LOG_TAG, "onActivityResult " + resultCode);
		}
		switch (requestCode) {

		case REQUEST_CONNECT_DEVICE:
			// When DeviceListActivity returns with a device to connect
			if (resultCode == Activity.RESULT_OK) {
				// Get the device MAC address
				String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
				// Get the BLuetoothDevice object
				BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
				// Attempt to connect to the device
				mSerialService.connect(device);
			}
			break;

		case REQUEST_ENABLE_BT:
			// When the request to enable Bluetooth returns
			if (resultCode != Activity.RESULT_OK) {
				if (isDEBUG()) {
					Log.d(LOG_TAG, "BT not enabled");
				}
				finishDialogNoBluetooth();
			} else {
				// Launch the DeviceListActivity to choose device
				Intent serverIntent = new Intent(this, DeviceListActivity.class);
				startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
			}
			break;

		}
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
		tBuilder.setPositiveButton(R.string.alert_dialog_ok, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				finish();
			}
		});
		AlertDialog tAlertDialog = tBuilder.create();
		tAlertDialog.show();
	}

	private void showAboutDialog() {
		mAboutDialog = new Dialog(BlueDisplay.this);
		mAboutDialog.setContentView(R.layout.about);
		mAboutDialog.setTitle(getString(R.string.app_name) + " " + getString(R.string.app_version));

		Button buttonOK = (Button) mAboutDialog.findViewById(R.id.buttonDialog);
		buttonOK.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mAboutDialog.dismiss();
			}
		});
		mAboutDialog.show();
	}

	@SuppressLint("InflateParams")
	public void showInputDialog(boolean aDoNumber, final int aCallbackAddress, String tShortPrompt) {
		LayoutInflater tLayoutInflater = LayoutInflater.from(this);
		View tInputView = tLayoutInflater.inflate(R.layout.input_data, null);

		if (tShortPrompt != null && tShortPrompt.length() > 0) {
			final TextView tTitle = (TextView) tInputView.findViewById(R.id.title_input_data);
			String tPromptLeading = getResources().getString(R.string.title_input_data_prompt_leading);
			if (tPromptLeading.length() == 0 && tShortPrompt.length() > 1) {
				// convert first character to upper case
				tShortPrompt = Character.toUpperCase(tShortPrompt.charAt(0)) + tShortPrompt.substring(1);
			}
			String tPromptTrailing = getResources().getString(R.string.title_input_data_prompt_trailing);
			tTitle.setText(tPromptLeading + " " + tShortPrompt + " " + tPromptTrailing);
		}

		final EditText tUserInput = (EditText) tInputView.findViewById(R.id.editTextDialogUserInput);
		if (aDoNumber) {
			tUserInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
		}
		AlertDialog.Builder tBuilder = new AlertDialog.Builder(this);
		tBuilder.setView(tInputView);
		tBuilder.setCancelable(true);
		tBuilder.setPositiveButton(R.string.alert_dialog_ok, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				Editable tNumber = tUserInput.getText();
				float tValue;
				try {
					tValue = Float.parseFloat(tNumber.toString());
					mSerialService.writeNumberCallbackEvent(BluetoothSerialService.EVENT_TAG_NUMBER_CALLBACK, aCallbackAddress,
							tValue);
				} catch (NumberFormatException e) {
					tValue = Float.NaN;
					Log.w(LOG_TAG, "Entered data \"" + tNumber + "\" is no float");
				}
			}
		});
		tBuilder.setNegativeButton(R.string.alert_dialog_cancel, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				dialog.cancel();
			}
		});
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
		tBuilder.setPositiveButton(R.string.alert_dialog_ok, new DialogInterface.OnClickListener() {
			// empty listener
			public void onClick(DialogInterface dialog, int id) {
			}
		});
		tBuilder.setNegativeButton(R.string.alert_dialog_reset, new DialogInterface.OnClickListener() {
			// empty listener
			public void onClick(DialogInterface dialog, int id) {
				mSerialService.resetStatistics();
			}
		});
		AlertDialog tAlertDialog = tBuilder.create();
		tAlertDialog.show();
	}

	/****************
	 * PREFERENCES
	 ****************/
	public static final int ORIENTATION_PORTRAIT = 1;
	public static final int ORIENTATION_LANDSCAPE = 2;

	private void readPreferences() {
		SharedPreferences tSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		/*
		 * Load preferences
		 */
		// integer preferences do not work on Android. Values of <integer-array>
		// are always stored as Strings in HashMap
		int tScreenOrientation = Integer.parseInt(tSharedPreferences.getString(SCREENORIENTATION_KEY, ""
				+ ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED));
		switch (tScreenOrientation) {
		case ORIENTATION_PORTRAIT:
			mPreferredScreenOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
			break;
		case ORIENTATION_LANDSCAPE:
			mPreferredScreenOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
			break;
		default:
			mPreferredScreenOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
		}

		mLoglevel = Integer.parseInt(tSharedPreferences.getString(LOGLEVEL_KEY, Log.INFO + ""));

		if (mSerialService != null) {
			mSerialService.setAllowInsecureConnections(tSharedPreferences.getBoolean(ALLOW_INSECURE_CONNECTIONS_KEY,
					mSerialService.getAllowInsecureConnections()));
		}
		if (mRPCView != null) {
			// don't use setter methods since they modify the preference too
			// mRPCView.mTouchMoveEnable = tSharedPreferences.getBoolean(TOUCH_MOVE_KEY, mRPCView.isTouchMoveEnable());
			mRPCView.mShowTouchCoordinates = tSharedPreferences.getBoolean(SHOW_TOUCH_COORDINATES_KEY, false);
		}
	}

	// public void setTouchMoveModePreference(boolean aNewMode) {
	// Editor tEditor = PreferenceManager.getDefaultSharedPreferences(this).edit();
	// tEditor.putBoolean(TOUCH_MOVE_KEY, aNewMode);
	// tEditor.apply();
	// }

}
