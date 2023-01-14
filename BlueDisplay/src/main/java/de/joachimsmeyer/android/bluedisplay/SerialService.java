/*
 *     SUMMARY
 *     Blue Display is an Open Source Android remote Display for Arduino etc.
 *     It receives basic draw requests from Arduino etc. over Bluetooth and renders it.
 *     It also implements basic GUI elements as buttons and sliders.
 *     It sends touch or GUI callback events over Bluetooth back to Arduino.
 * 
 *  Copyright (C) 2014-2020  Armin Joachimsmeyer
 *  armin.joachimsmeyer@gmail.com
 *  
 *     This file is part of BlueDisplay.
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
    /*
     * Current end of buffer content. Last content byte + 1. Ranges from SIZE_OF_IN_BUFFER -
     * max(BluetoothSerialSocket.BT_READ_MAX_SIZE,SerialInputOutputManager.BUFSIZE) to SIZE_OF_IN_BUFFER
     */
    volatile int mInputBufferWrapAroundIndex = SIZE_OF_IN_BUFFER;

    volatile int mReceiveBufferInIndex; // first free byte
    volatile int mReceiveBufferOutIndex; // first unprocessed byte

    byte[] mDataBuffer = new byte[4096]; // Buffer to hold data for one data command
    private volatile boolean inBufferReadingLock = false; // Safety net to avoid 2 instances of search command calls. Should never
                                                          // happen :-).

    public static final int SIZE_OF_DEBUG_BUFFER = 16;
    byte[] mHexOutputTempBuffer = new byte[SIZE_OF_DEBUG_BUFFER]; // holds one output line for verbose HEX output
    int mHexOutputTempBufferCurrentIndex = 0;
    public static final int SIZE_OF_SERIAL_PRINT_BUFFER = 512;
    public byte[] mSerialPrintBuffer = new byte[SIZE_OF_SERIAL_PRINT_BUFFER];
    volatile int mSerialPrintBufferInIndex; // first free byte

    // Forces the end of writing to bitmap after 0.5 seconds and thus allow bitmap to be displayed
    private static final long MAX_DRAW_INTERVAL_NANOS = 500000000;

    // Statistics
    public int mStatisticNumberOfReceivedBytes;
    public int mStatisticNumberOfReceivedCommands;
    public int mStatisticNumberOfReceivedChartCommands;
    public int mStatisticNumberOfSentBytes;
    public int mStatisticNumberOfSentCommands;
    public int mStatisticNumberOfBufferWrapAround;
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

//    public final static int EVENT_TEXT_CALLBACK = 0x2C; // not used yet

    public final static int EVENT_NOP = 0x2F;

    public final static int EVENT_FIRST_SENSOR_ACTION_CODE = 0x30;

    public final static int EVENT_REQUESTED_DATA_CANVAS_SIZE = 0x60;

    private final BlueDisplay mBlueDisplayContext;
    private final Handler mHandler;

    final static int CALLBACK_DATA_SIZE = 15;
    private final byte[] mSendByteBuffer = new byte[CALLBACK_DATA_SIZE]; // Static buffer since we only send one item at a time

    /**
     * Constructor. Prepares a new BluetoothChat session.
     * 
     * @param aContext
     *            The UI Activity Context
     * @param aHandler
     *            A Handler to send messages back to the UI Activity
     */
    public SerialService(BlueDisplay aContext, Handler aHandler) {
        mBlueDisplayContext = aContext;
        mHandler = aHandler;
        resetStatistics();
    }

    /*
     * Called by BT or USB driver thread. Handle statistics, buffer overflow, buffer wrap around.
     */
    void handleReceived(int aReadLength) {

        if (aReadLength == 0) {
            MyLog.w(LOG_TAG, "Read length = 0");
        } else {
            int tOldInIndex = mReceiveBufferInIndex;
            int tOutIndex = mReceiveBufferOutIndex;
            mStatisticNumberOfReceivedBytes += aReadLength;
            mReceiveBufferInIndex += aReadLength;
            int tBytesInBuffer = getBufferBytesAvailable();
            if (tOldInIndex < tOutIndex && mReceiveBufferInIndex > tOutIndex) {
                // input index overtakes out index
                MyLog.e(LOG_TAG, "Buffer overflow! InIndex=" + mReceiveBufferInIndex);
            }

            // check for wrap around
            if (mReceiveBufferInIndex >= SerialService.SIZE_OF_IN_BUFFER
                    - Math.max(BluetoothSerialSocket.BT_READ_MAX_SIZE, SerialInputOutputManager.BUFSIZ)) {
                /*
                 * Not enough space after current mReceiveBufferInIndex for a complete reading of 4096 bytes from driver. Buffer
                 * wrap around. Start new input at start of buffer and note new end of buffer in mInputBufferWrapAroundIndex
                 */
                mInputBufferWrapAroundIndex = mReceiveBufferInIndex;
                mReceiveBufferInIndex = 0;
                mStatisticNumberOfBufferWrapAround++;
                if (MyLog.isVERBOSE()) {
                    // Output length
                    Log.v(LOG_TAG, "Buffer wrap around. Bytes in buffer=" + (mInputBufferWrapAroundIndex - tOutIndex));
                }
                if (mReceiveBufferInIndex > tOutIndex) {
                    // After wrap bigger than out index
                    MyLog.e(LOG_TAG, "Buffer overflow! InIndex=" + mReceiveBufferInIndex);
                }
            }
            if (MyLog.isVERBOSE()) {
                // Output length
                Log.v(LOG_TAG, "Read length=" + aReadLength + " BufferInIndex=" + mReceiveBufferInIndex);
            }

            if (mRequireUpdateViewMessage) {
                mHandler.sendEmptyMessage(BlueDisplay.MESSAGE_UPDATE_VIEW);
                mRequireUpdateViewMessage = false;
                if (MyLog.isDEVELOPMENT_TESTING()) {
                    Log.v(LOG_TAG, "Send MESSAGE_UPDATE_VIEW. Bytes in buffer=" + tBytesInBuffer);
                }
            } else {
                if (MyLog.isDEVELOPMENT_TESTING() && MyLog.isVERBOSE()) {
                    Log.v(LOG_TAG, "No required to send message MESSAGE_UPDATE_VIEW. Bytes in buffer=" + tBytesInBuffer);
                }
            }
        }
    }

    void resetReceiveBuffer() {
        mReceiveBufferInIndex = 0;
        mReceiveBufferOutIndex = 0;
        mSerialPrintBufferInIndex = 0;
    }

    void resetStatistics() {
        mStatisticNumberOfReceivedBytes = 0;
        mStatisticNumberOfReceivedCommands = 0;
        mStatisticNumberOfReceivedChartCommands = 0;
        mStatisticNumberOfSentBytes = 0;
        mStatisticNumberOfSentCommands = 0;
        mStatisticNumberOfBufferWrapAround = 0;
        mStatisticNanoTimeForCommands = 0;
        mStatisticNanoTimeForChart = 0;
    }

    public String getStatisticsString() {
        String tReturn = mStatisticNumberOfReceivedBytes + " bytes, " + mStatisticNumberOfReceivedCommands + " commands and "
                + mStatisticNumberOfReceivedChartCommands + " charts received\n";
        if (mStatisticNumberOfReceivedCommands != 0) {
            tReturn += ((mStatisticNanoTimeForCommands / 1000) / mStatisticNumberOfReceivedCommands) + " \u00B5s per command\n";
        }
        if (mStatisticNumberOfReceivedChartCommands != 0) {
            tReturn += ((mStatisticNanoTimeForChart / 1000) / mStatisticNumberOfReceivedChartCommands) + " \u00B5s per chart command\n";
        }
        tReturn += mStatisticNumberOfSentBytes + " bytes, " + mStatisticNumberOfSentCommands + " commands sent\n";

        tReturn += "Buffer wrap arounds=" + mStatisticNumberOfBufferWrapAround + "\n";
        int tInputBufferOutIndex = mReceiveBufferOutIndex;
        int tBytesInBuffer = getBufferBytesAvailable();
        String tSearchStateDataLengthToWaitForString = "";
        if (searchStateInputLengthToWaitFor > MIN_MESSAGE_SIZE) {
            tSearchStateDataLengthToWaitForString = ", waited for " + searchStateInputLengthToWaitFor;
        }
        tReturn += "InputBuffer: size=" + SIZE_OF_IN_BUFFER + ", in=" + mReceiveBufferInIndex + ", out=" + tInputBufferOutIndex
                + ", not processed=" + tBytesInBuffer + tSearchStateDataLengthToWaitForString + "\n";
        if (MyLog.isDEBUG()) {
            if (tBytesInBuffer > 0) {
                if (tBytesInBuffer > 20) {
                    tBytesInBuffer = 20;
                }
                // output content but max. 20 bytes
                StringBuilder tContent = new StringBuilder(5 * 24);
                int tValue;
                for (int i = 0; i < tBytesInBuffer; i++) {
                    tContent.append(" 0x");
                    tValue = mBigReceiveBuffer[tInputBufferOutIndex + i];
                    tContent.append(Integer.toHexString(tValue & 0xFF));
                }
                tReturn += tContent + "\n";
            }
        }

        RPCView tRPCView = mBlueDisplayContext.mRPCView;
        tReturn += "Scale=" + tRPCView.mScaleFactor * 100 + "%     max=" + tRPCView.mCurrentViewWidth + "/"
                + tRPCView.mCurrentViewHeight + "\nRequested=" + tRPCView.mRequestedCanvasWidth + "*"
                + tRPCView.mRequestedCanvasHeight + " -> current=" + tRPCView.mCurrentCanvasWidth + "*"
                + tRPCView.mCurrentCanvasHeight + "\n";
        tReturn += "Codepage=" + System.getProperty("file.encoding");
        return tReturn;
    }

    /*
     * Signal connection to Arduino. First write a NOP command for synchronizing, i.e. the client receive buffer is filled up once.
     * Then send EVENT_CONNECTION_BUILD_UP, which calls the connect and redraw callback, specified at initCommunication(), on the
     * client. The very first call for USB sends 0 as current size values. In response we first get a NOP command for syncing the
     * host, and then the commands of the client ConnectCallback() function.
     */
    void signalBlueDisplayConnection() {
        // first write a NOP command for synchronizing
        writeGuiCallbackEvent(SerialService.EVENT_NOP, 0, 0, 0, null);
        writeTwoIntegerEventAndTimestamp(SerialService.EVENT_CONNECTION_BUILD_UP, mBlueDisplayContext.mRPCView.mCurrentViewWidth,
                mBlueDisplayContext.mRPCView.mCurrentViewHeight);
    }

    void writeEvent(byte[] aEventDataBuffer, int aEventDataLength) {
        if (mBlueDisplayContext.mDeviceConnected) {
            if (mBlueDisplayContext.mUSBDeviceAttached) {
                mBlueDisplayContext.mUSBSerialSocket.writeEvent(aEventDataBuffer, aEventDataLength);
            } else {
                mBlueDisplayContext.mBTSerialSocket.writeEvent(aEventDataBuffer, aEventDataLength);
            }
        } else {
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
        mSendByteBuffer[tIndex] = SYNC_TOKEN;
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
        mSendByteBuffer[tIndex] = SYNC_TOKEN;
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
        mSendByteBuffer[tIndex] = SYNC_TOKEN;
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
        mSendByteBuffer[tIndex] = SYNC_TOKEN;

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
        mSendByteBuffer[tIndex++] = (byte) (0x00); // LSB
        mSendByteBuffer[tIndex++] = (byte) (0x00); // MSB
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
        mSendByteBuffer[tIndex] = SYNC_TOKEN;
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
        mSendByteBuffer[tIndex] = SYNC_TOKEN;
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
        mSendByteBuffer[tIndex] = SYNC_TOKEN;
        if (MyLog.isDEBUG()) {
            String tType = RPCView.sActionMappings.get(tEventType);
            MyLog.d(LOG_TAG, "Send Sensor Event Type=0x" + Integer.toHexString(tEventType) + "|" + tType + " X=" + aValueX + " Y="
                    + aValueY + " Z=" + aValueZ);
        }

        mStatisticNumberOfSentBytes += tEventLength;
        mStatisticNumberOfSentCommands++;

        writeEvent(mSendByteBuffer, tEventLength);

    }

    /*
     * Never used yet
     */
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
        mSendByteBuffer[tIndex] = SYNC_TOKEN;
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
        mSendByteBuffer[tIndex] = SYNC_TOKEN;
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
    volatile boolean mRequireUpdateViewMessage = true;

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
    private static final int[] mParameters = new int[MAX_NUMBER_OF_PARAMS];

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
        for (byte aDatum : aData) {
            int tValue;
            // Output parameter buffer as hex
            tValue = aDatum;
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
            mHexOutputTempBuffer[mHexOutputTempBufferCurrentIndex++] = tByte;
            // print 16 values as HEX and ASCII
            if (mHexOutputTempBufferCurrentIndex == SIZE_OF_DEBUG_BUFFER) {
                mHexOutputTempBufferCurrentIndex = 0;
                StringBuilder tDataRaw = new StringBuilder();
                StringBuilder tDataString = new StringBuilder();
                byte tValue;
                for (int i = 0; i < SIZE_OF_DEBUG_BUFFER; i++) {
                    // Output parameter buffer as hex
                    tValue = mHexOutputTempBuffer[i];
                    appendByteAsHex(tDataRaw, tValue);
                    tDataRaw.append(" ");
                    byte tChar = mHexOutputTempBuffer[i];
                    if (tChar < 0x20) {
                        tChar = 0x20;
                    }
                    if (tValue == SYNC_TOKEN) {
                        // Sync token which starts a new command
                        tDataString.append("|->");
                    } else {
                        tDataString.append(" ");
                        tDataString.append((char) tChar);
                        tDataString.append(" ");
                    }
                }
                // use empty log tag and padding to better formatting of the two lines
                MyLog.v("", "Hex=" + tDataRaw + "\n   Asc=" + tDataString);
            }
        }
        // clear processed content
        mBigReceiveBuffer[mReceiveBufferOutIndex] = 0x00;
        mReceiveBufferOutIndex++;
        if (mReceiveBufferOutIndex >= mInputBufferWrapAroundIndex) {
            // do output wrap around and reset mInputBufferWrapAroundIndex
            mInputBufferWrapAroundIndex = SIZE_OF_IN_BUFFER;
            mReceiveBufferOutIndex = 0;
        }
        return tByte;
    }

    public static final int RPCVIEW_DO_NOTHING = 0; // No data in buffer, no need for draw -> request trigger from socket.
    public static final int RPCVIEW_DO_WAIT = 1; // We had data, but not a complete command, so rendering makes no sense, wait and
                                                 // call again.
    public static final int RPCVIEW_DO_DRAW = 2; // The canvas should be rendered, and we have no more commands -> draw and request
                                                 // new trigger.
    public static final int RPCVIEW_DO_DRAW_AND_CALL_AGAIN = 3; // The canvas should be rendered, but we may have more data, so try
                                                                // it again

    // after rendering -> call invalidate().

    /**
     * Search the input buffer for valid commands and call interpretCommand() as long as there is data available.
     * 
     * @param aRPCView pointer to RPCView object
     * @return true if we have more data in the buffer but want to redraw now, e.g. after a FUNCTION_DRAW_CHART command.
     */
    int searchCommand(RPCView aRPCView) {
        if (inBufferReadingLock || (mReceiveBufferOutIndex == mReceiveBufferInIndex)) {
            if (MyLog.isVERBOSE()) {
                Log.v(LOG_TAG, "searchCommand just returns. No buffer content. Lock=" + inBufferReadingLock + " BufferInIndex="
                        + mReceiveBufferInIndex);
            }
            return RPCVIEW_DO_NOTHING;
        }
        int tRetval = RPCVIEW_DO_WAIT;
        long tStartOfSearchCommand = System.nanoTime(); // We require it as nanos, because we compute the
                                                        // mStatisticNanoTimeForCommands with it
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
         * While reprogramming the client we also interpret this data, since it is sent over the same Serial line. But in this case
         * we tend to misinterpreting the data, since it is not meant for BlueDisplay. Sometimes we misinterpret it as a command
         * with a big chunk of data, but the data will of course not be delivered. So we introduced a 2 seconds timeout for data to
         * arrive. Otherwise we start a fresh scan.
         */
        long tCurrentNanos = System.nanoTime();
        if (searchStateInputLengthToWaitFor > MIN_MESSAGE_SIZE && getBufferBytesAvailable() < searchStateInputLengthToWaitFor) {
            // Here we assume that we wait for a big chunk of data, but it was not completely received yet
            if (sTimestampOfLastDataWait + 2000000000 < tCurrentNanos) {
                // reset state and continue with searching for sync token
                searchStateInputLengthToWaitFor = MIN_COMMAND_SIZE;
                searchStateMustBeLoaded = false;
                sTimestampOfLastDataWait = tCurrentNanos;
            }
        } else {
            sTimestampOfLastDataWait = tCurrentNanos;
        }

        /*
         * Now the available bytes are more than the number we wait for (e.g. MIN_COMMAND_SIZE or MIN_MESSAGE_SIZE or data size) so
         * we have a chance, that we received a complete command.
         */
        while (getBufferBytesAvailable() >= searchStateInputLengthToWaitFor) {
            if (searchStateMustBeLoaded) {
                // restore state of last call to searchCommand()
                tLengthReceived = searchStateInputLengthToWaitFor;
                tCommandReceived = searchStateCommandReceived;
                tCommand = searchStateCommand;
                tParamsLength = searchStateParamsLength;
                searchStateMustBeLoaded = false;
                if (MyLog.isVERBOSE()) {
                    MyLog.v(LOG_TAG, "Restore previous state");
                }
            } else {

                /*
                 * Scan for SYNC token. Here we expect the buffer to start with a sync token.
                 */
                if (!scanBufferForSyncToken(tStartIn, tStartOut)) {
                    return RPCVIEW_DO_NOTHING;
                }

                /*
                 * Read command token from InputStream
                 */
                tCommandReceived = getByteFromBuffer();

                /*
                 * Read parameter/data length
                 */
                tByte = getByteFromBuffer();
                tLengthReceived = convert2BytesToInt(tByte, getByteFromBuffer());

                if (tCommandReceived <= RPCView.INDEX_LAST_FUNCTION_DATAFIELD) {
                    /*
                     * Data length received
                     */
                    if (MyLog.isVERBOSE()) {
                        MyLog.v(LOG_TAG, "Data: length=" + tLengthReceived + " at ptr=" + (mReceiveBufferOutIndex - 1));
                    }
                    // Plausi
                    if (tLengthReceived > mDataBuffer.length) {
                        MyLog.e(LOG_TAG,
                                "DataLength of " + tLengthReceived + " wrong. Command=0x" + Integer.toHexString(tCommandReceived)
                                        + " Out=" + mReceiveBufferOutIndex);
                        continue;
                    }

                } else {
                    /*
                     * Parameter length received
                     */
                    if (MyLog.isVERBOSE()) {
                        MyLog.v(LOG_TAG, "Command=0x" + Integer.toHexString(tCommandReceived) + " ParameterLength="
                                + tLengthReceived + " at ptr=" + (mReceiveBufferOutIndex - 1));
                    }
                    // Plausi
                    if (tLengthReceived > MAX_NUMBER_OF_PARAMS * 2) {
                        MyLog.e(LOG_TAG, "ParameterLength of " + tLengthReceived + "/0x" + Integer.toHexString(tLengthReceived)
                                + " wrong. Command=0x" + Integer.toHexString(tCommandReceived) + " Out=" + mReceiveBufferOutIndex);
                        continue;
                    }
                }

                /*
                 * Save state and return if not enough bytes for data or command parameter are available in buffer
                 */
                if (getBufferBytesAvailable() < tLengthReceived) {
                    searchStateInputLengthToWaitFor = tLengthReceived;
                    searchStateCommandReceived = tCommandReceived;
                    searchStateCommand = tCommand;
                    searchStateParamsLength = tParamsLength;

                    searchStateMustBeLoaded = true;
                    if (MyLog.isVERBOSE()) {
                        if (tCommandReceived <= RPCView.INDEX_LAST_FUNCTION_DATAFIELD) {
                            Log.v(LOG_TAG, getBufferBytesAvailable() + "bytes in buffer, but " + tLengthReceived
                                    + " required for data field");
                        } else {
                            Log.v(LOG_TAG, getBufferBytesAvailable() + "bytes in buffer, but " + tLengthReceived
                                    + " required for command parameters");
                        }
                    }
                    break;
                }
            }

            /*
             * Now all bytes available to interpret command or data
             */
            if (tCommandReceived <= RPCView.INDEX_LAST_FUNCTION_DATAFIELD) {
                /*
                 * Data buffer command
                 */
                long tStart1 = System.nanoTime();

                for (i = 0; i < tLengthReceived; i++) {
                    mDataBuffer[i] = getByteFromBuffer();
                }
                if (MyLog.isDEVELOPMENT_TESTING() && MyLog.isVERBOSE()) {
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
                 * Now both command and data buffer filled -> interpret command.
                 */
                searchStateInputLengthToWaitFor = MIN_COMMAND_SIZE;
                aRPCView.interpretCommand(tCommand, mParameters, tParamsLength, mDataBuffer, null, tLengthReceived);
                tRetval = RPCVIEW_DO_DRAW;
                if (tCommand == RPCView.FUNCTION_DRAW_CHART || tCommand == RPCView.FUNCTION_DRAW_CHART_WITHOUT_DIRECT_RENDERING) {
                    // do statistics
                    mStatisticNumberOfReceivedChartCommands++;
                    tNanosForChart += System.nanoTime() - tStart1;

                    if (tCommand == RPCView.FUNCTION_DRAW_CHART) {
                        int tBufferBytesAvailable = getBufferBytesAvailable();
                        if (tBufferBytesAvailable > 0) {
                            // We still have bytes in the buffer, so call again
                            tRetval = RPCVIEW_DO_DRAW_AND_CALL_AGAIN;
                            if (tBufferBytesAvailable > 2000) {
                                /*
                                 * Scan for clear screen command and skip content until it
                                 */
                                scanBufferForClearDisplayOptionalCommandAndSkip(tBufferBytesAvailable);
                            }
                        }
                        // break in order to draw a chart directly
                        break;
                    }
                } else {
                    mStatisticNumberOfReceivedCommands++;
                }

                if ((System.nanoTime() - tStartOfSearchCommand) > MAX_DRAW_INTERVAL_NANOS) {
                    // Safety net, never seen this.
                    Log.w(LOG_TAG, "Return searchCommand() prematurely after 0.5 seconds");
                    // break after 0.5 seconds to enable drawing of the bitmap
                    tRetval = RPCVIEW_DO_DRAW_AND_CALL_AGAIN;
                    break;
                }

            } else /* Data buffer command */{
                /*
                 * Command parameters here
                 */
                tParamsLength = tLengthReceived / 2;
                tCommand = tCommandReceived;

                for (i = 0; i < tParamsLength; i++) {
                    tByte = getByteFromBuffer();
                    mParameters[i] = convert2BytesToInt(tByte, getByteFromBuffer());
                }
                if (MyLog.isDEVELOPMENT_TESTING() && MyLog.isVERBOSE()) {
                    // Output parameter buffer as short hex values
                    StringBuilder tParamsHex = new StringBuilder("Params=");
                    int tValue;
                    for (i = 0; i < tParamsLength; i++) {
                        tValue = mParameters[i];
                        tParamsHex.append(" 0x").append(Integer.toHexString(tValue & 0xFFFF));
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
                        if (getBufferBytesAvailable() > 0) {
                            // We still have bytes in the buffer so call again
                            tRetval = RPCVIEW_DO_DRAW_AND_CALL_AGAIN;
                        }
                        // break in order to draw the bitmap as requested by FUNCTION_DRAW_DISPLAY
                        break;
                    }

                    if ((System.nanoTime() - tStartOfSearchCommand) > MAX_DRAW_INTERVAL_NANOS) {
                        Log.w(LOG_TAG, "Return searchCommand() prematurely after 0.5 seconds to enable display refresh");
                        tRetval = RPCVIEW_DO_DRAW_AND_CALL_AGAIN;
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
            Log.v(LOG_TAG, "End searchCommand. Out=" + tStartOut + "->" + mReceiveBufferOutIndex + "="
                    + (mReceiveBufferOutIndex - tStartOut) + " In=" + tStartIn + "->" + mReceiveBufferInIndex + "="
                    + (mReceiveBufferInIndex - tStartIn) + " bytes in buffer=" + (mReceiveBufferInIndex - mReceiveBufferOutIndex));
        }
        inBufferReadingLock = false;
        mStatisticNanoTimeForCommands += System.nanoTime() - tStartOfSearchCommand - tNanosForChart;
        mStatisticNanoTimeForChart += tNanosForChart;
        return tRetval;
    }

    /*
     * Scan for SYNC token. Here we expect the buffer to start with a sync token.
     */
    private boolean scanBufferForSyncToken(int aStartIn, int aStartOut) {
        byte tByte;
        do {
            tByte = getByteFromBuffer();
            if (tByte != SYNC_TOKEN) {
                if (tByte >= ' ') { // byte us signed!
                    // If ASCII append it to string buffer
                    mSerialPrintBuffer[mSerialPrintBufferInIndex++] = tByte;
                    if (mSerialPrintBufferInIndex == SIZE_OF_SERIAL_PRINT_BUFFER) {
                        mSerialPrintBufferInIndex--;
                    }

                } else if ((tByte == '\n' || tByte == '\r')) {
                    if (mSerialPrintBufferInIndex > 0) {
                        // print string buffer as warning to be contained in the log
                        String tStringFromSerial = new String(mSerialPrintBuffer, 0, mSerialPrintBufferInIndex);
                        MyLog.w("Serial.print", tStringFromSerial);
                        mSerialPrintBufferInIndex = 0;
                        mBlueDisplayContext.mRPCView.showAsDebugToast(tStringFromSerial);
                    }
                } else {
                    // reset string buffer
                    mSerialPrintBufferInIndex = 0;
                    if (!MyLog.isVERBOSE()) {
                        /*
                         * Do not output this at level verbose, since at this level RawData is output
                         */
                        Log.w(LOG_TAG, "Byte=0x" + Integer.toHexString(tByte) + " at:" + mReceiveBufferOutIndex
                                + " is no SYNC_TOKEN");
                    }
                }
                if (mReceiveBufferOutIndex == mReceiveBufferInIndex) {
                    inBufferReadingLock = false;
                    if (mSerialPrintBufferInIndex == 0) {
                        Log.i(LOG_TAG, "Sync Token not found util end of buffer. End searchCommand. Out=" + aStartOut + "->"
                                + mReceiveBufferOutIndex + " In=" + aStartIn + "->" + mReceiveBufferInIndex);
                    }
                    return false;
                }

            } else {
                if (mSerialPrintBufferInIndex > 0) {
                    // print if \n or \r are missing
                    // print string buffer as warning to be contained in the log
                    String tStringFromSerial = new String(mSerialPrintBuffer, 0, mSerialPrintBufferInIndex);
                    MyLog.w("Serial.print", tStringFromSerial);
                    mSerialPrintBufferInIndex = 0;
                    mBlueDisplayContext.mRPCView.showAsDebugToast(tStringFromSerial);
}
            }
        } while (tByte != SYNC_TOKEN);
        return true;
    }

    /*
     * Scan for next clear screen command. return true if found
     */
    private void scanBufferForClearDisplayOptionalCommandAndSkip(int aBytesToScan) {
        int tByteCount = 0;
        // initialize
        int tBufferIndex = mReceiveBufferOutIndex;
        int tSyncInputBufferWrapAroundIndex = mInputBufferWrapAroundIndex;

        while (tByteCount < (aBytesToScan - 1)) {
            // store sync values
            int tIndexOfSyncToken = tBufferIndex;
            int tWrapAroundIndexOfSyncToken = tSyncInputBufferWrapAroundIndex;

            // get byte
            byte tByte = mBigReceiveBuffer[tBufferIndex];

            // advance pointers for next byte
            tByteCount++;
            tBufferIndex++;
            // wrap around
            if (tBufferIndex >= mInputBufferWrapAroundIndex) {
                tBufferIndex = 0;
                // not totally threadsafe ;-)
                tSyncInputBufferWrapAroundIndex = SIZE_OF_IN_BUFFER;
            }

            // double check
            if (tByte == SYNC_TOKEN) {
                tByte = mBigReceiveBuffer[tBufferIndex];
                if (tByte == RPCView.FUNCTION_CLEAR_DISPLAY_OPTIONAL) {
                    /*
                     * Found clear display optional -> skip buffer content and change command to clear buffer.
                     */
                    mBigReceiveBuffer[tBufferIndex] = RPCView.FUNCTION_CLEAR_DISPLAY;
                    mReceiveBufferOutIndex = tIndexOfSyncToken;
                    mInputBufferWrapAroundIndex = tWrapAroundIndexOfSyncToken;
                    Log.w(LOG_TAG, "Skip " + (tByteCount - 2)
                            + " bytes until next CLEAR_DISPLAY_OPTIONAL command. Bytes in buffer==" + aBytesToScan + "->"
                            + getBufferBytesAvailable());
                    break;
                }

                // not the right command -> advance pointers for next byte
                tBufferIndex++;
                tByteCount++;
                // wrap around
                if (tBufferIndex >= mInputBufferWrapAroundIndex) {
                    tBufferIndex = 0;
                    // not totally threadsafe ;-)
                    tSyncInputBufferWrapAroundIndex = SIZE_OF_IN_BUFFER;
                }
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
