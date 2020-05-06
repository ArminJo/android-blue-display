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
 * This service handles the Bluetooth connection and the Bluetooth socket communication.
 */

package de.joachimsmeyer.android.bluedisplay;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

/**
 * This class does all the work for setting up and managing Bluetooth connections with other devices. It has a thread for connecting
 * with a device, and a thread for performing data transmissions when connected.
 */
public class BluetoothSerialSocket {
	// Logging
	private static final String LOG_TAG = "BluetoothSerialSocket";

	private static final UUID SerialPortServiceClass_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

	// Member fields
	private final BluetoothAdapter mAdapter;
	private final Handler mHandler;
	private ConnectThread mConnectThread;
	private ConnectedThread mConnectedThread;
	private int mState;

	public static long sLastFailOrDisconnectTimestampMillis = 0;
	private boolean mAllowInsecureConnections;

	public void setAllowInsecureConnections(boolean allowInsecureConnections) {
		mAllowInsecureConnections = allowInsecureConnections;
	}

	public boolean getAllowInsecureConnections() {
		return mAllowInsecureConnections;
	}

	private BlueDisplay mBlueDisplayContext;
	SerialService mSerialService;

	// Constants that indicate the current connection state
	public static final int STATE_NONE = 0; // we're doing nothing
	public static final int STATE_CONNECTING = 1; // now initiating an BT connection
	public static final int STATE_CONNECTED = 2; // now connected to a remote device

	/**
	 * Constructor. Prepares a new BluetoothChat session.
	 * 
	 * @param context
	 *            The UI Activity Context
	 * @param handler
	 *            A Handler to send messages back to the UI Activity
	 */
	public BluetoothSerialSocket(BlueDisplay aContext, SerialService aSerialService, Handler aHandler) {
		mAdapter = BluetoothAdapter.getDefaultAdapter();
		mState = STATE_NONE;
		mSerialService = aSerialService;
		mHandler = aHandler;
		mBlueDisplayContext = aContext;
		mAllowInsecureConnections = true;
	}

	/**
	 * Return the current connection state.
	 */
	public synchronized int getState() {
		return mState;
	}

	/**
	 * Start the ConnectThread to initiate a connection to a remote device.
	 * 
	 * @param device
	 *            The BluetoothDevice to connect
	 */
	public synchronized void connect(BluetoothDevice device) {
		if (MyLog.isINFO()) {
			MyLog.i(LOG_TAG, "Start connect thread and try to connect to: " + device);
		}

		// Cancel any thread attempting to make a connection
		if (mState == STATE_CONNECTING) {
			if (mConnectThread != null) {
				mConnectThread.cancel();
				mConnectThread = null;
			}
		}

		// Cancel any thread currently running a connection
		if (mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}

		// Start the thread to connect with the given device
		mConnectThread = new ConnectThread(device);
		mConnectThread.start();
		mState = STATE_CONNECTING;
	}

	/**
	 * Start the ConnectedThread to begin managing a Bluetooth connection
	 * 
	 * @param socket
	 *            The BluetoothSocket on which the connection was made
	 * @param device
	 *            The BluetoothDevice that has been connected
	 */
	public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
		// Cancel the thread that completed the connection
		if (mConnectThread != null) {
			// can it really happen to end up here ???
			mConnectThread.cancel();
			mConnectThread = null;
		}

		// Cancel any thread currently running a connection
		if (mConnectedThread != null) {
			// can it really happen to end up here ???
			mConnectedThread.cancel();
			mConnectedThread = null;
		}

		// clear local log
		MyLog.clear();
		// output this with level warning, so it can not be suppressed
		MyLog.w(LOG_TAG, "Connected to " + device.getName() + " - " + device.getAddress());

		// Start the thread to manage the connection and perform transmissions
		mConnectedThread = new ConnectedThread(socket);
		mConnectedThread.start();

		/*
		 * Send the event to the UI Activity, which in turn shows the connected toast and sets window to always on
		 */
		Message msg = mHandler.obtainMessage(BlueDisplay.MESSAGE_BT_CONNECT);
		mHandler.sendMessage(msg);

		mState = STATE_CONNECTED;
	}

	/**
	 * Stop all threads
	 */
	public synchronized void stop() {
		if (MyLog.isDEBUG()) {
			Log.d(LOG_TAG, "stop threads");
		}

		if (mConnectThread != null) {
			mConnectThread.cancel();
			mConnectThread = null;
		}

		if (mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}

		mState = STATE_NONE;
	}

	/**
	 * Write to the ConnectedThread in an unsynchronized manner
	 */
	public void writeEvent(byte[] aEventDataBuffer, int aEventDataLength) {
		// Create temporary object
		ConnectedThread r;
		// Synchronize a copy of the ConnectedThread
		synchronized (this) {
			if (mState != STATE_CONNECTED)
				return;
			r = mConnectedThread;
		}
		// Perform the write unsynchronized
		r.sendEvent(aEventDataBuffer, aEventDataLength);
	}

	/**
	 * Indicate that the connection attempt failed and notify the UI Activity.
	 */
	private void connectionFailed(String aName) {
		mState = STATE_NONE;
		sLastFailOrDisconnectTimestampMillis = System.currentTimeMillis();

		// Send a failure message back to the Activity
		Message msg = mHandler.obtainMessage(BlueDisplay.MESSAGE_TOAST);
		Bundle bundle = new Bundle();
		bundle.putString(BlueDisplay.TOAST, mBlueDisplayContext.getString(R.string.toast_unable_to_connect) + " " + aName);
		msg.setData(bundle);
		mHandler.sendMessage(msg);
	}

	/**
	 * Indicate that the connection was lost and notify the UI Activity.
	 */
	private void connectionLost() {
		mState = STATE_NONE;

		// Send a disconnect message back to the Activity
		mHandler.sendEmptyMessage(BlueDisplay.MESSAGE_BT_DISCONNECT);
	}

	/**
	 * This thread runs while attempting to make an outgoing connection with a device. It runs straight through; the connection
	 * either succeeds or fails.
	 */
	private class ConnectThread extends Thread {
		private final BluetoothSocket mmSocket;
		private final BluetoothDevice mmDevice;

		public ConnectThread(BluetoothDevice device) {
			mmDevice = device;
			BluetoothSocket tSocket = null;
			String tSocketType = "insecure";
			/*
			 * try with high priority to avoid long breaks which seems to crash the BT driver if to much data was received e.g. in
			 * the Arduino DSO example
			 */
			setPriority(MAX_PRIORITY);

			// Get a BluetoothSocket for a connection with the
			// given BluetoothDevice
			try {
				if (mAllowInsecureConnections) {
					Method method = device.getClass().getMethod("createRfcommSocket", new Class[] { int.class });
					tSocket = (BluetoothSocket) method.invoke(device, 1);
				} else {
					tSocketType = "secure";
					tSocket = device.createRfcommSocketToServiceRecord(SerialPortServiceClass_UUID);
				}
			} catch (Exception e) {
				Log.e(LOG_TAG, "Socket Type: " + tSocketType + "create() failed", e);
			}
			mmSocket = tSocket;
		}

		public void run() {
			if (MyLog.isINFO()) {
				Log.i(LOG_TAG, "run mConnectThread");
			}
			setName("ConnectThread");

			// Always cancel discovery because it will slow down a connection
			mAdapter.cancelDiscovery();

			// Make a connection to the BluetoothSocket
			try {
				// This is a blocking call and will only return on a
				// successful connection or an exception
				mmSocket.connect();
			} catch (IOException e) {
				Log.i(LOG_TAG, "Connect() failed. BT device maybe not active or not in range? Exception=" + e.getMessage());
				connectionFailed(mmDevice.getName());
				// Close the socket
				try {
					mmSocket.close();
				} catch (IOException e2) {
					Log.e(LOG_TAG, "unable to close() socket during connection failure", e2);
				}
				return;
			}

			// Reset the ConnectThread because we're done
			synchronized (BluetoothSerialSocket.this) {
				mConnectThread = null;
			}

			// Start the connected thread
			connected(mmSocket, mmDevice);
		}

		public void cancel() {
			try {
				mmSocket.close();
			} catch (IOException e) {
				Log.e(LOG_TAG, "close() of connect socket failed", e);
			}
		}
	}

	/**
	 * This thread runs during a connection with a remote device. It handles all incoming and outgoing transmissions.
	 */
	private class ConnectedThread extends Thread {
		private final BluetoothSocket mmSocket;
		private InputStream mmInStream = null;
		private OutputStream mmOutStream = null;

		// private byte[] mSendByteBuffer = new byte[CALLBACK_DATA_SIZE]; // Static buffer since we only send one item at a time

		public ConnectedThread(BluetoothSocket socket) {
			if (MyLog.isINFO()) {
				Log.i("ConnectedThread", "+++ ON CREATE +++");
			}
			mmSocket = socket;

			// Get the BluetoothSocket input and output streams
			try {
				mmInStream = socket.getInputStream();
				mmOutStream = socket.getOutputStream();
			} catch (IOException e) {
				Log.e(LOG_TAG, "temp sockets not created", e);
			}
		}

		public void run() {
			if (MyLog.isINFO()) {
				Log.i(LOG_TAG, "BEGIN mConnectedThread");
			}

			/*
			 * reset flags, buttons, sliders and sensors (and log this :-)) Must be at first
			 */
			mBlueDisplayContext.mRPCView.resetAll();

			// Read old content from Bluetooth buffer
			// Data is present if data was sent before connection
			int tReadSize = 0;
			int tBytesAvailable;
			/*
			 * Read old bytes from input stream
			 */
			try {
				try {
					// 50 is to little, 100 works sometimes, 200 works reliable.
					Thread.sleep(200);
				} catch (InterruptedException e) {
					// Just do nothing
				}
				tBytesAvailable = mmInStream.available();
				while (tBytesAvailable > 0) {
					tReadSize += mmInStream.read(mSerialService.mDataBuffer, 0, tBytesAvailable);
					tBytesAvailable = mmInStream.available();
				}
				if (tReadSize > 0) {
					if (MyLog.isINFO()) {
						MyLog.i("ConnectedThread", "read " + tReadSize + " old bytes from Bluetooth stream");
					}
				}
			} catch (IOException e1) {
				// end up here if cancel() / mmSocket.close() was called before
				if (MyLog.isVERBOSE()) {
					Log.v(LOG_TAG, "Start run - catched IOException - assume disconnected");
				}
				connectionLost(); // show toast
				return;
			}
			/*
			 * Now we have read all old bytes from input stream. Start and initialize big ring buffer.
			 */
			mSerialService.resetReceiveBuffer();
			mSerialService.resetStatistics();
			// signal connection to arduino
			mSerialService.signalBlueDisplayConnection();

			/*
			 * read forever into buffer
			 */
			int tReadLength;
			// Keep listening to the InputStream while connected
			while (true) {
				try {
					// Read block of bytes from InputStream
//					long tStartTimestampMillis = System.currentTimeMillis();
					tReadLength = mmInStream.read(mSerialService.mBigReceiveBuffer, mSerialService.mReceiveBufferInIndex, 256);
					mSerialService.handleReceived(tReadLength);
//					long tReadDuration = System.currentTimeMillis() - tStartTimestampMillis;
//					if (tReadDuration > 100) {
//						Log.e(LOG_TAG, "Read duration=" + tReadDuration + "ms, length=" + tReadLength);
//					} else {
//						Log.d(LOG_TAG, "Read duration=" + tReadDuration + "ms, length=" + tReadLength);
//					}

				} catch (IOException e) {
					// end up here if cancel() / mmSocket.close() was called
					// before
					if (MyLog.isINFO()) {
						MyLog.i(LOG_TAG, "Catched IOException - assume disconnected");
					}
					connectionLost(); // show toast
					break;
				}
			}
		}

		/*
		 * send prepared data buffer
		 */
		public void sendEvent(byte[] aEventDataBuffer, int aEventDataLength) {
			try {
				mmOutStream.write(aEventDataBuffer, 0, aEventDataLength);
				mmOutStream.flush();
			} catch (IOException e) {
				MyLog.e(LOG_TAG, "Exception during write: " + e);
			}
		}

		public void cancel() {
			try {
				mmSocket.close();
			} catch (IOException e) {
				MyLog.e(LOG_TAG, "close() of connect socket failed: " + e);
			}
		}
	}

}
