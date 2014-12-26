/*
 * TouchSlider.cpp
 *
 * Slider which returns value as unsigned byte value
 *
 *  Created on: 30.01.2012
 *      Author: Armin Joachimsmeyer
 *      Email:   armin.joachimsmeyer@gmx.de
 *      License: GPL v3 (http://www.gnu.org/licenses/gpl.html)
 *      Version: 1.0.0
 *
 *
 *  LCD interface used:
 * 		getHeight()
 * 		getWidth()
 * 		fillRect()
 * 		drawText()
 * 		TEXT_SIZE_11_WIDTH
 * 		TEXT_SIZE_11_HEIGHT
 *
 * 	Ram usage:
 * 		15 byte + 39 bytes per slider
 *
 * 	Code size:
 * 		2,8 kByte
 *
 */

#include "TouchSlider.h"
#include <stddef.h> // for NULL
#include <stdio.h> // for printf
#include <string.h> // for strlen
TouchSlider * TouchSlider::sListStart = NULL;
uint16_t TouchSlider::sDefaultSliderColor = TOUCHSLIDER_DEFAULT_SLIDER_COLOR;
uint16_t TouchSlider::sDefaultBarColor = TOUCHSLIDER_DEFAULT_BAR_COLOR;
uint16_t TouchSlider::sDefaultBarThresholdColor = TOUCHSLIDER_DEFAULT_BAR_THRESHOLD_COLOR;
uint16_t TouchSlider::sDefaultBarBackgroundColor = TOUCHSLIDER_DEFAULT_BAR_BACK_COLOR;
uint16_t TouchSlider::sDefaultCaptionColor = TOUCHSLIDER_DEFAULT_CAPTION_COLOR;
uint16_t TouchSlider::sDefaultValueColor = TOUCHSLIDER_DEFAULT_VALUE_COLOR;
uint16_t TouchSlider::sDefaultValueCaptionBackgroundColor = TOUCHSLIDER_DEFAULT_CAPTION_VALUE_BACK_COLOR;

int8_t TouchSlider::sDefaultTouchBorder = TOUCHSLIDER_DEFAULT_TOUCH_BORDER;

TouchSlider::TouchSlider() {
    mDisplay = &BlueDisplay1;

    mBarThresholdColor = sDefaultBarThresholdColor;
    mBarBackgroundColor = sDefaultBarBackgroundColor;
    mCaptionColor = sDefaultCaptionColor;
    mValueColor = sDefaultValueColor;
    mValueCaptionBackgroundColor = sDefaultValueCaptionBackgroundColor;
    mTouchBorder = sDefaultTouchBorder;
    mNextObject = NULL;
    if (sListStart == NULL) {
        // first button
        sListStart = this;
    } else {
        // put object in button list
        TouchSlider * tObjectPointer = sListStart;
        // search last list element
        while (tObjectPointer->mNextObject != NULL) {
            tObjectPointer = tObjectPointer->mNextObject;
        }
        //insert actual button in last element
        tObjectPointer->mNextObject = this;
    }
}

/*
 * Static initialization of slider default colors
 */
void TouchSlider::setDefaults(const int8_t aDefaultTouchBorder, const uint16_t aDefaultSliderColor, const uint16_t aDefaultBarColor,
        const uint16_t aDefaultBarThresholdColor, const uint16_t aDefaultBarBackgroundColor, const uint16_t aDefaultCaptionColor,
        const uint16_t aDefaultValueColor, const uint16_t aDefaultValueCaptionBackgroundColor) {
    sDefaultSliderColor = aDefaultSliderColor;
    sDefaultBarColor = aDefaultBarColor;
    sDefaultBarThresholdColor = aDefaultBarThresholdColor;
    sDefaultBarBackgroundColor = aDefaultBarBackgroundColor;
    sDefaultCaptionColor = aDefaultCaptionColor;
    sDefaultValueColor = aDefaultValueColor;
    sDefaultValueCaptionBackgroundColor = aDefaultValueCaptionBackgroundColor;
    sDefaultTouchBorder = aDefaultTouchBorder;
}

void TouchSlider::setDefaultSliderColor(const uint16_t aDefaultSliderColor) {
    sDefaultSliderColor = aDefaultSliderColor;
}

void TouchSlider::setDefaultBarColor(const uint16_t aDefaultBarColor) {
    sDefaultBarColor = aDefaultBarColor;
}

void TouchSlider::initSliderColors(const uint16_t aSliderColor, const uint16_t aBarColor, const uint16_t aBarThresholdColor,
        const uint16_t aBarBackgroundColor, const uint16_t aCaptionColor, const uint16_t aValueColor,
        const uint16_t aValueCaptionBackgroundColor) {
    mSliderColor = aSliderColor;
    mBarColor = aBarColor;
    mBarThresholdColor = aBarThresholdColor;
    mBarBackgroundColor = aBarBackgroundColor;
    mCaptionColor = aCaptionColor;
    mValueColor = aValueColor;
    mValueCaptionBackgroundColor = aValueCaptionBackgroundColor;
}

void TouchSlider::setValueAndCaptionBackgroundColor(const uint16_t aValueCaptionBackgroundColor) {
    mValueCaptionBackgroundColor = aValueCaptionBackgroundColor;
}

void TouchSlider::setValueColor(const uint16_t aValueColor) {
    mValueColor = aValueColor;
}

/*
 * Static convenience method - activate all sliders
 */
void TouchSlider::activateAllSliders() {
    TouchSlider * tObjectPointer = sListStart;
    while (tObjectPointer != NULL) {
        tObjectPointer->activate();
        tObjectPointer = tObjectPointer->mNextObject;
    }
}

/*
 * Static convenience method - deactivate all sliders
 */
void TouchSlider::deactivateAllSliders() {
    TouchSlider * tObjectPointer = sListStart;
    while (tObjectPointer != NULL) {
        tObjectPointer->deactivate();
        tObjectPointer = tObjectPointer->mNextObject;
    }
}
/**
 *  for simple predefined slider
 */
void TouchSlider::initSimpleSlider(const uint16_t aPositionX, const uint16_t aPositionY, const uint8_t aSize, const char * aCaption,
        const uint8_t aOptions, int16_t (*aOnChangeHandler)(TouchSlider * const, const int16_t),
        const char * (*aValueHandler)(int16_t)) {
    initSlider(aPositionX, aPositionY, aSize, TOUCHSLIDER_DEFAULT_MAX_VALUE, (TOUCHSLIDER_DEFAULT_MAX_VALUE / 4) * 3,
            TOUCHSLIDER_DEFAULT_THRESHOLD_VALUE, aCaption, sDefaultTouchBorder, aOptions, aOnChangeHandler, aValueHandler);
}

/**
 * @brief initialization with all parameters except color
 * @param aPositionX determines upper left corner
 * @param aPositionY determines upper left corner
 * @param aSize size of bar (and border) in pixel * #TOUCHSLIDER_OVERALL_SIZE_FACTOR
 * @param aMaxValue size of slider bar in pixel
 * @param aThresholdValue value where color of bar changes from #TOUCHSLIDER_DEFAULT_BAR_COLOR to #TOUCHSLIDER_DEFAULT_BAR_THRESHOLD_COLOR
 * @param aInitalValue
 * @param aCaption if NULL no caption is drawn
 * @param aTouchBorder border in pixel, where touches are recognized
 * @param aOptions see #TOUCHSLIDER_SHOW_BORDER etc.
 * @param aOnChangeHandler - if NULL no update of bar is done on touch
 * @param aValueHandler Handler to convert actualValue to string - if NULL sprintf("%3d", mActualValue) is used
 *  * @return false if parameters are not consistent ie. are internally modified
 *  or if not enough space to draw caption or value.
 */
void TouchSlider::initSlider(const uint16_t aPositionX, const uint16_t aPositionY, const uint16_t aSize, const uint16_t aMaxValue,
        const uint16_t aThresholdValue, const int16_t aInitalValue, const char * aCaption, const int8_t aTouchBorder,
        const uint8_t aOptions, int16_t (*aOnChangeHandler)(TouchSlider * const, const int16_t),
        const char * (*aValueHandler)(int16_t)) {

    mSliderColor = sDefaultSliderColor;
    mBarColor = sDefaultBarColor;
    /**
     * Copy parameter
     */
    mPositionX = aPositionX;
    mPositionY = aPositionY;
    mOptions = aOptions;
    mCaption = aCaption;
    mBarWidth = aSize;
    mBarLength = aMaxValue;
    mActualValue = aInitalValue;
    mThresholdValue = aThresholdValue;
    mTouchBorder = aTouchBorder;
    mOnChangeHandler = aOnChangeHandler;
    mValueHandler = aValueHandler;
    if (mValueHandler != NULL) {
        mOptions |= TOUCHSLIDER_SHOW_VALUE;
    }

    checkParameterValues();

    uint8_t tSizeOfBorders = 2 * mBarWidth;
    if (!(mOptions & TOUCHSLIDER_SHOW_BORDER)) {
        tSizeOfBorders = 0;
    }

    /*
     * compute lower right corner and validate
     */
    if (mOptions & TOUCHSLIDER_IS_HORIZONTAL) {
        mPositionXRight = mPositionX + mBarLength + tSizeOfBorders - 1;
        if (mPositionXRight >= mDisplay->getDisplayWidth()) {
            // simple fallback
            mBarWidth = 1;
            mPositionX = 0;
            mPositionXRight = mDisplay->getDisplayWidth() - 1;
        }
        mPositionYBottom = mPositionY + ((tSizeOfBorders + mBarWidth) * TOUCHSLIDER_SIZE_FACTOR) - 1;
        if (mPositionYBottom >= mDisplay->getDisplayHeight()) {
            // simple fallback
            mBarWidth = 1;
            mPositionY = 0;
            mPositionYBottom = mDisplay->getDisplayHeight() - 1;
        }

    } else {
        mPositionXRight = mPositionX + ((tSizeOfBorders + mBarWidth) * TOUCHSLIDER_SIZE_FACTOR) - 1;
        if (mPositionXRight >= mDisplay->getDisplayWidth()) {
            // simple fallback
            mBarWidth = 1;
            mPositionX = 0;
            mPositionXRight = mDisplay->getDisplayWidth() - 1;
        }
        mPositionYBottom = mPositionY + mBarLength + tSizeOfBorders - 1;
        if (mPositionYBottom >= mDisplay->getDisplayHeight()) {
            // simple fallback
            mBarWidth = 1;
            mPositionY = 0;
            mPositionYBottom = mDisplay->getDisplayHeight() - 1;
        }
    }
}

void TouchSlider::drawSlider(void) {
    mIsActive = true;

    if ((mOptions & TOUCHSLIDER_SHOW_BORDER)) {
        drawBorder();
    }

    // Fill middle bar with initial value
    drawBar();
    printCaption();
    // Print value as string
    printValue();
}

void TouchSlider::drawBorder() {
    if (mOptions & TOUCHSLIDER_IS_HORIZONTAL) {
        // Create value bar upper border
        mDisplay->fillRectRel(mPositionX, mPositionY, mBarLength + (2 * mBarWidth), TOUCHSLIDER_SIZE_FACTOR * mBarWidth, mSliderColor);
        // Create value bar lower border
        mDisplay->fillRectRel(mPositionX, mPositionY + (2 * TOUCHSLIDER_SIZE_FACTOR * mBarWidth), mBarLength + (2 * mBarWidth),
                TOUCHSLIDER_SIZE_FACTOR * mBarWidth, mSliderColor);

        // Create left border
        mDisplay->fillRectRel(mPositionX, mPositionY + (TOUCHSLIDER_SIZE_FACTOR * mBarWidth), mBarWidth, TOUCHSLIDER_SIZE_FACTOR * mBarWidth,
                mSliderColor);
        // Create right border
        mDisplay->fillRectRel(mPositionXRight - mBarWidth + 1, mPositionY + (TOUCHSLIDER_SIZE_FACTOR * mBarWidth), mBarWidth,
                TOUCHSLIDER_SIZE_FACTOR * mBarWidth, mSliderColor);
    } else {
        // Create left border
        mDisplay->fillRectRel(mPositionX, mPositionY, TOUCHSLIDER_SIZE_FACTOR * mBarWidth, mBarLength + (2 * mBarWidth), mSliderColor);
        // Create right border
        mDisplay->fillRectRel(mPositionX + (2 * TOUCHSLIDER_SIZE_FACTOR * mBarWidth), mPositionY, TOUCHSLIDER_SIZE_FACTOR * mBarWidth,
                mBarLength + (2 * mBarWidth), mSliderColor);

        // Create value bar upper border
        mDisplay->fillRectRel(mPositionX + (TOUCHSLIDER_SIZE_FACTOR * mBarWidth), mPositionY, TOUCHSLIDER_SIZE_FACTOR * mBarWidth, mBarWidth,
                mSliderColor);
        // Create value bar lower border
        mDisplay->fillRectRel(mPositionX + (TOUCHSLIDER_SIZE_FACTOR * mBarWidth), mPositionYBottom - mBarWidth + 1,
                TOUCHSLIDER_SIZE_FACTOR * mBarWidth, mBarWidth, mSliderColor);
    }
}

/*
 * (re)draws the middle bar according to actual value
 */
void TouchSlider::drawBar() {
    int16_t tActualValue = mActualValue;
    if (tActualValue > mBarLength) {
        tActualValue = mBarLength;
    }
    if (tActualValue < 0) {
        tActualValue = 0;
    }

    uint8_t tBorderSize = 0;
    if ((mOptions & TOUCHSLIDER_SHOW_BORDER)) {
        tBorderSize = mBarWidth;
    }
// draw background bar
    if (tActualValue < mBarLength) {
        if (mOptions & TOUCHSLIDER_IS_HORIZONTAL) {
            mDisplay->fillRectRel(mPositionX + tBorderSize + tActualValue, mPositionY + (tBorderSize * TOUCHSLIDER_SIZE_FACTOR),
                    mBarLength - tActualValue, tBorderSize * TOUCHSLIDER_SIZE_FACTOR, mBarBackgroundColor);
        } else {
            mDisplay->fillRectRel(mPositionX + (tBorderSize * TOUCHSLIDER_SIZE_FACTOR), mPositionY + tBorderSize,
                    tBorderSize * TOUCHSLIDER_SIZE_FACTOR, mBarLength - tActualValue, mBarBackgroundColor);
        }
    }

// Draw value bar
    if (tActualValue > 0) {
        uint16_t tColor = mBarColor;
        if (tActualValue > mThresholdValue) {
            tColor = mBarThresholdColor;
        }
        if (mOptions & TOUCHSLIDER_IS_HORIZONTAL) {
            mDisplay->fillRectRel(mPositionX + tBorderSize, mPositionY + (tBorderSize * TOUCHSLIDER_SIZE_FACTOR), tActualValue,
                    tBorderSize * TOUCHSLIDER_SIZE_FACTOR, tColor);
        } else {
            mDisplay->fillRectRel(mPositionX + (tBorderSize * TOUCHSLIDER_SIZE_FACTOR),
                    mPositionYBottom - tBorderSize - tActualValue + 1, tBorderSize * TOUCHSLIDER_SIZE_FACTOR, tActualValue, tColor);
        }
    }
}

/*
 * Print caption
 */
void TouchSlider::printCaption() {
    if (mCaption == NULL) {
        return;
    }
    uint16_t tLength = strlen(mCaption) * TEXT_SIZE_11_WIDTH;
    if (tLength == 0) {
        mCaption = NULL;
    }

    uint8_t tSliderWidthPixel;
    if (mOptions & TOUCHSLIDER_IS_HORIZONTAL) {
        tSliderWidthPixel = mBarLength;
        if ((mOptions & TOUCHSLIDER_SHOW_BORDER)) {
            tSliderWidthPixel += 2 * mBarWidth;
        }
    } else {
        tSliderWidthPixel = mBarWidth * TOUCHSLIDER_SIZE_FACTOR;
        if ((mOptions & TOUCHSLIDER_SHOW_BORDER)) {
            tSliderWidthPixel = 3 * tSliderWidthPixel;
        }
    }
// try to position the string in the middle below slider
    uint16_t tXOffset = (tSliderWidthPixel / 2) - (tLength / 2);
    uint16_t tCaptionPositionX = mPositionX + tXOffset;
// unsigned arithmetic overflow handling
    if (tCaptionPositionX > mPositionXRight) {
        tCaptionPositionX = 0;
    }
// space for caption?
    uint16_t tCaptionPositionY = mPositionYBottom + mBarWidth + TEXT_SIZE_11_ASCEND;
    if (tCaptionPositionY > mDisplay->getDisplayHeight() - TEXT_SIZE_11_DECEND) {
        // fallback
        tCaptionPositionY = mDisplay->getDisplayHeight() - TEXT_SIZE_11_DECEND;
    }
    mDisplay->drawText(tCaptionPositionX, tCaptionPositionY, (char *) mCaption, TEXT_SIZE_11, mCaptionColor,
            mValueCaptionBackgroundColor);
}

/*
 * Print value left aligned to slider below caption (if existent)
 */
int8_t TouchSlider::printValue() {
    if (!(mOptions & TOUCHSLIDER_SHOW_VALUE)) {
        return 0;
    }
    const char * pValueAsString;
    uint16_t tValuePositionY = mPositionYBottom + mBarWidth + TEXT_SIZE_11_ASCEND;
    if (mCaption != NULL && !((mOptions & TOUCHSLIDER_IS_HORIZONTAL) && !(mOptions & TOUCHSLIDER_HORIZONTAL_VALUE_BELOW_TITLE))) {
        tValuePositionY += TEXT_SIZE_11_HEIGHT;
    }

    if (tValuePositionY > mDisplay->getDisplayHeight() - TEXT_SIZE_11_DECEND) {
        // fallback
        tValuePositionY = mDisplay->getDisplayHeight() - TEXT_SIZE_11_DECEND;
    }
    if (mValueHandler == NULL) {
        char tValueAsString[4];
        pValueAsString = &tValueAsString[0];
        sprintf(tValueAsString, "%03d", mActualValue);
    } else {
        // mValueHandler has to provide the char array
        pValueAsString = mValueHandler(mActualValue);
    }
    mDisplay->drawText(mPositionX, tValuePositionY, (char *) pValueAsString, TEXT_SIZE_11, mValueColor,
            mValueCaptionBackgroundColor);
    return 0;
}

/*
 * Check if touch event is in slider
 * if yes - set bar and value call callback function and return true
 * if no - return false
 */
bool TouchSlider::checkSlider(const uint16_t aTouchPositionX, const uint16_t aTouchPositionY) {
    uint16_t tPositionBorderX = mPositionX - mTouchBorder;
    if (mTouchBorder > mPositionX) {
        tPositionBorderX = 0;
    }
    uint16_t tPositionBorderY = mPositionY - mTouchBorder;
    if (mTouchBorder > mPositionY) {
        tPositionBorderY = 0;
    }
    if (!mIsActive || aTouchPositionX < tPositionBorderX || aTouchPositionX > mPositionXRight + mTouchBorder
            || aTouchPositionY < tPositionBorderY || aTouchPositionY > mPositionYBottom + mTouchBorder) {
        return false;
    }
    uint8_t tTinyBorderSize = 0;
    if ((mOptions & TOUCHSLIDER_SHOW_BORDER)) {
        tTinyBorderSize = mBarWidth;
    }
    /*
     *  Touch position is in slider (plus additional touch border) here
     */
// adjust value according to size of upper and lower border
    int16_t tActualTouchValue;
    if (mOptions & TOUCHSLIDER_IS_HORIZONTAL) {
        if (aTouchPositionX < (mPositionX + tTinyBorderSize)) {
            tActualTouchValue = 0;
        } else if (aTouchPositionX > (mPositionXRight - tTinyBorderSize)) {
            tActualTouchValue = mBarLength;
        } else {
            tActualTouchValue = aTouchPositionX - mPositionX - tTinyBorderSize + 1;
        }
    } else {
        if (aTouchPositionY > (mPositionYBottom - tTinyBorderSize)) {
            tActualTouchValue = 0;
        } else if (aTouchPositionY < (mPositionY + tTinyBorderSize)) {
            tActualTouchValue = mBarLength;
        } else {
            tActualTouchValue = mPositionYBottom - tTinyBorderSize - aTouchPositionY + 1;
        }
    }

    if (tActualTouchValue != mActualTouchValue) {
        mActualTouchValue = tActualTouchValue;
        if (mOnChangeHandler != NULL) {
            // call change handler and take the result as new value
            tActualTouchValue = mOnChangeHandler(this, tActualTouchValue);

            if (tActualTouchValue == mActualValue) {
                // value returned did not change - do nothing
                return true;
            }
            if (tActualTouchValue > mBarLength) {
                tActualTouchValue = mBarLength;
            }
        }
        // value changed - store and redraw
        mActualValue = tActualTouchValue;
        drawBar();
        printValue();
    }
    return true;
}

/*
 * Static convenience method - checks all buttons in  array sManagesButtonsArray for events.
 */
bool TouchSlider::checkAllSliders(const uint16_t aTouchPositionX, const uint16_t aTouchPositionY) {
    TouchSlider * tObjectPointer = sListStart;
// walk through list
    while (tObjectPointer != NULL) {
        if (tObjectPointer->mIsActive && tObjectPointer->checkSlider(aTouchPositionX, aTouchPositionY)) {
            sSliderTouched = true;
            return true;
        }
        tObjectPointer = tObjectPointer->mNextObject;
    }
    sSliderTouched = false;
    return false;
}

int16_t TouchSlider::getActualValue() const {
    return mActualValue;
}

/*
 * also redraws value bar
 */
void TouchSlider::setActualValue(int16_t actualValue) {
    mActualValue = actualValue;
}

void TouchSlider::setActualValueAndDraw(int16_t actualValue) {
    mActualValue = actualValue;
    drawBar();
    printValue();
}

void TouchSlider::setActualValueAndDrawBar(int16_t actualValue) {
    mActualValue = actualValue;
    drawBar();
}

uint16_t TouchSlider::getPositionXRight() const {
    return mPositionXRight;
}

uint16_t TouchSlider::getPositionYBottom() const {
    return mPositionYBottom;
}

void TouchSlider::activate() {
    mIsActive = true;
}
void TouchSlider::deactivate() {
    mIsActive = false;
}

int8_t TouchSlider::checkParameterValues() {
    /**
     * Check and copy parameter
     */
    int8_t tRetValue = 0;

    if (mBarWidth == 0) {
        mBarWidth = TOUCHSLIDER_DEFAULT_SIZE;
        tRetValue = TOUCHSLIDER_ERROR_SIZE_ZERO;
    } else if (mBarWidth > 20) {
        mBarWidth = TOUCHSLIDER_DEFAULT_SIZE;
        tRetValue = TOUCHSLIDER_ERROR_SIZE;
    }
    if (mBarLength == 0) {
        tRetValue = TOUCHSLIDER_ERROR_MAX_VALUE;
        mBarLength = 1;
    }
    if (mActualValue > mBarLength) {
        tRetValue = TOUCHSLIDER_ERROR_ACTUAL_VALUE;
        mActualValue = mBarLength;
    }

    return tRetValue;
}

void TouchSlider::setBarThresholdColor(uint16_t barThresholdColor) {
    mBarThresholdColor = barThresholdColor;
}

void TouchSlider::setSliderColor(uint16_t sliderColor) {
    mSliderColor = sliderColor;
}

void TouchSlider::setBarColor(uint16_t barColor) {
    mBarColor = barColor;
}

