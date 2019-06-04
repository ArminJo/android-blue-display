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

	boolean isAnySensorEnabled = false;
	static List<SensorInfo> sAvailableSensorList = new ArrayList<SensorInfo>(32); // have seen more than 18 sensors in a device

	class SensorInfo {
		Sensor mSensor;
		int mType;
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

		// Avoid noise (event value is solely switching between 2 values) -> skip value if it is equal last or second last value
		boolean simpleFilter(SensorEvent aEvent) {
			if ((aEvent.values[0] == mLastValueX || aEvent.values[0] == mSecondLastValueX)
					&& (aEvent.values[1] == mLastValueY || aEvent.values[1] == mSecondLastValueY)
					&& (aEvent.values[2] == mLastValueZ || aEvent.values[2] == mSecondLastValueZ)) {
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

	private BlueDisplay mBlueDisplayContext;

	SensorManager mSensorManager;

	public OrientationEventListener mOrientationEventListener;
	boolean isOrientationEventListenerEnabled = false;

	public Sensors(BlueDisplay aContext, SensorManager aSensorManager) {
		mBlueDisplayContext = aContext;
		mSensorManager = aSensorManager;
		getAllAvailableSensors();

		/*
		 * To detect direct switching from 0 to 180 and from 90 to 270 degrees and vice versa which does not call
		 * onConfigurationChanged() Used by onSensorChanged, to adjust X + Y values relative to rotation of canvas
		 */
		mOrientationEventListener = new OrientationEventListener(mBlueDisplayContext, SensorManager.SENSOR_DELAY_NORMAL) {
			@Override
			public void onOrientationChanged(int mAngle) {
				if (mAngle > 0) {
					/**
					 * Process only, if orientation can change <br>
					 * angle 0 is Surface.ROTATION_0 <br>
					 * angle 90 is Surface.ROTATION_270<br>
					 * angle 180 is Surface.ROTATION_180<br>
					 * angle 270 is Surface.ROTATION_90
					 */
					if (mBlueDisplayContext.mRequestedScreenOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
						if ((315 < mAngle || mAngle < 45 && mBlueDisplayContext.mActualRotation != Surface.ROTATION_0)
								|| (45 < mAngle && mAngle < 135 && mBlueDisplayContext.mActualRotation != Surface.ROTATION_270)
								|| (135 < mAngle && mAngle < 225 && mBlueDisplayContext.mActualRotation != Surface.ROTATION_180)
								|| (225 < mAngle && mAngle < 315 && mBlueDisplayContext.mActualRotation != Surface.ROTATION_90)) {
							if (MyLog.isDEBUG()) {
								MyLog.d(LOG_TAG, "Trigger reading of rotation. Angle=" + mAngle);
							}
							if (mBlueDisplayContext.mActualRotation != mBlueDisplayContext.getWindowManager().getDefaultDisplay()
									.getRotation()) {
								mBlueDisplayContext
										.setActualScreenOrientationVariables(mBlueDisplayContext.mActualScreenOrientation);
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

	boolean isSensorEnabledAndEventValuesNotInHistory(SensorEvent aEvent) {
		for (SensorInfo tSensorInfo : sAvailableSensorList) {
			Sensor tSensor = tSensorInfo.mSensor;
			if (tSensor.getType() == tSensor.getType() && tSensorInfo.isActive) {
				// Sensor found and active, check for simple noise cancellation
				if (tSensorInfo.mFilterFlag == FLAG_SENSOR_NO_FILTER || tSensorInfo.simpleFilter(aEvent)) {
					return true;
				}
			}
		}
		return false;
	}

	/*
	 * Activate or deactivate sensor To be called from interpret command
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

	@Override
	public void onSensorChanged(SensorEvent aEvent) {
		int tSensorType = aEvent.sensor.getType();
		if (isSensorEnabledAndEventValuesNotInHistory(aEvent)) {
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
					// timestamp in nanoseconds
					Log.v(LOG_TAG, "Values=" + ValueX + " " + ValueY + " " + ValueZ + " rotation="
							+ mBlueDisplayContext.mActualRotation + " TimeStamp=" + aEvent.timestamp / 1000 + "µs");
				}

				if (mBlueDisplayContext.mActualRotation == Surface.ROTATION_90) {
					ValueY = ValueX;
					ValueX = -aEvent.values[1]; // -ValueY
				} else if (mBlueDisplayContext.mActualRotation == Surface.ROTATION_270) {
					ValueY = -ValueX;
					ValueX = aEvent.values[1]; // -ValueY
				} else if (mBlueDisplayContext.mActualRotation == Surface.ROTATION_180) {
					ValueX = -ValueX;
					ValueY = -ValueY;
				}
				mBlueDisplayContext.mSerialService.writeSensorEvent(SerialService.EVENT_FIRST_SENSOR_ACTION_CODE
						+ tSensorType, ValueX, ValueY, ValueZ);
			}
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		MyLog.i(LOG_TAG, "onAccuracyChanged accuracy=" + accuracy + " sensor=" + sensor);
	}

}