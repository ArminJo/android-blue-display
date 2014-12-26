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
 * This class does all the work for setting up and managing Bluetooth connections with other devices. It has a thread that listens
 * for incoming connections, a thread for connecting with a device, and a thread for performing data transmissions when connected.
 */
public class BluetoothSerialService {
	// Logging
	private static final String LOG_TAG = "BluetoothSerialService";

	private static final UUID SerialPortServiceClass_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

	// Forces the end of writing to bitmap after 0.5 seconds and thus allow
	// bitmap to be displayed
	private static final long MAX_DRAW_INTERVAL_NANOS = 500000000;

	// Member fields
	private final BluetoothAdapter mAdapter;
	private final Handler mHandler;
	private ConnectThread mConnectThread;
	private ConnectedThread mConnectedThread;
	private int mState;

	// Statistics
	public int mStatisticNumberOfReceivedBytes;
	public int mStatisticNumberOfReceivedCommands;
	public int mStatisticNumberOfReceivedChartCommands;
	public int mStatisticNumberOfSentBytes;
	public int mStatisticNumberOfSentCommands;
	public int mStatisticNumberOfBufferWrapArounds;
	public long mStatisticNanoTimeForCommands;
	public long mStatisticNanoTimeForChart;

	public final static int EVENT_TAG_CONNECTION_BUILD_UP = 0x10;
	public final static int EVENT_TAG_RESIZE_ACTION = 0x11;
	// Must be below 0x20 since it only sends 4 bytes data
	public final static int EVENT_TAG_LONG_TOUCH_DOWN_CALLBACK_ACTION = 0x18;

	public final static int EVENT_TAG_FIRST_CALLBACK_ACTION = 0x20;
	public final static int EVENT_TAG_BUTTON_CALLBACK_ACTION = 0x20;
	public final static int EVENT_TAG_SLIDER_CALLBACK_ACTION = 0x21;
	public final static int EVENT_TAG_SWIPE_CALLBACK_ACTION = 0x22;

	public final static int EVENT_TAG_NUMBER_CALLBACK = 0x28;

	private boolean mAllowInsecureConnections;

	public void setAllowInsecureConnections(boolean allowInsecureConnections) {
		mAllowInsecureConnections = allowInsecureConnections;
	}

	public boolean getAllowInsecureConnections() {
		return mAllowInsecureConnections;
	}

	private BlueDisplay mBlueDisplayContext;

	// Constants that indicate the current connection state
	public static final int STATE_NONE = 0; // we're doing nothing
	public static final int STATE_LISTEN = 1; // now listening for incoming
												// connections
	public static final int STATE_CONNECTING = 2; // now initiating an outgoing
													// connection
	public static final int STATE_CONNECTED = 3; // now connected to a remote
													// device

	/**
	 * Constructor. Prepares a new BluetoothChat session.
	 * 
	 * @param context
	 *            The UI Activity Context
	 * @param handler
	 *            A Handler to send messages back to the UI Activity
	 */
	public BluetoothSerialService(BlueDisplay aContext, Handler aHandler) {
		mAdapter = BluetoothAdapter.getDefaultAdapter();
		mState = STATE_NONE;
		mHandler = aHandler;
		mBlueDisplayContext = aContext;
		mAllowInsecureConnections = true;
		resetStatistics();
	}

	void resetStatistics() {
		mStatisticNumberOfReceivedBytes = 0;
		mStatisticNumberOfReceivedCommands = 0;
		mStatisticNumberOfReceivedChartCommands = 0;
		mStatisticNumberOfSentBytes = 0;
		mStatisticNumberOfSentCommands = 0;
		mStatisticNumberOfBufferWrapArounds = 0;
		mStatisticNanoTimeForCommands = 0;
		mStatisticNanoTimeForChart = 0;
	}

	/**
	 * Set the current state of the chat connection
	 * 
	 * @param state
	 *            An integer defining the current connection state
	 */
	private synchronized void setState(int state) {
		if (BlueDisplay.isDEBUG()) {
			Log.d(LOG_TAG, "setState() " + mState + " -> " + state);
		}
		mState = state;

		// Give the new state to the Handler so the UI Activity can update
		mHandler.obtainMessage(BlueDisplay.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
	}

	/**
	 * Return the current connection state.
	 */
	public synchronized int getState() {
		return mState;
	}

	public String getStatisticsString() {
		String tReturn = mStatisticNumberOfReceivedBytes + " bytes / " + mStatisticNumberOfReceivedCommands + " / "
				+ mStatisticNumberOfReceivedChartCommands + " commands received\n";
		tReturn += mStatisticNumberOfSentBytes + " bytes / " + mStatisticNumberOfSentCommands + " commands sent\n";
		if (mStatisticNumberOfReceivedCommands != 0) {
			tReturn += ((mStatisticNanoTimeForCommands / 1000) / mStatisticNumberOfReceivedCommands) + " µs per command\n";
		}
		if (mStatisticNumberOfReceivedChartCommands != 0) {
			tReturn += ((mStatisticNanoTimeForChart / 1000) / mStatisticNumberOfReceivedChartCommands) + " µs per chart command\n";
		}
		tReturn += "Buffer wrap arounds=" + mStatisticNumberOfBufferWrapArounds + "\n";
		int tInputBufferOutIndex = mReceiveBufferOutIndex;
		int tBytes = getBufferBytesAvailable();
		tReturn += "InputBuffer: in=" + mReceiveBufferInIndex + ", out=" + tInputBufferOutIndex + ", not processed=" + tBytes
				+ ", size=" + SIZE_OF_IN_BUFFER + "\n";
		if (BlueDisplay.isDEBUG()) {
			if (tBytes > 0) {
				if (tBytes > 20) {
					tBytes = 20;
				}
				// output content but max. 20 bytes
				StringBuilder tContent = new StringBuilder(5 * 24);
				int tValue;
				for (int i = 0; i < tBytes; i++) {
					tContent.append(" 0x");
					tValue = mBigReceiveBuffer[tInputBufferOutIndex + i];
					tContent.append(Integer.toHexString(tValue & 0xFF));
				}
				tReturn += tContent.toString() + "\n";
			}
		}

		RPCView tRPCView = mBlueDisplayContext.mRPCView;
		tReturn += "\nScale=" + tRPCView.mScaleFactor * 100 + "%    " + tRPCView.mRequestedCanvasWidth + "*"
				+ tRPCView.mRequestedCanvasHeight + " -> " + tRPCView.mActualCanvasWidth + "*" + tRPCView.mActualCanvasHeight
				+ "  max=" + tRPCView.mActualViewWidth + "/" + tRPCView.mActualViewHeight + "\n";
		tReturn += "Codepage=" + System.getProperty("file.encoding");
		return tReturn;
	}

	/**
	 * Start the ConnectThread to initiate a connection to a remote device.
	 * 
	 * @param device
	 *            The BluetoothDevice to connect
	 */
	public synchronized void connect(BluetoothDevice device) {
		if (BlueDisplay.isDEBUG()) {
			Log.d(LOG_TAG, "connect to: " + device);
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
		setState(STATE_CONNECTING);
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
		if (BlueDisplay.isDEBUG()) {
			Log.d(LOG_TAG, "connected");
		}

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

		// Start the thread to manage the connection and perform transmissions
		mConnectedThread = new ConnectedThread(socket);
		mConnectedThread.start();

		// Send the name of the connected device back to the UI Activity
		Message msg = mHandler.obtainMessage(BlueDisplay.MESSAGE_DEVICE_NAME);
		Bundle bundle = new Bundle();
		bundle.putString(BlueDisplay.DEVICE_NAME, device.getName());
		msg.setData(bundle);
		mHandler.sendMessage(msg);

		setState(STATE_CONNECTED);
	}

	/**
	 * Stop all threads
	 */
	public synchronized void stop() {
		if (BlueDisplay.isDEBUG()) {
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

		setState(STATE_NONE);
	}

	/**
	 * Write to the ConnectedThread in an unsynchronized manner
	 * 
	 * @param out
	 *            The bytes to write
	 * @see ConnectedThread#write(byte[])
	 */
	public void writeEvent(int aEventType, int aXPos, int aYPos) {
		// Create temporary object
		ConnectedThread r;
		// Synchronize a copy of the ConnectedThread
		synchronized (this) {
			if (mState != STATE_CONNECTED)
				return;
			r = mConnectedThread;
		}
		// Perform the write unsynchronized
		r.write(aEventType, aXPos, aYPos);
	}

	public void writeEvent(int aEventType, int aButtonIndex, int aCallbackAddress, int aValue) {
		// Create temporary object
		ConnectedThread r;
		// Synchronize a copy of the ConnectedThread
		synchronized (this) {
			if (mState != STATE_CONNECTED)
				return;
			r = mConnectedThread;
		}
		// Perform the write unsynchronized
		r.write(aEventType, aButtonIndex, aCallbackAddress, aValue);
	}

	public void writeEvent(int aEventType, int aCallbackAddress, float aValue) {
		// Create temporary object
		ConnectedThread r;
		// Synchronize a copy of the ConnectedThread
		synchronized (this) {
			if (mState != STATE_CONNECTED)
				return;
			r = mConnectedThread;
		}
		// Perform the write unsynchronized
		r.write(aEventType, aCallbackAddress, aValue);
	}

	public void writeEvent(int aEventType, int aIsXDirection, int aStartX, int aStartY, int aDeltaX, int aDeltaY) {
		// Create temporary object
		ConnectedThread r;
		// Synchronize a copy of the ConnectedThread
		synchronized (this) {
			if (mState != STATE_CONNECTED)
				return;
			r = mConnectedThread;
		}
		// Perform the write unsynchronized
		r.write(aEventType, aIsXDirection, aStartX, aStartY, aDeltaX, aDeltaY);
	}

	/**
	 * Indicate that the connection attempt failed and notify the UI Activity.
	 */
	private void connectionFailed() {
		setState(STATE_NONE);

		// Send a failure message back to the Activity
		Message msg = mHandler.obtainMessage(BlueDisplay.MESSAGE_TOAST);
		Bundle bundle = new Bundle();
		bundle.putString(BlueDisplay.TOAST, mBlueDisplayContext.getString(R.string.toast_unable_to_connect));
		msg.setData(bundle);
		mHandler.sendMessage(msg);
	}

	/**
	 * Indicate that the connection was lost and notify the UI Activity.
	 */
	private void connectionLost() {
		setState(STATE_NONE);

		// Send a failure message back to the Activity
		Message msg = mHandler.obtainMessage(BlueDisplay.MESSAGE_TOAST);
		Bundle bundle = new Bundle();
		bundle.putString(BlueDisplay.TOAST, mBlueDisplayContext.getString(R.string.toast_connection_lost));
		msg.setData(bundle);
		mHandler.sendMessage(msg);
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
			if (BlueDisplay.isINFO()) {
				Log.i(LOG_TAG, "BEGIN mConnectThread");
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
				connectionFailed();
				// Close the socket
				try {
					mmSocket.close();
				} catch (IOException e2) {
					Log.e(LOG_TAG, "unable to close() socket during connection failure", e2);
				}
				return;
			}

			// Reset the ConnectThread because we're done
			synchronized (BluetoothSerialService.this) {
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

	public static final int SIZE_OF_IN_BUFFER = 40960;
	public static final int MIN_MESSAGE_SIZE = 5; // data message with one byte
	public static final int MIN_COMMAND_SIZE = 6; // command message with one
													// (short) parameter

	public volatile byte[] mBigReceiveBuffer = new byte[SIZE_OF_IN_BUFFER];
	private volatile int mInputBufferWrapAroundIndex = 0; // first free byte
															// after last byte in
															// buffer
	private volatile int mReceiveBufferInIndex; // first free byte
	private volatile int mReceiveBufferOutIndex; // first unprocessed byte
	byte[] mDataBuffer = new byte[4096];
	private volatile boolean inBufferReadingLock = false;

	/*
	 * internal state for searchCommand()
	 */
	// to signal searchCommand(), that searchState must be loaded.
	private boolean searchStateMustBeLoaded = false;

	// To signal BT receive thread, that invalidate() message should be sent on next read.
	private boolean mNeedUpdateViewMessage = true;

	private byte searchStateCommand;
	private int searchStateParamsLength;
	private byte searchStateCommandReceived;
	private int searchStateDataLengthToWaitFor = MIN_MESSAGE_SIZE;

	public static final byte SYNC_TOKEN = (byte) 0xA5;
	public static final int MAX_NUMBER_OF_PARAMS = 12;
	private static int[] mParameters = new int[MAX_NUMBER_OF_PARAMS];

	public int getInputBufferInIndex() {
		return mReceiveBufferInIndex;
	}

	int getBufferBytesAvailable() {
		if (mReceiveBufferOutIndex == mReceiveBufferInIndex) {
			// buffer empty
			return 0;
		}
		// check for wrap around
		if (mReceiveBufferInIndex < mReceiveBufferOutIndex) {
			return (mInputBufferWrapAroundIndex - (mReceiveBufferOutIndex - mReceiveBufferInIndex));
		}
		return (mReceiveBufferInIndex - mReceiveBufferOutIndex);
	}

	/*
	 * Get byte from buffer, clear buffer, increment pointer and handle wrap around
	 */
	byte getByteFromBuffer() {
		byte tByte = mBigReceiveBuffer[mReceiveBufferOutIndex];
		// clear processed content
		mBigReceiveBuffer[mReceiveBufferOutIndex] = 0x00;
		mReceiveBufferOutIndex++;
		if (mInputBufferWrapAroundIndex > 0 && mReceiveBufferOutIndex >= mInputBufferWrapAroundIndex) {
			mInputBufferWrapAroundIndex = 0;
			mReceiveBufferOutIndex = 0;
		}
		return tByte;
	}

	/*
	 * Search the input buffer for valid commands and call interpreteCommand() as long as there is data available Returns true if it
	 * must be called again.
	 */
	boolean searchCommand(RPCView aRPCView) {
		if (inBufferReadingLock || (mReceiveBufferOutIndex == mReceiveBufferInIndex)) {
			if (BlueDisplay.isDEBUG()) {
				Log.d(LOG_TAG, "searchCommand just returns. Lock=" + inBufferReadingLock + " BufferInIndex="
						+ mReceiveBufferInIndex);
			}
			return false;
		}
		boolean tRetval = false;
		long tStart = System.nanoTime();
		long tNanosForChart = 0;
		inBufferReadingLock = true;
		byte tCommand = 0;
		int tParamsLength = 0;
		byte tByte;
		int i;
		byte tCommandReceived;
		int tLengthReceived;
		int tStartIn = mReceiveBufferInIndex;
		int tStartOut = mReceiveBufferOutIndex;

		// If data command, wait also for data
		while (getBufferBytesAvailable() >= searchStateDataLengthToWaitFor) {
			mNeedUpdateViewMessage = false;

			if (searchStateMustBeLoaded) {
				// restore state
				tLengthReceived = searchStateDataLengthToWaitFor;
				tCommandReceived = searchStateCommandReceived;
				tCommand = searchStateCommand;
				tParamsLength = searchStateParamsLength;
				searchStateMustBeLoaded = false;
			} else {
				/*
				 * check for SYNC Token
				 */
				do {
					tByte = getByteFromBuffer();
					if (tByte != SYNC_TOKEN) {
						if (mReceiveBufferOutIndex == mReceiveBufferInIndex) {
							inBufferReadingLock = false;
							Log.e(LOG_TAG, "Sync Token not found til end of buffer. End searchCommand. Out=" + tStartOut + "->"
									+ mReceiveBufferOutIndex + " In=" + tStartIn + "->" + mReceiveBufferInIndex);
							mNeedUpdateViewMessage = true;
							return false;
						}
						Log.w(LOG_TAG, "Byte=" + Integer.toHexString(tByte) + " at:" + mReceiveBufferOutIndex + " is no SYNC_TOKEN");
					}
				} while (tByte != SYNC_TOKEN);

				/*
				 * Read command token from InputStream
				 */
				tCommandReceived = getByteFromBuffer();

				/*
				 * read parameter length
				 */
				tByte = getByteFromBuffer();
				tLengthReceived = convert2BytesToInt(tByte, getByteFromBuffer());

				if (BlueDisplay.isDEBUG()) {
					Log.d(LOG_TAG, "Command=0x" + Integer.toHexString(tCommandReceived) + " length=" + tLengthReceived + " at ptr="
							+ (mReceiveBufferOutIndex - 1));
				}

				/*
				 * Plausi for tLengthReceived
				 */
				if ((tCommandReceived < 8 && tLengthReceived > mDataBuffer.length)
						|| (tCommandReceived >= 8 && tLengthReceived > MAX_NUMBER_OF_PARAMS * 2)) {
					Log.e(LOG_TAG,
							"ParamsLength of " + tLengthReceived + " wrong. Command=0x" + Integer.toHexString(tCommandReceived)
									+ " Out=" + mReceiveBufferOutIndex);
					continue;
				}

				/*
				 * Save state and return if not enough bytes are available in buffer
				 */
				if (getBufferBytesAvailable() < tLengthReceived) {
					searchStateDataLengthToWaitFor = tLengthReceived;
					searchStateCommandReceived = tCommandReceived;
					searchStateCommand = tCommand;
					searchStateParamsLength = tParamsLength;

					searchStateMustBeLoaded = true;
					if (BlueDisplay.isDEBUG()) {
						Log.d(LOG_TAG, "Not enough data");
					}
					// break in order to draw the bitmap
					tRetval = true;
					break;
				}
			}

			/*
			 * All data available to interpret command or data
			 */
			if (tCommandReceived <= RPCView.LAST_FUNCTION_TAG_DATAFIELD) {
				long tStart1 = System.nanoTime();
				/*
				 * Data buffer command
				 */
				for (i = 0; i < tLengthReceived; i++) {
					mDataBuffer[i] = getByteFromBuffer();
				}
				if (BlueDisplay.isVERBOSE()) {
					StringBuilder tData = new StringBuilder("Data=");
					for (i = 0; i < tLengthReceived; i++) {
						// Output parameter buffer as character
						tData.append((char) mDataBuffer[i]);
					}
					Log.v(LOG_TAG, tData.toString());
				}
				/*
				 * now both command and data buffer filled
				 */
				searchStateDataLengthToWaitFor = MIN_COMMAND_SIZE;
				aRPCView.interpreteCommand(tCommand, mParameters, tParamsLength, mDataBuffer, null, tLengthReceived);
				if (tCommand == RPCView.FUNCTION_TAG_DRAW_CHART
						|| tCommand == RPCView.FUNCTION_TAG_DRAW_CHART_WITHOUT_DIRECT_RENDERING) {
					mStatisticNumberOfReceivedChartCommands++;
					tNanosForChart += System.nanoTime() - tStart1;
					if (tCommand == RPCView.FUNCTION_TAG_DRAW_CHART) {
						// break in order to draw the bitmap
						tRetval = true;
						break;
					}
				} else {
					mStatisticNumberOfReceivedCommands++;
				}
				if ((System.nanoTime() - tStart) > MAX_DRAW_INTERVAL_NANOS) {
					if (getBufferBytesAvailable() > SIZE_OF_IN_BUFFER / 2) {
						// TODO skip requests

					}
					Log.w(LOG_TAG, "Return searchCommand() prematurely after 0.5 seconds");
					// break after 0.5 seconds to enable drawing of the bitmap
					tRetval = true;
					break;
				}

			} else {
				tParamsLength = tLengthReceived / 2;
				tCommand = tCommandReceived;
				/*
				 * Parameters
				 */

				for (i = 0; i < tParamsLength; i++) {
					tByte = getByteFromBuffer();
					mParameters[i] = convert2BytesToInt(tByte, getByteFromBuffer());
				}
				if (BlueDisplay.isVERBOSE()) {
					// Output parameter buffer as short hex values
					StringBuilder tParamsHex = new StringBuilder("Params=");
					int tValue;
					for (i = 0; i < tParamsLength; i++) {
						tValue = mParameters[i];
						tParamsHex.append(" 0x" + Integer.toHexString(tValue & 0xFFFF));
					}
					Log.v(LOG_TAG, tParamsHex.toString());
				}

				if (tCommand < RPCView.FIRST_FUNCTION_TAG_WITH_DATA) {
					searchStateDataLengthToWaitFor = MIN_COMMAND_SIZE;
					/*
					 * direct commands without data
					 */
					aRPCView.interpreteCommand(tCommand, mParameters, tParamsLength, null, null, 0);
					mStatisticNumberOfReceivedCommands++;
					if (tCommand == RPCView.FUNCTION_TAG_DRAW_DISPLAY) {
						// break in order to draw the bitmap
						tRetval = true;
						break;
					}

					if ((System.nanoTime() - tStart) > MAX_DRAW_INTERVAL_NANOS) {
						Log.w(LOG_TAG, "Return searchCommand() prematurely after 0.5 seconds");
						// break after 0.5 seconds to enable drawing of the
						// bitmap
						tRetval = true;
						break;
					}
				} else {
					searchStateDataLengthToWaitFor = MIN_MESSAGE_SIZE;
					/*
					 * Wait for header of message part containing the expected data
					 */
					i = 0;
					while (getBufferBytesAvailable() < MIN_MESSAGE_SIZE && i < 100) {
						try {
							if (BlueDisplay.isDEBUG()) {
								Log.d(LOG_TAG, "wait for data header i=" + i);
							}
							Thread.sleep(10);
							i++;
						} catch (InterruptedException e) {
							Log.e(LOG_TAG, "Wait for data header was interrupted", e);
						}
					}
					if (i == 100) {
						Log.e(LOG_TAG, "Timeout waiting for data sync token. Out=" + mReceiveBufferOutIndex + " In="
								+ mReceiveBufferInIndex);
					}
				}
			}
		} /* while */
		if (!tRetval) {
			// no retrigger by doDraw -> need trigger from ConnectedThread
			mNeedUpdateViewMessage = true;
		}
		if (BlueDisplay.isDEBUG()) {
			Log.d(LOG_TAG, "End searchCommand. Out=" + tStartOut + "->" + mReceiveBufferOutIndex + " /"
					+ (mReceiveBufferOutIndex - tStartOut) + "  In=" + tStartIn + "->" + mReceiveBufferInIndex + " /"
					+ (mReceiveBufferInIndex - tStartIn));
		}
		inBufferReadingLock = false;
		mStatisticNanoTimeForCommands += System.nanoTime() - tStart - tNanosForChart;
		mStatisticNanoTimeForChart += tNanosForChart;
		return tRetval;
	}

	/**
	 * This thread runs during a connection with a remote device. It handles all incoming and outgoing transmissions.
	 */
	// TODO put Thread on other processor
	private class ConnectedThread extends Thread {
		private final BluetoothSocket mmSocket;
		private InputStream mmInStream = null;
		private OutputStream mmOutStream = null;

		public ConnectedThread(BluetoothSocket socket) {
			if (BlueDisplay.isINFO()) {
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
			if (BlueDisplay.isINFO()) {
				Log.i(LOG_TAG, "BEGIN mConnectedThread");
			}
			// Read old content from Bluetooth buffer
			// Data is present if data was sent before connection
			int tReadSize = 0;
			int tBytesAvailable;
			try {
				try {
					// 50 is to little, 100 works sometimes, 200 works reliable
					Thread.sleep(200);
				} catch (InterruptedException e) {
					// Just do nothing
				}
				tBytesAvailable = mmInStream.available();
				while (tBytesAvailable > 0) {
					tReadSize += mmInStream.read(mDataBuffer, 0, tBytesAvailable);
					tBytesAvailable = mmInStream.available();
				}
				if (tReadSize > 0) {
					if (BlueDisplay.isINFO()) {
						Log.i("ConnectedThread", "read " + tReadSize + " old bytes from Bluetooth stream");
					}
				}
			} catch (IOException e1) {
				// end up here if cancel() / mmSocket.close() was called before
				if (BlueDisplay.isVERBOSE()) {
					Log.w(LOG_TAG, "Start run - catched IOException - assume disconnected");
				}
				connectionLost(); // show toast
				return;
			}
			mReceiveBufferInIndex = 0;
			mReceiveBufferOutIndex = 0;

			mBlueDisplayContext.mRPCView.resetAll();
			write(EVENT_TAG_CONNECTION_BUILD_UP, mBlueDisplayContext.mRPCView.mActualViewWidth,
					mBlueDisplayContext.mRPCView.mActualViewHeight);

			int tReadLength;
			// Keep listening to the InputStream while connected
			while (true) {
				try {
					/*
					 * read until sync token
					 */
					do {
						// Read block from InputStream
						tReadLength = mmInStream.read(mBigReceiveBuffer, mReceiveBufferInIndex, 256);
						if (tReadLength > 0) {
							int tBytesInBufferToProcess = getBufferBytesAvailable();
							int tOldInIndex = mReceiveBufferInIndex;
							int tOutIndex = mReceiveBufferOutIndex;
							mStatisticNumberOfReceivedBytes += tReadLength;
							mReceiveBufferInIndex += tReadLength;
							if (tOldInIndex < tOutIndex && mReceiveBufferInIndex > tOutIndex) {
								// input index overhauls out index
								Log.e(LOG_TAG, "Buffer overflow! InIndex=" + mReceiveBufferInIndex);
							}
							if (mReceiveBufferInIndex >= SIZE_OF_IN_BUFFER - 256) {
								// buffer wrap around
								mInputBufferWrapAroundIndex = mReceiveBufferInIndex;
								mReceiveBufferInIndex = 0;
								mStatisticNumberOfBufferWrapArounds++;
								if (BlueDisplay.isDEBUG()) {
									// Output length
									Log.d(LOG_TAG, "Buffer wrap around " + (mInputBufferWrapAroundIndex - tOutIndex)
											+ " bytes unprocessed");
								}
								if (mReceiveBufferInIndex > tOutIndex) {
									// after wrap bigger than out index
									Log.e(LOG_TAG, "Buffer overflow! InIndex=" + mReceiveBufferInIndex);
								}
							}
							if (BlueDisplay.isDEBUG()) {
								// Output length
								Log.d(LOG_TAG, "Read length=" + tReadLength + " BufferInIndex=" + mReceiveBufferInIndex);
							}

							/*
							 * Do not send message if searchCommand is just running or will be running.
							 */
							if (mNeedUpdateViewMessage || tBytesInBufferToProcess < MIN_COMMAND_SIZE
									|| tBytesInBufferToProcess < MIN_MESSAGE_SIZE) {
								mHandler.sendEmptyMessage(BlueDisplay.MESSAGE_UPDATE_VIEW);
							} else {
								if (BlueDisplay.isVERBOSE()) {
									Log.d(LOG_TAG, "skip update view message tBytesInBufferToProcess=" + tBytesInBufferToProcess);
								}
							}

						} else {
							Log.w(LOG_TAG, "Read length = 0");
						}
					} while (true);

				} catch (IOException e) {
					// end up here if cancel() / mmSocket.close() was called
					// before
					if (BlueDisplay.isVERBOSE()) {
						Log.w(LOG_TAG, "Catched IOException - assume disconnected");
					}
					connectionLost(); // show toast
					break;
				}
			}
		}

		/**
		 * send 16 bit X and Y position
		 */
		public void write(int aEventType, int aXPos, int aYPos) {
			byte[] tTouchData = new byte[7];
			int tEventLength = 7;

			try {
				// assemble data buffer
				tTouchData[0] = (byte) tEventLength; // gross message
														// length in bytes
				tTouchData[1] = (byte) (aEventType & 0xFF); // Function
															// token
				short tXPos = (short) aXPos;
				tTouchData[2] = (byte) (tXPos & 0xFF); // LSB
				tTouchData[3] = (byte) ((tXPos >> 8) & 0xFF); // MSB
				short tYPos = (short) aYPos;
				tTouchData[4] = (byte) (tYPos & 0xFF); // LSB
				tTouchData[5] = (byte) ((tYPos >> 8) & 0xFF); // MSB
				tTouchData[6] = SYNC_TOKEN;
				if (BlueDisplay.isINFO()) {
					Log.i(LOG_TAG, "Write Type=" + (aEventType & 0xFF) + " X=" + aXPos + " Y=" + aYPos);
				}

				mmOutStream.write(tTouchData, 0, tEventLength);
				mmOutStream.flush();
				mStatisticNumberOfSentBytes += tEventLength;
				mStatisticNumberOfSentCommands++;
			} catch (IOException e) {
				Log.e(LOG_TAG, "Exception during write", e);
			}
		}

		/*
		 * send 16 bit button index 32 bit callback address and 32 bit value
		 */
		public void write(int aEventType, int aButtonIndex, int aCallbackAddress, int aValue) {
			byte[] tTouchData = new byte[13];
			int tEventLength = 13;

			try {
				// assemble data buffer
				int tIndex = 0;
				tTouchData[tIndex++] = (byte) tEventLength; // gross message
															// length in bytes
				tTouchData[tIndex++] = (byte) (aEventType & 0xFF); // Function
																	// token
				short tShortValue = (short) aButtonIndex;
				tTouchData[tIndex++] = (byte) (tShortValue & 0xFF); // LSB
				tTouchData[tIndex++] = (byte) ((tShortValue >> 8) & 0xFF); // MSB

				tTouchData[tIndex++] = (byte) (aCallbackAddress & 0xFF); // LSB
				tTouchData[tIndex++] = (byte) ((aCallbackAddress >> 8) & 0xFF); // MSB
				tTouchData[tIndex++] = (byte) ((aCallbackAddress >> 16) & 0xFF);
				tTouchData[tIndex++] = (byte) ((aCallbackAddress >> 24) & 0xFF);
				tTouchData[tIndex++] = (byte) (aValue & 0xFF); // LSB
				tTouchData[tIndex++] = (byte) ((aValue >> 8) & 0xFF); // MSB
				tTouchData[tIndex++] = (byte) ((aValue >> 16) & 0xFF);
				tTouchData[tIndex++] = (byte) ((aValue >> 24) & 0xFF);
				tTouchData[tIndex++] = SYNC_TOKEN;
				if (BlueDisplay.isINFO()) {
					Log.i(LOG_TAG, "Write Type=" + (aEventType & 0xFF) + " ButtonIndex=" + aButtonIndex + " CallbackAddress=0x"
							+ Integer.toHexString(aCallbackAddress) + " Value=" + aValue);
				}

				mmOutStream.write(tTouchData, 0, tEventLength);
				mmOutStream.flush();
				mStatisticNumberOfSentBytes += tEventLength;
				mStatisticNumberOfSentCommands++;
			} catch (IOException e) {
				Log.e(LOG_TAG, "Exception during write", e);
			}
		}

		/*
		 * send 16 bit button index 32 bit callback address and 32 bit FLOAT value
		 */
		public void write(int aEventType, int aCallbackAddress, float aValue) {
			byte[] tTouchData = new byte[13];
			int tEventLength = 13;

			try {
				// assemble data buffer
				int tIndex = 0;
				tTouchData[tIndex++] = (byte) tEventLength; // gross message
															// length in bytes
				tTouchData[tIndex++] = (byte) (aEventType & 0xFF); // Function
																	// token
				short tShortValue = (short) 0xFF;
				tTouchData[tIndex++] = (byte) (tShortValue & 0xFF); // LSB
				tTouchData[tIndex++] = (byte) ((tShortValue >> 8) & 0xFF); // MSB

				tTouchData[tIndex++] = (byte) (aCallbackAddress & 0xFF); // LSB
				tTouchData[tIndex++] = (byte) ((aCallbackAddress >> 8) & 0xFF); // MSB
				tTouchData[tIndex++] = (byte) ((aCallbackAddress >> 16) & 0xFF);
				tTouchData[tIndex++] = (byte) ((aCallbackAddress >> 24) & 0xFF);
				int tValue = Float.floatToIntBits(aValue);
				tTouchData[tIndex++] = (byte) (tValue & 0xFF); // LSB
				tTouchData[tIndex++] = (byte) ((tValue >> 8) & 0xFF); // MSB
				tTouchData[tIndex++] = (byte) ((tValue >> 16) & 0xFF);
				tTouchData[tIndex++] = (byte) ((tValue >> 24) & 0xFF);
				tTouchData[tIndex++] = SYNC_TOKEN;
				if (BlueDisplay.isINFO()) {
					Log.i(LOG_TAG,
							"Write Type=" + (aEventType & 0xFF) + " CallbackAddress=0x" + Integer.toHexString(aCallbackAddress)
									+ " Value=" + aValue);
				}

				mmOutStream.write(tTouchData, 0, tEventLength);
				mmOutStream.flush();
				mStatisticNumberOfSentBytes += tEventLength;
				mStatisticNumberOfSentCommands++;
			} catch (IOException e) {
				Log.e(LOG_TAG, "Exception during write", e);
			}
		}

		/*
		 * send 16 bit direction 32 bit start position and 32 bit delta
		 */
		public void write(int aEventType, int aIsXDirection, int aStartX, int aStartY, int aDeltaX, int aDeltaY) {
			byte[] tTouchData = new byte[13];
			int tEventLength = 13;

			try {
				// assemble data buffer
				int tIndex = 0;
				tTouchData[tIndex++] = (byte) tEventLength; // gross message
															// length in bytes
				tTouchData[tIndex++] = (byte) (aEventType & 0xFF); // Function
																	// token
				short tShortValue = (short) aIsXDirection;
				tTouchData[tIndex++] = (byte) (tShortValue & 0xFF); // LSB
				tTouchData[tIndex++] = (byte) ((tShortValue >> 8) & 0xFF); // MSB

				tTouchData[tIndex++] = (byte) (aStartX & 0xFF); // LSB
				tTouchData[tIndex++] = (byte) ((aStartX >> 8) & 0xFF); // MSB
				tTouchData[tIndex++] = (byte) (aStartY & 0xFF); // LSB
				tTouchData[tIndex++] = (byte) ((aStartY >> 8) & 0xFF); // MSB
				tTouchData[tIndex++] = (byte) (aDeltaX & 0xFF); // LSB
				tTouchData[tIndex++] = (byte) ((aDeltaX >> 8) & 0xFF); // MSB
				tTouchData[tIndex++] = (byte) (aDeltaY & 0xFF); // LSB
				tTouchData[tIndex++] = (byte) ((aDeltaY >> 8) & 0xFF); // MSB
				tTouchData[tIndex++] = SYNC_TOKEN;
				if (BlueDisplay.isINFO()) {
					Log.i(LOG_TAG, "Write Type=" + (aEventType & 0xFF) + " Direction=" + aIsXDirection + " Start=" + aStartX + "/"
							+ aStartY + " Delta=" + aDeltaX + "/" + aDeltaY);
				}

				mmOutStream.write(tTouchData, 0, tEventLength);
				mmOutStream.flush();
				mStatisticNumberOfSentBytes += tEventLength;
				mStatisticNumberOfSentCommands++;
			} catch (IOException e) {
				Log.e(LOG_TAG, "Exception during write", e);
			}
		}

		public void cancel() {
			try {
				mmSocket.close();
			} catch (IOException e) {
				Log.e(LOG_TAG, "close() of connect socket failed", e);
			}
		}
	}

	public static int convert2BytesToInt(byte aLSB, byte aMSB) {
		int i = aLSB;
		i = i & 0x000000FF;
		i = i | (aMSB << 8);
		return i;
	}

	public static float convertByteToFloat(byte aByte) {
		if (aByte < 0) {
			return aByte + 256;
		}
		return aByte;
	}

}
