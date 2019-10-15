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
 * This service handles the data in the receive buffer and assembles the event buffer.
 */

package de.joachimsmeyer.android.bluedisplay;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import com.hoho.android.usbserial.util.SerialInputOutputManager;

import android.os.Handler;
import android.util.Log;

/**
 * This class does all the work for setting up and managing Bluetooth connections with other devices. It has a thread for connecting
 * with a device, and a thread for performing data transmissions when connected.
 */
public class SerialService {
	// Logging
	private static final String LOG_TAG = "SerialService";

	/*
	 * The big receive ring buffer
	 */
	public static final int SIZE_OF_IN_BUFFER = 10 * 4096;
	public static final int MIN_MESSAGE_SIZE = 5; // data message with one byte
	public static final int MIN_COMMAND_SIZE = 4; // command message with no parameter

	public volatile byte[] mBigReceiveBuffer = new byte[SIZE_OF_IN_BUFFER];
	volatile int mInputBufferWrapAroundIndex = 0; // first free byte
													// after last byte in
													// buffer
	volatile int mReceiveBufferInIndex; // first free byte
	volatile int mReceiveBufferOutIndex; // first unprocessed byte

	byte[] mDataBuffer = new byte[4096]; // Buffer to hold data for one data command
	private volatile boolean inBufferReadingLock = false; // Safety net to avoid 2 instances of search command calls. Should never
															// happen :-).

	public static final int SIZE_OF_DEBUG_BUFFER = 16;
	byte[] mHexOutputTempBuffer = new byte[SIZE_OF_DEBUG_BUFFER]; // holds one output line for verbose HEX output
	int mHexOutputTempBufferActualIndex = 0;
	// Forces the end of writing to bitmap after 0.5 seconds and thus allow
	// bitmap to be displayed
	private static final long MAX_DRAW_INTERVAL_NANOS = 500000000;

	// Statistics
	public int mStatisticNumberOfReceivedBytes;
	public int mStatisticNumberOfReceivedCommands;
	public int mStatisticNumberOfReceivedChartCommands;
	public int mStatisticNumberOfSentBytes;
	public int mStatisticNumberOfSentCommands;
	public int mStatisticNumberOfBufferWrapArounds;
	public long mStatisticNanoTimeForCommands;
	public long mStatisticNanoTimeForChart;

	public final static int EVENT_CONNECTION_BUILD_UP = 0x10;
	public final static int EVENT_REDRAW = 0x11;
	public final static int EVENT_REORIENTATION = 0x12;
	public final static int EVENT_DISCONNECT = 0x14;

	public final static int EVENT_FIRST_CALLBACK = 0x20;
	public final static int EVENT_BUTTON_CALLBACK = 0x20;
	public final static int EVENT_SLIDER_CALLBACK = 0x21;
	public final static int EVENT_SWIPE_CALLBACK = 0x22;
	public final static int EVENT_LONG_TOUCH_DOWN_CALLBACK = 0x23;

	public final static int EVENT_NUMBER_CALLBACK = 0x28;
	public final static int EVENT_INFO_CALLBACK = 0x29;

	public final static int EVENT_TEXT_CALLBACK = 0x2C;

	public final static int EVENT_NOP = 0x2F;

	public final static int EVENT_FIRST_SENSOR_ACTION_CODE = 0x30;

	public final static int EVENT_REQUESTED_DATA_CANVAS_SIZE = 0x60;

	private BlueDisplay mBlueDisplayContext;
	private final Handler mHandler;

	final static int CALLBACK_DATA_SIZE = 15;
	private byte[] mSendByteBuffer = new byte[CALLBACK_DATA_SIZE]; // Static buffer since we only send one item at a time

	/**
	 * Constructor. Prepares a new BluetoothChat session.
	 * 
	 * @param context
	 *            The UI Activity Context
	 * @param handler
	 *            A Handler to send messages back to the UI Activity
	 */
	public SerialService(BlueDisplay aContext, Handler aHandler) {
		mBlueDisplayContext = aContext;
		mHandler = aHandler;
		resetStatistics();
	}

	/*
	 * Handle statistics, buffer overflow, buffer wrap around.
	 */
	void handleReceived(int aReadLength) {

		if (aReadLength == 0) {
			MyLog.w(LOG_TAG, "Read length = 0");
		} else {
			int tOldInIndex = mReceiveBufferInIndex;
			int tOutIndex = mReceiveBufferOutIndex;
			mStatisticNumberOfReceivedBytes += aReadLength;
			mReceiveBufferInIndex += aReadLength;
			int tBytesInBufferToProcess = getBufferBytesAvailable();
			if (tOldInIndex < tOutIndex && mReceiveBufferInIndex > tOutIndex) {
				// input index overtakes out index
				MyLog.e(LOG_TAG, "Buffer overflow! InIndex=" + mReceiveBufferInIndex);
			}
			if (mReceiveBufferInIndex >= SerialService.SIZE_OF_IN_BUFFER - SerialInputOutputManager.BUFSIZ) {
				/*
				 * buffer wrap around. Start new input at start of buffer and note new end of buffer in mInputBufferWrapAroundIndex
				 */
				mInputBufferWrapAroundIndex = mReceiveBufferInIndex;
				mReceiveBufferInIndex = 0;
				mStatisticNumberOfBufferWrapArounds++;
				if (MyLog.isVERBOSE()) {
					// Output length
					Log.v(LOG_TAG, "Buffer wrap around " + (mInputBufferWrapAroundIndex - tOutIndex) + " bytes unprocessed");
				}
				if (mReceiveBufferInIndex > tOutIndex) {
					// after wrap bigger than out index
					MyLog.e(LOG_TAG, "Buffer overflow! InIndex=" + mReceiveBufferInIndex);
				}
			}
			if (MyLog.isVERBOSE()) {
				// Output length
				Log.v(LOG_TAG, "Read length=" + aReadLength + " BufferInIndex=" + mReceiveBufferInIndex);
			}

			/*
			 * Do not send message if searchCommand is just running or will be running because of a former message.
			 */
			if (mNeedUpdateViewMessage) {
				mHandler.sendEmptyMessage(BlueDisplay.MESSAGE_UPDATE_VIEW);
				mNeedUpdateViewMessage = false;
				if (MyLog.isVERBOSE()) {
					// Output length
					Log.v(LOG_TAG, "Send MESSAGE_UPDATE_VIEW. Bytes to process=" + tBytesInBufferToProcess);
				}
			} else {
				if (MyLog.isDEVELOPMENT_TESTING()) {
					Log.d(LOG_TAG, "skip update view message tBytesInBufferToProcess=" + tBytesInBufferToProcess);
				}
			}
		}
	}

	void resetReceiveBuffer() {
		mReceiveBufferInIndex = 0;
		mReceiveBufferOutIndex = 0;
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

	public String getStatisticsString() {
		String tReturn = mStatisticNumberOfReceivedBytes + " bytes, " + mStatisticNumberOfReceivedCommands + " commands and "
				+ mStatisticNumberOfReceivedChartCommands + " charts received\n";
		if (mStatisticNumberOfReceivedCommands != 0) {
			tReturn += ((mStatisticNanoTimeForCommands / 1000) / mStatisticNumberOfReceivedCommands) + " µs per command\n";
		}
		if (mStatisticNumberOfReceivedChartCommands != 0) {
			tReturn += ((mStatisticNanoTimeForChart / 1000) / mStatisticNumberOfReceivedChartCommands) + " µs per chart command\n";
		}
		tReturn += mStatisticNumberOfSentBytes + " bytes, " + mStatisticNumberOfSentCommands + " commands sent\n";

		tReturn += "Buffer wrap arounds=" + mStatisticNumberOfBufferWrapArounds + "\n";
		int tInputBufferOutIndex = mReceiveBufferOutIndex;
		int tBytes = getBufferBytesAvailable();
		String tSearchStateDataLengthToWaitForString = "";
		if (searchStateInputLengthToWaitFor > MIN_MESSAGE_SIZE) {
			tSearchStateDataLengthToWaitForString = ", waited for " + searchStateInputLengthToWaitFor;
		}
		tReturn += "InputBuffer: size=" + SIZE_OF_IN_BUFFER + ", in=" + mReceiveBufferInIndex + ", out=" + tInputBufferOutIndex
				+ ", not processed=" + tBytes + tSearchStateDataLengthToWaitForString + "\n";
		if (MyLog.isDEBUG()) {
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

	/*
	 * Signal connection to Arduino. First write a NOP command for synchronizing, i.e. the client receive buffer is filled up once.
	 * Then send EVENT_CONNECTION_BUILD_UP, which calls the connect and redraw callback, specified at initCommunication(), on the
	 * client. The very first call for USB sends 0 as actual size values. In response we first get a NOP command for syncing the
	 * host, and then the commands of the client ConnectCallback() function.
	 */
	void signalBlueDisplayConnection() {
		// first write a NOP command for synchronizing
		writeGuiCallbackEvent(SerialService.EVENT_NOP, 0, 0, 0, null);
		writeTwoIntegerEventAndTimestamp(SerialService.EVENT_CONNECTION_BUILD_UP, mBlueDisplayContext.mRPCView.mActualViewWidth,
				mBlueDisplayContext.mRPCView.mActualViewHeight);
	}

	void writeEvent(byte[] aEventDataBuffer, int aEventDataLength) {
		if(mBlueDisplayContext.mDeviceConnected) {
		if (mBlueDisplayContext.mUSBDeviceAttached) {
			mBlueDisplayContext.mUSBSerialSocket.writeEvent(mSendByteBuffer, aEventDataLength);
		} else {
			mBlueDisplayContext.mBTSerialSocket.writeEvent(mSendByteBuffer, aEventDataLength);
		}
		} else{
			if (MyLog.isINFO()) {
				MyLog.i(LOG_TAG, "Do not send event, because client is not (yet) connected");
			}
		}
	}

	/**
	 * send 16 bit X and Y position
	 */
	public void writeTwoIntegerEvent(int aEventType, int aX, int aY) {
		int tEventLength = 7;
		int tEventType = aEventType & 0xFF;

		// assemble data buffer
		int tIndex = 0;
		mSendByteBuffer[tIndex++] = (byte) tEventLength; // gross message length in bytes
		mSendByteBuffer[tIndex++] = (byte) tEventType; // Function token

		short tXPos = (short) aX;
		mSendByteBuffer[tIndex++] = (byte) (tXPos & 0xFF); // LSB
		mSendByteBuffer[tIndex++] = (byte) ((tXPos >> 8) & 0xFF); // MSB
		short tYPos = (short) aY;
		mSendByteBuffer[tIndex++] = (byte) (tYPos & 0xFF); // LSB
		mSendByteBuffer[tIndex++] = (byte) ((tYPos >> 8) & 0xFF); // MSB
		mSendByteBuffer[tIndex++] = SYNC_TOKEN;
		if (MyLog.isINFO()) {
			String tType = RPCView.sActionMappings.get(tEventType);
			MyLog.i(LOG_TAG, "Send Type=0x" + Integer.toHexString(tEventType) + "|" + tType + " X=" + aX + " Y=" + aY);
		}

		mStatisticNumberOfSentBytes += tEventLength;
		mStatisticNumberOfSentCommands++;

		writeEvent(mSendByteBuffer, tEventLength);
	}

	/**
	 * send 16 bit X and Y position and 8 bit pointer index
	 */
	public void writeTwoIntegerAndAByteEvent(int aEventType, int aX, int aY, int aByte) {
		int tEventLength = 8;
		int tEventType = aEventType & 0xFF;

		// assemble data buffer
		int tIndex = 0;
		mSendByteBuffer[tIndex++] = (byte) tEventLength; // gross message length in bytes
		mSendByteBuffer[tIndex++] = (byte) tEventType; // Function token

		short tXPos = (short) aX;
		mSendByteBuffer[tIndex++] = (byte) (tXPos & 0xFF); // LSB
		mSendByteBuffer[tIndex++] = (byte) ((tXPos >> 8) & 0xFF); // MSB
		short tYPos = (short) aY;
		mSendByteBuffer[tIndex++] = (byte) (tYPos & 0xFF); // LSB
		mSendByteBuffer[tIndex++] = (byte) ((tYPos >> 8) & 0xFF); // MSB
		mSendByteBuffer[tIndex++] = (byte) (aByte & 0xFF); // Byte
		mSendByteBuffer[tIndex++] = SYNC_TOKEN;
		if (MyLog.isINFO()) {
			String tType = RPCView.sActionMappings.get(tEventType);
			MyLog.i(LOG_TAG, "Send Type=0x" + Integer.toHexString(tEventType) + "|" + tType + " X=" + aX + " Y=" + aY
					+ " PointerIndex=" + aByte);
		}

		mStatisticNumberOfSentBytes += tEventLength;
		mStatisticNumberOfSentCommands++;

		writeEvent(mSendByteBuffer, tEventLength);

	}

	/**
	 * send 16 bit X and Y position
	 */
	public void writeTwoIntegerEventAndTimestamp(int aEventType, int aX, int aY) {
		int tEventLength = 11;
		int tEventType = aEventType & 0xFF;

		// assemble data buffer
		int tIndex = 0;
		mSendByteBuffer[tIndex++] = (byte) tEventLength; // gross message length in bytes
		mSendByteBuffer[tIndex++] = (byte) tEventType; // Function token

		short tXPos = (short) aX;
		mSendByteBuffer[tIndex++] = (byte) (tXPos & 0xFF); // LSB
		mSendByteBuffer[tIndex++] = (byte) ((tXPos >> 8) & 0xFF); // MSB
		short tYPos = (short) aY;
		mSendByteBuffer[tIndex++] = (byte) (tYPos & 0xFF); // LSB
		mSendByteBuffer[tIndex++] = (byte) ((tYPos >> 8) & 0xFF); // MSB

		/*
		 * Timestamp of local time (for convenience reason)
		 */
		int tGmtOffset = TimeZone.getDefault().getRawOffset() + TimeZone.getDefault().getDSTSavings();
		long tTimestamp = System.currentTimeMillis() + tGmtOffset;
		long tTimestampSeconds = tTimestamp / 1000L;
		mSendByteBuffer[tIndex++] = (byte) (tTimestampSeconds & 0xFF); // LSB
		mSendByteBuffer[tIndex++] = (byte) ((tTimestampSeconds >> 8) & 0xFF);
		mSendByteBuffer[tIndex++] = (byte) ((tTimestampSeconds >> 16) & 0xFF);
		mSendByteBuffer[tIndex++] = (byte) ((tTimestampSeconds >> 24) & 0xFF); // MSB
		mSendByteBuffer[tIndex++] = SYNC_TOKEN;
		if (MyLog.isINFO()) {
			String tType = RPCView.sActionMappings.get(tEventType);
			// this does not respect the 24 hour setting of android :-(
			// DateFormat tDateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM, Locale.GERMAN);
			DateFormat tDateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());
			Date tDate = new Date(tTimestamp);
			MyLog.i(LOG_TAG, "Send Type=0x" + Integer.toHexString(tEventType) + "|" + tType + " X=" + aX + " Y=" + aY + " Date="
					+ tDateFormat.format(tDate));
		}

		mStatisticNumberOfSentBytes += tEventLength;
		mStatisticNumberOfSentCommands++;

		writeEvent(mSendByteBuffer, tEventLength);

	}

	/*
	 * send 16 bit button / slider index, 16 bit filler, 32 bit callback address and 32 bit value
	 */
	public void writeGuiCallbackEvent(int aEventType, int aButtonSliderIndex, int aCallbackAddress, int aValue, String aElementInfo) {
		int tEventLength = CALLBACK_DATA_SIZE;
		int tEventType = aEventType & 0xFF;

		// assemble data buffer
		int tIndex = 0;
		mSendByteBuffer[tIndex++] = (byte) tEventLength; // gross message length in bytes
		mSendByteBuffer[tIndex++] = (byte) tEventType; // Function token

		short tShortValue = (short) aButtonSliderIndex;
		mSendByteBuffer[tIndex++] = (byte) (tShortValue & 0xFF); // LSB
		mSendByteBuffer[tIndex++] = (byte) ((tShortValue >> 8) & 0xFF); // MSB
		// for 32 bit padding
		mSendByteBuffer[tIndex++] = (byte) (0x00); // LSB
		mSendByteBuffer[tIndex++] = (byte) (0x00); // MSB

		mSendByteBuffer[tIndex++] = (byte) (aCallbackAddress & 0xFF); // LSB
		mSendByteBuffer[tIndex++] = (byte) ((aCallbackAddress >> 8) & 0xFF); // MSB
		mSendByteBuffer[tIndex++] = (byte) ((aCallbackAddress >> 16) & 0xFF);
		mSendByteBuffer[tIndex++] = (byte) ((aCallbackAddress >> 24) & 0xFF);
		mSendByteBuffer[tIndex++] = (byte) (aValue & 0xFF); // LSB
		mSendByteBuffer[tIndex++] = (byte) ((aValue >> 8) & 0xFF); // MSB
		mSendByteBuffer[tIndex++] = (byte) ((aValue >> 16) & 0xFF);
		mSendByteBuffer[tIndex++] = (byte) ((aValue >> 24) & 0xFF);
		mSendByteBuffer[tIndex++] = SYNC_TOKEN;

		if (MyLog.isINFO()) {
			String tType = RPCView.sActionMappings.get(tEventType);
			String tElementInfo = "";
			if (tEventType == EVENT_BUTTON_CALLBACK) {
				tElementInfo = " ButtonIndex=" + aButtonSliderIndex + "|" + aElementInfo;
			} else if (tEventType == EVENT_SLIDER_CALLBACK) {
				tElementInfo = " SliderIndex=" + aButtonSliderIndex;
			}
			MyLog.i(LOG_TAG,
					"Send Type=0x" + Integer.toHexString(tEventType) + "|" + tType + " CallbackAddress=0x"
							+ Integer.toHexString(aCallbackAddress) + " Value=" + aValue + tElementInfo);
		}

		mStatisticNumberOfSentBytes += tEventLength;
		mStatisticNumberOfSentCommands++;
		writeEvent(mSendByteBuffer, tEventLength);
	}

	/*
	 * send 16 bit button index, 16 bit filler, 32 bit callback address and 32 bit FLOAT value
	 */
	public void writeNumberCallbackEvent(int aEventType, int aCallbackAddress, float aValue) {
		int tEventLength = CALLBACK_DATA_SIZE;
		int tEventType = aEventType & 0xFF;

		// assemble data buffer
		int tIndex = 0;
		mSendByteBuffer[tIndex++] = (byte) tEventLength; // gross message length in bytes
		mSendByteBuffer[tIndex++] = (byte) tEventType; // Function token

		// for future use (index of function calling getNumber etc.)
		short tShortValue = (short) 0;
		mSendByteBuffer[tIndex++] = (byte) (tShortValue & 0xFF); // LSB
		mSendByteBuffer[tIndex++] = (byte) ((tShortValue >> 8) & 0xFF); // MSB
		// for 32 bit padding
		mSendByteBuffer[tIndex++] = (byte) (0x00); // LSB
		mSendByteBuffer[tIndex++] = (byte) (0x00); // MSB

		mSendByteBuffer[tIndex++] = (byte) (aCallbackAddress & 0xFF); // LSB
		mSendByteBuffer[tIndex++] = (byte) ((aCallbackAddress >> 8) & 0xFF); // MSB
		mSendByteBuffer[tIndex++] = (byte) ((aCallbackAddress >> 16) & 0xFF);
		mSendByteBuffer[tIndex++] = (byte) ((aCallbackAddress >> 24) & 0xFF);
		int tValue = Float.floatToIntBits(aValue);
		mSendByteBuffer[tIndex++] = (byte) (tValue & 0xFF); // LSB
		mSendByteBuffer[tIndex++] = (byte) ((tValue >> 8) & 0xFF); // MSB
		mSendByteBuffer[tIndex++] = (byte) ((tValue >> 16) & 0xFF);
		mSendByteBuffer[tIndex++] = (byte) ((tValue >> 24) & 0xFF);
		mSendByteBuffer[tIndex++] = SYNC_TOKEN;
		if (MyLog.isINFO()) {
			String tType = RPCView.sActionMappings.get(tEventType);
			MyLog.i(LOG_TAG,
					"Send Type=0x" + Integer.toHexString(tEventType) + "|" + tType + " CallbackAddress=0x"
							+ Integer.toHexString(aCallbackAddress) + " Value=" + aValue);
		}

		mStatisticNumberOfSentBytes += tEventLength;
		mStatisticNumberOfSentCommands++;
		writeEvent(mSendByteBuffer, tEventLength);
	}

	/*
	 * send 16 bit direction,16 bit filler, 32 bit start position and 32 bit delta
	 */
	public void writeSwipeCallbackEvent(int aEventType, int aIsXDirection, int aStartX, int aStartY, int aDeltaX, int aDeltaY) {
		int tEventLength = CALLBACK_DATA_SIZE;
		int tEventType = aEventType & 0xFF;

		// assemble data buffer
		int tIndex = 0;
		mSendByteBuffer[tIndex++] = (byte) tEventLength; // gross message length in bytes
		mSendByteBuffer[tIndex++] = (byte) tEventType; // Function token
		short tShortValue = (short) aIsXDirection;
		mSendByteBuffer[tIndex++] = (byte) (tShortValue & 0xFF); // LSB
		mSendByteBuffer[tIndex++] = (byte) ((tShortValue >> 8) & 0xFF); // MSB
		// for 32 bit padding
		mSendByteBuffer[tIndex++] = (byte) (0x00); // LSB
		mSendByteBuffer[tIndex++] = (byte) (0x00); // MSB

		mSendByteBuffer[tIndex++] = (byte) (aStartX & 0xFF); // LSB
		mSendByteBuffer[tIndex++] = (byte) ((aStartX >> 8) & 0xFF); // MSB
		mSendByteBuffer[tIndex++] = (byte) (aStartY & 0xFF); // LSB
		mSendByteBuffer[tIndex++] = (byte) ((aStartY >> 8) & 0xFF); // MSB
		mSendByteBuffer[tIndex++] = (byte) (aDeltaX & 0xFF); // LSB
		mSendByteBuffer[tIndex++] = (byte) ((aDeltaX >> 8) & 0xFF); // MSB
		mSendByteBuffer[tIndex++] = (byte) (aDeltaY & 0xFF); // LSB
		mSendByteBuffer[tIndex++] = (byte) ((aDeltaY >> 8) & 0xFF); // MSB
		mSendByteBuffer[tIndex++] = SYNC_TOKEN;
		if (MyLog.isINFO()) {
			String tType = RPCView.sActionMappings.get(tEventType);
			MyLog.i(LOG_TAG, "Send Type=0x" + Integer.toHexString(tEventType) + "|" + tType + " Direction=" + aIsXDirection
					+ " Start=" + aStartX + "/" + aStartY + " Delta=" + aDeltaX + "/" + aDeltaY);
		}

		mStatisticNumberOfSentBytes += tEventLength;
		mStatisticNumberOfSentCommands++;

		writeEvent(mSendByteBuffer, tEventLength);
	}

	/*
	 * send sensor event type and xyz 32 bit FLOAT values
	 */
	public void writeSensorEvent(int aEventType, float aValueX, float aValueY, float aValueZ) {
		int tEventLength = CALLBACK_DATA_SIZE;
		int tEventType = aEventType & 0xFF;

		// assemble data buffer
		int tIndex = 0;
		mSendByteBuffer[tIndex++] = (byte) tEventLength; // gross message length in bytes
		mSendByteBuffer[tIndex++] = (byte) tEventType; // Function token

		int tValue = Float.floatToIntBits(aValueX);
		mSendByteBuffer[tIndex++] = (byte) (tValue & 0xFF); // LSB
		mSendByteBuffer[tIndex++] = (byte) ((tValue >> 8) & 0xFF); // MSB
		mSendByteBuffer[tIndex++] = (byte) ((tValue >> 16) & 0xFF);
		mSendByteBuffer[tIndex++] = (byte) ((tValue >> 24) & 0xFF);
		tValue = Float.floatToIntBits(aValueY);
		mSendByteBuffer[tIndex++] = (byte) (tValue & 0xFF); // LSB
		mSendByteBuffer[tIndex++] = (byte) ((tValue >> 8) & 0xFF); // MSB
		mSendByteBuffer[tIndex++] = (byte) ((tValue >> 16) & 0xFF);
		mSendByteBuffer[tIndex++] = (byte) ((tValue >> 24) & 0xFF);
		tValue = Float.floatToIntBits(aValueZ);
		mSendByteBuffer[tIndex++] = (byte) (tValue & 0xFF); // LSB
		mSendByteBuffer[tIndex++] = (byte) ((tValue >> 8) & 0xFF); // MSB
		mSendByteBuffer[tIndex++] = (byte) ((tValue >> 16) & 0xFF);
		mSendByteBuffer[tIndex++] = (byte) ((tValue >> 24) & 0xFF);
		mSendByteBuffer[tIndex++] = SYNC_TOKEN;
		if (MyLog.isDEBUG()) {
			String tType = RPCView.sActionMappings.get(tEventType);
			MyLog.d(LOG_TAG, "Send Sensor Event Type=0x" + Integer.toHexString(tEventType) + "|" + tType + " X=" + aValueX + " Y="
					+ aValueY + " Z=" + aValueZ);
		}

		mStatisticNumberOfSentBytes += tEventLength;
		mStatisticNumberOfSentCommands++;

		writeEvent(mSendByteBuffer, tEventLength);

	}

	public void writeInfoCallbackEvent(int aEventType, int aSubFunction, int aByteInfo, int aShortInfo, int aCallbackAddress,
			int aInfo_0, int aInfo_1) {
		int tEventLength = CALLBACK_DATA_SIZE;
		int tEventType = aEventType & 0xFF;

		// assemble data buffer
		int tIndex = 0;
		mSendByteBuffer[tIndex++] = (byte) tEventLength; // gross message length in bytes

		mSendByteBuffer[tIndex++] = (byte) tEventType; // Sub function token

		mSendByteBuffer[tIndex++] = (byte) (aSubFunction & 0xFF);

		mSendByteBuffer[tIndex++] = (byte) (aByteInfo & 0xFF);
		// put special info here
		mSendByteBuffer[tIndex++] = (byte) (aShortInfo & 0xFF); // LSB
		mSendByteBuffer[tIndex++] = (byte) ((aShortInfo >> 8) & 0xFF); // MSB

		mSendByteBuffer[tIndex++] = (byte) (aCallbackAddress & 0xFF); // LSB
		mSendByteBuffer[tIndex++] = (byte) ((aCallbackAddress >> 8) & 0xFF); // MSB
		mSendByteBuffer[tIndex++] = (byte) ((aCallbackAddress >> 16) & 0xFF);
		mSendByteBuffer[tIndex++] = (byte) ((aCallbackAddress >> 24) & 0xFF);

		mSendByteBuffer[tIndex++] = (byte) (aInfo_0 & 0xFF); // LSB
		mSendByteBuffer[tIndex++] = (byte) ((aInfo_0 >> 8) & 0xFF); // MSB
		mSendByteBuffer[tIndex++] = (byte) (aInfo_1 & 0xFF); // LSB
		mSendByteBuffer[tIndex++] = (byte) ((aInfo_1 >> 8) & 0xFF); // MSB
		mSendByteBuffer[tIndex++] = SYNC_TOKEN;
		if (MyLog.isINFO()) {
			String tType = RPCView.sActionMappings.get(tEventType);
			MyLog.i(LOG_TAG,
					"Send Type=0x" + Integer.toHexString(tEventType) + "|" + tType + " SubFunction=" + aSubFunction + " ByteInfo="
							+ (aByteInfo & 0xFF) + " ShortInfo=" + (aShortInfo & 0xFFFF) + " CallbackAddress=0x"
							+ Integer.toHexString(aCallbackAddress) + " Info 0=" + aInfo_0 + " Info 1=" + aInfo_1);
		}

		mStatisticNumberOfSentBytes += tEventLength;
		mStatisticNumberOfSentCommands++;

		writeEvent(mSendByteBuffer, tEventLength);

	}

	public void writeInfoCallbackEvent(int aEventType, int aSubFunction, int aByteInfo, int aShortInfo, int aCallbackAddress,
			long aLongInfo) {
		int tEventLength = CALLBACK_DATA_SIZE;
		int tEventType = aEventType & 0xFF;

		// assemble data buffer
		int tIndex = 0;
		mSendByteBuffer[tIndex++] = (byte) tEventLength; // gross message length in bytes

		mSendByteBuffer[tIndex++] = (byte) tEventType; // Function token

		mSendByteBuffer[tIndex++] = (byte) (aSubFunction & 0xFF); // Sub function token

		mSendByteBuffer[tIndex++] = (byte) (aByteInfo & 0xFF);
		// put special info here
		mSendByteBuffer[tIndex++] = (byte) (aShortInfo & 0xFF); // LSB
		mSendByteBuffer[tIndex++] = (byte) ((aShortInfo >> 8) & 0xFF); // MSB

		mSendByteBuffer[tIndex++] = (byte) (aCallbackAddress & 0xFF); // LSB
		mSendByteBuffer[tIndex++] = (byte) ((aCallbackAddress >> 8) & 0xFF); // MSB
		mSendByteBuffer[tIndex++] = (byte) ((aCallbackAddress >> 16) & 0xFF);
		mSendByteBuffer[tIndex++] = (byte) ((aCallbackAddress >> 24) & 0xFF);

		mSendByteBuffer[tIndex++] = (byte) (aLongInfo & 0xFF); // LSB
		mSendByteBuffer[tIndex++] = (byte) ((aLongInfo >> 8) & 0xFF); // MSB
		mSendByteBuffer[tIndex++] = (byte) ((aLongInfo >> 16) & 0xFF);
		mSendByteBuffer[tIndex++] = (byte) ((aLongInfo >> 24) & 0xFF);
		mSendByteBuffer[tIndex++] = SYNC_TOKEN;
		if (MyLog.isINFO()) {
			String tType = RPCView.sActionMappings.get(tEventType);
			MyLog.i(LOG_TAG,
					"Send Type=0x" + Integer.toHexString(tEventType) + "|" + tType + " SubFunction=" + aSubFunction + " ByteInfo="
							+ (aByteInfo & 0xFF) + " ShortInfo=" + (aShortInfo & 0xFFFF) + " CallbackAddress=0x"
							+ Integer.toHexString(aCallbackAddress) + " LongInfo=" + aLongInfo);
		}

		mStatisticNumberOfSentBytes += tEventLength;
		mStatisticNumberOfSentCommands++;

		writeEvent(mSendByteBuffer, tEventLength);
	}

	/*
	 * internal state for searchCommand()
	 */
	// to signal searchCommand(), that searchState must be loaded, because a command with yet missing data was processed.
	private boolean searchStateMustBeLoaded = false;

	/*
	 * To signal BT receive thread, that invalidate() message should be sent on next read. If once sent it triggers invalidate(),
	 * which triggers onDraw(), which sets it if not calling invalidate().
	 */
	volatile boolean mNeedUpdateViewMessage = true;

	/*
	 * state between two searchCommand() calls
	 */
	private byte searchStateCommand;
	private byte searchStateCommandReceived; // The command we received, for which data we wait now
	private int searchStateParamsLength; // Parameter length for the above command
	private int searchStateInputLengthToWaitFor = MIN_COMMAND_SIZE; // If available data is less than length, do nothing.
	private long sTimestampOfLastDataWait = 0;

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

	private static final int numberOfBitsInAHalfByte = 4;
	private static final int halfByte = 0x0F;
	private static final char[] hexDigits = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

	public static void appendByteAsHex(StringBuilder aStringBuilder, byte aByte) {
		aStringBuilder.append(hexDigits[(aByte >> numberOfBitsInAHalfByte) & halfByte]);
		aStringBuilder.append(hexDigits[aByte & halfByte]);
	}

	public static String convertByteArrayToHexString(byte[] aData) {
		StringBuilder tDataRaw = new StringBuilder();
		for (int i = 0; i < aData.length; i++) {
			int tValue;
			// Output parameter buffer as hex
			tValue = aData[i];
			SerialService.appendByteAsHex(tDataRaw, (byte) tValue);
			tDataRaw.append(" ");
		}
		return tDataRaw.toString();
	}

	/*
	 * Get byte from buffer, clear buffer, increment pointer and handle wrap around
	 */
	byte getByteFromBuffer() {
		byte tByte = mBigReceiveBuffer[mReceiveBufferOutIndex];
		if (MyLog.isVERBOSE()) {
			mHexOutputTempBuffer[mHexOutputTempBufferActualIndex++] = tByte;
			if (mHexOutputTempBufferActualIndex == SIZE_OF_DEBUG_BUFFER) {
				mHexOutputTempBufferActualIndex = 0;
				StringBuilder tDataRaw = new StringBuilder();
				StringBuilder tDataString = new StringBuilder();
				int tValue;
				for (int i = 0; i < SIZE_OF_DEBUG_BUFFER; i++) {
					// Output parameter buffer as hex
					tValue = mHexOutputTempBuffer[i];
					appendByteAsHex(tDataRaw, (byte) tValue);
					tDataRaw.append(" ");
					byte tChar = mHexOutputTempBuffer[i];
					if (tChar < 0x20) {
						tChar = 0x20;
					}
					tDataString.append(" ");
					tDataString.append((char) tChar);
					tDataString.append(" ");
				}
				// use empty log tag and padding to better formatting of the two lines
				MyLog.v("", "Hex=" + tDataRaw.toString() + "\n   Asc=" + tDataString.toString());
			}
		}
		// clear processed content
		mBigReceiveBuffer[mReceiveBufferOutIndex] = 0x00;
		mReceiveBufferOutIndex++;
		if (mInputBufferWrapAroundIndex > 0 && mReceiveBufferOutIndex >= mInputBufferWrapAroundIndex) {
			mInputBufferWrapAroundIndex = 0;
			mReceiveBufferOutIndex = 0;
		}
		return tByte;
	}

	/**
	 * Search the input buffer for valid commands and call interpretCommand() as long as there is data available. Returns true if it
	 * must be called again.
	 * 
	 * @param aRPCView
	 * @return true if view should be updated, false if no view update needed (e.g. in case of error)
	 */
	boolean searchCommand(RPCView aRPCView) {
		if (inBufferReadingLock || (mReceiveBufferOutIndex == mReceiveBufferInIndex)) {
			if (MyLog.isVERBOSE()) {
				Log.v(LOG_TAG, "searchCommand just returns. No buffer content. Lock=" + inBufferReadingLock + " BufferInIndex="
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

		/*
		 * 2 seconds timeout for data messages which does not arrive (because of reprogramming the client and misinterpreting the
		 * program data)
		 */
		long tActualNanos = System.nanoTime();
		if (searchStateInputLengthToWaitFor > MIN_MESSAGE_SIZE && getBufferBytesAvailable() < searchStateInputLengthToWaitFor) {
			// here we wait for a bigger chunk of data, but it was not completely received yet
			if (sTimestampOfLastDataWait + 2000000000 < tActualNanos) {
				// reset state and continue with searching for sync token
				searchStateInputLengthToWaitFor = MIN_COMMAND_SIZE;
				searchStateMustBeLoaded = false;
				sTimestampOfLastDataWait = tActualNanos;
			}
		} else {
			sTimestampOfLastDataWait = tActualNanos;
		}

		/*
		 * Now the available bytes are more than the number we wait for (e.g. MIN_COMMAND_SIZE or MIN_MESSAGE_SIZE or data size)
		 */
		while (getBufferBytesAvailable() >= searchStateInputLengthToWaitFor) {
			if (searchStateMustBeLoaded) {
				// restore state of last call to searchCommand()
				tLengthReceived = searchStateInputLengthToWaitFor;
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
							Log.e(LOG_TAG, "Sync Token not found util end of buffer. End searchCommand. Out=" + tStartOut + "->"
									+ mReceiveBufferOutIndex + " In=" + tStartIn + "->" + mReceiveBufferInIndex);
							return false;
						}
						if (!MyLog.isVERBOSE()) {
							/*
							 * Do not output this at level verbose, since then RawData is output
							 */
							Log.w(LOG_TAG, "Byte=" + Integer.toHexString(tByte) + " at:" + mReceiveBufferOutIndex
									+ " is no SYNC_TOKEN");
						}
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

				if (MyLog.isVERBOSE()) {
					if (tCommandReceived <= RPCView.INDEX_LAST_FUNCTION_DATAFIELD) {
						MyLog.v(LOG_TAG, "Data: length=" + tLengthReceived + " at ptr=" + (mReceiveBufferOutIndex - 1));
					} else {
						MyLog.v(LOG_TAG, "Command=0x" + Integer.toHexString(tCommandReceived) + " length=" + tLengthReceived
								+ " at ptr=" + (mReceiveBufferOutIndex - 1));
					}
				}

				/*
				 * Plausi for tLengthReceived
				 */
				if ((tCommandReceived <= RPCView.INDEX_LAST_FUNCTION_DATAFIELD && tLengthReceived > mDataBuffer.length)
						|| (tCommandReceived > RPCView.INDEX_LAST_FUNCTION_DATAFIELD && tLengthReceived > MAX_NUMBER_OF_PARAMS * 2)) {
					MyLog.e(LOG_TAG,
							"ParamsLength of " + tLengthReceived + " wrong. Command=0x" + Integer.toHexString(tCommandReceived)
									+ " Out=" + mReceiveBufferOutIndex);
					continue;
				}

				/*
				 * Save state and return if not enough bytes are available in buffer
				 */
				if (getBufferBytesAvailable() < tLengthReceived) {
					searchStateInputLengthToWaitFor = tLengthReceived;
					searchStateCommandReceived = tCommandReceived;
					searchStateCommand = tCommand;
					searchStateParamsLength = tParamsLength;

					searchStateMustBeLoaded = true;
					if (MyLog.isVERBOSE()) {
						Log.v(LOG_TAG, "Not enough data in buffer for a complete command");
					}
					// break in order to draw the bitmap
					tRetval = true;
					break;
				}
			}

			/*
			 * All data available to interpret command or data
			 */
			if (tCommandReceived <= RPCView.INDEX_LAST_FUNCTION_DATAFIELD) {
				long tStart1 = System.nanoTime();
				/*
				 * Data buffer command
				 */
				for (i = 0; i < tLengthReceived; i++) {
					mDataBuffer[i] = getByteFromBuffer();
				}
				if (MyLog.isDEVELOPMENT_TESTING()) {
					StringBuilder tData = new StringBuilder("Data=");
					int tValue;
					for (i = 0; i < tLengthReceived; i++) {
						if (tLengthReceived < 160) {
							// Output parameter buffer as character
							tData.append((char) mDataBuffer[i]);
						} else {
							// Output parameter buffer as hex
							tData.append("0x");
							tValue = mDataBuffer[i];
							appendByteAsHex(tData, (byte) tValue);
							tData.append(" ");
						}
					}
					Log.v(LOG_TAG, tData.toString());
				}
				/*
				 * now both command and data buffer filled
				 */
				searchStateInputLengthToWaitFor = MIN_COMMAND_SIZE;
				aRPCView.interpretCommand(tCommand, mParameters, tParamsLength, mDataBuffer, null, tLengthReceived);
				if (tCommand == RPCView.FUNCTION_DRAW_CHART || tCommand == RPCView.FUNCTION_DRAW_CHART_WITHOUT_DIRECT_RENDERING) {
					mStatisticNumberOfReceivedChartCommands++;
					tNanosForChart += System.nanoTime() - tStart1;
					if (tCommand == RPCView.FUNCTION_DRAW_CHART) {
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

			} else /* Data buffer command */{
				tParamsLength = tLengthReceived / 2;
				tCommand = tCommandReceived;
				/*
				 * Parameters here
				 */

				for (i = 0; i < tParamsLength; i++) {
					tByte = getByteFromBuffer();
					mParameters[i] = convert2BytesToInt(tByte, getByteFromBuffer());
				}
				if (MyLog.isDEVELOPMENT_TESTING()) {
					// Output parameter buffer as short hex values
					StringBuilder tParamsHex = new StringBuilder("Params=");
					int tValue;
					for (i = 0; i < tParamsLength; i++) {
						tValue = mParameters[i];
						tParamsHex.append(" 0x" + Integer.toHexString(tValue & 0xFFFF));
					}
					Log.v(LOG_TAG, tParamsHex.toString());
				}

				if (tCommand < RPCView.INDEX_FIRST_FUNCTION_WITH_DATA) {
					searchStateInputLengthToWaitFor = MIN_COMMAND_SIZE;
					/*
					 * direct commands without data
					 */
					aRPCView.interpretCommand(tCommand, mParameters, tParamsLength, null, null, 0);
					mStatisticNumberOfReceivedCommands++;
					if (tCommand == RPCView.FUNCTION_DRAW_DISPLAY) {
						// break in order to draw the bitmap
						tRetval = true;
						break;
					}

					if ((System.nanoTime() - tStart) > MAX_DRAW_INTERVAL_NANOS) {
						Log.w(LOG_TAG, "Return searchCommand() prematurely after 0.5 seconds to enable display refresh");
						tRetval = true;
						break;
					}
				} else {
					searchStateInputLengthToWaitFor = MIN_MESSAGE_SIZE;
					/*
					 * Wait for header of message part containing the expected data
					 */
					i = 0;
					while (getBufferBytesAvailable() < MIN_MESSAGE_SIZE && i < 100) {
						try {
							if (MyLog.isDEBUG()) {
								// happens quite rare
								Log.d(LOG_TAG, "wait for data header i=" + i);
							}
							Thread.sleep(10);
							i++;
						} catch (InterruptedException e) {
							Log.e(LOG_TAG, "Wait for data header was interrupted", e);
						}
					}
					if (i == 100) {
						MyLog.e(LOG_TAG, "Timeout waiting for data sync token. Out=" + mReceiveBufferOutIndex + " In="
								+ mReceiveBufferInIndex);
					}
				}
			}
		} /* while */
		if (MyLog.isVERBOSE()) {
			Log.v(LOG_TAG, "End searchCommand. Out=" + tStartOut + "->" + mReceiveBufferOutIndex + " = "
					+ (mReceiveBufferOutIndex - tStartOut) + "  In=" + tStartIn + "->" + mReceiveBufferInIndex + " = "
					+ (mReceiveBufferInIndex - tStartIn) + " | " + (mReceiveBufferInIndex - mReceiveBufferOutIndex));
		}
		inBufferReadingLock = false;
		mStatisticNanoTimeForCommands += System.nanoTime() - tStart - tNanosForChart;
		mStatisticNanoTimeForChart += tNanosForChart;
		return tRetval;
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
