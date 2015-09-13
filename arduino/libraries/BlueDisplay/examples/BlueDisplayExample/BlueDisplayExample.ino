/*
 *  BlueDisplayExample.cpp
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
#include "EventHandler.h"

// Use simple blocking serial version without receive buffer and other overhead
// Using it saves up to 1250 byte FLASH and 185 byte RAM since USART is used directly
// Comment it out here AND in BlueSerial on line 35 if you want to use it instead of Arduino Serial....
#define USE_SIMPLE_SERIAL

// Change this if you have reprogrammed the hc05 module for higher baud rate
//#define HC_05_BAUD_RATE BAUD_9600
#define HC_05_BAUD_RATE BAUD_115200
#define DISPLAY_WIDTH 320
#define DISPLAY_HEIGHT 240

#define DELAY_START_VALUE 600
#define DELAY_CHANGE_VALUE 10

#define SLIDER_X_POSITION 80

#define COLOR_DEMO_BACKGROUND COLOR_BLUE
#define COLOR_CAPTION COLOR_RED

// Pin 13 has an LED connected on most Arduino boards.
const int LED_PIN = 13;
const int TONE_PIN = 2;

volatile bool doBlink = true;
volatile int sDelay = 600;

// a string buffer for any purpose...
char StringBuffer[128];

/*
 * The 3 buttons
 */
BDButton TouchButtonStartStop;
BDButton TouchButtonPlus;
BDButton TouchButtonMinus;
BDButton TouchButtonValueDirect;

// Touch handler for buttons
void doStartStop(BDButton * aTheTochedButton, int16_t aValue);
void doPlusMinus(BDButton * aTheTochedButton, int16_t aValue);
void doGetDelay(BDButton * aTheTouchedButton, int16_t aValue);

/*
 * The horizontal slider
 */
BDSlider TouchSliderDelay;
// handler for value change
void doDelay(BDSlider * aTheTochedSlider, uint16_t aSliderValue);
void printDelayValue(void);

// Callback handler for (re)connect and resize
void initDisplay(void);
void drawGui(void);
void printDemoString(void);

void setup() {
    // initialize the digital pin as an output.
    pinMode(LED_PIN, OUTPUT);
    pinMode(TONE_PIN, OUTPUT);
#ifdef USE_SIMPLE_SERIAL  // see line 50 in BlueSerial.h
    initSimpleSerial(HC_05_BAUD_RATE, false);
#else
    Serial.begin(HC_05_BAUD_RATE);
#endif
    // Must be called first since it sets the display size,
    // which is used by internal slider plausibility
    initDisplay();

    // Register callback handler
    registerSimpleConnectCallback(&initDisplay);
    registerSimpleResizeAndConnectCallback(&drawGui);
    drawGui();
    // to signal that boot has finished
    tone(TONE_PIN, 2000, 200);

}

void loop() {
    if (doBlink) {
        uint8_t i;
        digitalWrite(LED_PIN, HIGH);  // LED on
        BlueDisplay1.fillCircle(DISPLAY_WIDTH / 2, DISPLAY_HEIGHT / 2, 20, COLOR_RED);
        // wait for delay time but check touch events 8 times while waiting
        for (i = 0; i < 8; ++i) {
            checkAndHandleEvents();
            printDemoString();
            delay(sDelay / 8);
        }
        digitalWrite(LED_PIN, LOW);  // LED off
        BlueDisplay1.fillCircle(DISPLAY_WIDTH / 2, DISPLAY_HEIGHT / 2, 20, COLOR_DEMO_BACKGROUND);
        for (i = 0; i < 8; ++i) {
#ifdef USE_SIMPLE_SERIAL
            checkAndHandleEvents();
#else
            serialEvent();
#endif
            printDemoString();
            delay(sDelay / 8);
        }
        printDemoString();
    } else {
#ifdef USE_SIMPLE_SERIAL
        checkAndHandleEvents();
#else
        serialEvent();
#endif
    }
}

void initDisplay(void) {
    BlueDisplay1.setFlagsAndSize(BD_FLAG_FIRST_RESET_ALL | BD_FLAG_USE_MAX_SIZE | BD_FLAG_TOUCH_BASIC_DISABLE, DISPLAY_WIDTH,
    DISPLAY_HEIGHT);
    const char * tStartStopString = "Start";
    if (doBlink == true) {
        tStartStopString = "Stop";
    }

    TouchButtonPlus.init(270, 80, 40, 40, COLOR_YELLOW, "+", 33, BUTTON_FLAG_DO_BEEP_ON_TOUCH | BUTTON_FLAG_TYPE_AUTOREPEAT,
    DELAY_CHANGE_VALUE, &doPlusMinus);
    TouchButtonPlus.setButtonAutorepeatTiming(600, 100, 10, 30);

    TouchButtonMinus.init(10, 80, 40, 40, COLOR_YELLOW, "-", 33, BUTTON_FLAG_DO_BEEP_ON_TOUCH | BUTTON_FLAG_TYPE_AUTOREPEAT,
            -DELAY_CHANGE_VALUE, &doPlusMinus);
    TouchButtonMinus.setButtonAutorepeatTiming(600, 100, 10, 30);

    TouchButtonStartStop.init(30, 150, 140, 55, COLOR_DEMO_BACKGROUND, tStartStopString, 44,
            BUTTON_FLAG_DO_BEEP_ON_TOUCH | BUTTON_FLAG_TYPE_AUTO_RED_GREEN, doBlink, &doStartStop);

    // PSTR("...") and initPGM saves RAM since string "..." is stored in flash
    TouchButtonValueDirect.initPGM(210, 150, 90, 55, COLOR_YELLOW, PSTR("..."), 44, BUTTON_FLAG_DO_BEEP_ON_TOUCH, 0, &doGetDelay);

    TouchSliderDelay.init(SLIDER_X_POSITION, 40, 12, 150, 100, DELAY_START_VALUE / 10, COLOR_YELLOW, COLOR_GREEN,
            TOUCHSLIDER_SHOW_BORDER | TOUCHSLIDER_IS_HORIZONTAL, &doDelay);
    TouchSliderDelay.setCaptionProperties(TEXT_SIZE_22, SLIDER_VALUE_CAPTION_ALIGN_RIGHT, 4, COLOR_RED,
    COLOR_DEMO_BACKGROUND);
    TouchSliderDelay.setCaption("Delay");
    TouchSliderDelay.setPrintValueProperties(TEXT_SIZE_22, SLIDER_VALUE_CAPTION_ALIGN_LEFT, 4, COLOR_WHITE,
    COLOR_DEMO_BACKGROUND);
}

void drawGui(void) {
    BlueDisplay1.clearDisplay(COLOR_DEMO_BACKGROUND);
    TouchButtonStartStop.drawButton();
    TouchButtonPlus.drawButton();
    TouchButtonMinus.drawButton();
    TouchButtonValueDirect.drawButton();
    TouchSliderDelay.drawSlider();
    printDelayValue();
}

/*
 * Change doBlink flag as well as color and caption of the button.
 * Value sent by button is used for demonstration purposes in this case
 * you could also use doBlink flag for distinguish the two states
 */
void doStartStop(BDButton * aTheTouchedButton, int16_t aValue) {
    doBlink = !aValue;
    if (doBlink) {
        // green stop button
        aTheTouchedButton->setCaption("Stop");
    } else {
        // red start button
        aTheTouchedButton->setCaption("Start");
    }
    aTheTouchedButton->setValueAndDraw(doBlink);
}

/*
 * Is called by touch of both plus and minus buttons.
 * We just take the passed value and do not care which button was touched
 */
void doPlusMinus(BDButton * aTheTouchedButton, int16_t aValue) {
    sDelay += aValue;
    if (sDelay < DELAY_CHANGE_VALUE) {
        sDelay = DELAY_CHANGE_VALUE;
    }
    if (!doBlink) {
        // enable blinking
        doStartStop(&TouchButtonStartStop, false);
    }
    // set slider bar accordingly
    TouchSliderDelay.setActualValueAndDrawBar(sDelay / 10);
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
    TouchSliderDelay.setActualValueAndDrawBar(sDelay / 10);
    printDelayValue();
}

/*
 * Request delay value as number
 */
void doGetDelay(BDButton * aTheTouchedButton, int16_t aValue) {
    BlueDisplay1.getNumberWithShortPrompt(&doSetDelay, "delay [ms]");
}

/*
 * Is called by touch or move on slider and sets the new delay according to the passed slider value
 */
void doDelay(BDSlider * aTheTouchedSlider, uint16_t aSliderValue) {
    sDelay = 10 * aSliderValue;
    printDelayValue();
}

void printDelayValue(void) {
    snprintf(StringBuffer, sizeof StringBuffer, "%4ums", sDelay);
    TouchSliderDelay.printValue(StringBuffer);
}

#define MILLIS_PER_CHANGE 20 // gives minimal 2 seconds
void printDemoString(void) {
    static float tFadingFactor = 0.0; // 0 -> Background 1 -> Caption
    static float tInterpolationDelta = 0.01;

    static bool tFadingFactorDirectionFromBackground = true;
    static long MillisSinceLastChange = millis();

    // Timing
    if (millis() - MillisSinceLastChange > MILLIS_PER_CHANGE) {
        MillisSinceLastChange = millis();

        // slow fade near background color
        if (tFadingFactor <= 0.1) {
            tInterpolationDelta = 0.002;
        } else {
            tInterpolationDelta = 0.01;
        }

        // manage fading factor
        if (tFadingFactorDirectionFromBackground) {
            tFadingFactor += tInterpolationDelta;
            if (tFadingFactor >= (1.0 - 0.01)) {
                // toggle direction to background
                tFadingFactorDirectionFromBackground = !tFadingFactorDirectionFromBackground;
            }
        } else {
            tFadingFactor -= tInterpolationDelta;
            if (tFadingFactor <= tInterpolationDelta) {
                // toggle direction
                tFadingFactorDirectionFromBackground = !tFadingFactorDirectionFromBackground;
            }
        }

        // get resulting color
        uint8_t ColorRed = GET_RED(COLOR_DEMO_BACKGROUND)
                + ((int16_t) ( GET_RED(COLOR_CAPTION) - GET_RED(COLOR_DEMO_BACKGROUND)) * tFadingFactor);
        uint8_t ColorGreen = GET_GREEN(COLOR_DEMO_BACKGROUND)
                + ((int16_t) (GET_GREEN(COLOR_CAPTION) - GET_GREEN(COLOR_DEMO_BACKGROUND)) * tFadingFactor);
        uint8_t ColorBlue = GET_BLUE(COLOR_DEMO_BACKGROUND)
                + ((int16_t) ( GET_BLUE(COLOR_CAPTION) - GET_BLUE(COLOR_DEMO_BACKGROUND)) * tFadingFactor);
        BlueDisplay1.drawText(DISPLAY_WIDTH / 2 - 2 * getTextWidth(36), 4 + getTextAscend(36), "Demo", 36,
                RGB(ColorRed, ColorGreen, ColorBlue), COLOR_DEMO_BACKGROUND);
    }
}
