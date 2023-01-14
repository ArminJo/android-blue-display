/*
 * 	Sensors.java
 * 
 *  Android axis are defined for "natural" screen orientation, which is portrait for my devices:
 *  See https://source.android.com/devices/sensors/sensor-types
 *    When the device lies flat on a table and its left side is down and right side is up or pushed on its left side toward the right,
 *      the X acceleration value is positive.
 *    When the device lies flat on a table and its bottom side is down and top side is up or pushed on its bottom side toward the top,
 *      the Y acceleration value is positive.
 *    When the device lies flat on a table, the acceleration value along Z is +9.81 (m/s^2)
 *
 *  The BlueDisplay application converts the axis, so that this definition holds for EACH screen orientation.
 *  So we have:
 *  X positive -> left down
 *  X negative -> right down
 *  Y positive -> backward / bottom down
 *  Y negative -> forward  / top down
 *  Unit is (m/s^2)
 *
 *  Rotation is positive in the counterclockwise direction:
 *  X positive -> roll bottom downwards
 *  X negative -> roll top downwards
 *  Y positive -> pitch right downwards
 *  Y negative -> pitch left downwards
 *  Z positive -> rotate counterclockwise
 *  Z negative -> rotate clockwise
 *  Unit is radians per second (rad/s) 1 -> ~57 degree per second
 *
 *  If FLAG_SENSOR_SIMPLE_FILTER is set on sensor registering, then sensor values are sent via BT only if values changed.
 *  To avoid noise (event value is solely switching between 2 values), values are skipped too if they are equal last or second last value.
 * 
 *  Copyright (C) 2015-2020  Armin Joachimsmeyer
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
 *  This is the view which interprets the global and graphic commands received by serial service.
 *  It also handles touch events and swipe as well as long touch detection.
 *  
 */

package de.joachimsmeyer.android.bluedisplay;

import java.util.ArrayList;
import java.util.List;

import android.content.pm.ActivityInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.Surface;

public class Sensors implements SensorEventListener {

    public static final String LOG_TAG = "Sensors";

    public static final int FLAG_SENSOR_NO_FILTER = 0;
    public static final int FLAG_SENSOR_SIMPLE_FILTER = 1;

    static List<SensorInfo> sAvailableSensorList = new ArrayList<>(32); // have seen more than 18 sensors in a device

    static class SensorInfo {
        final Sensor mSensor;
        int mRate;
        int mFilterFlag;
        boolean isActive;
        float mLastValueX;
        float mLastValueY;
        float mLastValueZ;
        float mSecondLastValueX;
        float mSecondLastValueY;
        float mSecondLastValueZ;

        SensorInfo(Sensor aSensor, int aRate, boolean aIsActive) {
            mSensor = aSensor;
            mRate = aRate;
            isActive = aIsActive;
            mLastValueX = 0;
            mLastValueY = 0;
            mLastValueZ = 0;
            mSecondLastValueX = 0;
            mSecondLastValueY = 0;
            mSecondLastValueZ = 0;
        }

        /*
         * Sensor values are sent only if value changed. To avoid noise (event value is solely switching between 2 values), values
         * are skipped if they are equal last or second last value.
         * 
         * @return true if current value is no "noise"
         */
        boolean checkIfValueIsNoNoise(SensorEvent aEvent) {
            if ((aEvent.values[0] == mLastValueX || aEvent.values[0] == mSecondLastValueX)
                    && (aEvent.values[1] == mLastValueY || aEvent.values[1] == mSecondLastValueY)
                    && (aEvent.values[2] == mLastValueZ || aEvent.values[2] == mSecondLastValueZ)) {
                if (MyLog.isVERBOSE()) {
                    Log.v(LOG_TAG, "Event detected as noise");
                }
                return false;
            }
            mSecondLastValueX = mLastValueX;
            mSecondLastValueY = mLastValueY;
            mSecondLastValueZ = mLastValueZ;
            mLastValueX = aEvent.values[0];
            mLastValueY = aEvent.values[1];
            mLastValueZ = aEvent.values[2];
            return true;
        }
    }

    private final BlueDisplay mBlueDisplayContext;

    SensorManager mSensorManager;

    public OrientationEventListener mOrientationEventListener;
    boolean isOrientationEventListenerEnabled = false;

    public Sensors(BlueDisplay aContext, SensorManager aSensorManager) {
        mBlueDisplayContext = aContext;
        mSensorManager = aSensorManager;
        getAllAvailableSensors();

        /*
         * To detect direct switching from 0 to 180 and from 90 to 270 degrees and vice versa which does not call
         * onConfigurationChanged() Used by onSensorChanged, to adjust X + Y values relative to rotation of canvas.
         */
        mOrientationEventListener = new OrientationEventListener(mBlueDisplayContext, SensorManager.SENSOR_DELAY_NORMAL) {
            @Override
            public void onOrientationChanged(int mAngle) {
                if (mAngle > 0) {
                    /*
                     * Process only, if orientation can change <br>
                     * angle 0 is Surface.ROTATION_0 <br>
                     * angle 90 is Surface.ROTATION_270<br>
                     * angle 180 is Surface.ROTATION_180<br>
                     * angle 270 is Surface.ROTATION_90
                     */
                    if (!mBlueDisplayContext.mOrientationisLockedByClient
                            || mBlueDisplayContext.mCurrentScreenOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            || mBlueDisplayContext.mCurrentScreenOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT) {
                        if ((315 < mAngle || mAngle < 45 && mBlueDisplayContext.mCurrentRotation != Surface.ROTATION_0)
                                || (45 < mAngle && mAngle < 135 && mBlueDisplayContext.mCurrentRotation != Surface.ROTATION_270)
                                || (135 < mAngle && mAngle < 225 && mBlueDisplayContext.mCurrentRotation != Surface.ROTATION_180)
                                || (225 < mAngle && mAngle < 315 && mBlueDisplayContext.mCurrentRotation != Surface.ROTATION_90)) {
                            if (MyLog.isDEBUG()) {
                                MyLog.d(LOG_TAG, "Trigger reading of rotation. Angle=" + mAngle);
                            }
                            if (mBlueDisplayContext.mCurrentRotation != mBlueDisplayContext.getWindowManager().getDefaultDisplay()
                                    .getRotation()) {
                                mBlueDisplayContext
                                        .setCurrentScreenOrientationAndRotationVariables(mBlueDisplayContext.mCurrentScreenOrientation);
                            }
                        }
                    }
                }
            }
        };
    }

    public void getAllAvailableSensors() {
        List<Sensor> tSensorsList = mSensorManager.getSensorList(Sensor.TYPE_ALL);
        for (Sensor tSensor : tSensorsList) {
            SensorInfo tSensorInfo = new SensorInfo(tSensor, 3, false);
            sAvailableSensorList.add(tSensorInfo);
            if (MyLog.isINFO()) {
                MyLog.i(LOG_TAG, "Sensor found: " + tSensor.toString());
            }
        }
    }

    /*
     * Activate or deactivate sensor. To be called from interpret command.
     */
    public void setSensor(int aSensorType, boolean DoActivate, int aSensorRate, int aFilterFlag) {
        if (MyLog.isINFO()) {
            MyLog.i(LOG_TAG, "SetSensor sensor=" + aSensorType + " DoActivate=" + DoActivate + " rate=" + aSensorRate);
        }
        for (SensorInfo tSensorInfo : sAvailableSensorList) {
            if (tSensorInfo.mSensor.getType() == aSensorType) {
                if (aSensorRate > SensorManager.SENSOR_DELAY_NORMAL) {
                    // sensor rate received is in milliseconds -> convert it to microseconds
                    aSensorRate *= 1000;
                }
                if (DoActivate) {
                    mSensorManager.registerListener(this, tSensorInfo.mSensor, aSensorRate);
                } else {
                    mSensorManager.unregisterListener(this, tSensorInfo.mSensor);
                }
                tSensorInfo.isActive = DoActivate;
                tSensorInfo.mFilterFlag = aFilterFlag;
                if (!isOrientationEventListenerEnabled) {
                    if (mOrientationEventListener.canDetectOrientation()) {
                        if (MyLog.isINFO()) {
                            MyLog.i(LOG_TAG, "Enable OrientationEventListener");
                        }
                        mOrientationEventListener.enable();
                        isOrientationEventListenerEnabled = true;
                    } else {
                        MyLog.w(LOG_TAG, "Can't detect orientation");
                    }
                }
            }
        }
    }

    public static void disableAllSensors() {
        for (SensorInfo tSensorInfo : sAvailableSensorList) {
            tSensorInfo.isActive = false;
        }
    }

    public void registerAllActiveSensorListeners() {
        for (SensorInfo tSensorInfo : sAvailableSensorList) {
            if (tSensorInfo.isActive) {
                mSensorManager.registerListener(this, tSensorInfo.mSensor, SensorManager.SENSOR_DELAY_UI);
                if (MyLog.isDEBUG()) {
                    MyLog.d(LOG_TAG, "Register listener sensor=" + tSensorInfo.mSensor.getName());
                }
            }
        }
        if (!isOrientationEventListenerEnabled && mOrientationEventListener.canDetectOrientation()) {
            mOrientationEventListener.enable();
            isOrientationEventListenerEnabled = true;
        }
    }

    public void deregisterAllActiveSensorListeners() {
        mSensorManager.unregisterListener(this);
        mOrientationEventListener.disable();
        isOrientationEventListenerEnabled = false;
    }

    boolean isSensorEnabledAndEventValuesNoNoise(SensorEvent aEvent) {
        for (SensorInfo tSensorInfo : sAvailableSensorList) {
            Sensor tSensor = tSensorInfo.mSensor;
            if (tSensor.getType() == tSensor.getType() && tSensorInfo.isActive) {
                // Sensor found and active, do optional noise check
                if (tSensorInfo.mFilterFlag != FLAG_SENSOR_SIMPLE_FILTER || tSensorInfo.checkIfValueIsNoNoise(aEvent)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void onSensorChanged(SensorEvent aEvent) {
        int tSensorType = aEvent.sensor.getType();
        if (isSensorEnabledAndEventValuesNoNoise(aEvent)) {
            if (MyLog.isVERBOSE()) {
                Log.v(LOG_TAG, "onSensorChanged sensorType=" + tSensorType);
            }
            if (tSensorType == Sensor.TYPE_ACCELEROMETER || tSensorType == Sensor.TYPE_GRAVITY
                    || tSensorType == Sensor.TYPE_GYROSCOPE || tSensorType == Sensor.TYPE_LINEAR_ACCELERATION
                    || tSensorType == Sensor.TYPE_MAGNETIC_FIELD) {

                float ValueX = aEvent.values[0];
                float ValueY = aEvent.values[1];
                float ValueZ = aEvent.values[2];
                if (MyLog.isVERBOSE()) {
                    // timestamp is in nanoseconds
                    Log.v(LOG_TAG, "Values=" + ValueX + " " + ValueY + " " + ValueZ + " rotation="
                            + mBlueDisplayContext.mCurrentRotation + " TimeStamp=" + aEvent.timestamp / 1000 + "\u00B5s");
                }

                if (mBlueDisplayContext.mCurrentRotation == Surface.ROTATION_90) {
                    //noinspection SuspiciousNameCombination
                    ValueY = ValueX;
                    ValueX = -aEvent.values[1]; // -ValueY
                } else if (mBlueDisplayContext.mCurrentRotation == Surface.ROTATION_270) {
                    ValueY = -ValueX;
                    ValueX = aEvent.values[1]; // -ValueY
                } else if (mBlueDisplayContext.mCurrentRotation == Surface.ROTATION_180) {
                    ValueX = -ValueX;
                    ValueY = -ValueY;
                }
                mBlueDisplayContext.mSerialService.writeSensorEvent(SerialService.EVENT_FIRST_SENSOR_ACTION_CODE + tSensorType,
                        ValueX, ValueY, ValueZ);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        MyLog.i(LOG_TAG, "onAccuracyChanged accuracy=" + accuracy + " sensor=" + sensor);
    }

}