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

#include "BlueDisplay.h"
#include "BlueDisplayProtocol.h"
#include "BlueSerial.h"
#include "timing.h"
#include "stm32fx0xPeripherals.h"

#ifdef LOCAL_DISPLAY_EXISTS
#include "thickLine.h"
#include "myprint.h"
#endif

#include <string.h>  // for strlen

//-------------------- Constructor --------------------

BlueDisplay::BlueDisplay(void) {
    mReferenceDisplaySize.XWidth = DISPLAY_DEFAULT_WIDTH;
    mReferenceDisplaySize.YHeight = DISPLAY_DEFAULT_HEIGHT;
}

// One instance of BlueDisplay called BlueDisplay1
BlueDisplay BlueDisplay1;

bool isLocalDisplayAvailable = false;

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

/**
 *
 * @param aCodePage Number for ISO_8859_<Number>
 */
void BlueDisplay::setCodePage(uint16_t aCodePageNumber) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_GLOBAL_SETTINGS, 2, SET_CODEPAGE, aCodePageNumber);
    }
}

void BlueDisplay::setCharacterMapping(uint8_t aChar, uint16_t aUnicodeChar) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_GLOBAL_SETTINGS, 3, SET_CHARACTER_CODE_MAPPING, aChar, aUnicodeChar);
    }
}

void BlueDisplay::setLongTouchDownTimeout(uint16_t aLongTouchDownTimeoutMillis) {
#ifdef LOCAL_DISPLAY_EXISTS
    changeDelayCallback(&callbackLongTouchDownTimeout, aLongTouchDownTimeoutMillis);
#endif
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_GLOBAL_SETTINGS, 2, SET_LONG_TOUCH_DOWN_TIMEOUT, aLongTouchDownTimeoutMillis);
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

void BlueDisplay::clearDisplay(Color_t aColor) {
#ifdef LOCAL_DISPLAY_EXISTS
    LocalDisplay.clearDisplay(aColor);
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

void BlueDisplay::drawLineRel(uint16_t aXStart, uint16_t aYStart, uint16_t aXDelta, uint16_t aYDelta,
        Color_t aColor) {
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
        // Just draw plain line, no need to speed up
        sendUSART5Args(FUNCTION_TAG_DRAW_LINE, aXStart, aYStart, aXStart + 1, aYEnd, aColor);
    }
}

void BlueDisplay::drawLineWithThickness(uint16_t aXStart, uint16_t aYStart, uint16_t aXEnd, uint16_t aYEnd,
        int16_t aThickness, Color_t aColor) {
#ifdef LOCAL_DISPLAY_EXISTS
    drawThickLine(aXStart, aYStart, aXEnd, aYEnd, aThickness, LINE_THICKNESS_MIDDLE, aColor);
#endif
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_DRAW_LINE, 6, aXStart, aYStart, aXEnd, aYEnd, aColor, aThickness);
    }
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

void BlueDisplay::drawCircle(uint16_t aXCenter, uint16_t aYCenter, uint16_t aRadius, Color_t aColor,
        uint16_t aStrokeWidth) {
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
uint16_t BlueDisplay::drawChar(uint16_t aPosX, uint16_t aPosY, char aChar, uint8_t aCharSize, Color_t aFGColor,
        Color_t aBGColor) {
    uint16_t tRetValue = 0;
#ifdef LOCAL_DISPLAY_EXISTS
    tRetValue = LocalDisplay.drawChar(aPosX, aPosY - getTextAscend(aCharSize), aChar, getLocalTextSize(aCharSize),
            aFGColor, aBGColor);
#endif
    if (USART_isBluetoothPaired()) {
        tRetValue = aPosX + getTextWidth(aCharSize);
        sendUSARTArgs(FUNCTION_TAG_DRAW_CHAR, 6, aPosX, aPosY, aCharSize, aFGColor, aBGColor, aChar);
    }
    return tRetValue;
}

/**
 *
 * @param aPosX
 * @param aPosY
 * @param aStringPtr
 * @param aTextSize
 * @param aColor
 * @param aBGColor if COLOR_NO_BACKGROUND, then do not clear rest of line
 */
void BlueDisplay::drawMLText(uint16_t aPosX, uint16_t aPosY, const char *aStringPtr, uint8_t aTextSize,
        uint16_t aFGColor, uint16_t aBGColor) {
#ifdef LOCAL_DISPLAY_EXISTS
    LocalDisplay.drawMLText(aPosX, aPosY - getTextAscend(aTextSize), (char *) aStringPtr, getLocalTextSize(aTextSize),
            aFGColor, aBGColor);
#endif
    if (USART_isBluetoothPaired()) {
        sendUSART5ArgsAndByteBuffer(FUNCTION_TAG_DRAW_STRING, aPosX, aPosY, aTextSize, aFGColor, aBGColor,
                (uint8_t*) aStringPtr, strlen(aStringPtr));
    }
}

/**
 * @param aXStart left position
 * @param aYStart upper position
 * @param aStringPtr is (const char *) to avoid endless casts for string constants
 * @return uint16_t start x for next character - next x Parameter
 */
uint16_t BlueDisplay::drawText(uint16_t aPosX, uint16_t aPosY, const char *aStringPtr, uint8_t aTextSize,
        Color_t aFGColor, Color_t aBGColor) {
    uint16_t tRetValue = 0;
#ifdef LOCAL_DISPLAY_EXISTS
    tRetValue = LocalDisplay.drawText(aPosX, aPosY - getTextAscend(aTextSize), (char *) aStringPtr,
            getLocalTextSize(aTextSize), aFGColor, aBGColor);
#endif
    if (USART_isBluetoothPaired()) {
        tRetValue = aPosX + strlen(aStringPtr) * getTextWidth(aTextSize);
        sendUSART5ArgsAndByteBuffer(FUNCTION_TAG_DRAW_STRING, aPosX, aPosY, aTextSize, aFGColor, aBGColor,
                (uint8_t*) aStringPtr, strlen(aStringPtr));
    }
    return tRetValue;
}

// for use in syscalls.c
extern "C" uint16_t drawTextC(uint16_t aXStart, uint16_t aYStart, const char *aStringPtr, uint8_t aFontSize,
        Color_t aFGColor, uint16_t aBGColor) {
    uint16_t tRetValue = 0;
    if (USART_isBluetoothPaired()) {
        tRetValue = BlueDisplay1.drawText(aXStart, aYStart, (char *) aStringPtr, aFontSize, aFGColor, aBGColor);
    }
    return tRetValue;
}

/*
 * for printf implementation
 */
void BlueDisplay::setPrintfSizeAndColorAndFlag(int aPrintSize, Color_t aPrintColor, Color_t aPrintBackgroundColor,
bool aClearOnNewScreen) {
#ifdef LOCAL_DISPLAY_EXISTS
    printSetOptions(getLocalTextSize(aPrintSize), aPrintColor, aPrintBackgroundColor, aClearOnNewScreen);
#endif
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_WRITE_SETTINGS, 5, WRITE_FLAG_SET_SIZE_AND_COLORS_AND_FLAGS, aPrintSize,
                aPrintColor, aPrintBackgroundColor, aClearOnNewScreen);
    }
}

void BlueDisplay::setPrintfPosition(int aPosX, int aPosY) {
#ifdef LOCAL_DISPLAY_EXISTS
    printSetPosition(aPosX, aPosY);
#endif
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_WRITE_SETTINGS, 3, WRITE_FLAG_SET_POSITION, aPosX, aPosY);
    }
}

void BlueDisplay::setPrintfPositionColumnLine(int aColumnNumber, int aLineNumber) {
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

/**
 * Fast routine for drawing data charts
 */
void BlueDisplay::drawChartByteBuffer(uint16_t aXOffset, uint16_t aYOffset, Color_t aColor, Color_t aClearBeforeColor,
        uint8_t *aByteBuffer, uint16_t aByteBufferLength) {
    if (USART_isBluetoothPaired()) {
        // Just draw plain line, no need to speed up
        sendUSART5ArgsAndByteBuffer(FUNCTION_TAG_DRAW_CHART, aXOffset, aYOffset, aColor, aClearBeforeColor, 0,
                aByteBuffer, aByteBufferLength);
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
        sendUSARTArgsAndByteBuffer(tFunctionTag, 4, aXOffset, aYOffset, aColor, aClearBeforeColor, aByteBufferLength,
                aByteBuffer);
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

uint8_t getTextHeight(uint8_t aTextSize) {
    if (aTextSize == 11) {
        return TEXT_SIZE_11_HEIGHT;
    }
    if (aTextSize == 22) {
        return TEXT_SIZE_22_HEIGHT;
    }
    return aTextSize + aTextSize / 8; //
}

/*
 * Formula for Monospace Font on Android
 * TextSize * 0.6
 * Integer Formula: (TextSize *6)+4 / 10
 */
uint8_t getTextWidth(uint8_t aTextSize) {
    if (aTextSize == 11) {
        return TEXT_SIZE_11_WIDTH;
    }
    if (aTextSize == 22) {
        return TEXT_SIZE_22_WIDTH;
    }
    return ((aTextSize * 6) + 4) / 10;
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
    if (aTextSize == TEXT_SIZE_22) {
        return TEXT_SIZE_22_ASCEND;
    }
    uint16_t tRetvalue = aTextSize;
    tRetvalue = ((tRetvalue * 195) + 128) >> 8;
    return tRetvalue;
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
    if (aTextSize == TEXT_SIZE_22) {
        return TEXT_SIZE_22_ASCEND;
    }
    uint16_t tRetvalue = aTextSize;
    tRetvalue = ((tRetvalue * 61) + 128) >> 8;
    return tRetvalue;
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
    if (aTextSize == TEXT_SIZE_22) {
        return TEXT_SIZE_22_ASCEND - TEXT_SIZE_22_DECEND;
    }
    uint16_t tRetvalue = aTextSize;
    tRetvalue = ((tRetvalue * 133) + 128) >> 8;
    return tRetvalue;
}

/*
 * (Ascend -Decent)/2
 */
uint8_t getTextMiddle(uint8_t aTextSize) {
    if (aTextSize == TEXT_SIZE_11) {
        return (TEXT_SIZE_11_ASCEND - TEXT_SIZE_11_DECEND) / 2;
    }
    if (aTextSize == TEXT_SIZE_22) {
        return (TEXT_SIZE_22_ASCEND - TEXT_SIZE_22_DECEND) / 2;
    }
    uint16_t tRetvalue = aTextSize;
    tRetvalue = ((tRetvalue * 66) + 128) >> 8;
    return tRetvalue;
}

/*
 * fast divide by 11 for MI0283QT2 driver arguments
 */
uint8_t getLocalTextSize(uint8_t aTextSize) {
    if (aTextSize <= 11) {
        return 1;
    }
    if (aTextSize == 22) {
        return 2;
    }
    return aTextSize / 11;
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
        drawLineWithThickness(aLine->StartX, aLine->StartY, aLine->EndX, aLine->EndY, aLine->Thickness,
                aLine->BackgroundColor);
        // Draw new line
        /**
         * clipping
         */
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
        aLine->EndY = tNewEndY;

        drawLineWithThickness(aLine->StartX, aLine->StartY, tNewEndX, tNewEndY, aLine->Thickness, aLine->Color);
    }
}

/*****************************************************************************
 * Display and drawing tests
 *****************************************************************************/
/**
 * Draws a star consisting of 4 lines each quadrant
 */
void BlueDisplay::drawStar(int aXPos, int aYPos, int tOffsetCenter, int tLength, int tOffsetDiagonal,
        int aLengthDiagonal, Color_t aColor) {

    int X = aXPos + tOffsetCenter;
    // first right then left lines
    for (int i = 0; i < 2; i++) {
        drawLineRel(X, aYPos, tLength, 0, aColor);
        drawLineRel(X, aYPos - tOffsetDiagonal, tLength, -aLengthDiagonal, aColor); // < 45 degree
        drawLineRel(X, aYPos + tOffsetDiagonal, tLength, aLengthDiagonal, aColor); // < 45 degree
        X = aXPos - tOffsetCenter;
        tLength = -tLength;
    }

    int Y = aYPos + tOffsetCenter;
    // first lower then upper lines
    for (int i = 0; i < 2; i++) {
        drawLineRel(aXPos, Y, 0, tLength, aColor);
        drawLineRel(aXPos - tOffsetDiagonal, Y, -aLengthDiagonal, tLength, aColor);
        drawLineRel(aXPos + tOffsetDiagonal, Y, aLengthDiagonal, tLength, aColor);
        Y = aYPos - tOffsetCenter;
        tLength = -tLength;
    }

    X = aXPos + tOffsetCenter;
    int tLengthDiagonal = tLength;
    for (int i = 0; i < 2; i++) {
        drawLineRel(X, aYPos - tOffsetCenter, tLength, -tLengthDiagonal, aColor); // 45
        drawLineRel(X, aYPos + tOffsetCenter, tLength, tLengthDiagonal, aColor); // 45
        X = aXPos - tOffsetCenter;
        tLength = -tLength;
    }

    drawPixel(aXPos, aYPos, COLOR_BLUE);
}

/**
 * Draws a greyscale and 3 color bars
 */
void BlueDisplay::drawGreyscale(uint16_t aXPos, uint16_t tYPos, uint16_t aHeight) {
    uint16_t tY;
    for (int i = 0; i < 256; ++i) {
        tY = tYPos;
        drawLineRel(aXPos, tY, 0, aHeight, RGB(i, i, i));
        tY += aHeight;
        drawLineRel(aXPos, tY, 0, aHeight, RGB((0xFF - i), (0xFF - i), (0xFF - i)));
        tY += aHeight;
        drawLineRel(aXPos, tY, 0, aHeight, RGB(i, 0, 0));
        tY += aHeight;
        drawLineRel(aXPos, tY, 0, aHeight, RGB(0, i, 0));
        tY += aHeight;
        // For Test purposes: fillRectRel instead of drawLineRel gives missing pixel on different scale factors
        fillRectRel(aXPos, tY, 1, aHeight, RGB(0, 0, i));
        aXPos++;
    }
}

#ifdef LOCAL_DISPLAY_EXISTS
/**
 * Draws test page and a greyscale bar
 */
void BlueDisplay::testDisplay(void) {
    clearDisplay(COLOR_WHITE);

    fillRectRel(0, 0, 2, 2, COLOR_RED);
    fillRectRel(mReferenceDisplaySize.XWidth - 3, 0, 3, 3, COLOR_GREEN);
    fillRectRel(0, mReferenceDisplaySize.YHeight - 4, 4, 4, COLOR_BLUE);
    fillRectRel(mReferenceDisplaySize.XWidth - 3, mReferenceDisplaySize.YHeight - 3, 3, 3, COLOR_BLACK);

    fillRectRel(2, 2, 4, 4, COLOR_RED);
    fillRectRel(10, 20, 10, 20, COLOR_RED);
    drawRectRel(8, 18, 14, 24, COLOR_BLUE, 1);
    drawCircle(15, 30, 5, COLOR_BLUE, 1);
    fillCircle(20, 10, 10, COLOR_BLUE);

    drawLineRel(0, mReferenceDisplaySize.YHeight - 1, mReferenceDisplaySize.XWidth, -mReferenceDisplaySize.YHeight,
    COLOR_GREEN);
    drawLineRel(6, 6, mReferenceDisplaySize.XWidth - 9, mReferenceDisplaySize.YHeight - 9, COLOR_BLUE);
    drawChar(50, TEXT_SIZE_11_ASCEND, 'y', TEXT_SIZE_11, COLOR_GREEN, COLOR_YELLOW);
    drawText(0, 50 + TEXT_SIZE_11_ASCEND, "Calibration", TEXT_SIZE_11, COLOR_BLACK, COLOR_WHITE);
    drawText(0, 50 + TEXT_SIZE_11_HEIGHT + TEXT_SIZE_11_ASCEND, "Calibration", TEXT_SIZE_11, COLOR_WHITE,
    COLOR_BLACK);

    drawLineOverlap(120, 140, 180, 125, LINE_OVERLAP_MAJOR, COLOR_RED);
    drawLineOverlap(120, 143, 180, 128, LINE_OVERLAP_MINOR, COLOR_RED);
    drawLineOverlap(120, 146, 180, 131, LINE_OVERLAP_BOTH, COLOR_RED);

    fillRectRel(100, 100, 10, 5, COLOR_RED);
    fillRectRel(90, 95, 10, 5, COLOR_RED);
    fillRectRel(100, 90, 10, 10, COLOR_BLACK);
    fillRectRel(95, 100, 5, 5, COLOR_BLACK);

    drawStar(200, 120, 4, 6, 2, 2, COLOR_BLACK);
    drawStar(250, 120, 8, 12, 4, 4, COLOR_BLACK);

    uint16_t DeltaSmall = 20;
    uint16_t DeltaBig = 100;
    uint16_t tYPos = 30;

    tYPos += 45;
    drawLineWithThickness(10, tYPos, 10 + DeltaSmall, tYPos + DeltaBig, 4, COLOR_GREEN);
    drawPixel(10, tYPos, COLOR_BLUE);

    drawLineWithThickness(70, tYPos, 70 - DeltaSmall, tYPos + DeltaBig, 4, COLOR_GREEN);
    drawPixel(70, tYPos, COLOR_BLUE);

    tYPos += 10;
    drawLineWithThickness(140, tYPos, 140 - DeltaSmall, tYPos - DeltaSmall, 3, COLOR_GREEN);
    drawPixel(140, tYPos, COLOR_BLUE);

    drawLineWithThickness(150, tYPos, 150 + DeltaSmall, tYPos - DeltaSmall, 3, COLOR_GREEN);
    drawPixel(150, tYPos, COLOR_BLUE);

#ifdef LOCAL_DISPLAY_EXISTS
    drawThickLine(190, tYPos, 190 - DeltaSmall, tYPos - DeltaSmall, 3, LINE_THICKNESS_DRAW_CLOCKWISE, COLOR_GREEN);
    drawPixel(190, tYPos, COLOR_BLUE);

    drawThickLine(200, tYPos, 200 + DeltaSmall, tYPos - DeltaSmall, 3, LINE_THICKNESS_DRAW_CLOCKWISE, COLOR_GREEN);
    drawPixel(200, tYPos, COLOR_BLUE);

    tYPos -= 55;
    drawThickLine(140, tYPos, 140 + DeltaBig, tYPos - DeltaSmall, 9, LINE_THICKNESS_DRAW_CLOCKWISE, COLOR_GREEN);
    drawPixel(140, tYPos, COLOR_BLUE);

    tYPos += 5;
    drawThickLine(60, tYPos, 60 + DeltaBig, tYPos + DeltaSmall, 9, LINE_THICKNESS_DRAW_CLOCKWISE, COLOR_GREEN);
    drawPixel(100, tYPos + 5, COLOR_BLUE);
#endif
    drawGreyscale(5, 180, 10);
}
#endif

#define COLOR_SPECTRUM_SEGMENTS 6 // red->yellow, yellow-> green, green-> cyan, cyan-> blue, blue-> magent, magenta-> red
#define COLOR_RESOLUTION 32 // 5 bit for 16 bit color (green really has 6 bit, but dont use it)
const uint16_t colorIncrement[COLOR_SPECTRUM_SEGMENTS] = { 1 << 6, 0x1F << 11, 1, 0x3FF << 6, 1 << 11, 0xFFFF };

/**
 * generates a full color spectrum beginning with a black line,
 * increasing saturation to full colors and then fading to a white line
 * customized for a 320 x 240 display
 */
void BlueDisplay::generateColorSpectrum(void) {
    clearDisplay(COLOR_WHITE);
    uint16_t tColor;
    uint16_t tXPos;
    uint16_t tDelta;
    uint16_t tError;

    uint16_t tColorChangeAmount;
    uint16_t tYpos = mReferenceDisplaySize.YHeight;
    uint16_t tColorLine;
    for (int line = 4; line < mReferenceDisplaySize.YHeight + 4; ++line) {
        tColorLine = line / 4;
        // colors for line 31 and 32 are identical
        if (tColorLine >= COLOR_RESOLUTION) {
            // line 32 to 63 full saturated basic colors to pure white
            tColorChangeAmount = ((2 * COLOR_RESOLUTION) - 1) - tColorLine; // 31 - 0
            tColor = 0x1f << 11 | (tColorLine - COLOR_RESOLUTION) << 6 | (tColorLine - COLOR_RESOLUTION);
        } else {
            // line 0 - 31 pure black to full saturated basic colors
            tColor = tColorLine << 11; // RED
            tColorChangeAmount = tColorLine; // 0 - 31
        }
        tXPos = 0;
        tYpos--;
        for (int i = 0; i < COLOR_SPECTRUM_SEGMENTS; ++i) {
            tDelta = colorIncrement[i];
//          tError = COLOR_RESOLUTION / 2;
//          for (int j = 0; j < COLOR_RESOLUTION; ++j) {
//              // draw start value + 31 slope values
//              _drawPixel(tXPos++, tYpos, tColor);
//              tError += tColorChangeAmount;
//              if (tError > COLOR_RESOLUTION) {
//                  tError -= COLOR_RESOLUTION;
//                  tColor += tDelta;
//              }
//          }
            tError = ((mReferenceDisplaySize.XWidth / COLOR_SPECTRUM_SEGMENTS) - 1) / 2;
            for (int j = 0; j < (mReferenceDisplaySize.XWidth / COLOR_SPECTRUM_SEGMENTS) - 1; ++j) {
                drawPixel(tXPos++, tYpos, tColor);
                tError += tColorChangeAmount;
                if (tError > ((mReferenceDisplaySize.XWidth / COLOR_SPECTRUM_SEGMENTS) - 1)) {
                    tError -= ((mReferenceDisplaySize.XWidth / COLOR_SPECTRUM_SEGMENTS) - 1);
                    tColor += tDelta;
                }
            }
            // draw greyscale in the last 8 pixel :-)
//          _drawPixel(mReferenceDisplaySize.XWidth - 2, tYpos, (tColorLine & 0x3E) << 10 | tColorLine << 5 | tColorLine >> 1);
//          _drawPixel(mReferenceDisplaySize.XWidth - 1, tYpos, (tColorLine & 0x3E) << 10 | tColorLine << 5 | tColorLine >> 1);
            drawLine(mReferenceDisplaySize.XWidth - 8, tYpos, mReferenceDisplaySize.XWidth - 1, tYpos,
                    (tColorLine & 0x3E) << 10 | tColorLine << 5 | tColorLine >> 1);

        }
    }
}

/***************************************************************************************************************************************************
 *
 * INPUT
 *
 **************************************************************************************************************************************************/

void BlueDisplay::getNumber(void (*aNumberHandler)(float)) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_GET_NUMBER, 2, aNumberHandler, ((uint32_t) aNumberHandler >> 16));
    }
}

void BlueDisplay::getNumberWithShortPrompt(void (*aNumberHandler)(float), const char *aShortPromptString) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgsAndByteBuffer(FUNCTION_TAG_GET_NUMBER_WITH_SHORT_PROMPT, 2, aNumberHandler,
                ((uint32_t) aNumberHandler >> 16), strlen(aShortPromptString), (uint8_t*) aShortPromptString);
    }
}

void BlueDisplay::getNumberWithShortPrompt(void (*aNumberHandler)(float), const char *aShortPromptString,
        float aInitialValue) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgsAndByteBuffer(FUNCTION_TAG_GET_NUMBER_WITH_SHORT_PROMPT_AND_INITIAL_VALUE, 3, aNumberHandler,
                ((uint32_t) aNumberHandler >> 16), aInitialValue, strlen(aShortPromptString),
                (uint8_t*) aShortPromptString);
    }
}

void BlueDisplay::getText(void (*aTextHandler)(const char *)) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_GET_TEXT, 2, aTextHandler, ((uint32_t) aTextHandler >> 16));
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
 * BUTTONS
 *
 **************************************************************************************************************************************************/

BDButtonHandle_t BlueDisplay::createButton(uint16_t aPositionX, uint16_t aPositionY, uint16_t aWidthX,
        uint16_t aHeightY, Color_t aButtonColor, const char * aCaption, uint8_t aCaptionSize, uint8_t aFlags,
        int16_t aValue, void (*aOnTouchHandler)(BDButton *, int16_t)) {

    BDButtonHandle_t tButtonNumber = sLocalButtonIndex++;
    if (USART_isBluetoothPaired()) {
        sendUSARTArgsAndByteBuffer(FUNCTION_TAG_BUTTON_CREATE, 10, tButtonNumber, aPositionX, aPositionY, aWidthX,
                aHeightY, aButtonColor, aCaptionSize | (aFlags << 8), aValue, aOnTouchHandler,
                ((uint32_t) aOnTouchHandler >> 16), strlen(aCaption), aCaption);
    }
    return tButtonNumber;
}

void BlueDisplay::drawButton(BDButtonHandle_t aButtonNumber) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_BUTTON_DRAW, 1, aButtonNumber);
    }
}

void BlueDisplay::removeButton(BDButtonHandle_t aButtonNumber, Color_t aBackgroundColor) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_BUTTON_REMOVE, 2, aButtonNumber, aBackgroundColor);
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
        sendUSARTArgs(FUNCTION_TAG_BUTTON_SETTINGS, 3, aButtonNumber, BUTTON_FLAG_SET_BUTTON_COLOR_AND_DRAW,
                aButtonColor);
    }
}

void BlueDisplay::setButtonPosition(BDButtonHandle_t aButtonNumber, int16_t aPositionX, int16_t aPositionY) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_BUTTON_SETTINGS, 4, aButtonNumber, BUTTON_FLAG_SET_POSITION, aPositionX,
                aPositionY);
    }
}

void BlueDisplay::setButtonAutorepeatTiming(BDButtonHandle_t aButtonNumber, uint16_t aMillisFirstDelay,
        uint16_t aMillisFirstRate, uint16_t aFirstCount, uint16_t aMillisSecondRate) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_BUTTON_SETTINGS, 7, aButtonNumber, BUTTON_FLAG_SET_AUTOREPEAT_TIMING,
                aMillisFirstDelay, aMillisFirstRate, aFirstCount, aMillisSecondRate);
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
 * @param aBarWidth width of bar (and border) in pixel
 * @param aBarLength size of slider bar in pixel = maximum slider value
 * @param aThresholdValue value where color of bar changes from #SLIDER_DEFAULT_BAR_COLOR to #SLIDER_DEFAULT_BAR_THRESHOLD_COLOR
 * @param aInitalValue
 * @param aSliderColor
 * @param aBarColor
 * @param aOptions see #SLIDER_SHOW_BORDER etc.
 * @param aOnChangeHandler - if NULL no update of bar is done on touch
 * @return slider index.
 */
BDSliderHandle_t BlueDisplay::createSlider(uint16_t aPositionX, uint16_t aPositionY, uint8_t aBarWidth,
        uint16_t aBarLength, uint16_t aThresholdValue, int16_t aInitalValue, Color_t aSliderColor, Color_t aBarColor,
        uint8_t aOptions, void (*aOnChangeHandler)(BDSliderHandle_t *, int16_t)) {
    BDSliderHandle_t tSliderNumber = localSliderIndex++;

    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_SLIDER_CREATE, 12, tSliderNumber, aPositionX, aPositionY, aBarWidth, aBarLength,
                aThresholdValue, aInitalValue, aSliderColor, aBarColor, aOptions, aOnChangeHandler,
                ((uint32_t) aOnChangeHandler >> 16));
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

void BlueDisplay::setSliderActualValueAndDrawBar(BDSliderHandle_t aSliderNumber, int16_t aActualValue) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_SLIDER_SETTINGS, 3, aSliderNumber, SLIDER_FLAG_SET_VALUE_AND_DRAW_BAR,
                aActualValue);
    }
}

void BlueDisplay::setSliderColorBarThreshold(BDSliderHandle_t aSliderNumber, uint16_t aBarThresholdColor) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_SLIDER_SETTINGS, 3, aSliderNumber, SLIDER_FLAG_SET_COLOR_THRESHOLD,
                aBarThresholdColor);
    }
}

void BlueDisplay::setSliderColorBarBackground(BDSliderHandle_t aSliderNumber, uint16_t aBarBackgroundColor) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_SLIDER_SETTINGS, 3, aSliderNumber, SLIDER_FLAG_SET_COLOR_BAR_BACKGROUND,
                aBarBackgroundColor);
    }
}

void BlueDisplay::setSliderCaptionProperties(BDSliderHandle_t aSliderNumber, uint8_t aCaptionSize,
        uint8_t aCaptionPosition, uint8_t aCaptionMargin, Color_t aCaptionColor, Color_t aCaptionBackgroundColor) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_SLIDER_SETTINGS, 7, aSliderNumber, SLIDER_FLAG_SET_CAPTION_PROPERTIES,
                aCaptionSize, aCaptionPosition, aCaptionMargin, aCaptionColor, aCaptionBackgroundColor);
    }
}

void BlueDisplay::setSliderCaption(BDSliderHandle_t aSliderNumber, const char * aCaption) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgsAndByteBuffer(FUNCTION_TAG_SLIDER_SET_CAPTION, 1, aSliderNumber, strlen(aCaption), aCaption);
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
