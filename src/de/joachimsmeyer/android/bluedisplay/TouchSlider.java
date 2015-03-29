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
 * This class implements simple touch slider compatible with the one available as c++ code for arduino.
 * Usage of the java slider reduces data sent over bluetooth as well as arduino program size.
 */

package de.joachimsmeyer.android.bluedisplay;

import java.util.ArrayList;
import java.util.List;

import android.graphics.Color;
import android.util.Log;

public class TouchSlider {

	private static final String LOG_TAG = "BlueTouchSlider";

	RPCView mRPCView;
	int mSliderColor;
	int mBarColor;
	int mBarBackgroundColor;
	int mBarThresholdColor;

	int mPositionX; // position of leftmost pixel of slider
	int mPositionXRight; // position of rightmost pixel of slider
	int mPositionY; // position of uppermost pixel of slider
	int mPositionYBottom; // position of lowest pixel of slider
	int mBarWidth; // width of bar (and main borders) in pixel
	int mShortBorderWidth; // used internally - half of mBarWidth
	int mBarLength; // size of slider bar in pixel = maximum slider value
	int mTouchBorder; // border in pixel, where touches outside slider are recognized - default is 4

	int mOptions;
	// constants for options
	private static final int TOUCHSLIDER_SHOW_BORDER = 0x01;
	private static final int TOUCHSLIDER_VALUE_BY_CALLBACK = 0x02; // if set value will be set by callback handler
	private static final int TOUCHSLIDER_IS_HORIZONTAL = 0x04;

	int mActualTouchValue;
	// This value can be different from mActualTouchValue and is provided by callback handler
	int mActualValue;

	int mThresholdValue;
	int mListIndex; // index in sButtonList
	int mOnChangeHandlerCallbackAddress;
	boolean mIsActive;
	boolean mIsInitialized;

	static int sDefaultBackgroundColor = Color.WHITE;
	static int sDefaultThresholdColor = Color.RED;

	private static final int SLIDER_LIST_INITIAL_SIZE = 10;
	private static List<TouchSlider> sSliderList = new ArrayList<TouchSlider>(SLIDER_LIST_INITIAL_SIZE);

	private static final int FUNCTION_TAG_SLIDER_CREATE = 0x50;
	private static final int FUNCTION_TAG_SLIDER_DRAW = 0x51;
	private static final int FUNCTION_TAG_SLIDER_SETTINGS = 0x52;
	private static final int FUNCTION_TAG_SLIDER_DRAW_BORDER = 0x53;

	// Flags for SLIDER_SETTINGS
	private static final int SLIDER_FLAG_SET_COLOR_THRESHOLD = 0x00;
	private static final int SLIDER_FLAG_SET_COLOR_BAR_BACKGROUND = 0x01;
	private static final int SLIDER_FLAG_SET_COLOR_BAR = 0x02;
	private static final int SLIDER_FLAG_SET_VALUE_AND_DRAW_BAR = 0x03;
	private static final int SLIDER_FLAG_SET_POSITION = 0x04;
	private static final int SLIDER_FLAG_SET_ACTIVE = 0x05;
	private static final int SLIDER_FLAG_RESET_ACTIVE = 0x06;

	// static functions
	private static final int FUNCTION_TAG_SLIDER_ACTIVATE_ALL = 0x58;
	private static final int FUNCTION_TAG_SLIDER_DEACTIVATE_ALL = 0x59;

	/*
	 * setup 10 empty sliders for direct addressing
	 */
	static {
		for (int i = 0; i < SLIDER_LIST_INITIAL_SIZE; i++) {
			TouchSlider tNewSlider = new TouchSlider();
			int tSliderNumber = sSliderList.size();
			sSliderList.add(tNewSlider);
			tNewSlider.mListIndex = tSliderNumber;
		}
	}

	TouchSlider() {
		mIsInitialized = false;
		mTouchBorder = 4;
	}

	void initSlider(final RPCView aRPCView, final int aPositionX, final int aPositionY, final int aBarWidth, final int aBarLength,
			final int aThresholdValue, final int aInitalValue, final int aSliderColor, final int aBarColor, final int aOptions,
			final int aOnChangeHandlerCallbackAddress) {

		mBarBackgroundColor = sDefaultBackgroundColor;
		mBarThresholdColor = sDefaultThresholdColor;

		mRPCView = aRPCView;
		/**
		 * Copy parameter
		 */
		mPositionX = aPositionX;
		mPositionY = aPositionY;

		mSliderColor = aSliderColor;
		mBarColor = aBarColor;

		mOptions = aOptions;
		mBarWidth = aBarWidth;
		mShortBorderWidth = aBarWidth / 2;
		mBarLength = aBarLength;
		mActualValue = aInitalValue;
		mThresholdValue = aThresholdValue;
		mOnChangeHandlerCallbackAddress = aOnChangeHandlerCallbackAddress;

		int tShortBordersAddedWidth = mBarWidth;
		int tLongBordersAddedWidth = 2 * mBarWidth;
		if ((mOptions & TOUCHSLIDER_SHOW_BORDER) == 0) {
			tLongBordersAddedWidth = 0;
			tShortBordersAddedWidth = 0;
		}
		/*
		 * compute lower right corner and validate
		 */
		if ((mOptions & TOUCHSLIDER_IS_HORIZONTAL) != 0) {
			// border at the long ends are half the size of the other borders
			mPositionXRight = mPositionX + mBarLength + tShortBordersAddedWidth - 1;
			if (mPositionXRight >= aRPCView.mRequestedCanvasWidth) {
				Log.e(LOG_TAG, "PositionXRight=" + mPositionXRight + " is greater than DisplayWidth="
						+ aRPCView.mRequestedCanvasWidth);
			}
			mPositionYBottom = mPositionY + (tLongBordersAddedWidth + mBarWidth) - 1;
			if (mPositionYBottom >= aRPCView.mRequestedCanvasHeight) {
				Log.e(LOG_TAG, "PositionYBottom=" + mPositionYBottom + " is greater than DisplayHeight="
						+ aRPCView.mRequestedCanvasHeight);
			}

		} else {
			mPositionXRight = mPositionX + (tLongBordersAddedWidth + mBarWidth) - 1;
			if (mPositionXRight >= aRPCView.mRequestedCanvasWidth) {
				Log.e(LOG_TAG, "PositionXRight=" + mPositionXRight + " is greater than DisplayWidth="
						+ aRPCView.mRequestedCanvasWidth);
			}
			mPositionYBottom = mPositionY + mBarLength + tShortBordersAddedWidth - 1;
			if (mPositionYBottom >= aRPCView.mRequestedCanvasHeight) {
				Log.e(LOG_TAG, "PositionYBottom=" + mPositionYBottom + " is greater than DisplayHeight="
						+ aRPCView.mRequestedCanvasHeight);
			}
		}
		mIsActive = false;
		mIsInitialized = true;
	}

	void drawSlider() {
		mIsActive = true;
		if ((mOptions & TOUCHSLIDER_SHOW_BORDER) != 0) {
			drawBorder();
		}
		// Fill middle bar with initial value
		drawBar();
	}

	void drawBorder() {
		if ((mOptions & TOUCHSLIDER_IS_HORIZONTAL) != 0) {
			// Create value bar upper border
			mRPCView.fillRectRel(mPositionX, mPositionY, mBarLength + mBarWidth, mBarWidth, mSliderColor);
			// Create value bar lower border
			mRPCView.fillRectRel(mPositionX, mPositionY + (2 * mBarWidth), mBarLength + mBarWidth, mBarWidth, mSliderColor);

			// Create left border
			mRPCView.fillRectRel(mPositionX, mPositionY + mBarWidth, mShortBorderWidth, mBarWidth, mSliderColor);
			// Create right border
			mRPCView.fillRectRel(mPositionXRight - mShortBorderWidth + 1, mPositionY + mBarWidth, mShortBorderWidth, mBarWidth,
					mSliderColor);
		} else {
			// Create left border
			mRPCView.fillRectRel(mPositionX, mPositionY, mBarWidth, mBarLength + mBarWidth, mSliderColor);
			// Create right border
			mRPCView.fillRectRel(mPositionX + (2 * mBarWidth), mPositionY, mBarWidth, mBarLength + mBarWidth, mSliderColor);

			// Create value bar upper border
			mRPCView.fillRectRel(mPositionX + mBarWidth, mPositionY, mBarWidth, mShortBorderWidth, mSliderColor);
			// Create value bar lower border
			mRPCView.fillRectRel(mPositionX + mBarWidth, mPositionYBottom - mShortBorderWidth + 1, mBarWidth, mShortBorderWidth,
					mSliderColor);
		}
	}

	/*
	 * (re)draws the middle bar according to actual value
	 */
	void drawBar() {
		int tActualValue = mActualValue;
		if (tActualValue > mBarLength) {
			tActualValue = mBarLength;
		}
		if (tActualValue < 0) {
			tActualValue = 0;
		}

		int tShortBorderWidth = mShortBorderWidth;
		int tLongBorderWidth = mBarWidth;
		if ((mOptions & TOUCHSLIDER_SHOW_BORDER) == 0) {
			tLongBorderWidth = 0;
			tShortBorderWidth = 0;
		}

		// draw background bar
		if (tActualValue < mBarLength) {
			if ((mOptions & TOUCHSLIDER_IS_HORIZONTAL) != 0) {
				mRPCView.fillRectRel(mPositionX + tShortBorderWidth + tActualValue, mPositionY + tLongBorderWidth, mBarLength
						- tActualValue, mBarWidth, mBarBackgroundColor);
			} else {
				mRPCView.fillRectRel(mPositionX + tLongBorderWidth, mPositionY + tShortBorderWidth, mBarWidth, mBarLength
						- tActualValue, mBarBackgroundColor);
			}
		}

		// Draw value bar
		if (tActualValue > 0) {
			int tColor = mBarColor;
			if (tActualValue > mThresholdValue) {
				tColor = mBarThresholdColor;
			}
			if ((mOptions & TOUCHSLIDER_IS_HORIZONTAL) != 0) {
				mRPCView.fillRectRel(mPositionX + tShortBorderWidth, mPositionY + tLongBorderWidth, tActualValue, mBarWidth, tColor);
			} else {
				mRPCView.fillRectRel(mPositionX + tLongBorderWidth, mPositionYBottom - tShortBorderWidth - tActualValue + 1,
						mBarWidth, tActualValue, tColor);
			}
		}
	}

	/*
	 * Check if touch event is in slider. If yes - set bar and value call callback function and return true. If no - return false
	 */
	boolean checkIfTouchInSlider(final int aTouchPositionX, final int aTouchPositionY) {
		int tPositionBorderX = mPositionX - mTouchBorder;
		if (tPositionBorderX < 0) {
			tPositionBorderX = 0;
		}
		int tPositionBorderY = mPositionY - mTouchBorder;
		if (tPositionBorderY < 0) {
			tPositionBorderY = 0;
		}
		if (!mIsActive || aTouchPositionX < tPositionBorderX || aTouchPositionX > mPositionXRight + mTouchBorder
				|| aTouchPositionY < tPositionBorderY || aTouchPositionY > mPositionYBottom + mTouchBorder) {
			return false;
		}
		int tShortBorderWidth = 0;
		if ((mOptions & TOUCHSLIDER_SHOW_BORDER) != 0) {
			tShortBorderWidth = mShortBorderWidth;
		}
		/*
		 * Touch position is in slider (plus additional touch border) here
		 */
		// adjust value according to size of upper and lower border
		int tActualTouchValue;
		if ((mOptions & TOUCHSLIDER_IS_HORIZONTAL) != 0) {
			if (aTouchPositionX < (mPositionX + tShortBorderWidth)) {
				tActualTouchValue = 0;
			} else if (aTouchPositionX > (mPositionXRight - tShortBorderWidth)) {
				tActualTouchValue = mBarLength;
			} else {
				tActualTouchValue = aTouchPositionX - mPositionX - tShortBorderWidth + 1;
			}
		} else {
			if (aTouchPositionY > (mPositionYBottom - tShortBorderWidth)) {
				tActualTouchValue = 0;
			} else if (aTouchPositionY < (mPositionY + tShortBorderWidth)) {
				tActualTouchValue = mBarLength;
			} else {
				tActualTouchValue = mPositionYBottom - tShortBorderWidth - aTouchPositionY + 1;
			}
		}

		if (tActualTouchValue != mActualTouchValue) {
			mActualTouchValue = tActualTouchValue;
			if (mOnChangeHandlerCallbackAddress != 0) {
				// call change handler
				mRPCView.mBlueDisplayContext.mSerialService.writeGuiCallbackEvent(
						BluetoothSerialService.EVENT_TAG_SLIDER_CALLBACK_ACTION, mListIndex, mOnChangeHandlerCallbackAddress,
						tActualTouchValue);
			}
			if ((mOptions & TOUCHSLIDER_VALUE_BY_CALLBACK) == 0) {
				// store value and redraw
				mActualValue = tActualTouchValue;
				drawBar();
			}
		}
		return true;
	}

	/**
	 * 
	 * @param aTouchPositionX
	 * @param aTouchPositionY
	 * @param doCallback
	 * @return true if a match was found
	 */
	static boolean checkAllSliders(int aTouchPositionX, int aTouchPositionY) {
		// walk through list of active elements
		for (TouchSlider tSlider : sSliderList) {
			if (tSlider.mIsActive && tSlider.checkIfTouchInSlider(aTouchPositionX, aTouchPositionY)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Static convenience method - activate all buttons (e.g. before switching screen)
	 */
	static void activateAllSliders() {
		for (TouchSlider tSlider : sSliderList) {
			if (tSlider != null) {
				tSlider.mIsActive = true;
			}
		}
	}

	/**
	 * Static convenience method - deactivate all buttons (e.g. before switching screen)
	 */
	static void deactivateAllSliders() {
		// check needed, because method is called also by setFlags()
		if (sSliderList != null && sSliderList.size() > 0) {
			for (TouchSlider tSlider : sSliderList) {
				if (tSlider != null) {
					tSlider.mIsActive = false;
				}
			}
		}
	}

	public static void interpretCommand(final RPCView aRPCView, int aCommand, int[] aParameters, int aParamsLength,
			byte[] aDataBytes, int[] aDataInts, int aDataLength) {
		int tSliderNumber = -1;
		TouchSlider tSlider = null;

		/*
		 * Plausi
		 */
		if (aCommand != FUNCTION_TAG_SLIDER_ACTIVATE_ALL && aCommand != FUNCTION_TAG_SLIDER_DEACTIVATE_ALL) {
			/*
			 * We need a slider for the command
			 */
			if (aParamsLength <= 0) {
				Log.e(LOG_TAG, "aParamsLength is <=0 but Command=0x" + Integer.toHexString(aCommand) + " is not one of "
						+ FUNCTION_TAG_SLIDER_ACTIVATE_ALL + " or " + FUNCTION_TAG_SLIDER_DEACTIVATE_ALL);
				return;
			} else {
				tSliderNumber = aParameters[0];

				if (tSliderNumber >= 0 && tSliderNumber < sSliderList.size()) {
					tSlider = sSliderList.get(tSliderNumber);
					if (aCommand != FUNCTION_TAG_SLIDER_CREATE && (tSlider == null || !tSlider.mIsInitialized)) {
						Log.e(LOG_TAG, "Command=0x" + Integer.toHexString(aCommand) + " SliderNr=" + tSliderNumber
								+ " is null or not initialized.");
						return;
					}
				} else if (aCommand != FUNCTION_TAG_SLIDER_CREATE) {
					Log.e(LOG_TAG, "Command=0x" + Integer.toHexString(aCommand) + " SliderNr=" + tSliderNumber
							+ " not found. Only " + sSliderList.size() + " sliders created.");
					return;
				}
			}
		}

		try {
			switch (aCommand) {

			case FUNCTION_TAG_SLIDER_ACTIVATE_ALL:
				if (BlueDisplay.isINFO()) {
					Log.i(LOG_TAG, "Activate all sliders");
				}
				activateAllSliders();
				break;

			case FUNCTION_TAG_SLIDER_DEACTIVATE_ALL:
				if (BlueDisplay.isINFO()) {
					Log.i(LOG_TAG, "Deactivate all sliders");
				}
				deactivateAllSliders();
				break;

			case FUNCTION_TAG_SLIDER_DRAW:
				if (BlueDisplay.isINFO()) {
					Log.i(LOG_TAG, "Draw slider. SliderNr=" + tSliderNumber);
				}
				tSlider.drawSlider();
				break;

			case FUNCTION_TAG_SLIDER_DRAW_BORDER:
				if (BlueDisplay.isINFO()) {
					Log.i(LOG_TAG, "Draw border. SliderNr=" + tSliderNumber);
				}
				tSlider.drawBorder();
				break;

			case FUNCTION_TAG_SLIDER_SETTINGS:
				int tSubcommand = aParameters[1];
				switch (tSubcommand) {
				case SLIDER_FLAG_SET_COLOR_THRESHOLD:
					tSlider.mBarThresholdColor = RPCView.shortToLongColor(aParameters[2]);
					if (BlueDisplay.isINFO()) {
						Log.i(LOG_TAG, "Set threshold color= " + RPCView.shortToColorString(tSlider.mBarThresholdColor)
								+ " for SliderNr=" + tSliderNumber);
					}
					break;
				case SLIDER_FLAG_SET_COLOR_BAR_BACKGROUND:
					tSlider.mBarBackgroundColor = RPCView.shortToLongColor(aParameters[2]);
					if (BlueDisplay.isINFO()) {
						Log.i(LOG_TAG, "Set bar background color= " + RPCView.shortToColorString(tSlider.mBarBackgroundColor)
								+ " for SliderNr=" + tSliderNumber);
					}
					break;
				case SLIDER_FLAG_SET_COLOR_BAR:
					tSlider.mBarColor = RPCView.shortToLongColor(aParameters[2]);
					if (BlueDisplay.isINFO()) {
						Log.i(LOG_TAG, "Set bar color= " + RPCView.shortToColorString(tSlider.mBarColor) + " for SliderNr="
								+ tSliderNumber);
					}
					break;
				case SLIDER_FLAG_SET_VALUE_AND_DRAW_BAR:
					// Log on Info level!
					if (BlueDisplay.isDEBUG()) {
						Log.d(LOG_TAG, "Set value=" + aParameters[2] + " for SliderNr=" + tSliderNumber);
					}
					tSlider.mActualValue = aParameters[2];
					tSlider.drawBar();
					break;
				case SLIDER_FLAG_SET_POSITION:
					if (BlueDisplay.isINFO()) {
						Log.i(LOG_TAG, "Set position=" + aParameters[2] + " / " + aParameters[3] + " for SliderNr=" + tSliderNumber);
					}
					tSlider.mPositionX = aParameters[2];
					tSlider.mPositionY = aParameters[3];
					break;

				case SLIDER_FLAG_SET_ACTIVE:
					if (BlueDisplay.isINFO()) {
						Log.i(LOG_TAG, "Set active=true for SliderNr=" + tSliderNumber);
					}
					tSlider.mIsActive = true;
					break;

				case SLIDER_FLAG_RESET_ACTIVE:
					if (BlueDisplay.isINFO()) {
						Log.i(LOG_TAG, "Set active=false for SliderNr=" + tSliderNumber);
					}
					tSlider.mIsActive = false;
					break;

				}
				break;

			case FUNCTION_TAG_SLIDER_CREATE:
				int tOnChangeHandlerCallbackAddress = aParameters[10];
				if (aParamsLength == 12) {
					// 32 bit callback address
					tOnChangeHandlerCallbackAddress = tOnChangeHandlerCallbackAddress | (aParameters[11] << 16);
				}
				if (BlueDisplay.isINFO()) {
					Log.i(LOG_TAG,
							"Create slider. SliderNr=" + tSliderNumber + ", new TouchSlider(" + aParameters[1] + ", "
									+ aParameters[2] + ", " + aParameters[3] + ", " + aParameters[4] + ", " + aParameters[5] + ", "
									+ aParameters[6] + ", color= " + RPCView.shortToColorString(aParameters[7]) + ", bar color= "
									+ RPCView.shortToColorString(aParameters[8]) + ", " + Integer.toHexString(aParameters[9])
									+ ", 0x" + Integer.toHexString(tOnChangeHandlerCallbackAddress) + ")");
				}
				if (tSlider == null) {
					/*
					 * create new slider
					 */
					tSlider = new TouchSlider();
					if (tSliderNumber < sSliderList.size()) {
						sSliderList.set(tSliderNumber, tSlider);
					} else {
						tSliderNumber = sSliderList.size();
						sSliderList.add(tSlider);
						Log.w(LOG_TAG, "Slider with index " + tSliderNumber + " appended at end of list. List size now "
								+ sSliderList.size());
					}
					tSlider.mListIndex = tSliderNumber;
				} else {
					tSlider.initSlider(aRPCView, aParameters[1], aParameters[2], aParameters[3], aParameters[4], aParameters[5],
							aParameters[6], RPCView.shortToLongColor(aParameters[7]), RPCView.shortToLongColor(aParameters[8]),
							aParameters[9], tOnChangeHandlerCallbackAddress);
				}
				break;

			default:
				Log.e(LOG_TAG, "unknown command 0x" + Integer.toHexString(aCommand) + " received. paramsLength=" + aParamsLength
						+ " dataLenght=" + aDataLength);
				break;
			}
		} catch (Exception e) {
			Log.e(LOG_TAG, "Exception catched for command 0x" + Integer.toHexString(aCommand) + ". paramsLength=" + aParamsLength
					+ " dataLenght=" + aDataLength);
		}
	}
}