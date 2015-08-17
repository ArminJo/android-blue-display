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
	String mEscapedCaption; // contains the caption for Logging e.g. with \n replaced by " | "
	String[] mCaptionStrings; // contains the array of caption strings for multiline Captions
	int mValue;
	int mListIndex; // index in sButtonList
	int mCallbackAddress;
	boolean mDoBeep;
	boolean mIsRedGreen;
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
	private static final int BUTTON_FLAG_SET_COLOR_BUTTON_AND_DRAW = 0x01;
	private static final int BUTTON_FLAG_SET_COLOR_CAPTION = 0x02;
	private static final int BUTTON_FLAG_SET_COLOR_CAPTION_AND_DRAW = 0x03;
	private static final int BUTTON_FLAG_SET_VALUE = 0x04;
	private static final int BUTTON_FLAG_SET_VALUE_AND_DRAW = 0x05;
	private static final int BUTTON_FLAG_SET_COLOR_AND_VALUE = 0x06;
	private static final int BUTTON_FLAG_SET_COLOR_AND_VALUE_AND_DRAW = 0x07;
	private static final int BUTTON_FLAG_SET_POSITION = 0x08;
	private static final int BUTTON_FLAG_SET_POSITION_AND_DRAW = 0x09;
	private static final int BUTTON_FLAG_SET_ACTIVE = 0x10;
	private static final int BUTTON_FLAG_RESET_ACTIVE = 0x11;

	// Flags for BUTTON_GLOBAL_SETTINGS
	private static final int USE_UP_EVENTS_FOR_BUTTONS = 0x01;
	private static final int BUTTONS_SET_BEEP_TONE = 0x02;

	// Flags for local settings
	private static final int BUTTON_FLAG_DO_BEEP_ON_TOUCH = 0x01;
	// Red if value == 0 else green
	private static final int BUTTON_FLAG_TYPE_AUTO_RED_GREEN = 0x02;

	TouchButton() {
		// empty button
		mIsInitialized = false;
	}

	/**
	 * Static convenience method - reset all button lists and button flags
	 */
	static void resetButtons(final RPCView aRPCView) {
		sButtonList.clear();
		aRPCView.mUseUpEventForButtons = false;
		sTouchBeepIndex = ToneGenerator.TONE_CDMA_KEYPAD_VOLUME_KEY_LITE;
	}

	void initButton(final RPCView aRPCView, final int aPositionX, final int aPositionY, final int aWidthX, final int aHeightY,
			final int aButtonColor, final String aCaptionForLogging, final String[] aCaptionArray, final int aCaptionSizeAndFlags,
			final int aValue, final int aCallbackAddress) {
		mRPCView = aRPCView;

		mEscapedCaption = aCaptionForLogging; // for logging purposes do it here
		mCaptionStrings = aCaptionArray;

		mWidth = aWidthX;
		mHeight = aHeightY;
		// Plausi is also done here
		setPosition(aPositionX, aPositionY);

		mButtonColor = aButtonColor;
		mValue = aValue;
		mCallbackAddress = aCallbackAddress;
		mCaptionColor = sDefaultCaptionColor;

		mCaptionSize = aCaptionSizeAndFlags & 0xFF;
		positionCaption();

		if (aButtonColor == 0) {
			mButtonColor = sDefaultButtonColor;
		}

		/*
		 * local flags
		 */
		int tFlags = aCaptionSizeAndFlags >> 8;
		mDoBeep = false;
		mIsRedGreen = false;

		if ((tFlags & BUTTON_FLAG_DO_BEEP_ON_TOUCH) != 0) {
			if (mToneGenerator == null) {
				mToneGenerator = new ToneGenerator(AudioManager.STREAM_SYSTEM, ToneGenerator.MAX_VOLUME);
			}
			mDoBeep = true;
		}
		if ((tFlags & BUTTON_FLAG_TYPE_AUTO_RED_GREEN) != 0) {
			mIsRedGreen = true;
			if (mValue != 0) {
				mValue = 1;
				mButtonColor = Color.GREEN;
			} else {
				mButtonColor = Color.RED;
			}
		}

		mIsActive = false;
		mIsInitialized = true;
	}

	void drawButton() {
		// Draw rect
		mRPCView.fillRectRel(mPositionX, mPositionY, mWidth, mHeight, mButtonColor);
		drawCaption();
	}

	private void positionCaption() {
		if (mCaptionSize > 0 && mEscapedCaption.length() >= 0) { // don't render anything if caption size == 0 or caption is empty
			if (mCaptionStrings.length > 1) {
				/*
				 * Multiline caption first position the string vertical
				 */
				if (mCaptionSize * mCaptionStrings.length >= mHeight) {
					// Font height to big
					Log.w(LOG_TAG, "caption\"" + mEscapedCaption + "\" with " + mCaptionStrings.length + " lines to high");
					mCaptionPositionY = mPositionY + (int) (0.76 * mCaptionSize); // Fallback - start at top + ascend
				} else {
					mCaptionPositionY = (mPositionY + ((mHeight - mCaptionSize * (mCaptionStrings.length - 1)) / 2) + (int) (0.262 * mCaptionSize));
				}
				mCaptionPositionX = -1; // to indicate multiline caption

			} else {
				// try to position the string in the middle of the box
				int tLength = (int) (0.6 * mCaptionSize * mEscapedCaption.length());
				if (tLength >= mWidth) {
					// String too long here
					mCaptionPositionX = mPositionX;
					Log.w(LOG_TAG, "caption\"" + mEscapedCaption + "\" to long");
				} else {
					mCaptionPositionX = mPositionX + ((mWidth - tLength) / 2);
				}

				if (mCaptionSize >= mHeight) {
					// Font height to big
					Log.w(LOG_TAG, "caption\"" + mEscapedCaption + "\" to high");
				}
				// (0.262 * mCaptionSize) is ((Ascend - Decent)/2) needed for positioning center of font middle of font height
				mCaptionPositionY = (int) (mPositionY + (mHeight / 2) + (0.262 * mCaptionSize));
			}
		}
	}

	/**
	 * draws the caption of a button
	 */
	void drawCaption() {
		mIsActive = true;
		if (mCaptionSize > 0) { // don't render anything if caption size == 0
			if (mCaptionStrings.length == 1) {
				mRPCView.drawTextWithBackground(mCaptionPositionX, mCaptionPositionY, mEscapedCaption, mCaptionSize, mCaptionColor,
						mButtonColor);
			} else {
				int TPosY = mCaptionPositionY;
				// Multiline caption
				for (int i = 0; i < mCaptionStrings.length; i++) {
					// try to position the string in the middle of the box
					int tLength = (int) (0.6 * mCaptionSize * mCaptionStrings[i].length());
					int tCaptionPositionX;
					if (tLength >= mWidth) {
						// String too long here
						tCaptionPositionX = mPositionX;
						Log.w(LOG_TAG, "sub caption\"" + mCaptionStrings[i] + "\" to long");
					} else {
						tCaptionPositionX = mPositionX + ((mWidth - tLength) / 2);
					}
					mRPCView.drawTextWithBackground(tCaptionPositionX, TPosY, mCaptionStrings[i], mCaptionSize, mCaptionColor,
							mButtonColor);
					TPosY += mCaptionSize;
				}
			}
		}
	}

	void setPosition(int aPositionX, int aPositionY) {
		mPositionX = aPositionX;
		mPositionY = aPositionY;

		// check values
		if (aPositionX + mWidth > mRPCView.mRequestedCanvasWidth) {
			Log.e(LOG_TAG, mEscapedCaption + " Button x-position/width " + aPositionX + "+" + mWidth + " wrong. Set width to:"
					+ (mRPCView.mRequestedCanvasWidth - aPositionX));
			mWidth = mRPCView.mRequestedCanvasWidth - aPositionX;
		}
		if (aPositionY + mHeight > mRPCView.mRequestedCanvasHeight) {
			Log.e(LOG_TAG, mEscapedCaption + " Button y-position/height " + aPositionY + "+" + mHeight + " wrong. Set height to:"
					+ (mRPCView.mRequestedCanvasHeight - aPositionY));
			mHeight = mRPCView.mRequestedCanvasHeight - aPositionY;

		}
	}

	/**
	 * Check if touch event is in button area if yes - call callback function and return true if no - return false
	 */
	boolean checkIfTouchInButton(int aTouchPositionX, int aTouchPositionY, boolean aJustCheck) {
		if (mIsActive && mCallbackAddress != 0 && checkButtonInArea(aTouchPositionX, aTouchPositionY)) {
			if (!aJustCheck) {
				/*
				 * Touch position is in button - call callback function
				 */
				if (mDoBeep) {
					mToneGenerator.startTone(sTouchBeepIndex);
				}
				mRPCView.mBlueDisplayContext.mSerialService.writeGuiCallbackEvent(
						BluetoothSerialService.EVENT_TAG_BUTTON_CALLBACK_ACTION, mListIndex, mCallbackAddress, mValue);
			}
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
	static int checkAllButtons(int aTouchPositionX, int aTouchPositionY, boolean aJustCheck) {
		// walk through list of active elements
		for (TouchButton tButton : sButtonList) {
			if (tButton.mIsActive && tButton.checkIfTouchInButton(aTouchPositionX, aTouchPositionY, aJustCheck)) {
				return tButton.mListIndex;
			}
		}
		return -1;
	}

	/**
	 * Static convenience method - activate all buttons
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

	public static void interpretCommand(final RPCView aRPCView, int aCommand, int[] aParameters, int aParamsLength,
			byte[] aDataBytes, int[] aDataInts, int aDataLength) {
		int tButtonNumber = -1; // to have it initialized ;-)
		TouchButton tButton = null;
		String tButtonCaption = "";
		String tString;

		/*
		 * Plausi
		 */
		if (aCommand != FUNCTION_TAG_BUTTON_ACTIVATE_ALL && aCommand != FUNCTION_TAG_BUTTON_DEACTIVATE_ALL
				&& aCommand != FUNCTION_TAG_BUTTON_GLOBAL_SETTINGS) {
			/*
			 * We need a button for the command
			 */
			if (aParamsLength <= 0) {
				Log.e(LOG_TAG, "aParamsLength is <=0 but Command=0x" + Integer.toHexString(aCommand) + " is not one of "
						+ FUNCTION_TAG_BUTTON_ACTIVATE_ALL + ", " + FUNCTION_TAG_BUTTON_DEACTIVATE_ALL + " or "
						+ FUNCTION_TAG_BUTTON_GLOBAL_SETTINGS);
				return;
			} else {
				tButtonNumber = aParameters[0];
				if (tButtonNumber >= 0 && tButtonNumber < sButtonList.size()) {
					// get button for create with existent button number
					tButton = sButtonList.get(tButtonNumber);
					if (aCommand != FUNCTION_TAG_BUTTON_CREATE && (tButton == null || !tButton.mIsInitialized)) {
						Log.e(LOG_TAG, "Command=0x" + Integer.toHexString(aCommand) + " ButtonNr=" + tButtonNumber
								+ " is null or not initialized.");
						return;
					}
					if (BlueDisplay.isINFO()) {
						tButtonCaption = " \"" + tButton.mEscapedCaption + "\". ButtonNr=";
					}
				} else if (aCommand != FUNCTION_TAG_BUTTON_CREATE) {
					Log.e(LOG_TAG, "Command=0x" + Integer.toHexString(aCommand) + " ButtonNr=" + tButtonNumber
							+ " not found. Only " + sButtonList.size() + " buttons created.");
					return;
				}
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
					int tVolume = mActualToneVolume; // default is not to change volume
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
			tButton.mEscapedCaption = tString.replaceAll("\n", " | ");
			if (BlueDisplay.isINFO()) {
				Log.i(LOG_TAG, "Set caption=\"" + tButton.mEscapedCaption + "\" for" + tButtonCaption + tButtonNumber);
			}
			tButton.mCaptionStrings = tString.split("\n");
			tButton.positionCaption();
			if (aCommand == FUNCTION_TAG_BUTTON_SET_CAPTION_AND_DRAW_BUTTON) {
				tButton.drawButton();
			}
			break;

		case FUNCTION_TAG_BUTTON_SETTINGS:
			int tSubcommand = aParameters[1];
			switch (tSubcommand) {
			case BUTTON_FLAG_SET_COLOR_BUTTON:
			case BUTTON_FLAG_SET_COLOR_BUTTON_AND_DRAW:
				tButton.mButtonColor = RPCView.shortToLongColor(aParameters[2]);
				if (BlueDisplay.isINFO()) {
					String tFunction = " for";
					if (tSubcommand == BUTTON_FLAG_SET_COLOR_BUTTON_AND_DRAW) {
						tFunction = " and draw for";
					}
					Log.i(LOG_TAG, "Set button color= " + RPCView.shortToColorString(tButton.mButtonColor) + tFunction
							+ tButtonCaption + tButtonNumber);

				}
				if (tSubcommand == BUTTON_FLAG_SET_COLOR_BUTTON_AND_DRAW) {
					tButton.drawButton();
				}
				break;

			case BUTTON_FLAG_SET_COLOR_CAPTION_AND_DRAW:
			case BUTTON_FLAG_SET_COLOR_CAPTION:
				tButton.mCaptionColor = RPCView.shortToLongColor(aParameters[2]);
				if (BlueDisplay.isINFO()) {
					Log.i(LOG_TAG, "Set caption color= " + RPCView.shortToColorString(tButton.mCaptionColor) + " for"
							+ tButtonCaption + tButtonNumber);
				}
				if (tSubcommand == BUTTON_FLAG_SET_COLOR_CAPTION_AND_DRAW) {
					tButton.drawButton();
				}
				break;

			case BUTTON_FLAG_SET_VALUE:
			case BUTTON_FLAG_SET_VALUE_AND_DRAW:
				tButton.mValue = aParameters[2] & 0x0000FFFF;
				if (aParamsLength == 4) {
					tButton.mValue = tButton.mValue | (aParameters[3] << 16);
				}
				if (tButton.mIsRedGreen) {
					if (tButton.mValue != 0) {
						tButton.mValue = 1;
						tButton.mButtonColor = Color.GREEN;
					} else {
						tButton.mButtonColor = Color.RED;
					}
				}
				if (BlueDisplay.isINFO()) {
					Log.i(LOG_TAG, "Set value=" + aParameters[2] + " for" + tButtonCaption + tButtonNumber);
				}
				if (tSubcommand == BUTTON_FLAG_SET_VALUE_AND_DRAW) {
					tButton.drawButton();
				}
				break;

			case BUTTON_FLAG_SET_POSITION:
			case BUTTON_FLAG_SET_POSITION_AND_DRAW:
				if (BlueDisplay.isINFO()) {
					Log.i(LOG_TAG, "Set position=" + aParameters[2] + " / " + aParameters[3] + ". " + tButtonCaption
							+ tButtonNumber);
				}
				tButton.setPosition(aParameters[2], aParameters[3]);
				if (tSubcommand == BUTTON_FLAG_SET_POSITION_AND_DRAW) {
					tButton.drawButton();
				}
				break;

			case BUTTON_FLAG_SET_COLOR_AND_VALUE:
			case BUTTON_FLAG_SET_COLOR_AND_VALUE_AND_DRAW:
				tButton.mButtonColor = RPCView.shortToLongColor(aParameters[2]);
				tButton.mValue = aParameters[3];
				if (BlueDisplay.isINFO()) {
					Log.i(LOG_TAG, "set color= " + RPCView.shortToColorString(tButton.mButtonColor) + " and value="
							+ tButton.mValue + " for" + tButtonCaption + tButtonNumber);
				}
				if (tSubcommand == BUTTON_FLAG_SET_COLOR_AND_VALUE_AND_DRAW) {
					tButton.drawButton();
				}
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

		case FUNCTION_TAG_BUTTON_CREATE:
			aRPCView.myConvertChars(aDataBytes, RPCView.sCharsArray, aDataLength);
			tButtonCaption = new String(RPCView.sCharsArray, 0, aDataLength);
			tString = tButtonCaption.replaceAll("\n", " | ");
			int tCallbackAddress = aParameters[8] & 0x0000FFFF;
			if (aParamsLength == 10) {
				// 32 bit callback address
				tCallbackAddress = tCallbackAddress | (aParameters[9] << 16);
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
					if (BlueDisplay.isDEBUG()) {
						Log.d(LOG_TAG, "Button with index " + tButtonNumber + " appended at end of list. List size now "
								+ sButtonList.size());
					}
				}
				tButton.mListIndex = tButtonNumber;
			}

			if (BlueDisplay.isINFO()) {
				Log.i(LOG_TAG,
						"Create button. ButtonNr=" + tButtonNumber + ", new TouchButton(\"" + tString + "\", " + aParameters[1]
								+ ", " + aParameters[2] + ", " + aParameters[3] + ", " + aParameters[4] + ", "
								+ RPCView.shortToColorString(aParameters[5]) + ", " + aParameters[6] + ", " + aParameters[7]
								+ ", 0x" + Integer.toHexString(tCallbackAddress) + ") ListSize=" + sButtonList.size());
			}

			tButton.initButton(aRPCView, aParameters[1], aParameters[2], aParameters[3], aParameters[4],
					RPCView.shortToLongColor(aParameters[5]), tString, tButtonCaption.split("\n"), aParameters[6], aParameters[7],
					tCallbackAddress);
			break;

		default:
			Log.e(LOG_TAG, "unknown command 0x" + Integer.toHexString(aCommand) + " received. paramsLength=" + aParamsLength
					+ " dataLenght=" + aDataLength);
			break;
		}
	}
}
