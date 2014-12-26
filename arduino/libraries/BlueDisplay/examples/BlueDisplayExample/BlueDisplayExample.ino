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
#include "BlueSerial.h"
#include "TouchLib.h"

// Change this if you have reprogrammed the hc05 module for higher baud rate
//#define HC_05_BAUD_RATE BAUD_9600
#define HC_05_BAUD_RATE BAUD_115200
#define DISPLAY_WIDTH 320
#define DISPLAY_HEIGHT 240

#define DELAY_START_VALUE 600
#define DELAY_CHANGE_VALUE 20

#define COLOR_DEMO_BACKGROUND COLOR_BLUE

// Pin 13 has an LED connected on most Arduino boards.
int led = 13;

volatile bool doBlink = true;
volatile int sDelay = 600;

// a string buffer for any purpose...
char StringBuffer[128];

/*
 * The 3 buttons
 */
uint8_t TouchButtonOnOff;
uint8_t TouchButtonPlus;
uint8_t TouchButtonMinus;
uint8_t TouchButtonValueDirect;

// Touch handler for buttons
void doStartStop(uint8_t aTheTochedButton, int16_t aValue);
void doPlusMinus(uint8_t aTheTochedButton, int16_t aValue);
void doGetDelay(uint8_t aTheTouchedButton, int16_t aValue);

/*
 * The horizontal slider
 */
uint8_t TouchSliderDelay;
// handler for value change
void doDelay(uint8_t aTheTochedSlider, int16_t aSliderValue);
void printDelayValue(void);

// Callback handler for reconnect and resize
void initDisplay(void);
void drawGui(void);

void setup() {
    // initialize the digital pin as an output.
    pinMode(led, OUTPUT);
#ifdef USE_SIMPLE_SERIAL
    initSimpleSerial(HC_05_BAUD_RATE, false);
#else
    Serial.begin(HC_05_BAUD_RATE);
#endif
    // Must be called first since it sets the display size,
    // which is used by internal slider plausibility
    initDisplay();

    // Register callback handler
    registerSimpleConnectCallback(&initDisplay);
    registerSimpleResizeAndReconnectCallback(&drawGui);
    drawGui();
}

void loop() {
    if (doBlink) {
        uint8_t i;
        if (doBlink) {
            digitalWrite(led, HIGH);  // LED on
            BlueDisplay1.fillCircle(DISPLAY_WIDTH / 2, DISPLAY_HEIGHT / 2, 20, COLOR_RED);
            // wait for delay time but check touch events 8 times while waiting
            for (i = 0; i < 8; ++i) {
                checkAndHandleEvents();
                delay(sDelay / 8);
            }
            digitalWrite(led, LOW);  // LED off
            BlueDisplay1.fillCircle(DISPLAY_WIDTH / 2, DISPLAY_HEIGHT / 2, 20, COLOR_DEMO_BACKGROUND);
            for (i = 0; i < 8; ++i) {
                checkAndHandleEvents();
                delay(sDelay / 8);
            }
        }
    } else {
        checkAndHandleEvents();
    }
}

void initDisplay(void) {
    BlueDisplay1.setFlagsAndSize(BD_FLAG_FIRST_RESET_ALL | BD_FLAG_USE_MAX_SIZE | BD_FLAG_TOUCH_BASIC_DISABLE, DISPLAY_WIDTH,
            DISPLAY_HEIGHT);
    const char * tStartStopString = "Start";
    if (doBlink == true) {
        tStartStopString = "Stop";
    }

    TouchButtonPlus = BlueDisplay1.createButton(270, 80, 40, 40, COLOR_YELLOW, "+", 33, BUTTON_FLAG_DO_BEEP_ON_TOUCH,
            DELAY_CHANGE_VALUE, &doPlusMinus);
    TouchButtonMinus = BlueDisplay1.createButton(10, 80, 40, 40, COLOR_YELLOW, "-", 33, BUTTON_FLAG_DO_BEEP_ON_TOUCH,
            -DELAY_CHANGE_VALUE, &doPlusMinus);

    TouchButtonOnOff = BlueDisplay1.createButton(30, 150, 140, 55, COLOR_DEMO_BACKGROUND, tStartStopString, 44,
            BUTTON_FLAG_DO_BEEP_ON_TOUCH, 0, &doStartStop);
    // set color and value accordingly
    BlueDisplay1.setRedGreenButtonColorAndDraw(TouchButtonOnOff, doBlink);
    TouchButtonValueDirect = BlueDisplay1.createButton(210, 150, 90, 55, COLOR_YELLOW, "...", 44, BUTTON_FLAG_DO_BEEP_ON_TOUCH, 0,
            &doGetDelay);

    TouchSliderDelay = BlueDisplay1.createSlider(80, 40, 12, 150, 100, DELAY_START_VALUE / 10, COLOR_YELLOW, COLOR_GREEN,
            TOUCHSLIDER_SHOW_BORDER | TOUCHSLIDER_IS_HORIZONTAL, &doDelay);
}

void drawGui(void) {
    BlueDisplay1.clearDisplay(COLOR_DEMO_BACKGROUND);
    BlueDisplay1.drawButton(TouchButtonOnOff);
    BlueDisplay1.drawButton(TouchButtonPlus);
    BlueDisplay1.drawButton(TouchButtonMinus);
    BlueDisplay1.drawButton(TouchButtonValueDirect);
    BlueDisplay1.drawSlider(TouchSliderDelay);
    BlueDisplay1.drawText(DISPLAY_WIDTH / 2 - 2 * TEXT_SIZE_22_WIDTH, 40 - 4 - TEXT_SIZE_22_DECEND, "Delay", TEXT_SIZE_22,
            COLOR_RED, COLOR_DEMO_BACKGROUND);
    printDelayValue();
}

/*
 * Change doBlink flag as well as color and caption of the button.
 * Value is used for demonstration purposes in this case
 * you could also use doBlink flag for distinguish the two states
 */
void doStartStop(uint8_t aTheTouchedButton, int16_t aValue) {
    doBlink = !aValue;
    if (doBlink) {
        // green stop button
        BlueDisplay1.setButtonCaption(aTheTouchedButton, "Stop", true);
    } else {
        // red start button
        BlueDisplay1.setButtonCaption(aTheTouchedButton, "Start", true);
    }
    BlueDisplay1.setRedGreenButtonColorAndDraw(aTheTouchedButton, doBlink);
}

/*
 * Is called by touch of both plus and minus buttons.
 * We just take the passed value and do not care which button was touched
 */
void doPlusMinus(uint8_t aTheTouchedButton, int16_t aValue) {
    sDelay += aValue;
    if (sDelay < DELAY_CHANGE_VALUE) {
        sDelay = DELAY_CHANGE_VALUE;
    }
    if (!doBlink) {
        // enable blinking
        doStartStop(TouchButtonOnOff, false);
    }
    // set slider bar accordingly
    BlueDisplay1.setSliderActualValueAndDraw(TouchSliderDelay, sDelay / 10);
    printDelayValue();
}

/*
 * Handler for number receive event - set delay to value
 */
void doSetDelay(float aValue) {
// clip value
    if (aValue > 100000) {
        aValue = 100000;
    } else if (aValue < 1) {
        aValue = 1;
    }
    sDelay = aValue;
    // set slider bar accordingly
    BlueDisplay1.setSliderActualValueAndDraw(TouchSliderDelay, sDelay / 10);
    printDelayValue();
}

/*
 * Request delay value as number
 */
void doGetDelay(uint8_t aTheTouchedButton, int16_t aValue) {
    BlueDisplay1.getNumberWithShortPrompt(&doSetDelay, "delay [ms]");
}

/*
 * Is called by touch or move on slider and sets the new delay according to the passed slider value
 */
void doDelay(uint8_t aTheTouchedSlider, int16_t aSliderValue) {
    sDelay = 10 * aSliderValue;
    printDelayValue();
}

void printDelayValue(void) {
    snprintf(StringBuffer, sizeof StringBuffer, "%4ums", sDelay);
    BlueDisplay1.drawText(80, 40 + 3 * 12 + TEXT_SIZE_22_ASCEND, StringBuffer, TEXT_SIZE_22, COLOR_WHITE, COLOR_DEMO_BACKGROUND);
}

