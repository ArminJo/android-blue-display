/*
 * BDSlider.cpp
 *
 *   SUMMARY
 *  Blue Display is an Open Source Android remote Display for Arduino etc.
 *  It receives basic draw requests from Arduino etc. over Bluetooth and renders it.
 *  It also implements basic GUI elements as buttons and sliders.
 *  GUI callback, touch and sensor events are sent back to Arduino.
 *
 *  Copyright (C) 2015  Armin Joachimsmeyer
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

#include "BDSlider.h"
#include "BlueDisplayProtocol.h"
#include "BlueSerial.h"

#include <string.h>  // for strlen
#include <stdlib.h> // for malloc/free

BDSliderHandle_t sLocalSliderIndex = 0;

BDSlider::BDSlider(void) {
}

#ifdef LOCAL_DISPLAY_EXISTS
BDSlider::BDSlider(BDSliderHandle_t aSliderHandle, TouchSlider * aLocalSliderPointer) {
    mSliderHandle = aSliderHandle;
    mLocalSliderPointer = aLocalSliderPointer;
}
#endif

/**
 * @brief initialization with all parameters except BarBackgroundColor
 * @param aPositionX determines upper left corner
 * @param aPositionY determines upper left corner
 * @param aBarWidth width of bar (and border) in pixel
 * @param aBarLength size of slider bar in pixel = maximum slider value
 * @param aThresholdValue value - if bigger, then color of bar changes from BarColor to BarBackgroundColor
 * @param aInitalValue
 * @param aSliderColor color of slider frame
 * @param aBarColor
 * @param aOptions see #FLAG_SLIDER_SHOW_BORDER etc.
 * @param aOnChangeHandler - if NULL no update of bar is done on touch
 */
void BDSlider::init(uint16_t aPositionX, uint16_t aPositionY, uint8_t aBarWidth, int16_t aBarLength, int16_t aThresholdValue,
        int16_t aInitalValue, Color_t aSliderColor, Color_t aBarColor, uint8_t aFlags,
        void (*aOnChangeHandler)(BDSlider *, uint16_t)) {
    BDSliderHandle_t tSliderNumber = sLocalSliderIndex++;

    if (USART_isBluetoothPaired()) {
#if (FLASHEND > 65535 || AVR != 1)
        sendUSARTArgs(FUNCTION_SLIDER_CREATE, 12, tSliderNumber, aPositionX, aPositionY, aBarWidth, aBarLength,
                aThresholdValue, aInitalValue, aSliderColor, aBarColor, aFlags, aOnChangeHandler,
                (reinterpret_cast<uint32_t>(aOnChangeHandler) >> 16));
#else
        sendUSARTArgs(FUNCTION_SLIDER_CREATE, 11, tSliderNumber, aPositionX, aPositionY, aBarWidth, aBarLength, aThresholdValue,
                aInitalValue, aSliderColor, aBarColor, aFlags, aOnChangeHandler);
#endif
    }
    mSliderHandle = tSliderNumber;

#ifdef LOCAL_DISPLAY_EXISTS
    mLocalSliderPointer = new TouchSlider();
    // Cast needed here. At runtime the right pointer is returned because of FLAG_USE_INDEX_FOR_CALLBACK
    mLocalSliderPointer->initSlider(aPositionX, aPositionY, aBarWidth, aBarLength, aThresholdValue, aInitalValue,
            aSliderColor, aBarColor, aFlags | FLAG_USE_BDSLIDER_FOR_CALLBACK,
            reinterpret_cast<void (*)(TouchSlider *, uint16_t)>( aOnChangeHandler));
    // keep the formatting
    mLocalSliderPointer ->mBDSliderPtr = this;
#endif
}

#ifdef LOCAL_DISPLAY_EXISTS
void BDSlider::deinit(void) {
    sLocalSliderIndex--;
    delete mLocalSliderPointer;
}
#endif

void BDSlider::drawSlider(void) {
#ifdef LOCAL_DISPLAY_EXISTS
    mLocalSliderPointer->drawSlider();
#endif
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_SLIDER_DRAW, 1, mSliderHandle);
    }
}

void BDSlider::drawBorder(void) {
#ifdef LOCAL_DISPLAY_EXISTS
    mLocalSliderPointer->drawBorder();
#endif
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_SLIDER_DRAW_BORDER, 1, mSliderHandle);
    }
}

void BDSlider::setActualValueAndDrawBar(int16_t aActualValue) {
#ifdef LOCAL_DISPLAY_EXISTS
    mLocalSliderPointer->setActualValueAndDrawBar(aActualValue);
#endif
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_SLIDER_SETTINGS, 3, mSliderHandle, SUBFUNCTION_SLIDER_SET_VALUE_AND_DRAW_BAR, aActualValue);
    }
}

void BDSlider::setBarThresholdColor(Color_t aBarThresholdColor) {
#ifdef LOCAL_DISPLAY_EXISTS
    mLocalSliderPointer->setBarThresholdColor(aBarThresholdColor);
#endif
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_SLIDER_SETTINGS, 3, mSliderHandle, SUBFUNCTION_SLIDER_SET_COLOR_THRESHOLD, aBarThresholdColor);
    }
}

void BDSlider::setBarBackgroundColor(Color_t aBarBackgroundColor) {
#ifdef LOCAL_DISPLAY_EXISTS
    mLocalSliderPointer->setBarBackgroundColor(aBarBackgroundColor);
#endif
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_SLIDER_SETTINGS, 3, mSliderHandle, SUBFUNCTION_SLIDER_SET_COLOR_BAR_BACKGROUND, aBarBackgroundColor);
    }
}

void BDSlider::setCaptionProperties(uint8_t aCaptionSize, uint8_t aCaptionPosition, uint8_t aCaptionMargin, Color_t aCaptionColor,
        Color_t aCaptionBackgroundColor) {
#ifdef LOCAL_DISPLAY_EXISTS
    mLocalSliderPointer->setCaptionColors(aCaptionColor, aCaptionBackgroundColor);
#endif
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_SLIDER_SETTINGS, 7, mSliderHandle, SUBFUNCTION_SLIDER_SET_CAPTION_PROPERTIES, aCaptionSize,
                aCaptionPosition, aCaptionMargin, aCaptionColor, aCaptionBackgroundColor);
    }
}

void BDSlider::setCaption(const char * aCaption) {
#ifdef LOCAL_DISPLAY_EXISTS
    mLocalSliderPointer->setCaption(aCaption);
#endif
    if (USART_isBluetoothPaired()) {
        sendUSARTArgsAndByteBuffer(FUNCTION_SLIDER_SET_CAPTION, 1, mSliderHandle, strlen(aCaption), aCaption);
    }
}

void BDSlider::setPrintValueProperties(uint8_t aPrintValueSize, uint8_t aPrintValuePosition, uint8_t aPrintValueMargin,
        Color_t aPrintValueColor, Color_t aPrintValueBackgroundColor) {
#ifdef LOCAL_DISPLAY_EXISTS
    mLocalSliderPointer->setValueStringColors(aPrintValueColor, aPrintValueBackgroundColor);
#endif
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_SLIDER_SETTINGS, 7, mSliderHandle, SUBFUNCTION_SLIDER_SET_VALUE_STRING_PROPERTIES, aPrintValueSize,
                aPrintValuePosition, aPrintValueMargin, aPrintValueColor, aPrintValueBackgroundColor);
    }
}

void BDSlider::printValue(const char * aValueString) {
#ifdef LOCAL_DISPLAY_EXISTS
    mLocalSliderPointer->printValue(aValueString);
#endif
    if (USART_isBluetoothPaired()) {
        sendUSARTArgsAndByteBuffer(FUNCTION_SLIDER_PRINT_VALUE, 1, mSliderHandle, strlen(aValueString), aValueString);
    }
}

void BDSlider::activate(void) {
#ifdef LOCAL_DISPLAY_EXISTS
    mLocalSliderPointer->activate();
#endif
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_SLIDER_SETTINGS, 2, mSliderHandle, SUBFUNCTION_SLIDER_SET_ACTIVE);
    }
}

void BDSlider::deactivate(void) {
#ifdef LOCAL_DISPLAY_EXISTS
    mLocalSliderPointer->deactivate();
#endif
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_SLIDER_SETTINGS, 2, mSliderHandle, SUBFUNCTION_SLIDER_RESET_ACTIVE);
    }
}

#ifdef LOCAL_DISPLAY_EXISTS

int BDSlider::printValue(void) {
    return mLocalSliderPointer->printValue();
}

void BDSlider::setXOffsetValue(int16_t aXOffsetValue) {
    mLocalSliderPointer->setXOffsetValue(aXOffsetValue);
}

int16_t BDSlider::getActualValue() const {
    return mLocalSliderPointer->getActualValue();
}

uint16_t BDSlider::getPositionXRight() const {
    return mLocalSliderPointer->getPositionXRight();
}

uint16_t BDSlider::getPositionYBottom() const {
    return mLocalSliderPointer->getPositionYBottom();
}
#endif

/*
 * Static functions
 */
void BDSlider::resetAllSliders(void) {
    sLocalSliderIndex = 0;
}

void BDSlider::activateAllSliders(void) {
#ifdef LOCAL_DISPLAY_EXISTS
    TouchSlider::activateAllSliders();
#endif
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_SLIDER_ACTIVATE_ALL, 0);
    }
}

void BDSlider::deactivateAllSliders(void) {
#ifdef LOCAL_DISPLAY_EXISTS
    TouchSlider::deactivateAllSliders();
#endif
    if (USART_isBluetoothPaired()) {
        sendUSARTArgs(FUNCTION_SLIDER_DEACTIVATE_ALL, 0);
    }
}

