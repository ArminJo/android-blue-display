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
 * This class implements simple touch buttons compatible with the one available as c++ code for arduino.
 * Usage of the java buttons reduces data sent over bluetooth as well as arduino program size.
 */

package de.joachimsmeyer.android.bluedisplay;

import java.util.ArrayList;
import java.util.List;

import android.graphics.Color;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.util.Log;

public class TouchButton {

	// Logging
	private static final String LOG_TAG = "BlueTouchButton";

	RPCView mRPCView;
	int mButtonColor;
	int mCaptionColor;
	int mPositionX;
	int mPositionY;
	int mCaptionPositionX;
	int mCaptionPositionY;
	int mWidth;
	int mHeight;
	int mCaptionSize;
	String mCaption;
	int mValue;
	int mListIndex; // index in sButtonList
	int mCallbackAddress;
	boolean mDoBeep;
	boolean mIsActive;
	boolean mIsInitialized;
	static int sTouchBeepIndex = ToneGenerator.TONE_CDMA_KEYPAD_VOLUME_KEY_LITE;
	static ToneGenerator mToneGenerator;
	static int mActualToneVolume;

	static int sDefaultButtonColor = Color.RED;
	static int sDefaultCaptionColor = Color.BLACK;

	private static final int BUTTON_INITIAL_LIST_SIZE = 40;

	private static List<TouchButton> sButtonList = new ArrayList<TouchButton>(BUTTON_INITIAL_LIST_SIZE);

	private static final int FUNCTION_TAG_BUTTON_DRAW = 0x40;
	private static final int FUNCTION_TAG_BUTTON_DRAW_CAPTION = 0x41;
	private static final int FUNCTION_TAG_BUTTON_SETTINGS = 0x42;
	private static final int FUNCTION_TAG_BUTTON_SET_COLOR_AND_VALUE_AND_DRAW = 0x43;

	// static functions
	private static final int FUNCTION_TAG_BUTTON_ACTIVATE_ALL = 0x48;
	private static final int FUNCTION_TAG_BUTTON_DEACTIVATE_ALL = 0x49;
	private static final int FUNCTION_TAG_BUTTON_GLOBAL_SETTINGS = 0x4A;

	// Function with variable data size
	private static final int FUNCTION_TAG_BUTTON_CREATE = 0x70;
	private static final int FUNCTION_TAG_BUTTON_SET_CAPTION = 0x72;
	private static final int FUNCTION_TAG_BUTTON_SET_CAPTION_AND_DRAW_BUTTON = 0x73;

	// Flags for BUTTON_SETTINGS
	private static final int BUTTON_FLAG_SET_COLOR_BUTTON = 0x00;
	private static final int BUTTON_FLAG_SET_COLOR_CAPTION = 0x01;
	private static final int BUTTON_FLAG_SET_VALUE = 0x02;
	private static final int BUTTON_FLAG_SET_POSITION = 0x04;
	private static final int BUTTON_FLAG_SET_ACTIVE = 0x05;
	private static final int BUTTON_FLAG_RESET_ACTIVE = 0x06;

	// Flags for BUTTON_GLOBAL_SETTINGS
	private static final int USE_UP_EVENTS_FOR_BUTTONS = 0x01;
	private static final int BUTTONS_SET_BEEP_TONE = 0x02;

	// Flags for local settings
	private static final int BUTTON_FLAG_DO_BEEP_ON_TOUCH = 0x01;

	int mFlags; // Flag for: Autorepeat type, allocated, only caption

	/*
	 * setup 40 empty buttons for direct addressing
	 */
	static {
		for (int i = 0; i < BUTTON_INITIAL_LIST_SIZE; i++) {
			TouchButton tNewButton = new TouchButton();
			int tButtonNumber = sButtonList.size();
			sButtonList.add(tNewButton);
			tNewButton.mListIndex = tButtonNumber;
		}
	}

	TouchButton() {
		// empty button
		mIsInitialized = false;
	}

	/**
	 * Static convenience method - deactivate all buttons (e.g. before switching screen)
	 */
	static void resetButtons(final RPCView aRPCView) {
		deactivateAllButtons();
		aRPCView.mUseUpEventForButtons = false;
		sTouchBeepIndex = ToneGenerator.TONE_CDMA_KEYPAD_VOLUME_KEY_LITE;
	}

	/**
	 * Static convenience method - deactivate all buttons (e.g. before switching screen)
	 */
	static void resetButtons() {
		// check needed, because method is called also by setFlags()
		if (sButtonList != null && sButtonList.size() > 0) {
			for (TouchButton tButton : sButtonList) {
				if (tButton != null) {
					tButton.mIsActive = false;
				}
			}
		}
	}

	void initButton(final RPCView aRPCView, final int aPositionX, final int aPositionY, final int aWidthX, final int aHeightY,
			final int aButtonColor, final String aCaption, final int aCaptionSize, final int aValue, final int aCallbackAddress) {
		mRPCView = aRPCView;
		// to have caption for error messages
		mCaption = aCaption;

		mWidth = aWidthX;
		mHeight = aHeightY;
		// Plausi is also done here
		setPosition(aPositionX, aPositionY);

		mButtonColor = aButtonColor;
		if (aButtonColor == 0) {
			mButtonColor = sDefaultButtonColor;
		}

		mCaptionColor = sDefaultCaptionColor;

		mCaptionSize = aCaptionSize & 0xFF;
		if ((aCaptionSize >> 8 & BUTTON_FLAG_DO_BEEP_ON_TOUCH) != 0) {
			if (mToneGenerator == null) {
				mToneGenerator = new ToneGenerator(AudioManager.STREAM_SYSTEM, ToneGenerator.MAX_VOLUME);
			}
			mDoBeep = true;
		} else {
			mDoBeep = false;
		}

		positionCaption(aCaption);

		mValue = aValue;
		mCallbackAddress = aCallbackAddress;

		mIsActive = false;
		mIsInitialized = true;

	}

	void drawButton() {
		// Draw rect
		mRPCView.fillRectRel(mPositionX, mPositionY, mWidth, mHeight, mButtonColor);
		drawCaption();
	}

	private void positionCaption(final String aCaption) {
		if (mCaptionSize > 0) { // don't render anything if caption size == 0
			if (mCaption.length() != 0) {
				// try to position the string in the middle of the box
				int tLength = (int) (0.6 * mCaptionSize * mCaption.length());
				if (tLength >= mWidth) {
					// String too long here
					mCaptionPositionX = mPositionX;
					Log.w(LOG_TAG, "caption\"" + mCaption + "\" to long");
				} else {
					mCaptionPositionX = mPositionX + ((mWidth - tLength) / 2);
				}

				if (mCaptionSize >= mHeight) {
					// Font height to big
					Log.w(LOG_TAG, "caption\"" + mCaption + "\" to high");
				}
				mCaptionPositionY = (int) (mPositionY + ((mHeight + 0.525 * mCaptionSize) / 2));
			}
		}
	}

	/*
	 * Set caption
	 */
	void setCaption(String aCaption) {
		mCaption = aCaption;
		positionCaption(aCaption);
	}

	/**
	 * draws the caption of a button
	 */
	void drawCaption() {
		mIsActive = true;
		if (mCaptionSize > 0) { // don't render anything if caption size == 0
			mRPCView.drawTextWithBackground(mCaptionPositionX, mCaptionPositionY, mCaption, mCaptionSize, mCaptionColor,
					mButtonColor);
		}
	}

	void setPosition(int aPositionX, int aPositionY) {
		mPositionX = aPositionX;
		mPositionY = aPositionY;

		// check values
		if (aPositionX + mWidth > mRPCView.mRequestedCanvasWidth) {
			Log.e(LOG_TAG, mCaption + " Button x-position/width " + aPositionX + "/" + mWidth + " wrong. Set width to:"
					+ (mRPCView.mRequestedCanvasWidth - aPositionX));
			mWidth = mRPCView.mRequestedCanvasWidth - aPositionX;
		}
		if (aPositionY + mHeight > mRPCView.mRequestedCanvasHeight) {
			Log.e(LOG_TAG, mCaption + " Button y-position/height " + aPositionY + "/" + mHeight + " wrong. Set height to:"
					+ (mRPCView.mRequestedCanvasHeight - aPositionY));
			mHeight = mRPCView.mRequestedCanvasHeight - aPositionY;

		}
	}

	/**
	 * Check if touch event is in button area if yes - call callback function and return true if no - return false
	 */
	boolean checkIfTouchInButton(int aTouchPositionX, int aTouchPositionY) {
		if (mIsActive && mCallbackAddress != 0 && checkButtonInArea(aTouchPositionX, aTouchPositionY)) {
			/*
			 * Touch position is in button - call callback function
			 */
			if (mDoBeep) {
				mToneGenerator.startTone(sTouchBeepIndex);
			}
			mRPCView.mSerialService.writeEvent(BluetoothSerialService.EVENT_TAG_BUTTON_CALLBACK_ACTION, mListIndex,
					mCallbackAddress, mValue);
			return true;
		}
		return false;
	}

	/**
	 * 
	 * @param aTouchPositionX
	 * @param aTouchPositionY
	 * @param doCallback
	 * @return true if a match was found
	 */
	static boolean checkAllButtons(int aTouchPositionX, int aTouchPositionY) {
		// walk through list of active elements
		for (TouchButton tButton : sButtonList) {
			if (tButton.mIsActive && tButton.checkIfTouchInButton(aTouchPositionX, aTouchPositionY)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Static convenience method - activate all buttons (e.g. before switching screen)
	 */
	static void activateAllButtons() {
		for (TouchButton tButton : sButtonList) {
			if (tButton != null) {
				tButton.mIsActive = true;
			}
		}
	}

	/**
	 * Static convenience method - deactivate all buttons (e.g. before switching screen)
	 */
	static void deactivateAllButtons() {
		// check needed, because method is called also by setFlags()
		if (sButtonList != null && sButtonList.size() > 0) {
			for (TouchButton tButton : sButtonList) {
				if (tButton != null) {
					tButton.mIsActive = false;
				}
			}
		}
	}

	/**
	 * Check if touch event is in button area if yes - return true if no - return false
	 */
	private boolean checkButtonInArea(int aTouchPositionX, int aTouchPositionY) {
		if (aTouchPositionX < mPositionX || aTouchPositionX > mPositionX + mWidth || aTouchPositionY < mPositionY
				|| aTouchPositionY > (mPositionY + mHeight)) {
			return false;
		}
		return true;
	}

	private static String tString;
	private static String tButtonCaption;

	public static void interpreteCommand(final RPCView aRPCView, int aCommand, int[] aParameters, int aParamsLength,
			byte[] aDataBytes, int[] aDataInts, int aDataLength) {
		int tButtonNumber = -1;
		TouchButton tButton = null;
		if (aParamsLength > 0 && aCommand != FUNCTION_TAG_BUTTON_ACTIVATE_ALL && aCommand != FUNCTION_TAG_BUTTON_DEACTIVATE_ALL
				&& aCommand != FUNCTION_TAG_BUTTON_GLOBAL_SETTINGS) {
			tButtonNumber = aParameters[0];
			if (tButtonNumber < sButtonList.size()) {
				// get button for create with existent button number
				tButton = sButtonList.get(tButtonNumber);
				if (aCommand != FUNCTION_TAG_BUTTON_CREATE && (tButton == null || !tButton.mIsInitialized)) {
					Log.e(LOG_TAG, "Command=0x" + Integer.toHexString(aCommand) + " ButtonNr=" + tButtonNumber
							+ " is null or not initialized.");
					return;
				}
				if (BlueDisplay.isINFO()) {
					tButtonCaption = " \"" + tButton.mCaption + "\". ButtonNr=";
				}
			} else if (aCommand != FUNCTION_TAG_BUTTON_CREATE) {
				Log.e(LOG_TAG, "Command=0x" + Integer.toHexString(aCommand) + " ButtonNr=" + tButtonNumber + " not found. Only "
						+ sButtonList.size() + " buttons created.");
				return;
			}
		}

		switch (aCommand) {

		case FUNCTION_TAG_BUTTON_ACTIVATE_ALL:
			if (BlueDisplay.isINFO()) {
				Log.i(LOG_TAG, "Activate all buttons");
			}
			activateAllButtons();
			break;

		case FUNCTION_TAG_BUTTON_DEACTIVATE_ALL:
			if (BlueDisplay.isINFO()) {
				Log.i(LOG_TAG, "Deactivate all buttons");
			}
			deactivateAllButtons();
			break;

		case FUNCTION_TAG_BUTTON_GLOBAL_SETTINGS:
			if ((aParameters[0] & USE_UP_EVENTS_FOR_BUTTONS) != 0) {
				if (aRPCView.mTouchIsActive && !aRPCView.mUseUpEventForButtons) {
					// since we switched mode while button was down
					aRPCView.mDisableButtonUpOnce = true;
				}
				aRPCView.mUseUpEventForButtons = true;
			} else {
				aRPCView.mUseUpEventForButtons = false;
			}

			if ((aParameters[0] & BUTTONS_SET_BEEP_TONE) != 0) {
				if (aParamsLength > 1) {
					// set Tone
					int tVolume = mActualToneVolume; // default is, not to change volume
					if (aParamsLength > 2) {
						/*
						 * set volume
						 */
						if (aParameters[2] >= 0 || aParameters[2] < ToneGenerator.MAX_VOLUME) {
							tVolume = aParameters[2];
						}
						if (mActualToneVolume != tVolume) {
							mToneGenerator = new ToneGenerator(AudioManager.STREAM_SYSTEM, tVolume);
							mActualToneVolume = tVolume;
						}
					}
					if (aParameters[1] > 0 && aParameters[1] < ToneGenerator.TONE_CDMA_SIGNAL_OFF) {
						sTouchBeepIndex = aParameters[1];
					}
				}
			}
			if (BlueDisplay.isINFO()) {
				tString = "";
				if (aRPCView.mUseUpEventForButtons) {
					tString = " UseUpEventForButtons";
				}
				Log.i(LOG_TAG, "Global settings. Flags=0x" + Integer.toHexString(aParameters[0]) + tString + ". Touch tone volume="
						+ mActualToneVolume + ", index=" + sTouchBeepIndex);
			}
			break;

		case FUNCTION_TAG_BUTTON_DRAW:
			if (BlueDisplay.isINFO()) {
				Log.i(LOG_TAG, "Draw button" + tButtonCaption + tButtonNumber);
			}
			tButton.drawButton();
			break;

		case FUNCTION_TAG_BUTTON_DRAW_CAPTION:
			if (BlueDisplay.isINFO()) {
				Log.i(LOG_TAG, "Draw caption" + tButtonCaption + tButtonNumber);
			}
			tButton.drawCaption();
			break;

		case FUNCTION_TAG_BUTTON_SET_CAPTION:
		case FUNCTION_TAG_BUTTON_SET_CAPTION_AND_DRAW_BUTTON:
			aRPCView.myConvertChars(aDataBytes, RPCView.sCharsArray, aDataLength);
			tString = new String(RPCView.sCharsArray, 0, aDataLength);

			if (BlueDisplay.isINFO()) {
				Log.i(LOG_TAG, "Set caption=\"" + tString + "\" for" + tButtonCaption + tButtonNumber);
			}
			tButton.setCaption(tString);
			if (aCommand == FUNCTION_TAG_BUTTON_SET_CAPTION_AND_DRAW_BUTTON) {
				tButton.drawButton();
			}
			break;

		case FUNCTION_TAG_BUTTON_SETTINGS:
			int tSubcommand = aParameters[1];
			switch (tSubcommand) {
			case BUTTON_FLAG_SET_COLOR_BUTTON:
				tButton.mButtonColor = RPCView.shortToLongColor(aParameters[2]);
				if (BlueDisplay.isINFO()) {
					Log.i(LOG_TAG, "Set color=0x" + Integer.toHexString(tButton.mButtonColor) + " for" + tButtonCaption
							+ tButtonNumber);

				}
				break;
			case BUTTON_FLAG_SET_COLOR_CAPTION:
				tButton.mCaptionColor = RPCView.shortToLongColor(aParameters[2]);
				if (BlueDisplay.isINFO()) {
					Log.i(LOG_TAG, "Set caption color=0x" + Integer.toHexString(tButton.mCaptionColor) + " for" + tButtonCaption
							+ tButtonNumber);
				}
				break;
			case BUTTON_FLAG_SET_VALUE:
				tButton.mValue = aParameters[2] & 0x0000FFFF;
				if (aParamsLength == 4) {
					tButton.mValue = tButton.mValue | (aParameters[3] << 16);
				}
				if (BlueDisplay.isINFO()) {
					Log.i(LOG_TAG, "Set value=" + aParameters[2] + " for" + tButtonCaption + tButtonNumber);
				}
				break;
			case BUTTON_FLAG_SET_POSITION:
				if (BlueDisplay.isINFO()) {
					Log.i(LOG_TAG, "Set position=" + aParameters[2] + " / " + aParameters[3] + ". " + tButtonCaption
							+ tButtonNumber);
				}
				tButton.setPosition(aParameters[2], aParameters[3]);
				break;
			case BUTTON_FLAG_SET_ACTIVE:
				if (BlueDisplay.isINFO()) {
					Log.i(LOG_TAG, "Set active=true for" + tButtonCaption + tButtonNumber);
				}
				tButton.mIsActive = true;
				break;
			case BUTTON_FLAG_RESET_ACTIVE:
				if (BlueDisplay.isINFO()) {
					Log.i(LOG_TAG, "Set active=false for" + tButtonCaption + tButtonNumber);
				}
				tButton.mIsActive = false;
				break;

			}
			break;

		case FUNCTION_TAG_BUTTON_SET_COLOR_AND_VALUE_AND_DRAW:
			tButton.mButtonColor = RPCView.shortToLongColor(aParameters[1]);
			tButton.mValue = aParameters[2];
			if (BlueDisplay.isINFO()) {
				Log.i(LOG_TAG, "set color=0x" + Integer.toHexString(tButton.mButtonColor) + " and value=" + tButton.mValue + " for"
						+ tButtonCaption + tButtonNumber);
			}
			tButton.drawButton();
			break;

		case FUNCTION_TAG_BUTTON_CREATE:
			aRPCView.myConvertChars(aDataBytes, RPCView.sCharsArray, aDataLength);
			tString = new String(RPCView.sCharsArray, 0, aDataLength);
			int tCallbackAddress = aParameters[8];
			if (aParamsLength == 10) {
				// 32 bit callback address
				tCallbackAddress = tCallbackAddress | (aParameters[9] << 16);
			}
			if (BlueDisplay.isINFO()) {
				Log.i(LOG_TAG, "Create button. ButtonNr=" + tButtonNumber + ", new TouchButton(\"" + tString + "\", "
						+ aParameters[1] + ", " + aParameters[2] + ", " + aParameters[3] + ", " + aParameters[4] + ", color=0x"
						+ Integer.toHexString(RPCView.shortToLongColor(aParameters[5])) + ", " + aParameters[6] + ", "
						+ aParameters[7] + ", 0x" + Integer.toHexString(tCallbackAddress) + ")");
			}
			if (tButton == null) {
				/*
				 * create new button
				 */
				tButton = new TouchButton();
				if (tButtonNumber < sButtonList.size()) {
					sButtonList.set(tButtonNumber, tButton);
				} else {
					tButtonNumber = sButtonList.size();
					sButtonList.add(tButton);
					Log.w(LOG_TAG,
							"Button with index " + tButtonNumber + " appended at end of list. List size now " + sButtonList.size());
				}
				tButton.mListIndex = tButtonNumber;
			}
			tButton.initButton(aRPCView, aParameters[1], aParameters[2], aParameters[3], aParameters[4],
					RPCView.shortToLongColor(aParameters[5]), tString, aParameters[6], aParameters[7], tCallbackAddress);
			break;

		default:
			Log.e(LOG_TAG, "unknown command 0x" + Integer.toHexString(aCommand) + " received. paramsLength=" + aParamsLength
					+ " dataLenght=" + aDataLength);
			break;
		}
	}
}
