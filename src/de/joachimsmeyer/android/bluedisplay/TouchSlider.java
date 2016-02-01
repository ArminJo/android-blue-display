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
 * This class implements simple touch slider almost compatible with the one available as c++ code for arduino.
 * Usage of the java slider reduces data sent over bluetooth as well as arduino program size.
 */

package de.joachimsmeyer.android.bluedisplay;

import java.util.ArrayList;
import java.util.List;

import android.annotation.SuppressLint;
import android.graphics.Color;

public class TouchSlider {

	private static final String LOG_TAG = "SL";

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

	private class TextLayoutInfo {
		int mSize = mRPCView.mRequestedCanvasHeight / 20; // text size
		int mMargin = mRPCView.mRequestedCanvasHeight / 40; // distance from slider
		boolean mAbove = false;
		int mAlign = FLAG_SLIDER_CAPTION_ALIGN_MIDDLE;
		int mColor = Color.BLACK;
		int mBackgroundColor = Color.WHITE;
		int mPositionX; // resulting X positions computed from the above values and the text content
		int mPositionY; // resulting Y position
	}

	String mCaption;
	TextLayoutInfo mCaptionLayoutInfo;
	TextLayoutInfo mValueLayoutInfo;
	// private static final int SLIDER_VALUE_CAPTION_BELOW = 0x00;
	private static final int FLAG_SLIDER_CAPTION_ABOVE = 0x04;

	// private static final int SLIDER_VALUE_CAPTION_ALIGN_LEFT = 0x00;
	private static final int FLAG_SLIDER_CAPTION_ALIGN_RIGHT = 0x01;
	private static final int FLAG_SLIDER_CAPTION_ALIGN_MIDDLE = 0x02;
	private static final int FLAG_SLIDER_CAPTION_ALIGN_MASK = 0x03;

	int mOptions;
	// constants for options
	private static final int FLAG_SLIDER_SHOW_BORDER = 0x01;
	private static final int FLAG_SLIDER_SHOW_VALUE = 0x02;
	private static final int FLAG_SLIDER_IS_HORIZONTAL = 0x04;
	private static final int FLAG_SLIDER_IS_INVERSE = 0x08;
	private static final int FLAG_SLIDER_VALUE_BY_CALLBACK = 0x10; // if set value will only be set by callback handler
	private static final int FLAG_SLIDER_IS_ONLY_OUTPUT = 0x20;

	int mActualTouchValue;
	// Values are as delivered by client. Before internal use they must be scaled by mScaleFactorForValue
	// This value can be different from mActualTouchValue and is provided by callback handler
	int mActualValue;
	int mThresholdValue;
	float mScaleFactorForValue; // scale factor only for (normalized) value - default is 1

	int mListIndex; // index in sSliderList
	int mOnChangeHandlerCallbackAddress;
	boolean mIsActive;
	boolean mIsInitialized;

	static int sDefaultBorderColor = Color.BLUE;
	static int sDefaultBackgroundColor = Color.WHITE;
	static int sDefaultThresholdColor = Color.RED;

	private static final int SLIDER_LIST_INITIAL_SIZE = 10;
	private static List<TouchSlider> sSliderList = new ArrayList<TouchSlider>(SLIDER_LIST_INITIAL_SIZE);

	private static final int FUNCTION_SLIDER_CREATE = 0x50;
	private static final int FUNCTION_SLIDER_DRAW = 0x51;
	private static final int FUNCTION_SLIDER_SETTINGS = 0x52;
	private static final int FUNCTION_SLIDER_DRAW_BORDER = 0x53;

	// Flags for SLIDER_SETTINGS
	private static final int SUBFUNCTION_SLIDER_SET_COLOR_THRESHOLD = 0x00;
	private static final int SUBFUNCTION_SLIDER_SET_COLOR_BAR_BACKGROUND = 0x01;
	private static final int SUBFUNCTION_SLIDER_SET_COLOR_BAR = 0x02;
	private static final int SUBFUNCTION_SLIDER_SET_VALUE_AND_DRAW_BAR = 0x03;
	private static final int SUBFUNCTION_SLIDER_SET_POSITION = 0x04;
	private static final int SUBFUNCTION_SLIDER_SET_ACTIVE = 0x05;
	private static final int SUBFUNCTION_SLIDER_RESET_ACTIVE = 0x06;
	private static final int SUBFUNCTION_SLIDER_SET_VALUE_SCALE_FACTOR = 0x07;

	private static final int SUBFUNCTION_SLIDER_SET_CAPTION_PROPERTIES = 0x08;
	private static final int SUBFUNCTION_SLIDER_SET_PRINT_VALUE_PROPERTIES = 0x09;

	// static functions
	private static final int FUNCTION_SLIDER_ACTIVATE_ALL = 0x58;
	private static final int FUNCTION_SLIDER_DEACTIVATE_ALL = 0x59;

	// Function with variable data size
	private static final int FUNCTION_SLIDER_SET_CAPTION = 0x78;
	private static final int FUNCTION_SLIDER_PRINT_VALUE = 0x79;

	TouchSlider() {
		mIsInitialized = false;
		mTouchBorder = 4;
	}

	/**
	 * Static convenience method - reset all button lists and button flags
	 */
	static void resetSliders(final RPCView aRPCView) {
		sSliderList.clear();
	}

	void initSlider(final RPCView aRPCView, final int aPositionX, final int aPositionY, final int aBarWidth, final int aBarLength,
			final int aThresholdValue, final int aInitalValue, final int aSliderColor, final int aBarColor, final int aOptions,
			final int aOnChangeHandlerCallbackAddress) {

		mBarThresholdColor = sDefaultThresholdColor;

		mCaption = null;

		mRPCView = aRPCView;
		/**
		 * Copy parameter
		 */
		mPositionX = aPositionX;
		mPositionY = aPositionY;

		mBarColor = aBarColor;

		mOptions = aOptions;
		mBarWidth = aBarWidth;

		mShortBorderWidth = aBarWidth / 2;
		mBarLength = aBarLength;
		if (aBarLength < 0) {
			// inverse slider
			mBarLength = -aBarLength;
			mOptions |= FLAG_SLIDER_IS_INVERSE;
		}
		mActualValue = aInitalValue;
		if (aInitalValue < 0) {
			mActualValue = -aInitalValue;
		}
		mThresholdValue = aThresholdValue;
		if (aThresholdValue < 0) {
			mThresholdValue = -aThresholdValue;
		}
		mOnChangeHandlerCallbackAddress = aOnChangeHandlerCallbackAddress;

		int tShortBordersAddedWidth = mBarWidth;
		int tLongBordersAddedWidth = 2 * mBarWidth;
		if ((mOptions & FLAG_SLIDER_SHOW_BORDER) == 0) {
			tLongBordersAddedWidth = 0;
			tShortBordersAddedWidth = 0;
			/*
			 * If no border specified, then take unused slider color as bar background color.
			 */
			mBarBackgroundColor = aSliderColor;
			mSliderColor = sDefaultBorderColor; // is Color.BLUE
		} else {
			mSliderColor = aSliderColor;
			mBarBackgroundColor = sDefaultBackgroundColor;
		}

		/*
		 * compute lower right corner and validate
		 */
		if ((mOptions & FLAG_SLIDER_IS_HORIZONTAL) != 0) {
			// border at the long ends are half the size of the other borders
			mPositionXRight = mPositionX + mBarLength + tShortBordersAddedWidth - 1;
			if (mPositionXRight >= aRPCView.mRequestedCanvasWidth) {
				MyLog.e(LOG_TAG, "PositionXRight=" + mPositionXRight + " is greater than DisplayWidth="
						+ aRPCView.mRequestedCanvasWidth);
			}
			mPositionYBottom = mPositionY + (tLongBordersAddedWidth + mBarWidth) - 1;
			if (mPositionYBottom >= aRPCView.mRequestedCanvasHeight) {
				MyLog.e(LOG_TAG, "PositionYBottom=" + mPositionYBottom + " is greater than DisplayHeight="
						+ aRPCView.mRequestedCanvasHeight);
			}

		} else {
			mPositionXRight = mPositionX + (tLongBordersAddedWidth + mBarWidth) - 1;
			if (mPositionXRight >= aRPCView.mRequestedCanvasWidth) {
				MyLog.e(LOG_TAG, "PositionXRight=" + mPositionXRight + " is greater than DisplayWidth="
						+ aRPCView.mRequestedCanvasWidth);
			}
			mPositionYBottom = mPositionY + mBarLength + tShortBordersAddedWidth - 1;
			if (mPositionYBottom >= aRPCView.mRequestedCanvasHeight) {
				MyLog.e(LOG_TAG, "PositionYBottom=" + mPositionYBottom + " is greater than DisplayHeight="
						+ aRPCView.mRequestedCanvasHeight);
			}
		}

		/*
		 * Set initial layout info
		 */
		mValueLayoutInfo = new TextLayoutInfo();

		mCaptionLayoutInfo = new TextLayoutInfo();
		mCaptionLayoutInfo.mAbove = true;
		mCaptionLayoutInfo.mSize = mRPCView.mRequestedCanvasHeight / 12;
		mScaleFactorForValue = (float) 1.0;

		mIsActive = false;
		mIsInitialized = true;
	}

	void drawSlider() {
		mIsActive = true;
		if ((mOptions & FLAG_SLIDER_SHOW_BORDER) != 0) {
			drawBorder();
		}
		// Fill middle bar with initial value
		drawBar();
		if (mCaption != null && mCaptionLayoutInfo != null) {
			mRPCView.drawTextWithBackground(mCaptionLayoutInfo.mPositionX, mCaptionLayoutInfo.mPositionY, mCaption,
					mCaptionLayoutInfo.mSize, mCaptionLayoutInfo.mColor, mCaptionLayoutInfo.mBackgroundColor);
		}
		if ((mOptions & FLAG_SLIDER_SHOW_VALUE) != 0) {
			printValue();
		}
	}

	@SuppressLint("DefaultLocale")
	void printValue() {
		if (mValueLayoutInfo != null) {
			String tValueString = String.format("%3d", mActualValue);
			computeTextPositions(mValueLayoutInfo, tValueString);
			mRPCView.drawTextWithBackground(mValueLayoutInfo.mPositionX, mValueLayoutInfo.mPositionY, tValueString,
					mValueLayoutInfo.mSize, mValueLayoutInfo.mColor, mValueLayoutInfo.mBackgroundColor);
		}
	}

	void drawBorder() {
		if ((mOptions & FLAG_SLIDER_IS_HORIZONTAL) != 0) {
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
		int tActualValue = (int) (mActualValue * mScaleFactorForValue);
		if (tActualValue > mBarLength) {
			tActualValue = mBarLength;
		}
		if (tActualValue < 0) {
			tActualValue = 0;
		}

		int tShortBorderWidth = mShortBorderWidth;
		int tLongBorderWidth = mBarWidth;
		if ((mOptions & FLAG_SLIDER_SHOW_BORDER) == 0) {
			tLongBorderWidth = 0;
			tShortBorderWidth = 0;
		}

		int tBarBackgroundColor = mBarBackgroundColor;
		int tBarColor = mBarColor;

		if ((mOptions & FLAG_SLIDER_IS_INVERSE) != 0) {
			/*
			 * switch colors and invert value
			 */
			tBarBackgroundColor = tBarColor;
			if (tActualValue > (mThresholdValue * mScaleFactorForValue)) {
				tBarBackgroundColor = mBarThresholdColor;
			}
			tBarColor = mBarBackgroundColor;
			tActualValue = mBarLength - tActualValue;
		} else {
			if (tActualValue > (mThresholdValue * mScaleFactorForValue)) {
				tBarColor = mBarThresholdColor;
			}
		}

		// draw background bar
		if (tActualValue < mBarLength) {
			if ((mOptions & FLAG_SLIDER_IS_HORIZONTAL) != 0) {
				mRPCView.fillRectRel(mPositionX + tShortBorderWidth + tActualValue, mPositionY + tLongBorderWidth, mBarLength
						- tActualValue, mBarWidth, tBarBackgroundColor);
			} else {
				mRPCView.fillRectRel(mPositionX + tLongBorderWidth, mPositionY + tShortBorderWidth, mBarWidth, mBarLength
						- tActualValue, tBarBackgroundColor);
			}
		}

		// Draw value bar
		if (tActualValue > 0) {
			if ((mOptions & FLAG_SLIDER_IS_HORIZONTAL) != 0) {
				mRPCView.fillRectRel(mPositionX + tShortBorderWidth, mPositionY + tLongBorderWidth, tActualValue, mBarWidth,
						tBarColor);
			} else {
				mRPCView.fillRectRel(mPositionX + tLongBorderWidth, mPositionYBottom - tShortBorderWidth - tActualValue + 1,
						mBarWidth, tActualValue, tBarColor);
			}
		}
	}

	void computeTextPositions(TextLayoutInfo aTextLayoutInfo, String aText) {
		if (aText == null || aTextLayoutInfo == null) {
			return;
		}

		int tTextPixelLength = (int) ((0.6 * aTextLayoutInfo.mSize * aText.length()) + 0.5);

		int tSliderShortWidth = mBarWidth;
		int tSliderLongWidth = mBarLength;
		if ((mOptions & FLAG_SLIDER_SHOW_BORDER) != 0) {
			tSliderShortWidth = 3 * mBarWidth;
			tSliderLongWidth = (2 * mShortBorderWidth) + mBarLength;
		}

		if ((mOptions & FLAG_SLIDER_IS_HORIZONTAL) != 0) {
			// Horizontal slider
			if (aTextLayoutInfo.mAlign == FLAG_SLIDER_CAPTION_ALIGN_RIGHT) {
				aTextLayoutInfo.mPositionX = mPositionX + tSliderLongWidth - tTextPixelLength;
			} else if (aTextLayoutInfo.mAlign == FLAG_SLIDER_CAPTION_ALIGN_MIDDLE) {
				aTextLayoutInfo.mPositionX = mPositionX + ((tSliderLongWidth - tTextPixelLength) / 2);
			} else {
				aTextLayoutInfo.mPositionX = mPositionX;
			}
			if (aTextLayoutInfo.mAbove) {
				aTextLayoutInfo.mPositionY = mPositionY - aTextLayoutInfo.mMargin - aTextLayoutInfo.mSize
						+ (int) ((0.76 * aTextLayoutInfo.mSize) + 0.5);
			} else {
				aTextLayoutInfo.mPositionY = mPositionY + tSliderShortWidth + aTextLayoutInfo.mMargin
						+ (int) ((0.76 * aTextLayoutInfo.mSize) + 0.5);
			}

		} else {
			// Vertical slider
			if (aTextLayoutInfo.mAlign == FLAG_SLIDER_CAPTION_ALIGN_RIGHT) {
				aTextLayoutInfo.mPositionX = mPositionX + tSliderShortWidth - tTextPixelLength;
			} else if (aTextLayoutInfo.mAlign == FLAG_SLIDER_CAPTION_ALIGN_MIDDLE) {
				aTextLayoutInfo.mPositionX = mPositionX + ((tSliderShortWidth - tTextPixelLength) / 2);
			} else {
				aTextLayoutInfo.mPositionX = mPositionX;
			}
			if (aTextLayoutInfo.mAbove) {
				aTextLayoutInfo.mPositionY = mPositionY - aTextLayoutInfo.mMargin - aTextLayoutInfo.mSize
						+ (int) ((0.76 * aTextLayoutInfo.mSize)+ 0.5);
			} else {
				aTextLayoutInfo.mPositionY = mPositionY + tSliderLongWidth + aTextLayoutInfo.mMargin
						+ (int) ((0.76 * aTextLayoutInfo.mSize) + 0.5);
			}
		}
	}

	/*
	 * Check if touch event is in slider. If yes - set bar and value call callback function and return true. If no - return false
	 */
	boolean checkIfTouchInSlider(final int aTouchPositionX, final int aTouchPositionY, boolean aJustCheck) {
		if ((mOptions & FLAG_SLIDER_IS_ONLY_OUTPUT) != 0) {
			return false;
		}
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
		if ((mOptions & FLAG_SLIDER_SHOW_BORDER) != 0) {
			tShortBorderWidth = mShortBorderWidth;
		}
		/*
		 * Touch position is in slider (plus additional touch border) here
		 */
		if (aJustCheck) {
			return true;
		}

		// adjust value according to size of upper and lower border
		int tActualTouchValue;
		if ((mOptions & FLAG_SLIDER_IS_HORIZONTAL) != 0) {
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
		if ((mOptions & FLAG_SLIDER_IS_INVERSE) != 0) {
			tActualTouchValue = mBarLength - tActualTouchValue;
		}

		int tActualTouchValueInt = (int) (tActualTouchValue / mScaleFactorForValue);

		if (tActualTouchValueInt != mActualTouchValue) {
			mActualTouchValue = tActualTouchValueInt;
			if (mOnChangeHandlerCallbackAddress != 0) {
				// call change handler
				mRPCView.mBlueDisplayContext.mSerialService.writeGuiCallbackEvent(BluetoothSerialService.EVENT_SLIDER_CALLBACK,
						mListIndex, mOnChangeHandlerCallbackAddress, tActualTouchValueInt);
			}
			if ((mOptions & FLAG_SLIDER_VALUE_BY_CALLBACK) == 0) {
				// store value and redraw
				mActualValue = tActualTouchValueInt;
				drawBar();
				if ((mOptions & FLAG_SLIDER_SHOW_VALUE) != 0) {
					printValue();
				}
			}
		}
		return true;
	}

	/**
	 * 
	 * @param aTouchPositionX
	 * @param aTouchPositionY
	 * @param aJustCheck
	 *            - do not call callback function of slider
	 * @return number of slider if touched else -1
	 */
	static int checkAllSliders(int aTouchPositionX, int aTouchPositionY, boolean aJustCheck) {
		// walk through list of active elements
		for (TouchSlider tSlider : sSliderList) {
			if (tSlider.mIsActive && tSlider.checkIfTouchInSlider(aTouchPositionX, aTouchPositionY, aJustCheck)) {
				return tSlider.mListIndex;
			}
		}
		return -1;
	}

	static boolean checkIfTouchInSlider(final int aTouchPositionX, final int aTouchPositionY, final int aSliderNumber) {
		TouchSlider tSlider = sSliderList.get(aSliderNumber);
		if (tSlider.mIsActive) {
			return tSlider.checkIfTouchInSlider(aTouchPositionX, aTouchPositionY, false);
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
		if (aCommand != FUNCTION_SLIDER_ACTIVATE_ALL && aCommand != FUNCTION_SLIDER_DEACTIVATE_ALL) {
			/*
			 * We need a slider for the command
			 */
			if (aParamsLength <= 0) {
				MyLog.e(LOG_TAG,
						"aParamsLength is <=0 but Command=0x" + Integer.toHexString(aCommand) + " is not one of 0x"
								+ Integer.toHexString(FUNCTION_SLIDER_ACTIVATE_ALL) + " or 0x"
								+ Integer.toHexString(FUNCTION_SLIDER_DEACTIVATE_ALL));
				return;
			} else {
				tSliderNumber = aParameters[0];

				if (tSliderNumber >= 0 && tSliderNumber < sSliderList.size()) {
					tSlider = sSliderList.get(tSliderNumber);
					if (aCommand != FUNCTION_SLIDER_CREATE && (tSlider == null || !tSlider.mIsInitialized)) {
						MyLog.e(LOG_TAG, "Command=0x" + Integer.toHexString(aCommand) + " SliderNr=" + tSliderNumber
								+ " is null or not initialized.");
						return;
					}
				} else if (aCommand != FUNCTION_SLIDER_CREATE) {
					MyLog.e(LOG_TAG, "Command=0x" + Integer.toHexString(aCommand) + " SliderNr=" + tSliderNumber
							+ " not found. Only " + sSliderList.size() + " sliders created.");
					return;
				}
			}
		}

		try {
			switch (aCommand) {

			case FUNCTION_SLIDER_ACTIVATE_ALL:
				if (MyLog.isINFO()) {
					MyLog.i(LOG_TAG, "Activate all sliders");
				}
				activateAllSliders();
				break;

			case FUNCTION_SLIDER_DEACTIVATE_ALL:
				if (MyLog.isINFO()) {
					MyLog.i(LOG_TAG, "Deactivate all sliders");
				}
				deactivateAllSliders();
				break;

			case FUNCTION_SLIDER_DRAW:
				if (MyLog.isINFO()) {
					MyLog.i(LOG_TAG, "Draw slider. SliderNr=" + tSliderNumber);
				}
				tSlider.drawSlider();
				break;

			case FUNCTION_SLIDER_DRAW_BORDER:
				if (MyLog.isINFO()) {
					MyLog.i(LOG_TAG, "Draw border. SliderNr=" + tSliderNumber);
				}
				tSlider.drawBorder();
				break;

			case FUNCTION_SLIDER_SET_CAPTION:
				aRPCView.myConvertChars(aDataBytes, RPCView.sCharsArray, aDataLength);
				tSlider.mCaption = new String(RPCView.sCharsArray, 0, aDataLength);
				tSlider.computeTextPositions(tSlider.mCaptionLayoutInfo, tSlider.mCaption);

				if (MyLog.isINFO()) {
					MyLog.i(LOG_TAG, "Set caption=\"" + tSlider.mCaption + "\"" + " for SliderNr=" + tSliderNumber);
				}
				break;

			case FUNCTION_SLIDER_PRINT_VALUE:
				aRPCView.myConvertChars(aDataBytes, RPCView.sCharsArray, aDataLength);
				String tValue = new String(RPCView.sCharsArray, 0, aDataLength);

				if (tSlider.mValueLayoutInfo != null) {
					tSlider.computeTextPositions(tSlider.mValueLayoutInfo, tValue);
					tSlider.mRPCView.drawTextWithBackground(tSlider.mValueLayoutInfo.mPositionX,
							tSlider.mValueLayoutInfo.mPositionY, tValue, tSlider.mValueLayoutInfo.mSize,
							tSlider.mValueLayoutInfo.mColor, tSlider.mValueLayoutInfo.mBackgroundColor);
					if (MyLog.isINFO()) {
						MyLog.i(LOG_TAG, "Print value=\"" + tValue + "\"" + " for SliderNr=" + tSliderNumber);
					}
				} else {
					MyLog.e(LOG_TAG, "Print value=\"" + tValue + "\"" + " for SliderNr=" + tSliderNumber
							+ " failed. No print properties set");
				}
				break;

			case FUNCTION_SLIDER_SETTINGS:
				int tSubcommand = aParameters[1];
				switch (tSubcommand) {
				case SUBFUNCTION_SLIDER_SET_COLOR_THRESHOLD:
					tSlider.mBarThresholdColor = RPCView.shortToLongColor(aParameters[2]);
					if (MyLog.isINFO()) {
						MyLog.i(LOG_TAG, "Set threshold color= " + RPCView.shortToColorString(aParameters[2]) + " for SliderNr="
								+ tSliderNumber);
					}
					break;

				case SUBFUNCTION_SLIDER_SET_COLOR_BAR_BACKGROUND:
					tSlider.mBarBackgroundColor = RPCView.shortToLongColor(aParameters[2]);
					if (MyLog.isINFO()) {
						MyLog.i(LOG_TAG, "Set bar background color= " + RPCView.shortToColorString(aParameters[2])
								+ " for SliderNr=" + tSliderNumber);
					}
					break;

				case SUBFUNCTION_SLIDER_SET_COLOR_BAR:
					tSlider.mBarColor = RPCView.shortToLongColor(aParameters[2]);
					if (MyLog.isINFO()) {
						MyLog.i(LOG_TAG, "Set bar color= " + RPCView.shortToColorString(aParameters[2]) + " for SliderNr="
								+ tSliderNumber);
					}
					break;

				case SUBFUNCTION_SLIDER_SET_VALUE_AND_DRAW_BAR:
					// Log on Info level!
					if (MyLog.isDEBUG()) {
						MyLog.d(LOG_TAG, "Set value=" + aParameters[2] + " for SliderNr=" + tSliderNumber);
					}
					tSlider.mActualValue = aParameters[2];
					tSlider.drawBar();
					if ((tSlider.mOptions & FLAG_SLIDER_SHOW_VALUE) != 0) {
						tSlider.printValue();
					}
					break;

				case SUBFUNCTION_SLIDER_SET_POSITION:
					tSlider.mPositionX = aParameters[2];
					tSlider.mPositionY = aParameters[3];
					if (MyLog.isINFO()) {
						MyLog.i(LOG_TAG, "Set position=" + tSlider.mPositionX + " / " + tSlider.mPositionY + " for SliderNr="
								+ tSliderNumber);
					}
					break;

				case SUBFUNCTION_SLIDER_SET_ACTIVE:
					if (MyLog.isINFO()) {
						MyLog.i(LOG_TAG, "Set active=true for SliderNr=" + tSliderNumber);
					}
					tSlider.mIsActive = true;
					break;

				case SUBFUNCTION_SLIDER_RESET_ACTIVE:
					if (MyLog.isINFO()) {
						MyLog.i(LOG_TAG, "Set active=false for SliderNr=" + tSliderNumber);
					}
					tSlider.mIsActive = false;
					break;

				case SUBFUNCTION_SLIDER_SET_CAPTION_PROPERTIES:
				case SUBFUNCTION_SLIDER_SET_PRINT_VALUE_PROPERTIES:
					String tFunction;
					TextLayoutInfo tLayoutInfo = tSlider.new TextLayoutInfo();

					tLayoutInfo.mSize = aParameters[2];
					tLayoutInfo.mAlign = aParameters[3] & FLAG_SLIDER_CAPTION_ALIGN_MASK;
					tLayoutInfo.mMargin = aParameters[4];
					tLayoutInfo.mColor = RPCView.shortToLongColor(aParameters[5]);
					tLayoutInfo.mBackgroundColor = RPCView.shortToLongColor(aParameters[6]);
					String tAlign = "left";
					if (tLayoutInfo.mAlign == FLAG_SLIDER_CAPTION_ALIGN_RIGHT) {
						tAlign = "right";
					} else if (tLayoutInfo.mAlign == FLAG_SLIDER_CAPTION_ALIGN_MIDDLE) {
						tAlign = "middle";
					}

					String tAbove;
					if ((aParameters[3] & FLAG_SLIDER_CAPTION_ABOVE) != 0) {
						tLayoutInfo.mAbove = true;
						tAbove = "above";
					} else {
						tLayoutInfo.mAbove = false;
						tAbove = "below";
					}
					if (tSubcommand == SUBFUNCTION_SLIDER_SET_CAPTION_PROPERTIES) {
						tFunction = "caption";
						tSlider.mCaptionLayoutInfo = tLayoutInfo;
						tSlider.computeTextPositions(tLayoutInfo, tSlider.mCaption);
					} else {
						tFunction = "print value";
						tSlider.mValueLayoutInfo = tLayoutInfo;
					}
					if (MyLog.isINFO()) {
						MyLog.i(LOG_TAG,
								"Set " + tFunction + " properties size=" + tLayoutInfo.mSize + " position=" + tAbove + " align="
										+ tAlign + " margin=" + tLayoutInfo.mMargin + " color="
										+ RPCView.shortToColorString(aParameters[5]) + " background color="
										+ RPCView.shortToColorString(aParameters[6]) + " for SliderNr=" + tSliderNumber);
					}
					break;

				case SUBFUNCTION_SLIDER_SET_VALUE_SCALE_FACTOR:
					// convert 2 short parameters to one float
					int tFloat = aParameters[2] & 0xFFFF;
					tFloat = tFloat | ((aParameters[3] & 0xFFFF) << 16);
					float tNewScaleFactor = Float.intBitsToFloat(tFloat);
					if (MyLog.isINFO()) {
						MyLog.i(LOG_TAG, "Set value scale factor from " + tSlider.mScaleFactorForValue + " to " + tNewScaleFactor
								+ " for SliderNr=" + tSliderNumber);
					}
					tSlider.mScaleFactorForValue = tNewScaleFactor;
					break;
				}
				break;

			case FUNCTION_SLIDER_CREATE:
				int tOnChangeHandlerCallbackAddress = aParameters[10] & 0x0000FFFF;
				if (aParamsLength == 12) {
					// 32 bit callback address
					tOnChangeHandlerCallbackAddress = tOnChangeHandlerCallbackAddress | (aParameters[11] << 16);
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
						if (MyLog.isDEBUG()) {
							MyLog.d(LOG_TAG, "Slider with index " + tSliderNumber + " appended at end of list. List size now "
									+ sSliderList.size());
						}
					}
					tSlider.mListIndex = tSliderNumber;
				}

				if (MyLog.isINFO()) {
					MyLog.i(LOG_TAG,
							"Create slider. SliderNr=" + tSliderNumber + ", new TouchSlider(x=" + aParameters[1] + ", y="
									+ aParameters[2] + ", width=" + aParameters[3] + ", length=" + aParameters[4] + ", threshold="
									+ aParameters[5] + ", initial=" + aParameters[6] + ", color= "
									+ RPCView.shortToColorString(aParameters[7]) + ", bar color= "
									+ RPCView.shortToColorString(aParameters[8]) + ", options="
									+ Integer.toHexString(aParameters[9]) + ", callback=0x"
									+ Integer.toHexString(tOnChangeHandlerCallbackAddress) + ")");
				}

				tSlider.initSlider(aRPCView, aParameters[1], aParameters[2], aParameters[3], aParameters[4], aParameters[5],
						aParameters[6], RPCView.shortToLongColor(aParameters[7]), RPCView.shortToLongColor(aParameters[8]),
						aParameters[9], tOnChangeHandlerCallbackAddress);

				break;

			default:
				MyLog.e(LOG_TAG, "unknown command 0x" + Integer.toHexString(aCommand) + " received. paramsLength=" + aParamsLength
						+ " dataLenght=" + aDataLength);
				break;
			}
		} catch (Exception e) {
			MyLog.e(LOG_TAG, "Exception catched for command 0x" + Integer.toHexString(aCommand) + ". paramsLength=" + aParamsLength
					+ " dataLenght=" + aDataLength);
		}
	}
}