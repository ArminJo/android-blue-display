/*
 * 	SUMMARY
 * 	Blue Display is an Open Source Android remote Display for Arduino etc.
 * 	It receives basic draw requests from Arduino etc. over Bluetooth and renders it.
 * 	It also implements basic GUI elements as buttons and sliders.
 * 	It sends touch or GUI callback events over Bluetooth back to Arduino.
 *
 *  Copyright (C) 2019-2020  Armin Joachimsmeyer
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
 * This service handles the USB connection.
 */

package de.joachimsmeyer.android.bluedisplay;

import static android.app.PendingIntent.FLAG_MUTABLE;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Handler;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;

public class USBSerialSocket implements SerialInputOutputManager.Listener {

    private static final String LOG_TAG = "USBSerialSocket";

    public static final String ACTION_USB_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED";
    public static final String ACTION_USB_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED";
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    UsbManager mUsbManager;

    private static final int WRITE_WAIT_MILLIS = 2000; // 0 blocked infinitely on unprogrammed arduino

    private final BlueDisplay mBlueDisplayContext;
    SerialService mSerialService;
    private final Handler mHandler;
    UsbSerialDriver mUsbSerialDriver;
    UsbDevice mUSBDevice;
    UsbDeviceConnection mUSBDeviceConnection;
    UsbSerialPort mUSBSerialPort;
    SerialInputOutputManager mIoManager;

    boolean mIsConnected;

    final Object mWriteLock = new Object();

    USBSerialSocket(BlueDisplay aContext, SerialService aSerialService, Handler aHandler, UsbManager aUsbManager) {

        mBlueDisplayContext = aContext;
        mSerialService = aSerialService;
        mHandler = aHandler;
        mUsbManager = aUsbManager;
        setFilterAndRegisterUSBReceiver();
    }

    private void setFilterAndRegisterUSBReceiver() {
        IntentFilter tFilter = new IntentFilter();
        tFilter.addAction(ACTION_USB_PERMISSION);
        tFilter.addAction(ACTION_USB_DETACHED);
        tFilter.addAction(ACTION_USB_ATTACHED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mBlueDisplayContext.registerReceiver(mUSBReceiver, tFilter, Context.RECEIVER_EXPORTED);
        } else {
            mBlueDisplayContext.registerReceiver(mUSBReceiver, tFilter);
        }
    }

    /*
     * Different notifications will be received here (USB attached, detached, permission response)
     */
    final BroadcastReceiver mUSBReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context aContext, Intent aIntent) {
            if (aIntent.getAction().equals(ACTION_USB_PERMISSION)) {
                boolean granted = aIntent.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);
                if (granted) {
                    // User accepted our USB connection. Try to open the device as a serial port
                    MyLog.i(LOG_TAG, "Got user USB permission -> open device now.");
                    openUSBDevice();
                } else {
                    MyLog.i(LOG_TAG, "Got no user USB permission :-(");
                    disconnect();
                }
            } else if (aIntent.getAction().equals(ACTION_USB_ATTACHED)) {
                MyLog.d(LOG_TAG, "USB attached received");
                // Not needed to connect since the application is restarted (BlueDisplay OnCreate is called)
                // MyLog.i(LOG_TAG, "USB attached received -> call connect()");
                // connect(); // this leads to a read error
            } else if (aIntent.getAction().equals(ACTION_USB_DETACHED)) {
                MyLog.d(LOG_TAG, "USB detached received");
                // The driver get the detached info faster ( by IOException ), so this is redundant.
                // MyLog.i(LOG_TAG, "USB detached received -> call disconnect()");
                // disconnect();
            }
        }
    };

    /*
     * Gets USB devices and ...
     */
    void connect() {
        MyLog.i(LOG_TAG, "In connect()");

        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(mUsbManager);
        if (!availableDrivers.isEmpty()) {
//        mUsbSerialDriver = UsbSerialProber.getDefaultProber().probeDevice(mUsbManager.getDeviceList().get(0));
//        if (mUsbSerialDriver != null) {
            // Set this flag here, since it is used below for signalBlueDisplayConnection()
            mBlueDisplayContext.mUSBDeviceAttached = true;
            // Open a connection to the first available driver.
            mUsbSerialDriver = availableDrivers.get(0);
            mUSBDevice = mUsbSerialDriver.getDevice();
            if (mUsbManager.hasPermission(mUSBDevice)) {
                /*
                 * Open device if permission still granted
                 */
                openUSBDevice();
            } else {
                /*
                 * Request user permission
                 */
                MyLog.i(LOG_TAG,
                        "Request user USB permission for VID=" + mUSBDevice.getVendorId() + " ProductId="
                                + mUSBDevice.getProductId());
                PendingIntent tUSBPermissionIntent = PendingIntent.getBroadcast(mBlueDisplayContext, 0, new Intent(
                        ACTION_USB_PERMISSION), FLAG_MUTABLE);
                mUsbManager.requestPermission(mUSBDevice, tUSBPermissionIntent);
            }
        }
    }

    /*
     * Open USB device and port. Assume that permission is granted.
     */
    void openUSBDevice() {
        MyLog.i(LOG_TAG, "In openUSBDevice()");

        mUSBDeviceConnection = mUsbManager.openDevice(mUSBDevice);
        if (mUSBDeviceConnection == null) {
            MyLog.e(LOG_TAG, "UsbDeviceConnection result of openDevice() is null");
            mIsConnected = false;
        } else {
            /*
             * Open USB port
             */
            mUSBSerialPort = mUsbSerialDriver.getPorts().get(0);
            try {
                mUSBSerialPort.open(mUSBDeviceConnection); // Here I got IOException "Expected 0xee bytes, but get 0xa8 [init#6]"
                mUSBSerialPort.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

                mUSBSerialPort.setDTR(false); // No reset for arduino on app start!
                mUSBSerialPort.setRTS(true); // Channel readiness on some boards
                mIoManager = new SerialInputOutputManager(mUSBSerialPort, this);
                Executors.newSingleThreadExecutor().submit(mIoManager);
                /*
                 * Successful :-)
                 */
                mIsConnected = true;

                // reset flags, buttons, sliders and sensors (and log this :-))
                mBlueDisplayContext.mRPCView.resetAll();
                try {
                    // 50 is to little, 100 works sometimes, 200 works reliable.
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    // Just do nothing
                }
                /*
                 * Start and initialize big ring buffer.
                 */
                mSerialService.resetReceiveBuffer();
                mSerialService.resetStatistics();
                /*
                 * Sending connection message now is too early, since the display size
                 * is not yet set by RPCView SizeChanged event
                 * Let RPCView do the delayed signaling the connection to Client,
                 */
                mBlueDisplayContext.mRPCView.mSendPendingConnectMessage = true;
            } catch (IOException e) {
                MyLog.e(LOG_TAG, "USB open() failed: " + e.getMessage());
                disconnect();
            }
        }

    }

    /*
     * Stop IOManager and disconnect listener Set RTS + DTR to false Close Port and Connection
     */
    void disconnect() {

        MyLog.i(LOG_TAG, "In disconnect()");

        // listener = null; // ignore remaining data and errors
        if (mIoManager != null) {
            mIoManager.setListener(null);
            mIoManager.stop();
            mIoManager = null;
        }
        if (mUSBSerialPort != null) {
            try {
                mUSBSerialPort.setDTR(false);
                mUSBSerialPort.setRTS(false);
            } catch (Exception ignored) {
            }
            try {
                mUSBSerialPort.close();
            } catch (Exception ignored) {
            }
            mUSBSerialPort = null;
        }
        if (mUSBDeviceConnection != null) {
            mUSBDeviceConnection.close();
            mUSBDeviceConnection = null;

            // Indicate that the connection was lost and notify the UI Activity.
            // do not do it twice so do it here
            mIsConnected = false;
            mBlueDisplayContext.mUSBDeviceAttached = false;
            // Send a disconnect message back to the Activity, which resets mUSBDeviceAttached flag
            mHandler.sendEmptyMessage(BlueDisplay.MESSAGE_USB_DISCONNECT);
        }
    }

    @Override
    public void onNewData(byte[] aUSBInputData) {
        // Copy block of bytes from InputData to big receive array
        System.arraycopy(aUSBInputData, 0, mSerialService.mBigReceiveBuffer, mSerialService.mReceiveBufferInIndex,
                aUSBInputData.length);
        if (MyLog.isDEVELOPMENT_TESTING()) {
            MyLog.v(LOG_TAG, "Hex=" + SerialService.convertByteArrayToHexString(aUSBInputData) + "\n");
        }
        mSerialService.handleReceived(aUSBInputData.length);
    }

    public void writeEvent(byte[] aEventDataBuffer, int aEventDataLength) {
        // use synchronized to get synchronous behavior
        synchronized (mWriteLock) {
            // must create a new byte array here, since length of byte array is important :-(
            byte[] tEventDataBufferForUSBDriver = new byte[aEventDataLength];
            System.arraycopy(aEventDataBuffer, 0, tEventDataBufferForUSBDriver, 0, aEventDataLength);
            try {
                mUSBSerialPort.write(tEventDataBufferForUSBDriver, WRITE_WAIT_MILLIS);
            } catch (IOException e) {
                MyLog.e(LOG_TAG, "writeEvent() of " + aEventDataLength + " byte of data failed: " + e);
            }
        }
    }

    @Override
    public void onRunError(Exception e) {
        MyLog.e(LOG_TAG, " Got error on run: " + e);
        disconnect();
    }
}
