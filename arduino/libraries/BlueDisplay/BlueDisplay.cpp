/*
 * BlueDisplay.cpp
 * C stub for Android BlueDisplay app and the local MI0283QT2 Display from Watterott.
 * It implements a few display test functions.
 *
 *  Created on: 12.09.2014
 * @author Armin Joachimsmeyer
 *      Email:   armin.joachimsmeyer@gmail.com
 *      License: LGPL v3 (http://www.gnu.org/licenses/lgpl.html)
 * @version 1.0.0
 *
 */

#include <Arduino.h>
#include "BlueDisplay.h"

#include "BlueSerial.h"

const int FUNCTION_TAG_SET_FLAGS = 0x08;
const int BD_FLAG_COMPATIBILITY_MODE_ENABLE = 0x01;
const int BD_FLAG_TOUCH_DISABLE = 0x02;
const int BD_FLAG_TOUCH_MOVE_DISABLE = 0x04;
const int BD_FLAG_USE_MAX_SIZE = 0x08;

const int FUNCTION_TAG_SET_FLAGS_AND_SIZE = 0x09;
const int FUNCTION_TAG_SET_CODEPAGE = 0x0A;
const int FUNCTION_TAG_SET_CHARACTER_CODE_MAPPING = 0x0B;

const int LAST_FUNCTION_TAG_CONFIGURATION = 0x0F;
const int FUNCTION_TAG_CLEAR_DISPLAY = 0x10;

//3 parameter
const int FUNCTION_TAG_DRAW_PIXEL = 0x14;

// 5 parameter
const int FUNCTION_TAG_DRAW_LINE = 0x20;
const int FUNCTION_TAG_DRAW_RECT = 0x21;
const int FUNCTION_TAG_FILL_RECT = 0x22;
const int FUNCTION_TAG_DRAW_CIRCLE = 0x24;
const int FUNCTION_TAG_FILL_CIRCLE = 0x25;

const int LAST_FUNCTION_TAG_WITHOUT_DATA = 0x5F;
// Function with variable data size
const int FUNCTION_TAG_DRAW_CHAR = 0x60;
const int FUNCTION_TAG_DRAW_STRING = 0x61;

const int FUNCTION_TAG_DRAW_PATH = 0x68;
const int FUNCTION_TAG_FILL_PATH = 0x69;
const int FUNCTION_TAG_DRAW_CHART = 0x6A;

/*
 * Button and slider functions
 */
const int FUNCTION_TAG_BUTTON_CREATE = 0x40;
const int FUNCTION_TAG_BUTTON_DELETE = 0x41;
const int FUNCTION_TAG_BUTTON_DRAW = 0x42;
const int FUNCTION_TAG_BUTTON_DRAW_CAPTION = 0x43;
const int FUNCTION_TAG_BUTTON_SETTINGS = 0x44;
// Flags for BUTTON_SETTINGS
const int BUTTON_FLAG_SET_COLOR_BUTTON = 0x00;
const int BUTTON_FLAG_SET_COLOR_CAPTION = 0x01;
const int BUTTON_FLAG_SET_VALUE = 0x02;
const int BUTTON_FLAG_SET_POSITION = 0x03;
const int BUTTON_FLAG_SET_ACTIVE = 0x04;

const int FUNCTION_TAG_BUTTON_SET_CAPTION = 0x70;

const int FUNCTION_TAG_SLIDER_CREATE = 0x48;
const int FUNCTION_TAG_SLIDER_DELETE = 0x49;
const int FUNCTION_TAG_SLIDER_DRAW = 0x4A;

const int FUNCTION_TAG_SLIDER_SETTINGS = 0x4C;
// Flags for SLIDER_SETTINGS
const int BUTTON_FLAG_SET_COLOR_BAR = 0x01;
const int SLIDER_FLAG_SET_VALUE_AND_DRAW_BAR = 0x02;
const int SLIDER_FLAG_SET_POSITION = 0x03;
const int SLIDER_FLAG_SET_ACTIVE = 0x04;

//-------------------- Constructor --------------------

BlueDisplay::BlueDisplay(void) {
    mDisplayHeight = DISPLAY_DEFAULT_HEIGHT;
    mDisplayWidth = DISPLAY_DEFAULT_WIDTH;
    mNeedsRefresh = false;
    return;
}

// One instance of BlueDisplay called BlueDisplay1
BlueDisplay BlueDisplay1;

void BlueDisplay::setFlags(uint16_t aFlags) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_SET_FLAGS, 1, aFlags);
    }
}

void BlueDisplay::setFlagsAndSize(uint16_t aFlags, uint16_t aWidth, uint16_t aHeight) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_SET_FLAGS_AND_SIZE, 3, aFlags, aWidth, aHeight);
    }
}

void BlueDisplay::setCharacterMapping(uint8_t aChar, uint16_t aUnicodeChar) {
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_SET_CHARACTER_CODE_MAPPING, 2, aChar, aUnicodeChar);
    }
}

void BlueDisplay::clearDisplay(uint16_t aColor) {
#ifdef LOCAL_DISPLAY_EXISTS
    LocalDisplay.clear(aColor);
#endif
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_CLEAR_DISPLAY, 1, aColor);
    }
}

void BlueDisplay::drawPixel(uint16_t aXPos, uint16_t aYPos, uint16_t aColor) {
#ifdef LOCAL_DISPLAY_EXISTS
    LocalDisplay.drawPixel(aXPos, aYPos, aColor);
#endif
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_DRAW_PIXEL, 3, aXPos, aYPos, aColor);
    }
}

void BlueDisplay::drawLine(uint16_t aXStart, uint16_t aYStart, uint16_t aXEnd, uint16_t aYEnd, uint16_t aColor) {
#ifdef LOCAL_DISPLAY_EXISTS
    LocalDisplay.drawLine(aXStart, aYStart, aXEnd, aYEnd, aColor);
#endif
    if (USART_isBluetoothPaired()) {
        sendUSART5Args(FUNCTION_TAG_DRAW_LINE, aXStart, aYStart, aXEnd, aYEnd, aColor);
    }
}

/**
 * Fast routine for drawing data charts
 * draws a line only from x to x+1
 * first pixel is omitted because it is drawn by preceding line
 * uses setArea instead if drawPixel to speed up drawing
 */
void BlueDisplay::drawLineFastOneX(uint16_t aXStart, uint16_t aYStart, uint16_t aYEnd, uint16_t aColor) {
#ifdef LOCAL_DISPLAY_EXISTS
    LocalDisplay.drawLineFastOneX(aXStart, aYStart, aYEnd, aColor);
#endif
    if (USART_isBluetoothPaired()) {
        // Just draw plain line, no need to speed up
        sendUSART5Args(FUNCTION_TAG_DRAW_LINE, aXStart, aYStart, aXStart, aYEnd, aColor);
    }
}

void BlueDisplay::drawLineWithThickness(uint16_t aXStart, uint16_t aYStart, uint16_t aXEnd, uint16_t aYEnd, int16_t aThickness,
        uint8_t aThicknessMode, uint16_t aColor) {
    sendUSARTArgs(FUNCTION_TAG_DRAW_LINE, 6, aXStart, aYStart, aXEnd, aYEnd, aColor, aThickness);
}

void BlueDisplay::drawRect(uint16_t aXStart, uint16_t aYStart, uint16_t aXEnd, uint16_t aYEnd, uint16_t aColor,
        uint16_t aStrokeWidth) {
#ifdef LOCAL_DISPLAY_EXISTS
    LocalDisplay.drawRect(aXStart, aYStart, aXEnd, aYEnd, aColor);
#endif
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_DRAW_RECT, 6, aXStart, aYStart, aXEnd, aYEnd, aColor, aStrokeWidth);
    }
}

void BlueDisplay::fillRect(uint16_t aXStart, uint16_t aYStart, uint16_t aXEnd, uint16_t aYEnd, uint16_t aColor) {
#ifdef LOCAL_DISPLAY_EXISTS
    LocalDisplay.fillRect(aXStart, aYStart, aXEnd, aYEnd, aColor);
#endif
    if (USART_isBluetoothPaired()) {
        sendUSART5Args(FUNCTION_TAG_FILL_RECT, aXStart, aYStart, aXEnd, aYEnd, aColor);
    }
}

void BlueDisplay::drawCircle(uint16_t aXCenter, uint16_t aYCenter, uint16_t aRadius, uint16_t aColor, uint16_t aStrokeWidth) {
#ifdef LOCAL_DISPLAY_EXISTS
    LocalDisplay.drawCircle(aXCenter, aYCenter, aRadius, aColor);
#endif
    if (USART_isBluetoothPaired()) {
        sendUSART5Args(FUNCTION_TAG_DRAW_CIRCLE, aXCenter, aYCenter, aRadius, aColor, aStrokeWidth);
    }
}

void BlueDisplay::fillCircle(uint16_t aXCenter, uint16_t aYCenter, uint16_t aRadius, uint16_t aColor) {
#ifdef LOCAL_DISPLAY_EXISTS
    LocalDisplay.fillCircle(aXCenter, aYCenter, aRadius, aColor);
#endif
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_TAG_FILL_CIRCLE, 4, aXCenter, aYCenter, aRadius, aColor);
    }
}

/**
 * @return start x for next character / x + (FONT_WIDTH * size)
 */
uint16_t BlueDisplay::drawChar(uint16_t aPosX, uint16_t aPosY, char aChar, uint8_t aCharSize, uint16_t aFGColor,
        uint16_t aBGColor) {
    uint16_t tRetValue = 0;
#ifdef LOCAL_DISPLAY_EXISTS
    tRetValue = LocalDisplay.drawChar(aPosX, aPosY - getTextAscend(aCharSize), aChar, getLocalTextSize(aCharSize), aFGColor,
            aBGColor);
#endif
    if (USART_isBluetoothPaired()) {
        tRetValue = aPosX + getTextWidth(aCharSize);
        sendUSART5ArgsAndByteBuffer(FUNCTION_TAG_DRAW_CHAR, aPosX, aPosY, aCharSize, aFGColor, aBGColor, (uint8_t*) &aChar, 1);
    }
    return tRetValue;
}

/**
 * @param aXStart left position
 * @param aYStart upper position
 * @param aStringPtr is (const char *) to avoid endless casts for string constants
 * @return uint16_t start y for next line - next y Parameter
 */
uint16_t drawMLText(uint16_t aPosX, uint16_t aPosY, const char *aStringPtr, uint8_t aTextSize, uint16_t aColor, uint16_t aBGColor) {
    uint16_t tRetValue = 0;
#ifdef LOCAL_DISPLAY_EXISTS
    tRetValue = LocalDisplay.drawMLText(aPosX, aPosY - getTextAscend(aTextSize), (char *) aStringPtr, getLocalTextSize(aTextSize),
            aColor, aBGColor);
#endif
    if (USART_isBluetoothPaired()) {
        sendUSART5ArgsAndByteBuffer(FUNCTION_TAG_DRAW_STRING, aPosX, aPosY, aTextSize, aColor, aBGColor, (uint8_t*) aStringPtr,
                strlen(aStringPtr));
    }
    return tRetValue;
}

/**
 * @param aXStart left position
 * @param aYStart upper position
 * @param aStringPtr is (const char *) to avoid endless casts for string constants
 * @return uint16_t start x for next character - next x Parameter
 */
uint16_t BlueDisplay::drawText(uint16_t aPosX, uint16_t aPosY, const char *aStringPtr, uint8_t aTextSize, uint16_t aColor,
        uint16_t aBGColor) {
    uint16_t tRetValue = 0;
#ifdef LOCAL_DISPLAY_EXISTS
    tRetValue = LocalDisplay.drawText(aPosX, aPosY - getTextAscend(aTextSize), (char *) aStringPtr, getLocalTextSize(aTextSize),
            aColor, aBGColor);
#endif
    if (USART_isBluetoothPaired()) {
        tRetValue = aPosX + strlen(aStringPtr) * getTextWidth(aTextSize);
        sendUSART5ArgsAndByteBuffer(FUNCTION_TAG_DRAW_STRING, aPosX, aPosY, aTextSize, aColor, aBGColor, (uint8_t*) aStringPtr,
                strlen(aStringPtr));
    }
    return tRetValue;
}

extern char StringBuffer[40];
uint16_t BlueDisplay::drawTextPGM(uint16_t aXStart, uint16_t aYStart, PGM_P aPGMString, uint8_t aFontSize, uint16_t aColor,
uint16_t aBGColor) {
    strcpy_P(StringBuffer,aPGMString);
    return drawText(aXStart, aYStart, StringBuffer, aFontSize, aColor, aBGColor);
}

/**
 * Fast routine for drawing data charts
 */
void BlueDisplay::drawChartByteBuffer(uint16_t aXOffset, uint16_t aYOffset, uint16_t aColor, uint16_t aClearBeforeColor,
        uint8_t *aByteBuffer, uint16_t aByteBufferLength) {
    if (USART_isBluetoothPaired()) {
        // Just draw plain line, no need to speed up
        sendUSART5ArgsAndByteBuffer(FUNCTION_TAG_DRAW_CHART, aXOffset, aYOffset, aColor, aClearBeforeColor, 0, aByteBuffer,
                aByteBufferLength);
    }
}

void BlueDisplay::setMaxDisplaySize(int aMaxDisplayWidth, int aMaxDisplayHeight) {
    mMaxDisplayWidth = aMaxDisplayWidth;
    mMaxDisplayHeight = aMaxDisplayHeight;
}

uint16_t BlueDisplay::getDisplayWidth(void) {
    return mDisplayWidth;
}

uint16_t BlueDisplay::getDisplayHeight(void) {
    return mDisplayHeight;
}

void BlueDisplay::setNeedsRefresh(void) {
    mNeedsRefresh = true;
}

bool BlueDisplay::needsRefresh(void) {
    if (mNeedsRefresh) {
        mNeedsRefresh = false;
        return true;
    }
    return false;
}

void BlueDisplay::setConnectionJustBuildUp(void) {
    mConnectionBuildUp = true;
}

bool BlueDisplay::isConnectionJustBuildUp(void) {
    if (mConnectionBuildUp) {
        mConnectionBuildUp = false;
        return true;
    }
    return false;
}

/*
 * Formula for Monospace Font on Android
 * TextSize * 0.6
 * Integer Formula: (TextSize *6)+4 / 10
 */
uint8_t getTextWidth(uint8_t aTextSize) {
    if (aTextSize == 11) {
        return 7;
    }
    if (aTextSize == 22) {
        return 13;
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
