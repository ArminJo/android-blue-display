/*
 * TouchButton.cpp
 *
 * Renders touch buttons for lcd
 * A button can be a simple clickable text
 * or a box with or without text or even an invisible touch area
 *
 *  Created on:  30.01.2012
 *      Author:  Armin Joachimsmeyer
 *      Email:   armin.joachimsmeyer@gmx.de
 *      License: GPL v3 (http://www.gnu.org/licenses/gpl.html)
 *      Version: 1.1.0
 *
 *  LCD interface used:
 * 		getHeight()
 * 		getWidth()
 * 		fillRectRel()
 * 		drawText()
 * 		TEXT_SIZE_11_WIDTH
 * 		TEXT_SIZE_11_HEIGHT
 *
 * 	Ram usage:
 * 		7 byte + 24 bytes per button
 *
 * 	Code size:
 * 		1,5 kByte
 *
 */

#include "TouchButton.h"

#include <stddef.h> // for NULL
TouchButton * TouchButton::sListStart = NULL;
uint16_t TouchButton::sDefaultButtonColor = TOUCHBUTTON_DEFAULT_COLOR;
uint16_t TouchButton::sDefaultCaptionColor = TOUCHBUTTON_DEFAULT_CAPTION_COLOR;

TouchButton::TouchButton() {
    mDisplay = &BlueDisplay1;
    mButtonColor = sDefaultButtonColor;
    mCaptionColor = sDefaultCaptionColor;
    mIsActive = false;
    mPGMCaption = false;
    mNextObject = NULL;
    if (sListStart == NULL) {
        // first button
        sListStart = this;
    } else {
        // put object in button list
        TouchButton * tObjectPointer = sListStart;
        // search last list element
        while (tObjectPointer->mNextObject != NULL) {
            tObjectPointer = tObjectPointer->mNextObject;
        }
        //insert actual button in last element
        tObjectPointer->mNextObject = this;
    }
}

void TouchButton::setDefaultButtonColor(const uint16_t aDefaultButtonColor) {
    sDefaultButtonColor = aDefaultButtonColor;
}
void TouchButton::setDefaultCaptionColor(const uint16_t aDefaultCaptionColor) {
    sDefaultCaptionColor = aDefaultCaptionColor;
}

/*
 * Set parameters (except colors and touch border size) for touch button
 * if aWidthX == 0 render only text no background box
 * if aCaptionSize == 0 don't render anything, just check touch area -> transparent button ;-)
 */
int8_t TouchButton::initSimpleButton(const uint16_t aPositionX, const uint16_t aPositionY, const uint16_t aWidthX,
        const uint16_t aHeightY, const char * aCaption, const uint8_t aCaptionSize, const int16_t aValue,
        void (*aOnTouchHandler)(TouchButton * const, int16_t)) {
    return initButton(aPositionX, aPositionY, aWidthX, aHeightY, aCaption, aCaptionSize, sDefaultButtonColor, sDefaultCaptionColor,
            aValue, aOnTouchHandler);
}

/*
 * Set parameters (except colors and touch border size) for touch button
 * if aWidthX == 0 render only text no background box
 * if aCaptionSize == 0 don't render anything, just check touch area -> transparent button ;-)
 */
int8_t TouchButton::initSimpleButtonPGM(const uint16_t aPositionX, const uint16_t aPositionY, const uint16_t aWidthX,
        const uint16_t aHeightY, PGM_P aCaption, const uint8_t aCaptionSize, const int16_t aValue,
        void(*aOnTouchHandler)(TouchButton * const, int16_t)) {
            mPGMCaption = true;
            return initButton(aPositionX, aPositionY, aWidthX, aHeightY, aCaption, aCaptionSize,
                    sDefaultButtonColor, sDefaultCaptionColor, aValue, aOnTouchHandler);
        }

/*
 * Set parameters for touch button
 * if aWidthX == 0 render only text no background box
 * if aCaptionSize == 0 don't render anything, just check touch area -> transparent button ;-)
 */
int8_t TouchButton::initButton(const uint16_t aPositionX, const uint16_t aPositionY, const uint16_t aWidthX,
        const uint16_t aHeightY, const char * aCaption, const uint8_t aCaptionSize, const uint16_t aButtonColor,
        const uint16_t aCaptionColor, const int16_t aValue, void (*aOnTouchHandler)(TouchButton * const, int16_t)) {

    mWidth = aWidthX;
    mHeight = aHeightY;
    mButtonColor = aButtonColor;
    mCaptionColor = aCaptionColor;
    mCaption = aCaption;
    mCaptionSize = aCaptionSize;
    mOnTouchHandler = aOnTouchHandler;
    mValue = aValue;
    return setPosition(aPositionX, aPositionY);
}

int8_t TouchButton::setPosition(const uint16_t aPositionX, const uint16_t aPositionY) {
    int8_t tRetValue = 0;
    mPositionX = aPositionX;
    mPositionY = aPositionY;

    // check values
    if (aPositionX + mWidth > mDisplay->getDisplayWidth()) {
        mWidth = mDisplay->getDisplayWidth() - aPositionX;
        tRetValue = TOUCHBUTTON_ERROR_X_RIGHT;
    }
    if (aPositionY + mHeight > mDisplay->getDisplayHeight()) {
        mHeight = mDisplay->getDisplayHeight() - aPositionY;
        tRetValue = TOUCHBUTTON_ERROR_Y_BOTTOM;
    }
    return tRetValue;
}

/*
 * renders the button on lcd
 */
int8_t TouchButton::drawButton() {
    // Draw rect
    mDisplay->fillRectRel(mPositionX, mPositionY, mWidth, mHeight, mButtonColor);
    return drawCaption();
}

/**
 * deactivates the button and redraws its screen space with @a aBackgroundColor
 */
void TouchButton::removeButton(const uint16_t aBackgroundColor) {
    mIsActive = false;
    // Draw rect
    mDisplay->fillRectRel(mPositionX, mPositionY, mWidth, mHeight, aBackgroundColor);

}

int8_t TouchButton::drawCaption() {
    mIsActive = true;

    int8_t tRetValue = 0;
    if (mCaptionSize > 0) { // don't render anything if caption size == 0
        if (mCaption != NULL) {
            uint16_t tXCaptionPosition;
            uint16_t tYCaptionPosition;
            // try to position the string in the middle of the box
            uint8_t tLength = getCaptionLength((char *) mCaption);
            if (tLength >= mWidth) { // unsigned arithmetic
                // String too long here
                tXCaptionPosition = mPositionX;
                tRetValue = TOUCHBUTTON_ERROR_CAPTION_TOO_LONG;
            } else {
                tXCaptionPosition = mPositionX + ((mWidth - tLength) / 2);
            }
            tYCaptionPosition = mPositionY + ((mHeight + getTextAscendMinusDescend(mCaptionSize)) / 2);
            if (mPGMCaption) {
                mDisplay->drawTextPGM(tXCaptionPosition, tYCaptionPosition, (char *) mCaption, mCaptionSize, mCaptionColor,
                        mButtonColor);
            } else {
                mDisplay->drawText(tXCaptionPosition, tYCaptionPosition, (char *) mCaption, mCaptionSize, mCaptionColor,
                        mButtonColor);
            }
        }
    }
    return tRetValue;
}

/*
 * Check if touch event is in button area
 * if yes - call callback function and return true
 * if no - return false
 */
bool TouchButton::checkButton(const uint16_t aTouchPositionX, const uint16_t aTouchPositionY) {
    if (!mIsActive || aTouchPositionX < mPositionX || aTouchPositionX > mPositionX + mWidth || aTouchPositionY < mPositionY
            || aTouchPositionY > (mPositionY + mHeight)) {
        return false;
    }
    /*
     *  Touch position is in button - call callback function
     */
    if (mOnTouchHandler != NULL) {
        mOnTouchHandler(this, mValue);
    }
    return true;
}

/*
 * Static convenience method - checks all buttons for matching touch position.
 */
bool TouchButton::checkAllButtons(const uint16_t aTouchPositionX, const uint16_t aTouchPositionY) {
    TouchButton * tObjectPointer = sListStart;
// walk through list
    while (tObjectPointer != NULL) {
        if (tObjectPointer->mIsActive && tObjectPointer->checkButton(aTouchPositionX, aTouchPositionY)) {
            sButtonTouched = true;
            return BUTTON_TOUCHED;
        }
        tObjectPointer = tObjectPointer->mNextObject;
    }
    sButtonTouched = false;
    return NOT_TOUCHED;
}

/*
 * Static convenience method - deactivate all buttons (e.g. before switching screen)
 */
void TouchButton::deactivateAllButtons() {
    TouchButton * tObjectPointer = sListStart;
// walk through list
    while (tObjectPointer != NULL) {
        tObjectPointer->deactivate();
        tObjectPointer = tObjectPointer->mNextObject;
    }
}

/*
 * Static convenience method - activate all buttons
 */
void TouchButton::activateAllButtons() {
    TouchButton * tObjectPointer = sListStart;
// walk through list
    while (tObjectPointer != NULL) {
        tObjectPointer->activate();
        tObjectPointer = tObjectPointer->mNextObject;
    }
}

uint8_t TouchButton::getCaptionLength(char * aCaptionPointer) const {
    uint8_t tLength = 0;
    uint8_t tFontWidth = getTextWidth(mCaptionSize);
    if (mPGMCaption) {
        while (pgm_read_byte(aCaptionPointer++) != 0) {
            tLength += (tFontWidth);
        }
    } else {
        while (*aCaptionPointer++ != 0) {
            tLength += (tFontWidth);
        }
    }
    return tLength;
}
# ifdef DEBUG
/*
 * for debug purposes
 * needs char aStringBuffer[23+<CaptionLength>]
 */
void TouchButton::toString(char * aStringBuffer) const {
    sprintf(aStringBuffer, "X=%03u Y=%03u X1=%03u Y1=%03u B=%02u %s", mPositionX, mPositionY, mPositionX + mWidth - 1,
            mPositionY + mHeight - 1, mTouchBorder, mCaption);
}
# endif

const char * TouchButton::getCaption() const {
    return mCaption;
}

/*
 * Set caption
 */
void TouchButton::setCaption(const char * aCaption) {
    mPGMCaption = false;
    mCaption = aCaption;
}
void TouchButton::setCaptionPGM(PGM_P aCaption) {
    mPGMCaption = true;
    mCaption = aCaption;
}

/*
 * changes box color and redraws button
 */
void TouchButton::setColor(const uint16_t aColor) {
    mButtonColor = aColor;
}

void TouchButton::setCaptionColor(const uint16_t aColor) {
    mCaptionColor = aColor;
}

void TouchButton::setValue(const int16_t aValue) {
    mValue = aValue;

}
uint16_t TouchButton::getPositionX() const {
    return mPositionX;
}

uint16_t TouchButton::getPositionY() const {
    return mPositionY;
}
uint16_t TouchButton::getPositionXRight() const {
    return mPositionX + mWidth - 1;
}

uint16_t TouchButton::getPositionYBottom() const {
    return mPositionY + mHeight - 1;
}

/*
 * activate for touch checking
 */
void TouchButton::activate() {
    mIsActive = true;
}

/*
 * deactivate for touch checking
 */
void TouchButton::deactivate() {
    mIsActive = false;
}
