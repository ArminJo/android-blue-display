/*
 *  BlueDisplayExample.ino
 *  Demo of using the BlueDisplay library for HC-05 on Arduino

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
#include "TouchLib.h"
#include "TouchButton.h"
#include "TouchSlider.h"

#define DELAY_START_VALUE 600
#define DELAY_CHANGE_VALUE 20
// Change this if you have reprogrammed the hc05 module for higher baud rate
#define HC_05_BAUD_RATE 9600

// Pin 13 has an LED connected on most Arduino boards.
int led = 13;

bool doBlink = true;
volatile int sDelay = 600;

// a string buffer for any purpose...
char StringBuffer[128];

TouchButton TouchButtonOnOff;
TouchButton TouchButtonPlus;
TouchButton TouchButtonMinus;

TouchSlider TouchSliderDelay;
// handler for value changed
uint8_t doDelay(TouchSlider * const aTheTochedSlider, uint8_t aSliderValue);
// mapping for value displayed
const char * mapValueDelay(uint8_t aSliderValue);

void drawGui(void);
void checkEvents(void);

// Callback touch handler
void doStartStop(TouchButton * const aTheTochedButton, int16_t aValue);
void doPlusMinus(TouchButton * const aTheTochedButton, int16_t aValue);

void setup() {

    // initialize the digital pin as an output.
    pinMode(led, OUTPUT);

    Serial.begin(HC_05_BAUD_RATE);

    TouchButtonOnOff.initButton(40, 150, 240, 55, "Stop", 44, TOUCHBUTTON_DEFAULT_TOUCH_BORDER, COLOR_RED, COLOR_BLACK, 0,
            &doStartStop);

    TouchButtonPlus.initButton(270, 80, 40, 40, "+", 33, TOUCHBUTTON_DEFAULT_TOUCH_BORDER, COLOR_YELLOW, COLOR_BLACK,
            DELAY_CHANGE_VALUE, &doPlusMinus);

    TouchButtonMinus.initButton(10, 80, 40, 40, "-", 33, TOUCHBUTTON_DEFAULT_TOUCH_BORDER, COLOR_YELLOW, COLOR_BLACK,
            -DELAY_CHANGE_VALUE, &doPlusMinus);

    TouchSliderDelay.initSlider(80, 50, 6, 150, 100, DELAY_START_VALUE / 10, "Delay", 0,
            TOUCHSLIDER_SHOW_BORDER | TOUCHSLIDER_SHOW_VALUE | TOUCHSLIDER_IS_HORIZONTAL, &doDelay, &mapValueDelay);
    TouchSliderDelay.initValueAndCaptionBackgroundColor(COLOR_CYAN);

    drawGui();
}

void loop() {
    uint8_t i;
    checkEvents();
    if (doBlink) {
        // wait for delay time but check events 8 times while waiting
        for (i = 0; i < 8; ++i) {
            checkEvents();
            digitalWrite(led, HIGH);  // turn the LED on (HIGH is the voltage level)
            delay(sDelay / 8);
        }
        for (i = 0; i < 8; ++i) {
            checkEvents();
            digitalWrite(led, LOW);    // turn the LED off by making the voltage LOW
            delay(sDelay / 8);
        }
    }
}

void drawGui(void) {
    BlueDisplay1.clearDisplay(COLOR_CYAN);
    TouchButtonOnOff.drawButton();
    TouchButtonPlus.drawButton();
    TouchButtonMinus.drawButton();
    TouchSliderDelay.drawSlider();
}

void checkEvents(void) {
    if (wasTouched()) {
        /*
         * check if one of the above button was touched. This in turn calls the callback of the touched button.
         */
        TouchButton::checkAllButtons(getXActual(), getYActual());
    }
    TouchSlider::checkAllSliders(getXActual(), getYActual());
    if (BlueDisplay1.isConnectionJustBuildUp()) {
        BlueDisplay1.setFlagsAndSize(BD_FLAG_USE_MAX_SIZE, 320, 240);
    }
    if (BlueDisplay1.needsRefresh()) {
        drawGui();
    }

}
void doStartStop(TouchButton * const aTheTouchedButton, int16_t aValue) {
    if (aValue == 1) {
        aTheTouchedButton->setCaption("Stop");
        aTheTouchedButton->setColor(COLOR_RED);
        aTheTouchedButton->setValue(0);
        doBlink = true;
    } else {
        aTheTouchedButton->setCaption("Start");
        aTheTouchedButton->setColor(COLOR_GREEN);
        aTheTouchedButton->setValue(1);
        doBlink = false;
    }
    aTheTouchedButton->drawButton();
}

uint8_t doDelay(TouchSlider * const aTheTouchedSlider, uint8_t aSliderValue) {
    sDelay = 10 * aSliderValue;
    return aSliderValue;
}

const char * mapValueDelay(uint8_t aSliderValue) {
    uint16_t tSliderValue = aSliderValue * 10;
    snprintf(StringBuffer, sizeof StringBuffer, "%04u", tSliderValue);
    return StringBuffer;
}

void doPlusMinus(TouchButton * const aTheTouchedButton, int16_t aValue) {
    sDelay += aValue;
    if (sDelay < DELAY_CHANGE_VALUE) {
        sDelay = DELAY_CHANGE_VALUE;
    }
    doBlink = true;
    TouchSliderDelay.setActualValue(sDelay / 10);
}
