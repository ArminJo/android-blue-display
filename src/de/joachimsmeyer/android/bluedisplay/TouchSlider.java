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

    int mSliderColor; // Color of border
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
        int mMargin = mRPCView.mRequestedCanvasHeight / 60; // distance from slider
        boolean mAbove = false;
        int mAlign = FLAG_SLIDER_CAPTION_ALIGN_MIDDLE;
        int mColor = Color.BLACK;
        int mBackgroundColor = Color.WHITE;

        boolean mPositionsInvalid = true; // Values or caption text have changed, need to recompute position values
        int mPositionX; // resulting X positions computed from the above values and the text content
        int mPositionY; // resulting Y position
    }

    String mCaption;
    TextLayoutInfo mCaptionLayoutInfo;
    String mValueUnitString;
    String mValueFormatString;
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
    // if set, value is printed along with change of bar
    private static final int FLAG_SLIDER_SHOW_VALUE = 0x02;
    private static final int FLAG_SLIDER_IS_HORIZONTAL = 0x04;
    private static final int FLAG_SLIDER_IS_INVERSE = 0x08;
    // if set, bar (+ ASCII) value will only be set by callback handler, not by touch
    private static final int FLAG_SLIDER_VALUE_BY_CALLBACK = 0x10;
    private static final int FLAG_SLIDER_IS_ONLY_OUTPUT = 0x20;

    int mCurrentTouchValue;
    // mCurrentValue is as delivered by client. Before internal use they must be scaled by mScaleFactor
    // This value can be different from mCurrentTouchValue and is provided by callback handler
    int mCurrentValue;
    int mThresholdValue;
    float mScaleFactor; // Scale factor for size. 2 means the slider behaves like one which is 2 times larger - default is 1

    int mSliderNumber; // index in sSliderList - to identify slider while debugging
    int mOnChangeHandlerCallbackAddress;
    boolean mIsActive;
    boolean mIsInitialized;

    static int sDefaultBorderColor = Color.BLUE;
    static int sDefaultBackgroundColor = Color.WHITE;
    static int sDefaultThresholdColor = Color.RED;
    static int sDefaultBarColor = Color.GREEN; // not used yet, just a suggestion

    private static final int SLIDER_LIST_INITIAL_SIZE = 10;
    private static List<TouchSlider> sSliderList = new ArrayList<TouchSlider>(SLIDER_LIST_INITIAL_SIZE);

    private static final int FUNCTION_SLIDER_INIT = 0x50;
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
    private static final int SUBFUNCTION_SLIDER_SET_SCALE_FACTOR = 0x07;

    private static final int SUBFUNCTION_SLIDER_SET_CAPTION_PROPERTIES = 0x08;
    private static final int SUBFUNCTION_SLIDER_SET_PRINT_VALUE_PROPERTIES = 0x09;

    private static final int SUBFUNCTION_SLIDER_SET_VALUE = 0x0C;

    private static final int SUBFUNCTION_SLIDER_SET_CALLBACK = 0x20;

    // static functions
    private static final int FUNCTION_SLIDER_ACTIVATE_ALL = 0x58;
    private static final int FUNCTION_SLIDER_DEACTIVATE_ALL = 0x59;
    private static final int FUNCTION_SLIDER_GLOBAL_SETTINGS = 0x5A;

    // Flags for SLIDER_GLOBAL_SETTINGS
    private static final int SUBFUNCTION_SLIDER_SET_DEFAULT_COLOR_THRESHOLD = 0x01;

    // Function with variable data size
    private static final int FUNCTION_SLIDER_SET_CAPTION = 0x78;
    private static final int FUNCTION_SLIDER_PRINT_VALUE = 0x79;
    private static final int FUNCTION_SLIDER_SET_VALUE_UNIT_STRING = 0x7A;
    private static final int FUNCTION_SLIDER_SET_VALUE_FORMAT_STRING = 0x7B;

    TouchSlider() {
        mIsInitialized = false;
        mTouchBorder = 4;
    }

    /**
     * Static convenience method - reset slider list
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

        mCurrentValue = aInitalValue;
        if (aInitalValue < 0) {
            mCurrentValue = -aInitalValue;
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
            mSliderColor = sDefaultBorderColor; // is Color.BLUE - not used in this case
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
        // use different defaults for caption
        mCaptionLayoutInfo.mAbove = true;
        mCaptionLayoutInfo.mSize = mRPCView.mRequestedCanvasHeight / 12;

        mScaleFactor = (float) 1.0;

        setFormatString();

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
        if (mCaption != null) {
            computeTextPositions(mCaptionLayoutInfo, mCaption);
            mRPCView.drawTextWithBackground(mCaptionLayoutInfo.mPositionX, mCaptionLayoutInfo.mPositionY, mCaption,
                    mCaptionLayoutInfo.mSize, mCaptionLayoutInfo.mColor, mCaptionLayoutInfo.mBackgroundColor);
        }
        if ((mOptions & FLAG_SLIDER_SHOW_VALUE) != 0) {
            printCurrentValue();
        }
    }

    /**
     * Sets mValueFormatString according to maximum possible slider value and appends optional unit string E.g. %4dcm
     */
    void setFormatString() {
        float tMaxValue = mScaleFactor * mBarLength;
        int tNumberOfDigits = 2;
        if (tMaxValue > 9999) {
            tNumberOfDigits = 5;
        } else if (tMaxValue > 999) {
            tNumberOfDigits = 4;
        } else if (tMaxValue > 99) {
            tNumberOfDigits = 3;
        }
        mValueFormatString = "%" + tNumberOfDigits + "d";
        if (mValueUnitString != null) {
            mValueFormatString = mValueFormatString + mValueUnitString;
        }
    }

    @SuppressLint("DefaultLocale")
    void printCurrentValue() {
        String tValueString = String.format(mValueFormatString, mCurrentValue);
        printValueString(tValueString);
    }

    void printValueString(String aValueString) {
        computeTextPositions(mValueLayoutInfo, aValueString);
        mRPCView.drawTextWithBackground(mValueLayoutInfo.mPositionX, mValueLayoutInfo.mPositionY, aValueString,
                mValueLayoutInfo.mSize, mValueLayoutInfo.mColor, mValueLayoutInfo.mBackgroundColor);
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
     * (re)draws the middle bar according to current value
     */
    void drawBar() {
        int tCurrentValueScaled = (int) (mCurrentValue / mScaleFactor);
        if (tCurrentValueScaled > mBarLength) {
            tCurrentValueScaled = mBarLength;
        }
        if (tCurrentValueScaled < 0) {
            tCurrentValueScaled = 0;
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
            if (mCurrentValue > mThresholdValue) {
                tBarBackgroundColor = mBarThresholdColor;
            }
            tBarColor = mBarBackgroundColor;
            tCurrentValueScaled = mBarLength - tCurrentValueScaled;
        } else {
            if (mCurrentValue > mThresholdValue) {
                tBarColor = mBarThresholdColor;
            }
        }

        // draw part of background bar, which is visible
        if (tCurrentValueScaled < mBarLength) {
            if ((mOptions & FLAG_SLIDER_IS_HORIZONTAL) != 0) {
                mRPCView.fillRectRel(mPositionX + tShortBorderWidth + tCurrentValueScaled, mPositionY + tLongBorderWidth,
                        mBarLength - tCurrentValueScaled, mBarWidth, tBarBackgroundColor);
            } else {
                mRPCView.fillRectRel(mPositionX + tLongBorderWidth, mPositionY + tShortBorderWidth, mBarWidth, mBarLength
                        - tCurrentValueScaled, tBarBackgroundColor);
            }
        }

        // Draw value bar
        if (tCurrentValueScaled > 0) {
            if ((mOptions & FLAG_SLIDER_IS_HORIZONTAL) != 0) {
                mRPCView.fillRectRel(mPositionX + tShortBorderWidth, mPositionY + tLongBorderWidth, tCurrentValueScaled, mBarWidth,
                        tBarColor);
            } else {
                mRPCView.fillRectRel(mPositionX + tLongBorderWidth, mPositionYBottom - tShortBorderWidth - tCurrentValueScaled + 1,
                        mBarWidth, tCurrentValueScaled, tBarColor);
            }
        }
    }

    void computeTextPositions(TextLayoutInfo aTextLayoutInfo, String aText) {
        if (aText == null || aTextLayoutInfo == null || aTextLayoutInfo.mPositionsInvalid == false) {
            return;
        }
        aTextLayoutInfo.mPositionsInvalid = true;

        int tTextPixelLength = (int) ((RPCView.TEXT_WIDTH_FACTOR * aTextLayoutInfo.mSize * aText.length()) + 0.5);

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
                        + (int) ((RPCView.TEXT_ASCEND_FACTOR * aTextLayoutInfo.mSize) + 0.5);
            } else {
                aTextLayoutInfo.mPositionY = mPositionY + tSliderShortWidth + aTextLayoutInfo.mMargin
                        + (int) ((RPCView.TEXT_ASCEND_FACTOR * aTextLayoutInfo.mSize) + 0.5);
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
                if ((mPositionY - aTextLayoutInfo.mMargin - aTextLayoutInfo.mSize) < 0) {
                    MyLog.w(LOG_TAG, "Not enough space for text \"" + aText + "\" above slider");
                    aTextLayoutInfo.mPositionY = (int) ((RPCView.TEXT_ASCEND_FACTOR * aTextLayoutInfo.mSize) + 0.5);
                } else {
                    aTextLayoutInfo.mPositionY = mPositionY - aTextLayoutInfo.mMargin - aTextLayoutInfo.mSize
                            + (int) ((RPCView.TEXT_ASCEND_FACTOR * aTextLayoutInfo.mSize) + 0.5);
                }
            } else {
                if ((mPositionY + tSliderLongWidth + aTextLayoutInfo.mMargin + aTextLayoutInfo.mSize) > mRPCView.mRequestedCanvasHeight) {
                    MyLog.w(LOG_TAG, "Not enough space for text \"" + aText + "\" below slider");
                    aTextLayoutInfo.mPositionY = (mRPCView.mRequestedCanvasHeight - aTextLayoutInfo.mSize)
                            + (int) ((RPCView.TEXT_ASCEND_FACTOR * aTextLayoutInfo.mSize) + 0.5);
                } else {
                    aTextLayoutInfo.mPositionY = mPositionY + tSliderLongWidth + aTextLayoutInfo.mMargin
                            + (int) ((RPCView.TEXT_ASCEND_FACTOR * aTextLayoutInfo.mSize) + 0.5);
                }
            }
        }
    }

    /*
     * Check if touch event is in slider. If yes - set bar and value call callback function and return true. If no - return false
     */
    boolean checkIfTouchInSlider(final int aTouchPositionX, final int aTouchPositionY, boolean aJustCheck) {
        if ((mOptions & FLAG_SLIDER_IS_ONLY_OUTPUT) != 0 || mOnChangeHandlerCallbackAddress == 0) {
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
        int tCurrentTouchValue;
        if ((mOptions & FLAG_SLIDER_IS_HORIZONTAL) != 0) {
            if (aTouchPositionX < (mPositionX + tShortBorderWidth)) {
                tCurrentTouchValue = 0;
            } else if (aTouchPositionX > (mPositionXRight - tShortBorderWidth)) {
                tCurrentTouchValue = mBarLength;
            } else {
                tCurrentTouchValue = aTouchPositionX - mPositionX - tShortBorderWidth + 1;
            }
        } else {
            if (aTouchPositionY > (mPositionYBottom - tShortBorderWidth)) {
                tCurrentTouchValue = 0;
            } else if (aTouchPositionY < (mPositionY + tShortBorderWidth)) {
                tCurrentTouchValue = mBarLength;
            } else {
                tCurrentTouchValue = mPositionYBottom - tShortBorderWidth - aTouchPositionY + 1;
            }
        }
        if ((mOptions & FLAG_SLIDER_IS_INVERSE) != 0) {
            tCurrentTouchValue = mBarLength - tCurrentTouchValue;
        }

        int tCurrentTouchValueInt = (int) (tCurrentTouchValue * mScaleFactor);

        if (tCurrentTouchValueInt != mCurrentTouchValue) {
            mCurrentTouchValue = tCurrentTouchValueInt;
            // call change handler
            mRPCView.mBlueDisplayContext.mSerialService.writeGuiCallbackEvent(SerialService.EVENT_SLIDER_CALLBACK, mSliderNumber,
                    mOnChangeHandlerCallbackAddress, tCurrentTouchValueInt, mCaption);
            if ((mOptions & FLAG_SLIDER_VALUE_BY_CALLBACK) == 0) {
                // store value and redraw
                mCurrentValue = tCurrentTouchValueInt;
                drawBar();
                if ((mOptions & FLAG_SLIDER_SHOW_VALUE) != 0) {
                    printCurrentValue();
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
                return tSlider.mSliderNumber;
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
     * Static convenience method - activate all sliders (e.g. before switching screen)
     */
    static void activateAllSliders() {
        for (TouchSlider tSlider : sSliderList) {
            if (tSlider != null) {
                tSlider.mIsActive = true;
            }
        }
    }

    /**
     * Static convenience method - deactivate all sliders (e.g. before switching screen)
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
        String tSliderCaption = "";

        /*
         * Plausi
         */
        if (aCommand != FUNCTION_SLIDER_ACTIVATE_ALL && aCommand != FUNCTION_SLIDER_DEACTIVATE_ALL
                && aCommand != FUNCTION_SLIDER_GLOBAL_SETTINGS) {
            /*
             * We need a slider number for the command
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
                    if (aCommand != FUNCTION_SLIDER_INIT && (tSlider == null || !tSlider.mIsInitialized)) {
                        MyLog.e(LOG_TAG, "Command=0x" + Integer.toHexString(aCommand) + " SliderNr=" + tSliderNumber
                                + " is null or not initialized.");
                        return;
                    }
                    if (MyLog.isINFO()) {
                        if (tSlider.mCaption != null) {
                            tSliderCaption = ". \"" + tSlider.mCaption + "\" SliderNr=";
                        } else {
                            tSliderCaption = ". SliderNr=";
                        }
                    }
                } else if (aCommand != FUNCTION_SLIDER_INIT) {
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
                    MyLog.i(LOG_TAG, "Draw slider" + tSliderCaption + tSliderNumber);
                }
                tSlider.drawSlider();
                break;

            case FUNCTION_SLIDER_DRAW_BORDER:
                if (MyLog.isINFO()) {
                    MyLog.i(LOG_TAG, "Draw border" + tSliderCaption + tSliderNumber);
                }
                tSlider.drawBorder();
                break;

            case FUNCTION_SLIDER_SET_CAPTION:
                aRPCView.myConvertChars(aDataBytes, RPCView.sCharsArray, aDataLength);
                tSlider.mCaption = new String(RPCView.sCharsArray, 0, aDataLength);

                if (MyLog.isINFO()) {
                    MyLog.i(LOG_TAG, "Set caption=\"" + tSlider.mCaption + "\"" + tSliderCaption + tSliderNumber);
                }
                break;

            case FUNCTION_SLIDER_SET_VALUE_UNIT_STRING:
                aRPCView.myConvertChars(aDataBytes, RPCView.sCharsArray, aDataLength);
                tSlider.mValueUnitString = new String(RPCView.sCharsArray, 0, aDataLength);
                tSlider.setFormatString();

                if (MyLog.isINFO()) {
                    MyLog.i(LOG_TAG, "Set ValueUnitString=\"" + tSlider.mValueUnitString + "\" -> \"" + tSlider.mValueFormatString
                            + "\"" + tSliderCaption + tSliderNumber);
                }
                break;

            case FUNCTION_SLIDER_SET_VALUE_FORMAT_STRING:
                aRPCView.myConvertChars(aDataBytes, RPCView.sCharsArray, aDataLength);
                tSlider.mValueFormatString = new String(RPCView.sCharsArray, 0, aDataLength);

                if (MyLog.isINFO()) {
                    MyLog.i(LOG_TAG, "Set ValueFormatString=\"" + tSlider.mValueFormatString + "\"" + tSliderCaption
                            + tSliderNumber);
                }
                break;

            case FUNCTION_SLIDER_PRINT_VALUE:
                aRPCView.myConvertChars(aDataBytes, RPCView.sCharsArray, aDataLength);
                String tValueString = new String(RPCView.sCharsArray, 0, aDataLength);

                if (tSlider.mValueLayoutInfo != null) {
                    tSlider.printValueString(tValueString);
                    if (MyLog.isINFO()) {
                        MyLog.i(LOG_TAG, "Print value=\"" + tValueString + "\"" + tSliderCaption + tSliderNumber);
                    }
                } else {
                    MyLog.e(LOG_TAG, "Print value=\"" + tValueString + "\"" + tSliderCaption + tSliderNumber
                            + " failed. No print properties set");
                }
                break;

            case FUNCTION_SLIDER_SETTINGS:
                int tSubcommand = aParameters[1];
                switch (tSubcommand) {
                case SUBFUNCTION_SLIDER_SET_COLOR_THRESHOLD:
                    tSlider.mBarThresholdColor = RPCView.shortToLongColor(aParameters[2]);
                    if (MyLog.isINFO()) {
                        MyLog.i(LOG_TAG, "Set threshold color= " + RPCView.shortToColorString(aParameters[2]) + tSliderCaption
                                + tSliderNumber);
                    }
                    break;

                case SUBFUNCTION_SLIDER_SET_COLOR_BAR_BACKGROUND:
                    tSlider.mBarBackgroundColor = RPCView.shortToLongColor(aParameters[2]);
                    if (MyLog.isINFO()) {
                        MyLog.i(LOG_TAG, "Set bar background color= " + RPCView.shortToColorString(aParameters[2]) + tSliderCaption
                                + tSliderNumber);
                    }
                    break;

                case SUBFUNCTION_SLIDER_SET_COLOR_BAR:
                    tSlider.mBarColor = RPCView.shortToLongColor(aParameters[2]);
                    if (MyLog.isINFO()) {
                        MyLog.i(LOG_TAG, "Set bar color= " + RPCView.shortToColorString(aParameters[2]) + tSliderCaption
                                + tSliderNumber);
                    }
                    break;

                case SUBFUNCTION_SLIDER_SET_VALUE_AND_DRAW_BAR:
                case SUBFUNCTION_SLIDER_SET_VALUE:
                    String tFunction = "";
                    tSlider.mCurrentValue = aParameters[2];
                    if (tSubcommand == SUBFUNCTION_SLIDER_SET_VALUE_AND_DRAW_BAR) {
                        tSlider.drawBar();
                        tFunction = " and draw bar";
                        if ((tSlider.mOptions & FLAG_SLIDER_SHOW_VALUE) != 0) {
                            tSlider.printCurrentValue();
                        }
                    }
                    // Log on debug level, because it may be called very often
                    if (MyLog.isDEBUG()) {
                        MyLog.d(LOG_TAG, "Set value=" + aParameters[2] + tFunction + tSliderCaption + tSliderNumber);
                    }
                    break;

                case SUBFUNCTION_SLIDER_SET_POSITION:
                    tSlider.mPositionX = aParameters[2];
                    tSlider.mPositionY = aParameters[3];
                    if (MyLog.isINFO()) {
                        MyLog.i(LOG_TAG, "Set position=" + tSlider.mPositionX + " / " + tSlider.mPositionY + tSliderCaption
                                + tSliderNumber);
                    }
                    break;

                case SUBFUNCTION_SLIDER_SET_ACTIVE:
                    if (MyLog.isINFO()) {
                        MyLog.i(LOG_TAG, "Set active=true" + tSliderCaption + tSliderNumber);
                    }
                    tSlider.mIsActive = true;
                    break;

                case SUBFUNCTION_SLIDER_RESET_ACTIVE:
                    if (MyLog.isINFO()) {
                        MyLog.i(LOG_TAG, "Set active=false" + tSliderCaption + tSliderNumber);
                    }
                    tSlider.mIsActive = false;
                    break;

                case SUBFUNCTION_SLIDER_SET_CAPTION_PROPERTIES:
                case SUBFUNCTION_SLIDER_SET_PRINT_VALUE_PROPERTIES:
                    TextLayoutInfo tLayoutInfo;

                    if (tSubcommand == SUBFUNCTION_SLIDER_SET_CAPTION_PROPERTIES) {
                        tFunction = "caption";
                        tLayoutInfo = tSlider.mCaptionLayoutInfo;
                        tLayoutInfo.mPositionsInvalid = true;
                    } else {
                        tFunction = "print value";
                        tLayoutInfo = tSlider.mValueLayoutInfo;
                        tLayoutInfo.mPositionsInvalid = true;
                    }

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

                    if (MyLog.isINFO()) {
                        MyLog.i(LOG_TAG,
                                "Set " + tFunction + " properties size=" + tLayoutInfo.mSize + " position=" + tAbove + " align="
                                        + tAlign + " margin=" + tLayoutInfo.mMargin + " color="
                                        + RPCView.shortToColorString(aParameters[5]) + " background color="
                                        + RPCView.shortToColorString(aParameters[6]) + tSliderCaption + tSliderNumber);
                    }
                    break;

                case SUBFUNCTION_SLIDER_SET_SCALE_FACTOR:
                    // convert 2 short parameters to one float
                    int tFloat = aParameters[2] & 0xFFFF;
                    tFloat = tFloat | ((aParameters[3] & 0xFFFF) << 16);
                    float tNewScaleFactor = Float.intBitsToFloat(tFloat);
                    if (tNewScaleFactor < 0.001) {
                        MyLog.e(LOG_TAG, "New scale factor " + tNewScaleFactor + " seems to be invalid. Do not change old factor "
                                + tSlider.mScaleFactor + tSliderCaption + tSliderNumber);
                    } else {
                        if (MyLog.isINFO()) {
                            MyLog.i(LOG_TAG, "Set value scale factor from " + tSlider.mScaleFactor + " to " + tNewScaleFactor
                                    + tSliderCaption + tSliderNumber);
                        }
                        tSlider.mScaleFactor = tNewScaleFactor;
                        // do not modify mCurrentValue, since it was specified with scaling in mind!
                        // tSlider.mCurrentValue *= tNewScaleFactor;
                    }
                    break;

                case SUBFUNCTION_SLIDER_SET_CALLBACK:
                    // Output real Arduino function address, since function pointer on Arduino are address_of_function >> 1
                    String tCallbackAddressStringAdjustedForClientDebugging = "";
                    String tOldCallbackAddressStringAdjustedForClientDebugging = "";

                    int tCallbackAddress = aParameters[2] & 0x0000FFFF;
                    if (aParamsLength == 4) {
                        // 32 bit callback address
                        tCallbackAddress = tCallbackAddress | (aParameters[3] << 16);
                    } else {
                        // 16 bit Arduino / AVR address
                        tCallbackAddressStringAdjustedForClientDebugging = "/0x" + Integer.toHexString(tCallbackAddress << 1);
                        tOldCallbackAddressStringAdjustedForClientDebugging = "/0x"
                                + Integer.toHexString(tSlider.mOnChangeHandlerCallbackAddress << 1);
                    }

                    if (MyLog.isINFO()) {
                        MyLog.i(LOG_TAG,
                                "Set callback from 0x" + Integer.toHexString(tSlider.mOnChangeHandlerCallbackAddress)
                                        + tOldCallbackAddressStringAdjustedForClientDebugging + " to 0x"
                                        + Integer.toHexString(tCallbackAddress) + tCallbackAddressStringAdjustedForClientDebugging
                                        + tSliderCaption + tSliderNumber);
                    }
                    tSlider.mOnChangeHandlerCallbackAddress = tCallbackAddress;

                    break;
                }
                break;

            case FUNCTION_SLIDER_GLOBAL_SETTINGS:
                tSubcommand = aParameters[0];
                switch (tSubcommand) {
                case SUBFUNCTION_SLIDER_SET_DEFAULT_COLOR_THRESHOLD:
                    sDefaultThresholdColor = RPCView.shortToLongColor(aParameters[1]);
                    if (MyLog.isINFO()) {
                        MyLog.i(LOG_TAG, "Set default threshold color= " + RPCView.shortToColorString(aParameters[1]));
                    }
                    break;
                }
                break;

            case FUNCTION_SLIDER_INIT:
                int tOnChangeHandlerCallbackAddress = aParameters[10] & 0x0000FFFF;
                // Output real Arduino function address, since function pointer on Arduino are address_of_function >> 1
                String tCallbackAddressStringAdjustedForClientDebugging = "";
                if (aParamsLength == 12) {
                    // 32 bit callback address
                    tOnChangeHandlerCallbackAddress = tOnChangeHandlerCallbackAddress | (aParameters[11] << 16);
                } else {
                    tCallbackAddressStringAdjustedForClientDebugging = "/0x"
                            + Integer.toHexString(tOnChangeHandlerCallbackAddress << 1);
                }

                if (tSlider == null) {
                    /*
                     * create new slider
                     */
                    tSlider = new TouchSlider();
                    if (tSliderNumber < sSliderList.size()) {
                        // overwrite existing (old) slider
                        sSliderList.set(tSliderNumber, tSlider);
                    } else {
                        tSliderNumber = sSliderList.size();
                        sSliderList.add(tSlider);
                        if (MyLog.isDEBUG()) {
                            MyLog.d(LOG_TAG, "Slider with index " + tSliderNumber + " appended at end of list. List size now "
                                    + sSliderList.size());
                        }
                    }
                    tSlider.mSliderNumber = tSliderNumber;
                }

                if (MyLog.isINFO()) {
                    MyLog.i(LOG_TAG,
                            "Init slider. SliderNr=" + tSliderNumber + ", init(x=" + aParameters[1] + ", y=" + aParameters[2]
                                    + ", width=" + aParameters[3] + ", length=" + aParameters[4] + ", threshold=" + aParameters[5]
                                    + ", initial=" + aParameters[6] + ", color= " + RPCView.shortToColorString(aParameters[7])
                                    + ", bar color= " + RPCView.shortToColorString(aParameters[8]) + ", options=0x"
                                    + Integer.toHexString(aParameters[9]) + ", callback=0x"
                                    + Integer.toHexString(tOnChangeHandlerCallbackAddress)
                                    + tCallbackAddressStringAdjustedForClientDebugging + ")");
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