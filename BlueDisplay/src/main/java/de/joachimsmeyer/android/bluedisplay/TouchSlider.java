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

import android.annotation.SuppressLint;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.List;

public class TouchSlider {

    private static final String LOG_TAG = "SL";

    RPCView mRPCView;

    int mBorderColor;
    int mBarColor;
    int mBarBackgroundColor;
    int mBarThresholdColor;

    int mPositionX; // position of leftmost pixel of slider
    int mPositionXRight; // position of rightmost pixel of slider
    int mPositionY; // position of uppermost pixel of slider
    int mPositionYBottom; // position of lowest pixel of slider
    int mBarWidth; // width of bar (and main borders) in pixel
    int mShortBorderWidth; // mBarWidth / 4
    int mDefaultValueMargin; // mBarWidth / 6
    int mLongBorderWidth; // mBarWidth / 4
    int mBarLength; // size of slider bar in pixel = maximum slider value
    int mTouchAcceptanceBorder; // border in pixel, where touches outside slider are recognized - default is 4

    private class TextLayoutInfo {
        int mSize; // text size
        int mMargin; // distance from slider
        boolean mTakeDefaultMargin = true; // Caption mBarWidth / 6 below slider and value the same below caption
        boolean mAbove = false;
        int mAlign = FLAG_SLIDER_VALUE_CAPTION_ALIGN_MIDDLE;
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
    private static final int FLAG_SLIDER_VALUE_CAPTION_ABOVE = 0x04;

    // do not interpret the margin value
    private static final int FLAG_SLIDER_VALUE_CAPTION_TAKE_DEFAULT_MARGIN = 0x08;

    // private static final int SLIDER_VALUE_CAPTION_ALIGN_LEFT = 0x00;
    private static final int FLAG_SLIDER_VALUE_CAPTION_ALIGN_RIGHT = 0x01;
    private static final int FLAG_SLIDER_VALUE_CAPTION_ALIGN_MIDDLE = 0x02;
    private static final int FLAG_SLIDER_VALUE_CAPTION_ALIGN_MASK = 0x03;

    int mOptions;
    // constants for options / flags
    private static final int FLAG_SLIDER_SHOW_BORDER = 0x01;
    // if set, value is printed along with change of bar
    private static final int FLAG_SLIDER_SHOW_VALUE = 0x02;
    private static final int FLAG_SLIDER_IS_HORIZONTAL = 0x04;
    private static final int FLAG_SLIDER_IS_INVERSE = 0x08;
    // if set, bar (+ ASCII) value will only be set by callback handler, not by touch
    private static final int FLAG_SLIDER_VALUE_BY_CALLBACK = 0x10;
    private static final int FLAG_SLIDER_IS_ONLY_OUTPUT = 0x20; // No slider aOnChangeHandler available on the client

    int mCurrentTouchValue; // Value of slider for use at client. It is physical length adjusted by mMaxValue and mMinValue
    /*
     * mCurrentValue is as delivered by client. Before internal use it must be adjusted to mMaxValue and mMinValue
     * This value can be different from mCurrentTouchValue and is provided by callback handler
     */
    int mCurrentValue;
    int mThresholdValue;
    float mMaxValue; // The value of slider, if position is left or bottom - default is mBarLength
    float mMinValue; // The value of slider, if position is right or top - default is 0

    int mSliderNumber; // index in sSliderList - to identify slider while debugging
    int mOnChangeHandlerCallbackAddress;
    boolean mIsActive;
    boolean mIsInitialized;

    static int sDefaultBorderColor = Color.BLUE;
    static int sDefaultBackgroundColor = Color.WHITE;
    static int sDefaultThresholdColor = Color.RED;

    private static final int SLIDER_LIST_INITIAL_SIZE = 10;
    private static final List<TouchSlider> sSliderList = new ArrayList<>(SLIDER_LIST_INITIAL_SIZE);

    private static final int FUNCTION_SLIDER_INIT = 0x50;
    private static final int FUNCTION_SLIDER_DRAW = 0x51;
    private static final int FUNCTION_SLIDER_SETTINGS = 0x52;
    private static final int FUNCTION_SLIDER_REMOVE = 0x53;
    private static final int FUNCTION_SLIDER_DRAW_BORDER = 0x54;

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

    private static final int SUBFUNCTION_SLIDER_SET_MIN_MAX = 0x0A;
    private static final int SUBFUNCTION_SLIDER_SET_BORDER_SIZES_AND_COLOR = 0x0B;

    private static final int SUBFUNCTION_SLIDER_SET_VALUE = 0x0C;


    private static final int SUBFUNCTION_SLIDER_SET_CALLBACK = 0x20;
    private static final int SUBFUNCTION_SLIDER_SET_FLAGS = 0x30;

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
        mTouchAcceptanceBorder = 4;
    }

    /**
     * Static convenience method - reset slider list
     */
    static void resetSliders() {
        sSliderList.clear();
    }

    void initSlider(final RPCView aRPCView, final int aPositionX, final int aPositionY, final int aBarWidth, final int aBarLength,
                    final int aThresholdValue, final int aInitalValue, final int aSliderColor, final int aBarColor, final int aOptions,
                    final int aOnChangeHandlerCallbackAddress) {

        mBarThresholdColor = sDefaultThresholdColor;

        mCaption = null;

        mRPCView = aRPCView;
        // Copy parameter
        mPositionX = aPositionX;
        mPositionY = aPositionY;

        mBarColor = aBarColor;

        mOptions = aOptions;
        mBarWidth = aBarWidth;

        mShortBorderWidth = aBarWidth / 4;
        mLongBorderWidth = aBarWidth / 4;
        mDefaultValueMargin = aBarWidth / 6;
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

        if ((mOptions & FLAG_SLIDER_SHOW_BORDER) == 0) {
            /*
             * If no border specified, then take unused slider color as bar background color.
             */
            mBarBackgroundColor = aSliderColor;
            mBorderColor = sDefaultBorderColor; // is Color.BLUE - not used in this case
        } else {
            mBorderColor = aSliderColor;
            mBarBackgroundColor = sDefaultBackgroundColor;
        }

        computeLowerRightCorner();

        /*
         * Set initial layout info
         */
        mValueLayoutInfo = new TextLayoutInfo();
        mCaptionLayoutInfo = new TextLayoutInfo();

        if ((mOptions & FLAG_SLIDER_IS_HORIZONTAL) == 0) {
            // set value size defaults for vertical slider
            mValueLayoutInfo.mSize = mRPCView.mRequestedCanvasHeight / 20;
            mCaptionLayoutInfo.mSize = mRPCView.mRequestedCanvasHeight / 20;
            // use different defaults for caption
            mCaptionLayoutInfo.mAbove = true;
        } else {
            mValueLayoutInfo.mSize = (mBarWidth * 2) / 3;
            mCaptionLayoutInfo.mSize = (mBarWidth * 2) / 3;
        }

        mMaxValue = mBarLength;
        mMinValue = (float) 0.0;
        setFormatString();

        mIsActive = false;
        mIsInitialized = true;
    }

    /*
     * compute lower right corner and validate
     */
    private void computeLowerRightCorner() {

        int tShortBordersAddedWidth = 2 * mShortBorderWidth;
        int tLongBordersAddedWidth = 2 * mLongBorderWidth;
        if ((mOptions & FLAG_SLIDER_SHOW_BORDER) == 0) {
            tLongBordersAddedWidth = 0;
            tShortBordersAddedWidth = 0;
        }

        if ((mOptions & FLAG_SLIDER_IS_HORIZONTAL) != 0) {
            mPositionXRight = mPositionX + mBarLength + tShortBordersAddedWidth - 1; // -1 because it is the last pixel IN slider and not the first outside
            if (mPositionXRight >= mRPCView.mRequestedCanvasWidth) {
                mPositionX = mRPCView.mRequestedCanvasWidth - (mBarLength + tShortBordersAddedWidth);
                MyLog.e(LOG_TAG, "PositionXRight=" + mPositionXRight + " is greater than DisplayWidth="
                        + mRPCView.mRequestedCanvasWidth + ". Set mPositionX to " + mPositionX);
                mPositionXRight = mPositionX + mBarLength + tShortBordersAddedWidth - 1;
            }

            mPositionYBottom = mPositionY + (tLongBordersAddedWidth + mBarWidth) - 1;
            if (mPositionYBottom >= mRPCView.mRequestedCanvasHeight) {
                mPositionY = mRPCView.mRequestedCanvasHeight - (tLongBordersAddedWidth + mBarWidth);
                MyLog.e(LOG_TAG, "PositionYBottom=" + mPositionYBottom + " is greater than DisplayHeight="
                        + mRPCView.mRequestedCanvasHeight + ". Set mPositionY to " + mPositionY);
                mPositionYBottom = mPositionY + (tLongBordersAddedWidth + mBarWidth) - 1;
            }

        } else {
            mPositionXRight = mPositionX + (tLongBordersAddedWidth + mBarWidth) - 1;
            if (mPositionXRight >= mRPCView.mRequestedCanvasWidth) {
                mPositionX = mRPCView.mRequestedCanvasWidth - (tLongBordersAddedWidth + mBarWidth);
                MyLog.e(LOG_TAG, "PositionXRight=" + mPositionXRight + " is greater than DisplayWidth="
                        + mRPCView.mRequestedCanvasWidth + ". Set mPositionX to " + mPositionX);
                mPositionXRight = mPositionX + (tLongBordersAddedWidth + mBarWidth) - 1;
            }

            mPositionYBottom = mPositionY + mBarLength + tShortBordersAddedWidth - 1;
            if (mPositionYBottom >= mRPCView.mRequestedCanvasHeight) {
                mPositionY = mRPCView.mRequestedCanvasHeight - (mBarLength + tShortBordersAddedWidth);
                MyLog.e(LOG_TAG, "PositionYBottom=" + mPositionYBottom + " is greater than DisplayHeight="
                        + mRPCView.mRequestedCanvasHeight + ". Set mPositionY to " + mPositionY);
                mPositionYBottom = mPositionY + mBarLength + tShortBordersAddedWidth - 1;
            }
        }
    }

    void drawSlider() {
        mIsActive = true;
        if ((mOptions & FLAG_SLIDER_SHOW_BORDER) != 0) {
            drawBorder();
        }
        // Fill middle bar with current value
        drawBar();
        if (mCaption != null) {
            computeTextPositions(mCaptionLayoutInfo, mCaption, false);
            mRPCView.drawTextWithBackground(mCaptionLayoutInfo.mPositionX, mCaptionLayoutInfo.mPositionY, mCaption,
                    mCaptionLayoutInfo.mSize, mCaptionLayoutInfo.mColor, mCaptionLayoutInfo.mBackgroundColor);
        }
        if ((mOptions & FLAG_SLIDER_SHOW_VALUE) != 0) {
            printCurrentValue();
        }
    }

    /*
     * Overwrite and deactivate slider, but do not delete it.
     */
    void removeSlider(int tBackgroundColor) {
        // Remove slider with border - works only with + 1 because last "virtual" pixel can be bigger than 1 physical pixel
        mRPCView.fillRect(mPositionX, mPositionY, mPositionXRight + 1, mPositionYBottom + 1, tBackgroundColor);

        if (mCaption != null) {
            // Overwrite caption with text drawn with background color
            mRPCView.drawTextWithBackground(mCaptionLayoutInfo.mPositionX, mCaptionLayoutInfo.mPositionY, mCaption,
                    mCaptionLayoutInfo.mSize, tBackgroundColor, tBackgroundColor);
        }

        if ((mOptions & FLAG_SLIDER_SHOW_VALUE) != 0) {
            /*
             * Overwrite value with max value drawn with background color
             */
            mCurrentValue = (int) mMaxValue;
            mValueLayoutInfo.mColor = tBackgroundColor;
            mValueLayoutInfo.mBackgroundColor = tBackgroundColor;
            printCurrentValue();
        }
        mIsActive = false;
    }

    /**
     * Sets mValueFormatString according to maximum possible slider value and appends optional unit string E.g. %4dcm
     */
    void setFormatString() {
        int tNumberOfDigits = 2;
        if (mMaxValue > 99 || mMinValue < -9) {
            tNumberOfDigits = 3;
        }
        if (mMaxValue > 999 || mMinValue < -99) {
            tNumberOfDigits = 4;
        }
        if (mMaxValue > 9999 || mMinValue < -999) {
            tNumberOfDigits = 5;
        }
        if (mMaxValue > 99999 || mMinValue < -9999) {
            tNumberOfDigits = 6;
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
        computeTextPositions(mValueLayoutInfo, aValueString, true);
        mRPCView.drawTextWithBackground(mValueLayoutInfo.mPositionX, mValueLayoutInfo.mPositionY, aValueString,
                mValueLayoutInfo.mSize, mValueLayoutInfo.mColor, mValueLayoutInfo.mBackgroundColor);
    }

    void drawBorder() {
        if ((mOptions & FLAG_SLIDER_IS_HORIZONTAL) != 0) {
            // Create upper long border
            mRPCView.fillRectRel(mPositionX, mPositionY, mBarLength + (2 * mShortBorderWidth), mLongBorderWidth, mBorderColor);
            // Create lower long border
            mRPCView.fillRectRel(mPositionX, mPositionY + mLongBorderWidth + mBarWidth, mBarLength + (2 * mShortBorderWidth),
                    mLongBorderWidth, mBorderColor);

            // Create left short border (as extension of bar)
            mRPCView.fillRectRel(mPositionX, mPositionY + mLongBorderWidth, mShortBorderWidth, mBarWidth, mBorderColor);
            // Create right short border (as extension of bar)
            mRPCView.fillRectRel(mPositionXRight - mShortBorderWidth + 1, mPositionY + mLongBorderWidth,
                    mShortBorderWidth, mBarWidth, mBorderColor); // +1 because we want the first pixel in border and not the last before border
        } else {
            // Create left long border
            mRPCView.fillRectRel(mPositionX, mPositionY, mLongBorderWidth, mBarLength + (2 * mShortBorderWidth), mBorderColor);
            // Create right long border
            mRPCView.fillRectRel(mPositionX + mLongBorderWidth + mBarWidth, mPositionY, mLongBorderWidth,
                    mBarLength + (2 * mShortBorderWidth), mBorderColor);

            // Create upper short border (as extension of bar)
            mRPCView.fillRectRel(mPositionX + mLongBorderWidth, mPositionY, mBarWidth, mShortBorderWidth, mBorderColor);
            // Create lower short border (as extension of bar)
            mRPCView.fillRectRel(mPositionX + mLongBorderWidth, mPositionYBottom - mShortBorderWidth + 1, mBarWidth,
                    mShortBorderWidth, mBorderColor);
        }
    }

    /*
     * (re)draws the middle bar according to current value
     */
    void drawBar() {
        int tCurrentValueScaled = (int) ((mCurrentValue - mMinValue) * mBarLength / (mMaxValue - mMinValue));
        if (tCurrentValueScaled > mBarLength) {
            tCurrentValueScaled = mBarLength;
        }
        if (tCurrentValueScaled < 0) {
            tCurrentValueScaled = 0;
        }

        int tShortBorderWidth = mShortBorderWidth;
        int tLongBorderWidth = mLongBorderWidth;
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

        // draw part of background bar, which becomes visible
        if (tCurrentValueScaled < mBarLength) {
            if ((mOptions & FLAG_SLIDER_IS_HORIZONTAL) != 0) {
                // Horizontal slider
                mRPCView.fillRectRel(mPositionX + tShortBorderWidth + tCurrentValueScaled, mPositionY + tLongBorderWidth,
                        mBarLength - tCurrentValueScaled, mBarWidth, tBarBackgroundColor);
            } else {
                // Vertical slider
                mRPCView.fillRectRel(mPositionX + tLongBorderWidth, mPositionY + tShortBorderWidth, mBarWidth, mBarLength
                        - tCurrentValueScaled, tBarBackgroundColor);
            }
        }

        // Draw value bar
        if (tCurrentValueScaled > 0) {
            if ((mOptions & FLAG_SLIDER_IS_HORIZONTAL) != 0) {
                // Horizontal slider
                mRPCView.fillRectRel(mPositionX + tShortBorderWidth, mPositionY + tLongBorderWidth, tCurrentValueScaled, mBarWidth,
                        tBarColor);
            } else {
                // Vertical slider
                mRPCView.fillRectRel(mPositionX + tLongBorderWidth, mPositionYBottom - tShortBorderWidth - tCurrentValueScaled + 1,
                        mBarWidth, tCurrentValueScaled, tBarColor);
            }
        }
    }

    void computeTextPositions(TextLayoutInfo aTextLayoutInfo, String aText,
                              boolean isValuePosition) {
        if (aText == null || aTextLayoutInfo == null || !aTextLayoutInfo.mPositionsInvalid) {
            return;
        }
        // aTextLayoutInfo.mPositionsInvalid is true here

        int tMargin = aTextLayoutInfo.mMargin;
        if (aTextLayoutInfo.mTakeDefaultMargin) {
            if (isValuePosition) {
                // Position for value
                if (mCaption == null || aTextLayoutInfo.mAbove != mCaptionLayoutInfo.mAbove) {
                    tMargin = mDefaultValueMargin; // not the same Y position as caption
                } else {
                    // 1 default margin for caption but only half marging between caption and value,
                    // since below the caption text, we have an almost empty font decent
                    tMargin = mCaptionLayoutInfo.mSize + (mDefaultValueMargin) + (mDefaultValueMargin / 2);
                }
            } else {
                // Position for Caption
                tMargin = mDefaultValueMargin;
            }
        }
        int tTextPixelLength = (int) ((RPCView.TEXT_WIDTH_FACTOR * aTextLayoutInfo.mSize * aText.length()) + 0.5);

        int tSliderShortWidth = mBarWidth;
        int tSliderLongWidth = mBarLength;
        if ((mOptions & FLAG_SLIDER_SHOW_BORDER) != 0) {
            tSliderShortWidth = (2 * mLongBorderWidth) + mBarWidth;
            tSliderLongWidth = (2 * mShortBorderWidth) + mBarLength;
        }

        if ((mOptions & FLAG_SLIDER_IS_HORIZONTAL) != 0) {
            // Horizontal slider
            if (aTextLayoutInfo.mAlign == FLAG_SLIDER_VALUE_CAPTION_ALIGN_RIGHT) {
                aTextLayoutInfo.mPositionX = mPositionX + tSliderLongWidth - tTextPixelLength;
            } else if (aTextLayoutInfo.mAlign == FLAG_SLIDER_VALUE_CAPTION_ALIGN_MIDDLE) {
                aTextLayoutInfo.mPositionX = mPositionX + ((tSliderLongWidth - tTextPixelLength) / 2);
            } else {
                aTextLayoutInfo.mPositionX = mPositionX;
            }
            if (aTextLayoutInfo.mAbove) {
                aTextLayoutInfo.mPositionY = mPositionY - tMargin - aTextLayoutInfo.mSize
                        + (int) ((RPCView.TEXT_ASCEND_FACTOR * aTextLayoutInfo.mSize) + 0.5);
            } else {
                aTextLayoutInfo.mPositionY = mPositionY + tSliderShortWidth + tMargin
                        + (int) ((RPCView.TEXT_ASCEND_FACTOR * aTextLayoutInfo.mSize) + 0.5);
            }

        } else {
            // Vertical slider
            if (aTextLayoutInfo.mAlign == FLAG_SLIDER_VALUE_CAPTION_ALIGN_RIGHT) {
                aTextLayoutInfo.mPositionX = mPositionX + tSliderShortWidth - tTextPixelLength;
            } else if (aTextLayoutInfo.mAlign == FLAG_SLIDER_VALUE_CAPTION_ALIGN_MIDDLE) {
                aTextLayoutInfo.mPositionX = mPositionX + ((tSliderShortWidth - tTextPixelLength) / 2);
            } else {
                aTextLayoutInfo.mPositionX = mPositionX;
            }
            if (aTextLayoutInfo.mAbove) {
                if ((mPositionY - tMargin - aTextLayoutInfo.mSize) < 0) {
                    MyLog.w(LOG_TAG, "Not enough space for text \"" + aText + "\" above slider");
                    aTextLayoutInfo.mPositionY = (int) ((RPCView.TEXT_ASCEND_FACTOR * aTextLayoutInfo.mSize) + 0.5);
                } else {
                    aTextLayoutInfo.mPositionY = mPositionY - tMargin - aTextLayoutInfo.mSize
                            + (int) ((RPCView.TEXT_ASCEND_FACTOR * aTextLayoutInfo.mSize) + 0.5);
                }
            } else {
                if ((mPositionY + tSliderLongWidth + tMargin + aTextLayoutInfo.mSize) > mRPCView.mRequestedCanvasHeight) {
                    MyLog.w(LOG_TAG, "Not enough space for text \"" + aText + "\" below slider");
                    aTextLayoutInfo.mPositionY = (mRPCView.mRequestedCanvasHeight - aTextLayoutInfo.mSize)
                            + (int) ((RPCView.TEXT_ASCEND_FACTOR * aTextLayoutInfo.mSize) + 0.5);
                } else {
                    aTextLayoutInfo.mPositionY = mPositionY + tSliderLongWidth + tMargin
                            + (int) ((RPCView.TEXT_ASCEND_FACTOR * aTextLayoutInfo.mSize) + 0.5);
                }
            }
        }
    }

    /*
     * Check if touch event is in slider. If yes - set bar and value call callback function and return true. If no - return false
     */
    boolean checkIfTouchInSlider(final int aTouchPositionX, final int aTouchPositionY,
                                 boolean aJustCheck) {
        if ((mOptions & FLAG_SLIDER_IS_ONLY_OUTPUT) != 0 || mOnChangeHandlerCallbackAddress == 0) {
            return false;
        }
        int tPositionBorderX = mPositionX - mTouchAcceptanceBorder;
        if (tPositionBorderX < 0) {
            tPositionBorderX = 0;
        }
        int tPositionBorderY = mPositionY - mTouchAcceptanceBorder;
        if (tPositionBorderY < 0) {
            tPositionBorderY = 0;
        }
        if (!mIsActive || aTouchPositionX < tPositionBorderX || aTouchPositionX > mPositionXRight + mTouchAcceptanceBorder
                || aTouchPositionY < tPositionBorderY || aTouchPositionY > mPositionYBottom + mTouchAcceptanceBorder) {
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

        int tCurrentTouchValueInt = (int) ((tCurrentTouchValue * (mMaxValue - mMinValue) / mBarLength) + mMinValue);

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
     * @return number of slider if touched else -1
     */
    static int checkAllSliders(int aTouchPositionX, int aTouchPositionY) {
        // walk through list of active elements
        for (TouchSlider tSlider : sSliderList) {
            if (tSlider.mIsActive && tSlider.checkIfTouchInSlider(aTouchPositionX, aTouchPositionY, false)) {
                return tSlider.mSliderNumber;
            }
        }
        return -1;
    }

    static boolean checkIfTouchInSliderNumber(final int aTouchPositionX,
                                              final int aTouchPositionY, final int aSliderNumber) {
        TouchSlider tSlider = sSliderList.get(aSliderNumber);
        if (tSlider.mIsActive) {
            return tSlider.checkIfTouchInSlider(aTouchPositionX, aTouchPositionY, false); // no recursion, but different function
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

    public static void interpretCommand(final RPCView aRPCView, int aCommand,
                                        int[] aParameters, int aParamsLength,
                                        byte[] aDataBytes, int aDataLength) {
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

                case FUNCTION_SLIDER_REMOVE:
                    if (MyLog.isINFO()) {
                        MyLog.i(LOG_TAG, "Remove slider, background color=" + RPCView.shortToColorString(aParameters[1])
                                + tSliderCaption + tSliderNumber);
                    }
                    tSlider.removeSlider(RPCView.shortToLongColor(aParameters[1]));
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
                        MyLog.i(LOG_TAG, "Set ValueUnitString=\"" + tSlider.mValueUnitString + "\" to effective format string \"" + tSlider.mValueFormatString
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
                            tSlider.computeLowerRightCorner();
                            // recompute positions of mCaptionLayoutInfo.
                            if (tSlider.mCaption != null) {
                                tSlider.computeTextPositions(tSlider.mCaptionLayoutInfo, tSlider.mCaption, false);
                            }
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
                            } else {
                                tFunction = "print value";
                                tLayoutInfo = tSlider.mValueLayoutInfo;
                            }
                            tLayoutInfo.mPositionsInvalid = true;

                            tLayoutInfo.mSize = aParameters[2];
                            tLayoutInfo.mTakeDefaultMargin = ((aParameters[3] & FLAG_SLIDER_VALUE_CAPTION_TAKE_DEFAULT_MARGIN) != 0);
                            tLayoutInfo.mAlign = aParameters[3] & FLAG_SLIDER_VALUE_CAPTION_ALIGN_MASK;
                            tLayoutInfo.mMargin = aParameters[4];
                            tLayoutInfo.mColor = RPCView.shortToLongColor(aParameters[5]);
                            tLayoutInfo.mBackgroundColor = RPCView.shortToLongColor(aParameters[6]);
                            String tAlign = "left";
                            if (tLayoutInfo.mAlign == FLAG_SLIDER_VALUE_CAPTION_ALIGN_RIGHT) {
                                tAlign = "right";
                            } else if (tLayoutInfo.mAlign == FLAG_SLIDER_VALUE_CAPTION_ALIGN_MIDDLE) {
                                tAlign = "middle";
                            }

                            String tAbove;
                            if ((aParameters[3] & FLAG_SLIDER_VALUE_CAPTION_ABOVE) != 0) {
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

                        case SUBFUNCTION_SLIDER_SET_SCALE_FACTOR: // modifies only aMaxValue, not aMinValue
                            // convert 2 short parameters to one float
                            int tFloat = aParameters[2] & 0xFFFF;
                            tFloat = tFloat | ((aParameters[3] & 0xFFFF) << 16);
                            float tNewScaleFactor = Float.intBitsToFloat(tFloat);
                            float tOldScaleFactor = ((tSlider.mMaxValue - tSlider.mMinValue) / tSlider.mBarLength);
                            if (tNewScaleFactor < 0.001) {
                                MyLog.e(LOG_TAG, "New scale factor " + tNewScaleFactor + " seems to be invalid. Do not change old factor "
                                        + tOldScaleFactor + tSliderCaption + tSliderNumber);
                            } else {
                                if (MyLog.isINFO()) {
                                    MyLog.i(LOG_TAG, "Set mMaxValue from " + tSlider.mMaxValue + " to " + (tNewScaleFactor * tSlider.mBarLength)
                                            + " and mMinValue to 0 " + tSliderCaption + tSliderNumber);
                                }
                                tSlider.mMaxValue = tNewScaleFactor * tSlider.mMaxValue;
                                tSlider.mMinValue = (float) 0.0;
                                // do not modify mCurrentValue, since it was specified with scaling in mind!
                                // tSlider.mCurrentValue *= tNewScaleFactor;
                            }
                            break;

                        case SUBFUNCTION_SLIDER_SET_MIN_MAX:
                            tSlider.mCurrentValue = (int) ((tSlider.mCurrentValue * (aParameters[3] - aParameters[2])) / (tSlider.mMaxValue - tSlider.mMinValue)) + aParameters[2];
                            tSlider.mMinValue = aParameters[2];
                            tSlider.mMaxValue = aParameters[3];
                            if (MyLog.isINFO()) {
                                MyLog.i(LOG_TAG, "Set MinValue=" + tSlider.mMinValue + ", MaxValue=" + tSlider.mMaxValue + tSliderCaption
                                        + tSliderNumber);
                            }
                            break;

                        case SUBFUNCTION_SLIDER_SET_BORDER_SIZES_AND_COLOR:
                            tSlider.mCurrentValue = (int) ((tSlider.mCurrentValue * (aParameters[3] - aParameters[2])) / (tSlider.mMaxValue - tSlider.mMinValue)) + aParameters[2];
                            tSlider.mLongBorderWidth = aParameters[2];
                            tSlider.mShortBorderWidth = aParameters[3];
                            tSlider.mBorderColor = RPCView.shortToLongColor(aParameters[4]);
                            if (MyLog.isINFO()) {
                                MyLog.i(LOG_TAG, "Set long border width=" + tSlider.mLongBorderWidth + ", short=" + tSlider.mShortBorderWidth
                                        + ", color=" + RPCView.shortToColorString(aParameters[4]) + tSliderCaption + tSliderNumber);
                            }
                            break;

                        case SUBFUNCTION_SLIDER_SET_FLAGS:
                            tSlider.mOptions = aParameters[2];
                            if (MyLog.isINFO()) {
                                MyLog.i(LOG_TAG, "Set Options / Flags to 0x" + Integer.toHexString(tSlider.mOptions) + tSliderCaption + tSliderNumber);
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

                        default:
                            MyLog.w(LOG_TAG, "Unknown slider settings subfunction 0x" + Integer.toHexString(tSubcommand)
                                    + "  received. aParameters[2]=" + aParameters[2] + ", aParameters[3]=" + aParameters[3] + tSliderCaption
                                    + tSliderNumber);
                            break;
                    }
                    break;

                case FUNCTION_SLIDER_GLOBAL_SETTINGS:
                    tSubcommand = aParameters[0];
                    if (tSubcommand == SUBFUNCTION_SLIDER_SET_DEFAULT_COLOR_THRESHOLD) {
                        sDefaultThresholdColor = RPCView.shortToLongColor(aParameters[1]);
                        if (MyLog.isINFO()) {
                            MyLog.i(LOG_TAG, "Set default threshold color= " + RPCView.shortToColorString(aParameters[1]));
                        }
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
                            + " dataLength=" + aDataLength);
                    break;
            }
        } catch (Exception e) {
            MyLog.e(LOG_TAG, "Exception caught for command 0x" + Integer.toHexString(aCommand) + ". paramsLength=" + aParamsLength
                    + " dataLength=" + aDataLength);
        }
    }
}