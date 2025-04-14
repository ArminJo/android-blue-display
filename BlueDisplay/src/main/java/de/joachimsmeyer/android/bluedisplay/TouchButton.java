/*
 *     SUMMARY
 *     Blue Display is an Open Source Android remote Display for Arduino etc.
 *     It receives basic draw requests from Arduino etc. over Bluetooth and renders it.
 *     It also implements basic GUI elements as buttons and sliders.
 *     It sends touch or GUI callback events over Bluetooth back to Arduino.
 *
 *  Copyright (C) 2014  Armin Joachimsmeyer
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
 * This class implements simple touch buttons compatible with the one available as c++ code for arduino.
 * Usage of the java buttons reduces data sent over bluetooth as well as arduino program size.
 */

package de.joachimsmeyer.android.bluedisplay;

import static de.joachimsmeyer.android.bluedisplay.RPCView.COLOR32_NO_BACKGROUND;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class TouchButton {

    // Logging
    private static final String LOG_TAG = "BT";

    RPCView mRPCView;
    int mButtonColor;
    int mPositionX;
    int mPositionY;
    int mTextPositionX;
    int mTextPositionY;
    int mWidth;
    int mHeight;

    int mTextSize;
    int mTextColor;
    String mEscapedText; // contains the text for Logging e.g. with \n replaced by "|"
    String[] mTextStrings; // contains the array of text strings to implement multiline texts

    int mValue;
    int mListIndex; // index in sButtonList
    int mCallbackAddress;
    boolean mDoBeep;
    boolean mIsManualRefresh; // default = false, true = no automatic refresh (currently only for red/green buttons)
    boolean mIsRedGreen; // Red = false, true = green
    String mRawTextForValueFalse; // contains the string for RedGreen button value == false
    String mRawTextForValueTrue; // contains the string for RedGreen button value == true

    /*
     * Autorepeat stuff
     */
    boolean mIsAutorepeatButton;
    int mMillisFirstAutorepeatDelay;
    int mMillisFirstAutorepeatRate;
    int mFirstAutorepeatCount;
    int mMillisSecondAutorepeatRate;
    // For timer
    static int sAutorepeatState;
    private static final int BUTTON_AUTOREPEAT_FIRST_PERIOD = 1;
    private static final int BUTTON_AUTOREPEAT_SECOND_PERIOD = 2;
    private static final int BUTTON_AUTOREPEAT_DISABLED_UNTIL_END_OF_TOUCH = 3;
    static int sAutorepeatCount;
    // static int sMillisFirstAutorepeatRate;
    // static int sMillisSecondAutorepeatRate;

    boolean mIsActive;
    boolean mIsInitialized;
    static int sTouchBeepIndex = ToneGenerator.TONE_CDMA_KEYPAD_VOLUME_KEY_LITE; // 89
    static ToneGenerator sButtonToneGenerator;
    static int sLastRequestedToneVolume;
    static int sLastSystemToneVolume;
    static int sCurrentToneDurationMillis = -1; // -1 means till end of tone or forever

    static final int sDefaultButtonColor = Color.RED;
    static final int sDefaultTextColor = Color.BLACK;

    private static final int BUTTON_INITIAL_LIST_SIZE = 40;

    public static final List<TouchButton> sButtonList = new ArrayList<>(BUTTON_INITIAL_LIST_SIZE);

    private static final int FUNCTION_BUTTON_DRAW = 0x40;
    private static final int FUNCTION_BUTTON_DRAW_TEXT = 0x41;
    private static final int FUNCTION_BUTTON_SETTINGS = 0x42;
    private static final int FUNCTION_BUTTON_REMOVE = 0x43;

    // static functions
    private static final int FUNCTION_BUTTON_ACTIVATE_ALL = 0x48;
    private static final int FUNCTION_BUTTON_DEACTIVATE_ALL = 0x49;
    private static final int FUNCTION_BUTTON_GLOBAL_SETTINGS = 0x4A;

    private static final int FUNCTION_BUTTON_DISABLE_AUTOREPEAT_UNTIL_END_OF_TOUCH = 0x4B;
    // Flags for BUTTON_GLOBAL_SETTINGS
    private static final int FLAG_BUTTON_GLOBAL_USE_UP_EVENTS_FOR_BUTTONS = 0x01;
    private static final int FLAG_BUTTON_GLOBAL_SET_BEEP_TONE = 0x02;

    // Function with variable data size
    private static final int FUNCTION_BUTTON_INIT = 0x70;
    // Flags for button settings contained in flags parameter
    private static final int FLAG_BUTTON_DO_BEEP_ON_TOUCH = 0x01;
    // Red if value == 0 else green
    private static final int FLAG_BUTTON_TYPE_TOGGLE = 0x02;
    private static final int FLAG_BUTTON_TYPE_AUTOREPEAT = 0x04;
    private static final int BUTTON_FLAG_MANUAL_REFRESH = 0x08;

    private static final int FUNCTION_BUTTON_SET_TEXT_FOR_VALUE_TRUE = 0x71;
    private static final int FUNCTION_BUTTON_SET_TEXT = 0x72;
    private static final int FUNCTION_BUTTON_SET_TEXT_AND_DRAW_BUTTON = 0x73;

    // Flags for BUTTON_SETTINGS
    private static final int SUBFUNCTION_BUTTON_SET_BUTTON_COLOR = 0x00;
    private static final int SUBFUNCTION_BUTTON_SET_BUTTON_COLOR_AND_DRAW = 0x01;
    private static final int SUBFUNCTION_BUTTON_SET_TEXT_COLOR = 0x02;
    private static final int SUBFUNCTION_BUTTON_SET_TEXT_COLOR_AND_DRAW = 0x03;
    private static final int SUBFUNCTION_BUTTON_SET_VALUE = 0x04;
    private static final int SUBFUNCTION_BUTTON_SET_VALUE_AND_DRAW = 0x05;
    private static final int SUBFUNCTION_BUTTON_SET_COLOR_AND_VALUE = 0x06;
    private static final int SUBFUNCTION_BUTTON_SET_COLOR_AND_VALUE_AND_DRAW = 0x07;
    private static final int SUBFUNCTION_BUTTON_SET_POSITION = 0x08;
    private static final int SUBFUNCTION_BUTTON_SET_POSITION_AND_DRAW = 0x09;

    private static final int SUBFUNCTION_BUTTON_SET_ACTIVE = 0x10;
    private static final int SUBFUNCTION_BUTTON_RESET_ACTIVE = 0x11;
    private static final int SUBFUNCTION_BUTTON_SET_AUTOREPEAT_TIMING = 0x12;

    private static final int SUBFUNCTION_BUTTON_SET_CALLBACK = 0x20;
    private static final int SUBFUNCTION_BUTTON_SET_FLAGS = 0x30;

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
                    final int aButtonColor, final String aText, final int aTextSize, final int aFlags, final int aValue,
                    final int aCallbackAddress) {
        mRPCView = aRPCView;

        mWidth = aWidthX;
        mHeight = aHeightY;
        setEscapedText(aText); // required for error message of plausi, id called again by handleText(aText) below
        // Plausi is also done here
        setPosition(aPositionX, aPositionY);

        mButtonColor = aButtonColor;
        mValue = aValue;
        mCallbackAddress = aCallbackAddress;

        /*
         * Text
         */
        mTextColor = sDefaultTextColor;
        mTextSize = aTextSize;
        handleText(aText);

        if (aButtonColor == 0) {
            mButtonColor = sDefaultButtonColor;
        }

        handleFlags(aFlags);

        mMillisFirstAutorepeatDelay = 0;
        mRawTextForValueFalse = aText; // do it anyway to enable a later conversion to red green
        mIsActive = false;
        mIsInitialized = true;
    }

    void drawButton() {
        mIsActive = true;
        setColorForRedGreenButton();
        // Draw button rect
        if (mButtonColor != COLOR32_NO_BACKGROUND) {
            mRPCView.fillRectRel(mPositionX, mPositionY, mWidth, mHeight, mButtonColor);
        }
        drawText();
    }

    /*
     * Overwrite and deactivate button, but do not delete it.
     */
    void removeButton(int tBackgroundColor) {
        // Clear rect
        mRPCView.fillRectRel(mPositionX, mPositionY, mWidth, mHeight, tBackgroundColor);
        mIsActive = false;
    }

    /*
     * Sets color according to value
     */
    private void setColorForRedGreenButton() {
        if (mIsRedGreen) {
            if (mValue != 0) {
                // TRUE
                mButtonColor = Color.GREEN;
            } else {
                // FALSE
                mButtonColor = Color.RED;
            }
        }
    }

    private void setEscapedText(String aText) {
        mEscapedText = aText.replaceAll("\n", "|");
    }

    private void handleText(String aText) {
        setEscapedText(aText);
        mTextStrings = aText.split("\n");
        for (int i = 0; i < mTextStrings.length; i++) {
            // trim all strings
            mTextStrings[i] = mTextStrings[i].trim();
        }
        positionText();
    }

    /*
     * interpret flags and set local flags
     */
    private void handleFlags(int aFlags) {

        if ((aFlags & FLAG_BUTTON_DO_BEEP_ON_TOUCH) == 0) {
            mDoBeep = false;
        } else {
            if (sButtonToneGenerator == null) {
                int tCurrentSystemVolume = mRPCView.mBlueDisplayContext.mAudioManager.getStreamVolume(AudioManager.STREAM_SYSTEM);
                sButtonToneGenerator = new ToneGenerator(AudioManager.STREAM_SYSTEM,
                        (tCurrentSystemVolume * ToneGenerator.MAX_VOLUME) / mRPCView.mBlueDisplayContext.mMaxSystemVolume);
            }
            mDoBeep = true;
        }
        if ((aFlags & FLAG_BUTTON_TYPE_TOGGLE) == 0) {
            mIsRedGreen = false;
        } else {
            mIsRedGreen = true;
            if (mValue != 0) {
                mValue = 1;
            }
        }

        if ((aFlags & BUTTON_FLAG_MANUAL_REFRESH) == 0) {
            mIsManualRefresh = false;
        } else {
            mIsManualRefresh = true;
        }

        if ((aFlags & FLAG_BUTTON_TYPE_AUTOREPEAT) == 0) {
            mIsAutorepeatButton = false;
        } else {
            mIsAutorepeatButton = true;
        }
    }

    private void positionText() {
        if (mTextSize > 0 && !mEscapedText.isEmpty()) { // don't render anything if text size == 0 or text is empty
            if (mTextStrings.length > 1) {
                /*
                 * Multiline text first position the string vertical
                 */
                if (mTextSize * mTextStrings.length >= mHeight) {
                    // Font height to big
                    MyLog.w(LOG_TAG, "text \"" + mEscapedText + "\" height=" + mTextSize + " with " + mTextStrings.length + " lines is higher than button height=" + mHeight);
                    mTextPositionY = mPositionY + (int) ((RPCView.TEXT_ASCEND_FACTOR * mTextSize) + 0.5); // Fallback - start
                    // at top + ascend
                } else {
                    mTextPositionY = (int) (mPositionY + (((float) (mHeight - (mTextSize * (mTextStrings.length)))) / 2) + ((RPCView.TEXT_ASCEND_FACTOR * mTextSize) + 0.5));
                }
                mTextPositionX = -1; // to indicate multiline text

            } else {
                /*
                 * Single line text - just try to position the string in the middle of the box
                 */
                int tLength = (int) ((RPCView.TEXT_WIDTH_FACTOR * mTextSize * mEscapedText.length()) + 0.5);
                if (tLength >= mWidth) {
                    // String too long here
                    mTextPositionX = mPositionX;
                    MyLog.w(LOG_TAG, "text \"" + mEscapedText + "\" length=" + tLength + " is longer than button width=" + mWidth);

                } else {
                    mTextPositionX = mPositionX + ((mWidth - tLength) / 2);
                }

                if (mTextSize >= mHeight) {
                    // Font height to big
                    MyLog.w(LOG_TAG, "text \"" + mEscapedText + "\" height=" + mTextSize + " is higher than button height=" + mHeight);
                }
                // (RPCView.TEXT_ASCEND_FACTOR * mCTextSize) is Ascend
                mTextPositionY = (int) ((mPositionY + (((float) (mHeight - mTextSize)) / 2) + (RPCView.TEXT_ASCEND_FACTOR * mTextSize)) + 0.5);
            }
        }
    }

    /**
     * draws the text of a button
     */
    void drawText() {
        mIsActive = true;
        if (mIsRedGreen) {
            /*
             * Position red green text, it may have changed before
             */
            if (mRawTextForValueTrue != null) { // No need to reposition, if we have only one text for both values
                if (mValue != 0) {
                    // select and prepare text for value TRUE
                    handleText(mRawTextForValueTrue);
                } else {
                    // select and prepare text for value FALSE
                    handleText(mRawTextForValueFalse);
                }
            }
        }

        if (mTextSize > 0) { // don't render anything if text size == 0
            if (mTextStrings.length == 1) {
                // Draw mTextStrings[0], set by handleText()
                mRPCView.drawTextWithBackground(mTextPositionX, mTextPositionY, mTextStrings[0], mTextSize,
                        mTextColor, mButtonColor);
            } else {
                // Multiline text
                int TPosY = mTextPositionY;
                for (String mTextString : mTextStrings) {
                    // try to position the string in the middle of the box
                    int tLength = (int) ((RPCView.TEXT_WIDTH_FACTOR * mTextSize * mTextString.length()) + 0.5);
                    int tTextPositionX;
                    if (tLength >= mWidth) {
                        // String too long here
                        tTextPositionX = mPositionX;
                        MyLog.w(LOG_TAG, "sub text \"" + mTextString + "\" length=" + tLength + " is longer than button width=" + mWidth);
                    } else {
                        tTextPositionX = mPositionX + ((mWidth - tLength) / 2);
                    }
                    mRPCView.drawTextWithBackground(tTextPositionX, TPosY, mTextString, mTextSize, mTextColor,
                            mButtonColor);
                    TPosY += mTextSize + 1; // for space between lines otherwise we see "g" truncated
                }
            }
        }
    }

    void setPosition(int aPositionX, int aPositionY) {
        mPositionX = aPositionX;
        mPositionY = aPositionY;

        // check values
        if (aPositionX > mRPCView.mRequestedCanvasWidth) {
            MyLog.e(LOG_TAG, mEscapedText + " Button x-position " + aPositionX + " is greater than " + mRPCView.mRequestedCanvasWidth + ". Set position to 0");
            aPositionX = 0;
        }
        if (aPositionX + mWidth > mRPCView.mRequestedCanvasWidth) {
            MyLog.e(LOG_TAG, mEscapedText + " Button x-position + width " + aPositionX + " + " + mWidth + " is too great. Set width to:"
                    + (mRPCView.mRequestedCanvasWidth - aPositionX));
            mWidth = mRPCView.mRequestedCanvasWidth - aPositionX;
        }
        if (aPositionY > mRPCView.mRequestedCanvasHeight) {
            MyLog.e(LOG_TAG, mEscapedText + " Button y-position " + aPositionY + " is greater than " + mRPCView.mRequestedCanvasHeight + ". Set position to 0");
            aPositionY = 0;
        }
        if (aPositionY + mHeight > mRPCView.mRequestedCanvasHeight) {
            MyLog.e(LOG_TAG, mEscapedText + " Button y-position + height " + aPositionY + " + " + mHeight + " is too great. Set height to:"
                    + (mRPCView.mRequestedCanvasHeight - aPositionY));
            mHeight = mRPCView.mRequestedCanvasHeight - aPositionY;
        }
    }

    /**
     * Check if touch event is in button area. If yes - call callback function and return true if no - return false
     *
     * @return true if any active button touched
     */
    boolean checkIfTouchInButton(int aTouchPositionX, int aTouchPositionY,
                                 boolean aDoCallbackOnlyForAutorepeatButton) {
        if (mIsActive && mCallbackAddress != 0 && checkIfTouchInButtonArea(aTouchPositionX, aTouchPositionY)) {
            if (!aDoCallbackOnlyForAutorepeatButton || mIsAutorepeatButton) {
                /*
                 * Touch position is in button - call callback function
                 */
                if (mDoBeep) {
                    /*
                     * check if user changed volume
                     */
                    int tCurrentSystemVolume = mRPCView.mBlueDisplayContext.mAudioManager
                            .getStreamVolume(AudioManager.STREAM_SYSTEM);
                    if (sLastSystemToneVolume != tCurrentSystemVolume) {
                        sLastSystemToneVolume = tCurrentSystemVolume;
                        sButtonToneGenerator = new ToneGenerator(AudioManager.STREAM_SYSTEM,
                                (tCurrentSystemVolume * ToneGenerator.MAX_VOLUME) / mRPCView.mBlueDisplayContext.mMaxSystemVolume);
                    }
                    sButtonToneGenerator.startTone(sTouchBeepIndex, sCurrentToneDurationMillis);
                }
                /*
                 * Handle Toggle red/green button
                 */
                String tTextForInfo = mEscapedText;
                if (mIsRedGreen) {
                    /*
                     * Toggle value
                     */
                    if (mValue == 0) {
                        // TRUE
                        mValue = 1;
                    } else {
                        // FALSE
                        mValue = 0;
                    }
                    if (MyLog.isDEBUG()) {
                        MyLog.d(LOG_TAG, "Set value=" + mValue + " for \"" + mEscapedText + "\". ButtonNr=" + mListIndex);
                    }
                    if (!mIsManualRefresh) {
                        drawButton();
                        // Trigger next frame in order to show changed button
                        mRPCView.invalidate();
                    }
                }

                mRPCView.mBlueDisplayContext.mSerialService.writeGuiCallbackEvent(SerialService.EVENT_BUTTON_CALLBACK, mListIndex,
                        mCallbackAddress, mValue, tTextForInfo);

                /*
                 * Handle autorepeat
                 */
                if (mIsAutorepeatButton) {
                    if (mMillisFirstAutorepeatDelay == 0) {
                        MyLog.w(LOG_TAG, "Autorepeat button " + mEscapedText + " without timing!");
                    } else {
                        sAutorepeatState = BUTTON_AUTOREPEAT_FIRST_PERIOD;
                        sAutorepeatCount = mFirstAutorepeatCount;
                        mAutorepeatHandler.sendEmptyMessageDelayed(0, mMillisFirstAutorepeatDelay);
                    }
                }
            }
            return true;
        }
        return false;
    }

    /**
     * @return number of button if touched else -1
     */
    static int checkAllButtons(int aTouchPositionX, int aTouchPositionY,
                               boolean aDoCallbackOnlyForAutorepeatButton) {
        // walk through list of active elements
        for (TouchButton tButton : sButtonList) {
            if (tButton.mIsActive
                    && tButton.checkIfTouchInButton(aTouchPositionX, aTouchPositionY, aDoCallbackOnlyForAutorepeatButton)) {
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
        if (sButtonList != null && !sButtonList.isEmpty()) {
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
    private boolean checkIfTouchInButtonArea(int aTouchPositionX, int aTouchPositionY) {
        return aTouchPositionX >= mPositionX && aTouchPositionX <= mPositionX + mWidth && aTouchPositionY >= mPositionY
                && aTouchPositionY <= (mPositionY + mHeight);
    }

    @SuppressLint("HandlerLeak")
    private final Handler mAutorepeatHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (mRPCView.mTouchIsActive[0]) {
                if (sAutorepeatState != BUTTON_AUTOREPEAT_DISABLED_UNTIL_END_OF_TOUCH) {
                    // beep and send button event
                    if (mDoBeep) {
                        sButtonToneGenerator.startTone(sTouchBeepIndex, sCurrentToneDurationMillis);
                    }
                    mRPCView.mBlueDisplayContext.mSerialService.writeGuiCallbackEvent(SerialService.EVENT_BUTTON_CALLBACK, mListIndex,
                            mCallbackAddress, mValue, mEscapedText);
                    if (sAutorepeatState == BUTTON_AUTOREPEAT_FIRST_PERIOD) {
                        sAutorepeatCount--;
                        if (sAutorepeatCount <= 0) {
                            sAutorepeatState = BUTTON_AUTOREPEAT_SECOND_PERIOD;
                        }
                        sendEmptyMessageDelayed(0, mMillisFirstAutorepeatRate);
                    } else {
                        sendEmptyMessageDelayed(0, mMillisSecondAutorepeatRate);
                    }
                }
            }
        }
    };

    public static void interpretCommand(final RPCView aRPCView, int aCommand,
                                        int[] aParameters, int aParamsLength,
                                        byte[] aDataBytes, int aDataLength) {
        int tButtonNumber = -1; // to have it initialized ;-)
        TouchButton tButton = null;
        String tButtonText = ""; // Always contains a leading space and ends with ", ButtonNr=" + tButtonNumber
        String tString;

        /*
         * Plausi
         */
        if (aCommand != FUNCTION_BUTTON_ACTIVATE_ALL && aCommand != FUNCTION_BUTTON_DEACTIVATE_ALL
                && aCommand != FUNCTION_BUTTON_GLOBAL_SETTINGS && aCommand != FUNCTION_BUTTON_DISABLE_AUTOREPEAT_UNTIL_END_OF_TOUCH) {
            /*
             * We need a button for the command
             */
            if (aParamsLength <= 0) {
                MyLog.e(LOG_TAG,
                        "aParamsLength is <=0 but Command=0x" + Integer.toHexString(aCommand) + " is not one of 0x"
                                + Integer.toHexString(FUNCTION_BUTTON_ACTIVATE_ALL) + ", 0x"
                                + Integer.toHexString(FUNCTION_BUTTON_DEACTIVATE_ALL) + " or 0x"
                                + Integer.toHexString(FUNCTION_BUTTON_GLOBAL_SETTINGS) + " or 0x"
                                + Integer.toHexString(FUNCTION_BUTTON_DISABLE_AUTOREPEAT_UNTIL_END_OF_TOUCH));
                return;
            } else {
                tButtonNumber = aParameters[0];
                if (tButtonNumber >= 0 && tButtonNumber < sButtonList.size()) {
                    // get button for create with existent button number
                    tButton = sButtonList.get(tButtonNumber);
                    if (aCommand != FUNCTION_BUTTON_INIT && (tButton == null || !tButton.mIsInitialized)) {
                        MyLog.e(LOG_TAG, "Command=0x" + Integer.toHexString(aCommand) + " ButtonNr=" + tButtonNumber
                                + " is null or not initialized.");
                        return;
                    }
                    tButtonText = " \"" + tButton.mEscapedText + "\". ButtonNr=" + tButtonNumber;

                } else if (aCommand != FUNCTION_BUTTON_INIT) {
                    MyLog.e(LOG_TAG, "Command=0x" + Integer.toHexString(aCommand) + " ButtonNr=" + tButtonNumber
                            + " not found. Only " + sButtonList.size() + " buttons created.");
                    return;
                }
            }
        }

        switch (aCommand) {

            case FUNCTION_BUTTON_ACTIVATE_ALL:
                if (MyLog.isINFO()) {
                    MyLog.i(LOG_TAG, "Activate all buttons");
                }
                activateAllButtons();
                break;

            case FUNCTION_BUTTON_DEACTIVATE_ALL:
                if (MyLog.isINFO()) {
                    MyLog.i(LOG_TAG, "Deactivate all buttons");
                }
                deactivateAllButtons();
                break;

            case FUNCTION_BUTTON_DISABLE_AUTOREPEAT_UNTIL_END_OF_TOUCH:
                if (MyLog.isINFO()) {
                    MyLog.i(LOG_TAG, "Disable autorepeat until end of touch");
                }
                sAutorepeatState = BUTTON_AUTOREPEAT_DISABLED_UNTIL_END_OF_TOUCH;
                break;

            case FUNCTION_BUTTON_GLOBAL_SETTINGS:
                if ((aParameters[0] & FLAG_BUTTON_GLOBAL_USE_UP_EVENTS_FOR_BUTTONS) != 0) {
                    if (aRPCView.mTouchIsActive[0] && !aRPCView.mUseUpEventForButtons) {
                        // since we switched mode while button was down
                        aRPCView.mDisableButtonUpOnce = true;
                    }
                    aRPCView.mUseUpEventForButtons = true;
                } else {
                    aRPCView.mUseUpEventForButtons = false;
                }

                String tInfoString = "";
                if ((aParameters[0] & FLAG_BUTTON_GLOBAL_SET_BEEP_TONE) != 0) {
                    if (aParamsLength > 1) {
                        // set Tone
                        if (aParamsLength > 2) {
                            /*
                             * set duration in ms
                             */
                            sCurrentToneDurationMillis = aParameters[2];
                            if (aParamsLength > 3) {
                                /*
                                 * set absolute value of volume
                                 */
                                int tVolume = aParameters[3];
                                if (tVolume > ToneGenerator.MAX_VOLUME) {
                                    tVolume = ToneGenerator.MAX_VOLUME;
                                }
                                if (tVolume >= 0 && tVolume != sLastRequestedToneVolume) {
                                    sLastRequestedToneVolume = tVolume;
                                    sButtonToneGenerator = new ToneGenerator(AudioManager.STREAM_SYSTEM, tVolume);
                                }
                            } else {
                                /*
                                 * set to user defined value of volume
                                 */
                                int tCurrentSystemVolume = aRPCView.mBlueDisplayContext.mAudioManager
                                        .getStreamVolume(AudioManager.STREAM_SYSTEM);
                                sButtonToneGenerator = new ToneGenerator(AudioManager.STREAM_SYSTEM,
                                        (tCurrentSystemVolume * ToneGenerator.MAX_VOLUME)
                                                / aRPCView.mBlueDisplayContext.mMaxSystemVolume);
                            }
                        }
                        if (aParameters[1] > 0 && aParameters[1] < ToneGenerator.TONE_CDMA_SIGNAL_OFF) {
                            sTouchBeepIndex = aParameters[1];
                        }
                        if (MyLog.isINFO()) {
                            tInfoString = " Touch tone volume=" + sLastRequestedToneVolume + ", duration=" + sCurrentToneDurationMillis + "ms, index=" + sTouchBeepIndex;
                        }
                    }
                }
                if (MyLog.isINFO()) {
                    tString = "";
                    if (aRPCView.mUseUpEventForButtons) {
                        tString = " UseUpEventForButtons=";
                    }
                    MyLog.i(LOG_TAG, "Global settings. Flags=0x" + Integer.toHexString(aParameters[0]) + tString
                            + aRPCView.mUseUpEventForButtons + "." + tInfoString);
                }
                break;

            case FUNCTION_BUTTON_DRAW:
                if (MyLog.isINFO()) {
                    MyLog.i(LOG_TAG, "Draw button" + tButtonText); // tButton text always starts with a space :-)
                }
                tButton.drawButton();
                break;

            case FUNCTION_BUTTON_REMOVE:
                int tBackgroundColor = RPCView.shortToLongColor(aParameters[1]);
                if (MyLog.isINFO()) {
                    MyLog.i(LOG_TAG, "Remove button, background color=" + RPCView.shortToColorString(aParameters[1]) + " for"
                            + tButtonText);
                }
                tButton.removeButton(tBackgroundColor);
                break;

            case FUNCTION_BUTTON_DRAW_TEXT:
                if (MyLog.isINFO()) {
                    MyLog.i(LOG_TAG, "Draw text=" + tButtonText);
                }
                tButton.drawText();
                break;

            case FUNCTION_BUTTON_SET_TEXT:
            case FUNCTION_BUTTON_SET_TEXT_AND_DRAW_BUTTON:
                aRPCView.myConvertChars(aDataBytes, RPCView.sCharsArray, aDataLength);
                tString = new String(RPCView.sCharsArray, 0, aDataLength);
                tButton.mRawTextForValueFalse = tString; // store it as value for false for use at red green button
                tButton.handleText(tString);

                if (MyLog.isINFO()) {
                    MyLog.i(LOG_TAG, "Set text \"" + tButton.mEscapedText + "\" for" + tButtonText);
                }
                if (aCommand == FUNCTION_BUTTON_SET_TEXT_AND_DRAW_BUTTON) {
                    tButton.drawButton();
                }
                break;

            case FUNCTION_BUTTON_SET_TEXT_FOR_VALUE_TRUE:
                // This implicitly changes button to red/green type
                tButton.mIsRedGreen = true;

                aRPCView.myConvertChars(aDataBytes, RPCView.sCharsArray, aDataLength);
                tString = new String(RPCView.sCharsArray, 0, aDataLength);
                tButton.mRawTextForValueTrue = tString;

                if (tButton.mValue != 0) {
                    // set right text position etc. if value is already true
                    tButton.mValue = 1;
                    tButton.handleText(tString);
                }
                if (MyLog.isINFO()) {
                    String tEscapedText = tString.replaceAll("\n", "|");
                    MyLog.i(LOG_TAG, "Set text=\"" + tEscapedText + "\" for value true for" + tButtonText);
                }
                break;

            case FUNCTION_BUTTON_SETTINGS:
                int tSubcommand = aParameters[1];
                switch (tSubcommand) {
                    case SUBFUNCTION_BUTTON_SET_BUTTON_COLOR:
                    case SUBFUNCTION_BUTTON_SET_BUTTON_COLOR_AND_DRAW:
                        tButton.mButtonColor = RPCView.shortToLongColor(aParameters[2]);
                        if (MyLog.isINFO()) {
                            String tFunction = " for";
                            if (tSubcommand == SUBFUNCTION_BUTTON_SET_BUTTON_COLOR_AND_DRAW) {
                                tFunction = " and draw for";
                            }
                            MyLog.i(LOG_TAG, "Set button color=" + RPCView.shortToColorString(aParameters[2]) + tFunction + tButtonText);

                        }
                        if (tSubcommand == SUBFUNCTION_BUTTON_SET_BUTTON_COLOR_AND_DRAW) {
                            tButton.drawButton();
                        }
                        break;

                    case SUBFUNCTION_BUTTON_SET_TEXT_COLOR_AND_DRAW:
                    case SUBFUNCTION_BUTTON_SET_TEXT_COLOR:
                        tButton.mTextColor = RPCView.shortToLongColor(aParameters[2]);
                        if (MyLog.isINFO()) {
                            MyLog.i(LOG_TAG, "Set text color= " + RPCView.shortToColorString(aParameters[2]) + " for" + tButtonText);
                        }
                        if (tSubcommand == SUBFUNCTION_BUTTON_SET_TEXT_COLOR_AND_DRAW) {
                            tButton.drawButton();
                        }
                        break;

                    case SUBFUNCTION_BUTTON_SET_VALUE:
                    case SUBFUNCTION_BUTTON_SET_VALUE_AND_DRAW:
                        tButton.mValue = aParameters[2] & 0x0000FFFF;
                        if (aParamsLength == 4) {
                            tButton.mValue = tButton.mValue | (aParameters[3] << 16);
                        }
                        if (MyLog.isINFO()) {
                            MyLog.i(LOG_TAG, "Set value=" + aParameters[2] + " for" + tButtonText);
                        }
                        if (tSubcommand == SUBFUNCTION_BUTTON_SET_VALUE_AND_DRAW) {
                            tButton.drawButton();
                        }
                        break;

                    case SUBFUNCTION_BUTTON_SET_POSITION:
                    case SUBFUNCTION_BUTTON_SET_POSITION_AND_DRAW:
                        if (MyLog.isINFO()) {
                            MyLog.i(LOG_TAG, "Set position=" + aParameters[2] + " / " + aParameters[3] + ". " + tButtonText);
                        }
                        tButton.setPosition(aParameters[2], aParameters[3]);
                        // set new text position
                        tButton.positionText();
                        if (tSubcommand == SUBFUNCTION_BUTTON_SET_POSITION_AND_DRAW) {
                            tButton.drawButton();
                        }
                        break;

                    case SUBFUNCTION_BUTTON_SET_COLOR_AND_VALUE:
                    case SUBFUNCTION_BUTTON_SET_COLOR_AND_VALUE_AND_DRAW:
                        tButton.mButtonColor = RPCView.shortToLongColor(aParameters[2]);
                        tButton.mValue = aParameters[3];
                        if (MyLog.isINFO()) {
                            MyLog.i(LOG_TAG, "set color=" + RPCView.shortToColorString(aParameters[2]) + " and value=" + tButton.mValue
                                    + " for" + tButtonText);
                        }
                        if (tSubcommand == SUBFUNCTION_BUTTON_SET_COLOR_AND_VALUE_AND_DRAW) {
                            tButton.drawButton();
                        }
                        break;

                    case SUBFUNCTION_BUTTON_SET_ACTIVE:
                        if (MyLog.isINFO()) {
                            MyLog.i(LOG_TAG, "Set active=true for" + tButtonText);
                        }
                        tButton.mIsActive = true;
                        break;
                    case SUBFUNCTION_BUTTON_RESET_ACTIVE:
                        if (MyLog.isINFO()) {
                            MyLog.i(LOG_TAG, "Set active=false for" + tButtonText);
                        }
                        tButton.mIsActive = false;
                        break;

                    case SUBFUNCTION_BUTTON_SET_AUTOREPEAT_TIMING:
                        /*
                         * Implicitly set button type to autorepeat
                         */
                        tButton.mIsAutorepeatButton = true;
                        if (MyLog.isINFO()) {
                            MyLog.i(LOG_TAG, "Set autorepeat timing. 1.delay=" + aParameters[2] + ", 1.rate=" + aParameters[3]
                                    + ", 1.count=" + aParameters[4] + ", 2.rate=" + aParameters[5] + ". " + tButtonText);
                        }
                        tButton.mMillisFirstAutorepeatDelay = aParameters[2];
                        tButton.mMillisFirstAutorepeatRate = aParameters[3];
                        tButton.mFirstAutorepeatCount = aParameters[4];
                        tButton.mMillisSecondAutorepeatRate = aParameters[5];
                        break;

                    case SUBFUNCTION_BUTTON_SET_FLAGS:
                        tButton.handleFlags(aParameters[2]);
                        if (MyLog.isINFO()) {
                            MyLog.i(LOG_TAG, "Set Options to 0x" + Integer.toHexString(aParameters[2]) + tButtonText);
                        }
                        break;

                    case SUBFUNCTION_BUTTON_SET_CALLBACK:
                        // Output real Arduino function address, since function pointer on Arduino are address_of_function >> 1
                        String tCallbackAddressStringAdjustedForClientDebugging = "";
                        String tOldCallbackAddressStringAdjustedForClientDebugging;

                        int tCallbackAddress = aParameters[2] & 0x0000FFFF;
                        if (aParamsLength == 4) {
                            // 32 bit callback address
                            tCallbackAddress = tCallbackAddress | (aParameters[3] << 16);
                        } else {
                            // 16 bit Arduino / AVR address
                            tCallbackAddressStringAdjustedForClientDebugging = "/0x" + Integer.toHexString(tCallbackAddress << 1);
                        }
                        tOldCallbackAddressStringAdjustedForClientDebugging = "/0x" + Integer.toHexString(tButton.mCallbackAddress << 1);

                        if (MyLog.isINFO()) {
                            MyLog.i(LOG_TAG,
                                    "Set callback from 0x" + Integer.toHexString(tButton.mCallbackAddress)
                                            + tOldCallbackAddressStringAdjustedForClientDebugging + " to 0x"
                                            + Integer.toHexString(tCallbackAddress) + tCallbackAddressStringAdjustedForClientDebugging
                                            + ". " + tButtonText);
                        }
                        tButton.mCallbackAddress = tCallbackAddress;
                        break;

                    default:
                        MyLog.w(LOG_TAG, "Unknown button settings subfunction 0x" + Integer.toHexString(tSubcommand)
                                + "  received. aParameters[2]=" + aParameters[2] + ", aParameters[3]=" + aParameters[3]
                                + tButtonText);
                        break;
                }
                break;

            case FUNCTION_BUTTON_INIT:
                aRPCView.myConvertChars(aDataBytes, RPCView.sCharsArray, aDataLength);
                tButtonText = new String(RPCView.sCharsArray, 0, aDataLength);
                int tCallbackAddress;
                String tCallbackAddressStringAdjustedForClientDebugging = "";
                if (aParamsLength == 9) {
                    // 16 bit callback address
                    // pre 3.2.5 parameters
                    tCallbackAddress = aParameters[8] & 0x0000FFFF;
                } else {
                    // 16 bit callback address
                    tCallbackAddress = aParameters[9] & 0x0000FFFF;
                }
                if (aParamsLength == 11) {
                    // 32 bit callback address
                    tCallbackAddress = tCallbackAddress | (aParameters[10] << 16);
                } else {
                    // Output real Arduino function address, since function pointer on Arduino are address_of_function >> 1
                    // 16 bit Arduino / AVR address
                    tCallbackAddressStringAdjustedForClientDebugging = "/avr=0x" + Integer.toHexString(tCallbackAddress << 1);
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
                        if (MyLog.isDEBUG()) {
                            Log.d(LOG_TAG, "Button with index " + tButtonNumber + " appended at end of list. List size now "
                                    + sButtonList.size());
                        }
                    }
                    tButton.mListIndex = tButtonNumber;
                }
                // new Parameter set since version 3.2.5
                tButton.initButton(aRPCView, aParameters[1], aParameters[2], aParameters[3], aParameters[4],
                        RPCView.shortToLongColor(aParameters[5]), tButtonText, aParameters[6], aParameters[7], aParameters[8],
                        tCallbackAddress);

                if (MyLog.isINFO()) {
                    MyLog.i(LOG_TAG,
                            "Init button. ButtonNr=" + tButtonNumber + ", init(\"" + tButton.mEscapedText + "\", x="
                                    + aParameters[1] + ", y=" + aParameters[2] + ", width=" + aParameters[3] + ", height="
                                    + aParameters[4] + ", color=" + RPCView.shortToColorString(aParameters[5]) + ", size="
                                    + aParameters[6] + ", flags=" + Integer.toHexString(aParameters[7]) + ", value="
                                    + aParameters[8] + ", callback=0x" + Integer.toHexString(tCallbackAddress)
                                    + tCallbackAddressStringAdjustedForClientDebugging + ") ListSize=" + sButtonList.size());
                }
                break;

            default:
                MyLog.e(LOG_TAG, "unknown command 0x" + Integer.toHexString(aCommand) + " received. paramsLength=" + aParamsLength
                        + " dataLength=" + aDataLength);
                break;
        }
    }
}
