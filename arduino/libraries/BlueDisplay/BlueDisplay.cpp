/*
 * BlueDisplay.cpp
 * C stub for Android BlueDisplay app and the local MI0283QT2 Display from Watterott.
 * It implements a few display test functions.
 *
 *   SUMMARY
 *  Blue Display is an Open Source Android remote Display for Arduino etc.
 *  It receives basic draw requests from Arduino etc. over Bluetooth and renders it.
 *  It also implements basic GUI elements as buttons and sliders.
 *  GUI callback, touch and sensor events are sent back to Arduino.
 *
 *  Copyright (C) 2014  Armin Joachimsmeyer
 *  armin.joachimsmeyer@gmail.com
 *
 *  This file is part of BlueDisplay.
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
 */

#include <Arduino.h>
#include "BlueDisplay.h"
#include "BlueDisplayProtocol.h"

#include "BlueSerial.h"

//-------------------- ructor --------------------

BlueDisplay::BlueDisplay(void) {
    mReferenceDisplaySize.XWidth = DISPLAY_DEFAULT_WIDTH;
    mReferenceDisplaySize.YHeight = DISPLAY_DEFAULT_HEIGHT;
    return;
}

// One instance of BlueDisplay called BlueDisplay1
BlueDisplay BlueDisplay1;

void BlueDisplay::setFlagsAndSize(uint16_t aFlags, uint16_t aWidth, uint16_t aHeight) {
    mReferenceDisplaySize.XWidth = aWidth;
    mReferenceDisplaySize.YHeight = aHeight;
    if (USART_isBluetoothPaired()) {
        if (aFlags & BD_FLAG_FIRST_RESET_ALL) {
            // reset local buttons to be synchronized
            BDButton::resetAllButtons();
            BDSlider::resetAllSliders();
        }
        sendUSARTArgs(FUNCTION_TAG_GLOBAL_SETTINGS, 4, SET_FLAGS_AND_SIZE, aFlags, aWidth, aHeight);
    }
}

void BlueDisplay::setScreenOrientationLock(bool doLock) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_GLOBAL_SETTINGS, 2, SET_SCREEN_ORIENTATION_LOCK, doLock);
    }
}

/*
 * index is from android.media.ToneGenerator see also
 * http://www.syakazuka.com/Myself/android/sound_test.html
 */
void BlueDisplay::playTone(uint8_t aToneIndex) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_PLAY_TONE, 1, aToneIndex);
    }
}

void BlueDisplay::playTone(void) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_PLAY_TONE, 1, TONE_DEFAULT);
    }
}

void BlueDisplay::playFeedbackTone(bool isError) {
    if (isError) {
        BlueDisplay1.playTone(TONE_PROP_BEEP2);
    } else {
        BlueDisplay1.playTone(TONE_PROP_BEEP);
    }
}

void BlueDisplay::setCharacterMapping(uint8_t aChar, uint16_t aUnicodeChar) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_GLOBAL_SETTINGS, 3, SET_CHARACTER_CODE_MAPPING, aChar, aUnicodeChar);
    }
}

void BlueDisplay::setLongTouchDownTimeout(uint16_t aLongTouchTimeoutMillis) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_GLOBAL_SETTINGS, 2, SET_LONG_TOUCH_DOWN_TIMEOUT, aLongTouchTimeoutMillis);
    }
}

void BlueDisplay::clearDisplay(Color_t aColor) {
#ifdef LOCAL_DISPLAY_EXISTS
    LocalDisplay.clear(aColor);
#endif
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_CLEAR_DISPLAY, 1, aColor);
    }
}

// forces an rendering of the drawn bitmap
void BlueDisplay::drawDisplayDirect(void) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_DRAW_DISPLAY, 0);
    }
}

void BlueDisplay::drawPixel(uint16_t aXPos, uint16_t aYPos, Color_t aColor) {
#ifdef LOCAL_DISPLAY_EXISTS
    LocalDisplay.drawPixel(aXPos, aYPos, aColor);
#endif
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_DRAW_PIXEL, 3, aXPos, aYPos, aColor);
    }
}

void BlueDisplay::drawLine(uint16_t aXStart, uint16_t aYStart, uint16_t aXEnd, uint16_t aYEnd, Color_t aColor) {
#ifdef LOCAL_DISPLAY_EXISTS
    LocalDisplay.drawLine(aXStart, aYStart, aXEnd, aYEnd, aColor);
#endif
    if (USART_isBluetoothPaired()) {
        sendUSART5Args(FUNCTION_TAG_DRAW_LINE, aXStart, aYStart, aXEnd, aYEnd, aColor);
    }
}

void BlueDisplay::drawLineRel(uint16_t aXStart, uint16_t aYStart, uint16_t aXDelta, uint16_t aYDelta, Color_t aColor) {
#ifdef LOCAL_DISPLAY_EXISTS
    LocalDisplay.drawLine(aXStart, aYStart, aXStart + aXDelta, aYStart + aYDelta, aColor);
#endif
    if (USART_isBluetoothPaired()) {
        sendUSART5Args(FUNCTION_TAG_DRAW_LINE_REL, aXStart, aYStart, aXDelta, aYDelta, aColor);
    }
}

/**
 * Fast routine for drawing data charts
 * draws a line only from x to x+1
 * first pixel is omitted because it is drawn by preceding line
 * uses setArea instead if drawPixel to speed up drawing
 */
void BlueDisplay::drawLineFastOneX(uint16_t aXStart, uint16_t aYStart, uint16_t aYEnd, Color_t aColor) {
#ifdef LOCAL_DISPLAY_EXISTS
    LocalDisplay.drawLineFastOneX(aXStart, aYStart, aYEnd, aColor);
#endif
    if (USART_isBluetoothPaired()) {
        sendUSART5Args(FUNCTION_TAG_DRAW_LINE, aXStart, aYStart, aXStart + 1, aYEnd, aColor);
    }
}

void BlueDisplay::drawLineWithThickness(uint16_t aXStart, uint16_t aYStart, uint16_t aXEnd, uint16_t aYEnd, int16_t aThickness,
        Color_t aColor) {
    sendUSARTArgs(FUNCTION_TAG_DRAW_LINE, 6, aXStart, aYStart, aXEnd, aYEnd, aColor, aThickness);
}

void BlueDisplay::drawRect(uint16_t aXStart, uint16_t aYStart, uint16_t aXEnd, uint16_t aYEnd, Color_t aColor,
        uint16_t aStrokeWidth) {
#ifdef LOCAL_DISPLAY_EXISTS
    LocalDisplay.drawRect(aXStart, aYStart, aXEnd - 1, aYEnd - 1, aColor);
#endif
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_DRAW_RECT, 6, aXStart, aYStart, aXEnd, aYEnd, aColor, aStrokeWidth);
    }
}

void BlueDisplay::drawRectRel(uint16_t aXStart, uint16_t aYStart, uint16_t aWidth, uint16_t aHeight, Color_t aColor,
        uint16_t aStrokeWidth) {
#ifdef LOCAL_DISPLAY_EXISTS
    LocalDisplay.drawRect(aXStart, aYStart, aXStart + aWidth - 1, aYStart + aHeight - 1, aColor);
#endif
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_DRAW_RECT_REL, 6, aXStart, aYStart, aWidth, aHeight, aColor, aStrokeWidth);
    }
}

void BlueDisplay::fillRect(uint16_t aXStart, uint16_t aYStart, uint16_t aXEnd, uint16_t aYEnd, Color_t aColor) {
#ifdef LOCAL_DISPLAY_EXISTS
    LocalDisplay.fillRect(aXStart, aYStart, aXEnd, aYEnd, aColor);
#endif
    if (USART_isBluetoothPaired()) {
        sendUSART5Args(FUNCTION_TAG_FILL_RECT, aXStart, aYStart, aXEnd, aYEnd, aColor);
    }
}

void BlueDisplay::fillRectRel(uint16_t aXStart, uint16_t aYStart, uint16_t aWidth, uint16_t aHeight, Color_t aColor) {
#ifdef LOCAL_DISPLAY_EXISTS
    LocalDisplay.fillRect(aXStart, aYStart, aXStart + aWidth - 1, aYStart + aHeight - 1, aColor);
#endif
    if (USART_isBluetoothPaired()) {
        sendUSART5Args(FUNCTION_TAG_FILL_RECT_REL, aXStart, aYStart, aWidth, aHeight, aColor);
    }
}

void BlueDisplay::drawCircle(uint16_t aXCenter, uint16_t aYCenter, uint16_t aRadius, Color_t aColor, uint16_t aStrokeWidth) {
#ifdef LOCAL_DISPLAY_EXISTS
    LocalDisplay.drawCircle(aXCenter, aYCenter, aRadius, aColor);
#endif
    if (USART_isBluetoothPaired()) {
        sendUSART5Args(FUNCTION_TAG_DRAW_CIRCLE, aXCenter, aYCenter, aRadius, aColor, aStrokeWidth);
    }
}

void BlueDisplay::fillCircle(uint16_t aXCenter, uint16_t aYCenter, uint16_t aRadius, Color_t aColor) {
#ifdef LOCAL_DISPLAY_EXISTS
    LocalDisplay.fillCircle(aXCenter, aYCenter, aRadius, aColor);
#endif
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_FILL_CIRCLE, 4, aXCenter, aYCenter, aRadius, aColor);
    }
}

/**
 * @return start x for next character / x + (TEXT_SIZE_11_WIDTH * size)
 */
uint16_t BlueDisplay::drawChar(uint16_t aPosX, uint16_t aPosY, char aChar, uint8_t aCharSize, Color_t aFGColor, Color_t aBGColor) {
    uint16_t tRetValue = 0;
#ifdef LOCAL_DISPLAY_EXISTS
    tRetValue = LocalDisplay.drawChar(aPosX, aPosY - getTextAscend(aCharSize), aChar, getLocalTextSize(aCharSize), aFGColor,
            aBGColor);
#endif
    if (USART_isBluetoothPaired()) {
        tRetValue = aPosX + getTextWidth(aCharSize);
        sendUSARTArgs(FUNCTION_TAG_DRAW_CHAR, 6, aPosX, aPosY, aCharSize, aFGColor, aBGColor, aChar);
    }
    return tRetValue;
}

/**
 * @param aXStart left position
 * @param aYStart upper position
 * @param aStringPtr is (const char *) to avoid endless casts for string ants
 * @return uint16_t start y for next line - next y Parameter
 */
void BlueDisplay::drawMLText(uint16_t aPosX, uint16_t aPosY, const char *aStringPtr, uint8_t aTextSize, Color_t aFGColor,
        Color_t aBGColor) {
#ifdef LOCAL_DISPLAY_EXISTS
    LocalDisplay.drawMLText(aPosX, aPosY - getTextAscend(aTextSize), (char *) aStringPtr, getLocalTextSize(aTextSize),
            aFGColor, aBGColor);
#endif
    if (USART_isBluetoothPaired()) {
        sendUSART5ArgsAndByteBuffer(FUNCTION_TAG_DRAW_STRING, aPosX, aPosY, aTextSize, aFGColor, aBGColor, strlen(aStringPtr),
                (uint8_t*) aStringPtr);
    }
}

/**
 * @param aXStart left position
 * @param aYStart upper position
 * @param aStringPtr is (const char *) to avoid endless casts for string ants
 * @return uint16_t start x for next character - next x Parameter
 */
uint16_t BlueDisplay::drawText(uint16_t aPosX, uint16_t aPosY, const char *aStringPtr, uint8_t aTextSize, Color_t aFGColor,
        Color_t aBGColor) {
    uint16_t tRetValue = 0;
#ifdef LOCAL_DISPLAY_EXISTS
    tRetValue = LocalDisplay.drawText(aPosX, aPosY - getTextAscend(aTextSize), (char *) aStringPtr, getLocalTextSize(aTextSize),
            aFGColor, aBGColor);
#endif
    if (USART_isBluetoothPaired()) {
        tRetValue = aPosX + strlen(aStringPtr) * getTextWidth(aTextSize);
        sendUSART5ArgsAndByteBuffer(FUNCTION_TAG_DRAW_STRING, aPosX, aPosY, aTextSize, aFGColor, aBGColor, strlen(aStringPtr),
                (uint8_t*) aStringPtr);
    }
    return tRetValue;
}

/*
 * for printf implementation
 */
void BlueDisplay::setPrintfSizeAndColorAndFlag(uint8_t aPrintSize, Color_t aPrintColor, Color_t aPrintBackgroundColor,
bool aClearOnNewScreen) {
#ifdef LOCAL_DISPLAY_EXISTS
    printSetOptions(aPrintSize, aPrintColor, aPrintBackgroundColor, aClearOnNewScreen);
#endif
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_WRITE_SETTINGS, 5, WRITE_FLAG_SET_SIZE_AND_COLORS_AND_FLAGS, aPrintSize, aPrintColor,
                aPrintBackgroundColor, aClearOnNewScreen);
    }
}

void BlueDisplay::setPrintfPosition(uint16_t aPosX, uint16_t aPosY) {
#ifdef LOCAL_DISPLAY_EXISTS
    printSetPosition(aPosX, aPosY);
#endif
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_WRITE_SETTINGS, 3, WRITE_FLAG_SET_POSITION, aPosX, aPosY);
    }
}

void BlueDisplay::setPrintfPositionColumnLine(uint8_t aColumnNumber, uint8_t aLineNumber) {
#ifdef LOCAL_DISPLAY_EXISTS
    printSetPositionColumnLine(aColumnNumber, aLineNumber);
#endif
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_WRITE_SETTINGS, 3, WRITE_FLAG_SET_LINE_COLUMN, aColumnNumber, aLineNumber);
    }
}

void BlueDisplay::writeString(const char *aStringPtr, uint8_t aStringLength) {
#ifdef LOCAL_DISPLAY_EXISTS
    myPrint(aStringPtr, aStringLength);
#endif
    if (USART_isBluetoothPaired()) {
        sendUSARTArgsAndByteBuffer(FUNCTION_TAG_WRITE_STRING, 0, aStringLength, (uint8_t*) aStringPtr);
    }
}

// for use in syscalls.c
extern "C" void writeStringC(const char *aStringPtr, uint8_t aStringLength) {
#ifdef LOCAL_DISPLAY_EXISTS
    myPrint(aStringPtr, aStringLength);
#endif
    if (USART_isBluetoothPaired()) {
        sendUSARTArgsAndByteBuffer(FUNCTION_TAG_WRITE_STRING, 0, aStringLength, (uint8_t*) aStringPtr);
    }
}

/**
 * Output String as error log
 */
void BlueDisplay::debugMessage(const char *aStringPtr) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgsAndByteBuffer(FUNCTION_TAG_DEBUG_STRING, 0, strlen(aStringPtr), (uint8_t*) aStringPtr);
    }
}

uint16_t BlueDisplay::drawTextPGM(uint16_t aPosX, uint16_t aPosY, const char * aPGMString, uint8_t aTextSize, Color_t aFGColor,
Color_t aBGColor) {
    uint16_t tRetValue = 0;
    uint8_t tCaptionLength = strlen_P(aPGMString);
    if(tCaptionLength < STRING_BUFFER_STACK_SIZE) {
        char StringBuffer[STRING_BUFFER_STACK_SIZE];
        strcpy_P(StringBuffer, aPGMString);
#ifdef LOCAL_DISPLAY_EXISTS
        tRetValue = LocalDisplay.drawTextPGM(aPosX, aPosY - getTextAscend(aTextSize), aPGMString, getLocalTextSize(aTextSize), aFGColor, aBGColor);
#endif
        if (USART_isBluetoothPaired()) {
            tRetValue = aPosX + tCaptionLength * getTextWidth(aTextSize);
            sendUSART5ArgsAndByteBuffer(FUNCTION_TAG_DRAW_STRING, aPosX, aPosY, aTextSize, aFGColor, aBGColor, tCaptionLength,
            (uint8_t*) StringBuffer);
        }
    }
    return tRetValue;
}

/**
 * if aClearBeforeColor != 0 then previous line is cleared before
 */
void BlueDisplay::drawChartByteBuffer(uint16_t aXOffset, uint16_t aYOffset, Color_t aColor, Color_t aClearBeforeColor,
        uint8_t *aByteBuffer, uint16_t aByteBufferLength) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgsAndByteBuffer(FUNCTION_TAG_DRAW_CHART, 4, aXOffset, aYOffset, aColor, aClearBeforeColor, aByteBufferLength,
                aByteBuffer);
    }
}

/**
 * if aClearBeforeColor != 0 then previous line is cleared before
 * chart index is coded in the upper 4 bits of aYOffset
 */
void BlueDisplay::drawChartByteBuffer(uint16_t aXOffset, uint16_t aYOffset, Color_t aColor, Color_t aClearBeforeColor,
        uint8_t aChartIndex, bool aDoDrawDirect, uint8_t *aByteBuffer, uint16_t aByteBufferLength) {
    if (USART_isBluetoothPaired()) {
        aYOffset = aYOffset | ((aChartIndex & 0x0F) << 12);
        uint8_t tFunctionTag = FUNCTION_TAG_DRAW_CHART_WITHOUT_DIRECT_RENDERING;
        if (aDoDrawDirect) {
            tFunctionTag = FUNCTION_TAG_DRAW_CHART;
        }
        sendUSARTArgsAndByteBuffer(tFunctionTag, 4, aXOffset, aYOffset, aColor, aClearBeforeColor, aByteBufferLength, aByteBuffer);
    }
}

void BlueDisplay::setMaxDisplaySize(struct XYSize * aMaxDisplaySizePtr) {
    mMaxDisplaySize.XWidth = aMaxDisplaySizePtr->XWidth;
    mMaxDisplaySize.YHeight = aMaxDisplaySizePtr->YHeight;
}

void BlueDisplay::setActualDisplaySize(struct XYSize * aActualDisplaySizePrt) {
    mActualDisplaySize.XWidth = aActualDisplaySizePrt->XWidth;
    mActualDisplaySize.YHeight = aActualDisplaySizePrt->YHeight;
}

uint16_t BlueDisplay::getDisplayWidth(void) {
    return mReferenceDisplaySize.XWidth;
}

uint16_t BlueDisplay::getDisplayHeight(void) {
    return mReferenceDisplaySize.YHeight;
}

/*****************************************************************************
 * Vector for ThickLine
 *****************************************************************************/

/**
 * aNewRelEndX + Y are new x and y values relative to start point
 */
void BlueDisplay::refreshVector(struct ThickLine * aLine, int16_t aNewRelEndX, int16_t aNewRelEndY) {
    int16_t tNewEndX = aLine->StartX + aNewRelEndX;
    int16_t tNewEndY = aLine->StartY + aNewRelEndY;
    if (aLine->EndX != tNewEndX || aLine->EndX != tNewEndY) {
        //clear old line
        drawLineWithThickness(aLine->StartX, aLine->StartY, aLine->EndX, aLine->EndY, aLine->Thickness, aLine->BackgroundColor);
        // Draw new line
        /**
         * clipping
         * Ignore warning since we know that values are positive when compared :-)
         */
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wsign-compare"
        if (tNewEndX < 0) {
            tNewEndX = 0;
        } else if (tNewEndX > mReferenceDisplaySize.XWidth - 1) {
            tNewEndX = mReferenceDisplaySize.XWidth - 1;
        }
        aLine->EndX = tNewEndX;

        if (tNewEndY < 0) {
            tNewEndY = 0;
        } else if (tNewEndY > mReferenceDisplaySize.YHeight - 1) {
            tNewEndY = mReferenceDisplaySize.YHeight - 1;
        }
#pragma GCC diagnostic pop
        aLine->EndY = tNewEndY;

        drawLineWithThickness(aLine->StartX, aLine->StartY, tNewEndX, tNewEndY, aLine->Thickness, aLine->Color);
    }
}

/***************************************************************************************************************************************************
 *
 * INPUT
 *
 **************************************************************************************************************************************************/

void BlueDisplay::getNumber(void (*aNumberHandler)(float)) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_GET_NUMBER, 1, aNumberHandler);
    }
}

void BlueDisplay::getNumberWithShortPrompt(void (*aNumberHandler)(float), const char *aShortPromptString) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgsAndByteBuffer(FUNCTION_TAG_GET_NUMBER_WITH_SHORT_PROMPT, 1, aNumberHandler, strlen(aShortPromptString),
                (uint8_t*) aShortPromptString);
    }
}

void BlueDisplay::getNumberWithShortPrompt(void (*aNumberHandler)(float), const char *aShortPromptString, float aInitialValue) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgsAndByteBuffer(FUNCTION_TAG_GET_NUMBER_WITH_SHORT_PROMPT_AND_INITIAL_VALUE, 2, aNumberHandler, aInitialValue,
                strlen(aShortPromptString), (uint8_t*) aShortPromptString);
    }
}

void BlueDisplay::getNumberWithShortPromptPGM(void (*aNumberHandler)(float), const char *aPGMShortPromptString) {
    if (USART_isBluetoothPaired()) {
        uint8_t tShortPromptLength = strlen_P(aPGMShortPromptString);
        if (tShortPromptLength < STRING_BUFFER_STACK_SIZE) {
            char StringBuffer[STRING_BUFFER_STACK_SIZE];
            strcpy_P(StringBuffer, aPGMShortPromptString);
            sendUSARTArgsAndByteBuffer(FUNCTION_TAG_GET_NUMBER_WITH_SHORT_PROMPT, 1, aNumberHandler, tShortPromptLength,
                    (uint8_t*) StringBuffer);
        }
    }
}

void BlueDisplay::getText(void (*aTextHandler)(const char *)) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_GET_TEXT, 1, aTextHandler);
    }
}

/***************************************************************************************************************************************************
 *
 * BUTTONS
 *
 **************************************************************************************************************************************************/

BDButtonHandle_t BlueDisplay::createButton(uint16_t aPositionX, uint16_t aPositionY, uint16_t aWidthX, uint16_t aHeightY,
        Color_t aButtonColor, const char * aCaption, uint8_t aCaptionSize, uint8_t aFlags, int16_t aValue,
        void (*aOnTouchHandler)(BDButtonHandle_t *, int16_t)) {
    BDButtonHandle_t tButtonNumber = NO_BUTTON;
    if (USART_isBluetoothPaired()) {
        tButtonNumber = localButtonIndex++;
        sendUSARTArgsAndByteBuffer(FUNCTION_TAG_BUTTON_CREATE, 9, tButtonNumber, aPositionX, aPositionY, aWidthX, aHeightY,
                aButtonColor, aCaptionSize | (aFlags << 8), aValue, aOnTouchHandler, strlen(aCaption), aCaption);
    }
    return tButtonNumber;
}

BDButtonHandle_t BlueDisplay::createButtonPGM(uint16_t aPositionX, uint16_t aPositionY, uint16_t aWidthX, uint16_t aHeightY,
        Color_t aButtonColor, const char * aPGMCaption, uint8_t aCaptionSize, uint8_t aFlags,
int16_t aValue, void (*aOnTouchHandler)(BDButtonHandle_t *, int16_t)) {
    BDButtonHandle_t tButtonNumber = NO_BUTTON;
    if (USART_isBluetoothPaired()) {
        uint8_t tCaptionLength = strlen_P(aPGMCaption);
        if(tCaptionLength < STRING_BUFFER_STACK_SIZE) {
            char StringBuffer[STRING_BUFFER_STACK_SIZE];
            strcpy_P(StringBuffer,aPGMCaption);
            tButtonNumber = localButtonIndex++;
            sendUSARTArgsAndByteBuffer(FUNCTION_TAG_BUTTON_CREATE, 9, tButtonNumber, aPositionX, aPositionY, aWidthX,aHeightY,
            aButtonColor, aCaptionSize | (aFlags << 8), aValue, aOnTouchHandler, tCaptionLength, StringBuffer);
        }
    }
    return tButtonNumber;
}

void BlueDisplay::drawButton(BDButtonHandle_t aButtonNumber) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_BUTTON_DRAW, 1, aButtonNumber);
    }
}

void BlueDisplay::drawButtonCaption(BDButtonHandle_t aButtonNumber) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_BUTTON_DRAW_CAPTION, 1, aButtonNumber);
    }
}

void BlueDisplay::setButtonCaption(BDButtonHandle_t aButtonNumber, const char * aCaption, bool doDrawButton) {
    if (USART_isBluetoothPaired()) {
        uint8_t tFunctionCode = FUNCTION_TAG_BUTTON_SET_CAPTION;
        if (doDrawButton) {
            tFunctionCode = FUNCTION_TAG_BUTTON_SET_CAPTION_AND_DRAW_BUTTON;
        }
        sendUSARTArgsAndByteBuffer(tFunctionCode, 1, aButtonNumber, strlen(aCaption), aCaption);
    }
}

void BlueDisplay::setButtonCaptionPGM(BDButtonHandle_t aButtonNumber, const char * aPGMCaption, bool doDrawButton) {
    if (USART_isBluetoothPaired()) {
        uint8_t tCaptionLength = strlen_P(aPGMCaption);
        if(tCaptionLength < STRING_BUFFER_STACK_SIZE) {
            char StringBuffer[STRING_BUFFER_STACK_SIZE];
            strcpy_P(StringBuffer,aPGMCaption);

            uint8_t tFunctionCode = FUNCTION_TAG_BUTTON_SET_CAPTION;
            if (doDrawButton) {
                tFunctionCode = FUNCTION_TAG_BUTTON_SET_CAPTION_AND_DRAW_BUTTON;
            }
            sendUSARTArgsAndByteBuffer(tFunctionCode, 1, aButtonNumber, tCaptionLength, StringBuffer);
        }
    }
}

void BlueDisplay::setButtonValue(BDButtonHandle_t aButtonNumber, int16_t aValue) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_BUTTON_SETTINGS, 3, aButtonNumber, BUTTON_FLAG_SET_VALUE, aValue);
    }
}

void BlueDisplay::setButtonValueAndDraw(BDButtonHandle_t aButtonNumber, int16_t aValue) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_BUTTON_SETTINGS, 3, aButtonNumber, BUTTON_FLAG_SET_VALUE_AND_DRAW, aValue);
    }
}

void BlueDisplay::setButtonColor(BDButtonHandle_t aButtonNumber, Color_t aButtonColor) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_BUTTON_SETTINGS, 3, aButtonNumber, BUTTON_FLAG_SET_BUTTON_COLOR, aButtonColor);
    }
}

void BlueDisplay::setButtonColorAndDraw(BDButtonHandle_t aButtonNumber, Color_t aButtonColor) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_BUTTON_SETTINGS, 3, aButtonNumber, BUTTON_FLAG_SET_BUTTON_COLOR_AND_DRAW, aButtonColor);
    }
}

void BlueDisplay::activateButton(BDButtonHandle_t aButtonNumber) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_BUTTON_SETTINGS, 2, aButtonNumber, BUTTON_FLAG_SET_ACTIVE);
    }
}

void BlueDisplay::deactivateButton(BDButtonHandle_t aButtonNumber) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_BUTTON_SETTINGS, 2, aButtonNumber, BUTTON_FLAG_RESET_ACTIVE);
    }
}

void BlueDisplay::setButtonsGlobalFlags(uint16_t aFlags) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_BUTTON_GLOBAL_SETTINGS, 1, aFlags);
    }
}

/*
 * aToneVolume: value in percent
 */
void BlueDisplay::setButtonsTouchTone(uint8_t aToneIndex, uint8_t aToneVolume) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_BUTTON_GLOBAL_SETTINGS, 3, BUTTONS_SET_BEEP_TONE, aToneIndex, aToneVolume);
    }
}

void BlueDisplay::activateAllButtons(void) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_BUTTON_ACTIVATE_ALL, 0);
    }
}

void BlueDisplay::deactivateAllButtons(void) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_BUTTON_DEACTIVATE_ALL, 0);
    }
}

/***************************************************************************************************************************************************
 *
 * SLIDER
 *
 **************************************************************************************************************************************************/

/**
 * @brief initialization with all parameters except color
 * @param aPositionX determines upper left corner
 * @param aPositionY determines upper left corner
 * @param aBarWidth width of bar (and border) in pixel * #TOUCHSLIDER_OVERALL_SIZE_FACTOR
 * @param aBarLength size of slider bar in pixel = maximum slider value
 * @param aThresholdValue value where color of bar changes from #TOUCHSLIDER_DEFAULT_BAR_COLOR to #TOUCHSLIDER_DEFAULT_BAR_THRESHOLD_COLOR
 * @param aInitalValue
 * @param aSliderColor
 * @param aBarColor
 * @param aOptions see #TOUCHSLIDER_SHOW_BORDER etc.
 * @param aOnChangeHandler - if NULL no update of bar is done on touch
 * @return slider index.
 */
BDSliderHandle_t BlueDisplay::createSlider(uint16_t aPositionX, uint16_t aPositionY, uint8_t aBarWidth, uint16_t aBarLength,
        uint16_t aThresholdValue, int16_t aInitalValue, Color_t aSliderColor, Color_t aBarColor, uint8_t aOptions,
        void (*aOnChangeHandler)(BDSliderHandle_t *, int16_t)) {
    BDSliderHandle_t tSliderNumber = NO_SLIDER;

    if (USART_isBluetoothPaired()) {
        tSliderNumber = localSliderIndex++;
        sendUSARTArgs(FUNCTION_TAG_SLIDER_CREATE, 11, tSliderNumber, aPositionX, aPositionY, aBarWidth, aBarLength, aThresholdValue,
                aInitalValue, aSliderColor, aBarColor, aOptions, aOnChangeHandler);
    }
    return tSliderNumber;
}

void BlueDisplay::drawSlider(BDSliderHandle_t aSliderNumber) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_SLIDER_DRAW, 1, aSliderNumber);
    }
}

void BlueDisplay::drawSliderBorder(BDSliderHandle_t aSliderNumber) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_SLIDER_DRAW_BORDER, 1, aSliderNumber);
    }
}

void BlueDisplay::setSliderActualValueAndDraw(BDSliderHandle_t aSliderNumber, int16_t aActualValue) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_SLIDER_SETTINGS, 3, aSliderNumber, SLIDER_FLAG_SET_VALUE_AND_DRAW_BAR, aActualValue);
    }
}

void BlueDisplay::setSliderColorBarThreshold(BDSliderHandle_t aSliderNumber, uint16_t aBarThresholdColor) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_SLIDER_SETTINGS, 3, aSliderNumber, SLIDER_FLAG_SET_COLOR_THRESHOLD, aBarThresholdColor);
    }
}

void BlueDisplay::setSliderColorBarBackground(BDSliderHandle_t aSliderNumber, uint16_t aBarBackgroundColor) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_SLIDER_SETTINGS, 3, aSliderNumber, SLIDER_FLAG_SET_COLOR_BAR_BACKGROUND, aBarBackgroundColor);
    }
}

void BlueDisplay::activateSlider(BDSliderHandle_t aSliderNumber) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_SLIDER_SETTINGS, 2, aSliderNumber, SLIDER_FLAG_SET_ACTIVE);
    }
}

void BlueDisplay::deactivateSlider(BDSliderHandle_t aSliderNumber) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_SLIDER_SETTINGS, 2, aSliderNumber, SLIDER_FLAG_RESET_ACTIVE);
    }
}

void BlueDisplay::activateAllSliders(void) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_SLIDER_ACTIVATE_ALL, 0);
    }
}

void BlueDisplay::deactivateAllSliders(void) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_SLIDER_DEACTIVATE_ALL, 0);
    }
}

/***************************************************************************************************************************************************
 *
 * SENSOR
 *
 **************************************************************************************************************************************************/

/**
 *
 * @param aSensorType
 * @param aSensorRate one of  {@link #SENSOR_DELAY_NORMAL}, {@link #SENSOR_DELAY_UI},
 *        {@link #SENSOR_DELAY_GAME}, or {@link #SENSOR_DELAY_FASTEST}
 */
void BlueDisplay::setSensor(uint8_t aSensorType, bool aDoActivate, uint8_t aSensorRate) {
    aSensorRate &= 0x03;
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_SENSOR_SETTINGS, 3, aSensorType, aDoActivate, aSensorRate);
    }
}
/***************************************************************************************************************************************************
 *
 * Text sizes
 *
 **************************************************************************************************************************************************/

/*
 * Formula for Monospace Font on Android
 * TextSize * 0.6
 * Integer Formula: (TextSize *6)+4 / 10
 */
uint8_t getTextWidth(uint8_t aTextSize) {
    if (aTextSize == 11) {
        return TEXT_SIZE_11_WIDTH;
    }
#ifdef PGMSPACE_MATTERS
    return TEXT_SIZE_22_WIDTH;
#else
    if (aTextSize == 22) {
        return TEXT_SIZE_22_WIDTH;
    }
    return ((aTextSize * 6) + 4) / 10;
#endif
}

/*
 * Formula for Monospace Font on Android
 * float: TextSize * 0.76
 * int: (TextSize * 195 + 128) >> 8
 */
uint8_t getTextAscend(uint8_t aTextSize) {
    if (aTextSize == TEXT_SIZE_11) {
        return TEXT_SIZE_11_ASCEND;
    }
#ifdef PGMSPACE_MATTERS
    return TEXT_SIZE_22_ASCEND;
#else
    if (aTextSize == TEXT_SIZE_22) {
        return TEXT_SIZE_22_ASCEND;
    }
    uint16_t tRetvalue = aTextSize;
    tRetvalue = ((tRetvalue * 195) + 128) >> 8;
    return tRetvalue;
#endif
}

/*
 * Formula for Monospace Font on Android
 * float: TextSize * 0.24
 * int: (TextSize * 61 + 128) >> 8
 */
uint8_t getTextDecend(uint8_t aTextSize) {
    if (aTextSize == TEXT_SIZE_11) {
        return TEXT_SIZE_11_ASCEND;
    }
#ifdef PGMSPACE_MATTERS
    return TEXT_SIZE_22_ASCEND;
#else

    if (aTextSize == TEXT_SIZE_22) {
        return TEXT_SIZE_22_ASCEND;
    }
    uint16_t tRetvalue = aTextSize;
    tRetvalue = ((tRetvalue * 61) + 128) >> 8;
    return tRetvalue;
#endif
}
/*
 * Ascend - Decent
 * is used to position text in the middle of a button
 * Formula for positioning:
 * Position = ButtonTop + (ButtonHeight + getTextAscendMinusDescend())/2
 */
uint16_t getTextAscendMinusDescend(uint8_t aTextSize) {
    if (aTextSize == TEXT_SIZE_11) {
        return TEXT_SIZE_11_ASCEND - TEXT_SIZE_11_DECEND;
    }
#ifdef PGMSPACE_MATTERS
    return TEXT_SIZE_22_ASCEND - TEXT_SIZE_22_DECEND;
#else
    if (aTextSize == TEXT_SIZE_22) {
        return TEXT_SIZE_22_ASCEND - TEXT_SIZE_22_DECEND;
    }
    uint16_t tRetvalue = aTextSize;
    tRetvalue = ((tRetvalue * 133) + 128) >> 8;
    return tRetvalue;
#endif
}

/*
 * (Ascend -Decent)/2
 */
uint8_t getTextMiddle(uint8_t aTextSize) {
    if (aTextSize == TEXT_SIZE_11) {
        return (TEXT_SIZE_11_ASCEND - TEXT_SIZE_11_DECEND) / 2;
    }
#ifdef PGMSPACE_MATTERS
    return (TEXT_SIZE_22_ASCEND - TEXT_SIZE_22_DECEND) / 2;
#else
    if (aTextSize == TEXT_SIZE_22) {
        return (TEXT_SIZE_22_ASCEND - TEXT_SIZE_22_DECEND) / 2;
    }
    uint16_t tRetvalue = aTextSize;
    tRetvalue = ((tRetvalue * 66) + 128) >> 8;
    return tRetvalue;
#endif
}

/*
 * fast divide by 11 for MI0283QT2 driver arguments
 */
uint8_t getLocalTextSize(uint8_t aTextSize) {
    if (aTextSize <= 11) {
        return 1;
    }
#ifdef PGMSPACE_MATTERS
    return 2;
#else
    if (aTextSize == 22) {
        return 2;
    }
    return aTextSize / 11;
#endif
}

