/*
 * BDSlider.h
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

#ifndef BLUEDISPLAY_INCLUDE_BDSLIDER_H_
#define BLUEDISPLAY_INCLUDE_BDSLIDER_H_

#include <stdint.h>

#ifdef LOCAL_DISPLAY_EXISTS
#include "TouchSlider.h"
// assume we have only a restricted amount of local sliders
typedef uint8_t BDSliderHandle_t;
#else
typedef uint16_t BDSliderHandle_t;
#endif

extern BDSliderHandle_t localSliderIndex;

//#include "BlueDisplay.h" // for Color_t - cannot be included here since BlueDisplay.h needs BDSlider
typedef uint16_t Color_t;

#ifdef __cplusplus
class BDSlider {
public:

    static void resetAllSliders(void);
    static void activateAllSliders(void);
    static void deactivateAllSliders(void);

    // Constructors
    BDSlider();
#ifdef LOCAL_DISPLAY_EXISTS
    BDSlider(BDSliderHandle_t aSliderHandle, TouchSlider * aLocalSliderPointer);
#endif

    void init(uint16_t aPositionX, uint16_t aPositionY, uint8_t aBarWidth, uint16_t aBarLength,
            uint16_t aThresholdValue, int16_t aInitalValue, Color_t aSliderColor, Color_t aBarColor, uint8_t aFlags,
            void (*aOnChangeHandler)(BDSlider *, uint16_t));

    void drawSlider(void);
    void drawBorder(void);
    void setActualValueAndDrawBar(int16_t aActualValue);
    void setBarColor(Color_t aBarColor);
    void setBarThresholdColor(Color_t aBarThresholdColor);
    void setBarBackgroundColor(Color_t aBarBackgroundColor);

    void setCaptionProperties(uint8_t aCaptionSize, uint8_t aCaptionPosition, uint8_t aCaptionMargin,
            Color_t aCaptionColor, Color_t aCaptionBackgroundColor);
    void setCaption(const char * aCaption);
    void setPrintValueProperties(uint8_t aPrintValueSize, uint8_t aPrintValuePosition, uint8_t aPrintValueMargin,
            Color_t aPrintValueColor, Color_t aPrintValueBackgroundColor);
    void printValue(const char * aValueString);

    void activate(void);
    void deactivate(void);

    BDSliderHandle_t mSliderHandle;

#ifdef LOCAL_DISPLAY_EXISTS
    int printValue();
    void setXOffsetValue(int16_t aXOffsetValue);

    int16_t getActualValue(void) const;
    uint16_t getPositionXRight(void) const;
    uint16_t getPositionYBottom(void) const;
    void deinit(void);
    TouchSlider * mLocalSliderPointer;
#endif

private:
};
#endif

#endif /* BLUEDISPLAY_INCLUDE_BDSLIDER_H_ */
