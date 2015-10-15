/*
 *  RcCarControl.cpp
 *  Demo of using the BlueDisplay library for HC-05 on Arduino

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

#include <Arduino.h>

#include "BlueDisplay.h"
#include "BlueSerial.h"
#include "EventHandler.h"

#include <stdlib.h> // for dtostrf

#define HC_05_BAUD_RATE BAUD_115200

// Pin 13 has an LED connected on most Arduino boards.
const int LED_PIN = 13;
// These pins are used by Timer 2
const int BACKWARD_MOTOR_PWM_PIN = 11;
const int FORWARD_MOTOR_PWM_PIN = 3;
const int RIGHT_PIN = 4;
const int LEFT_PIN = 5;

/*
 * Buttons
 */
BDButton TouchButtonStartStop;
void doStartStop(BDButton * aTheTochedButton, int16_t aValue);
void stopOutputs(void);
bool doRun = true;

BDButton TouchButtonSetZero;
void doSetZero(BDButton * aTheTouchedButton, int16_t aValue);
#define CALLS_FOR_ZERO_ADJUSTMENT 8
int tSensorChangeCallCount;
float sYZeroValueAdded;
float sYZeroValue = 0;

/*
 * Slider
 */
#define SLIDER_BACKGROUND_COLOR COLOR_YELLOW
#define SLIDER_BAR_COLOR COLOR_GREEN
#define SLIDER_THRESHOLD_COLOR COLOR_BLUE
/*
 * Velocity
 */
BDSlider SliderVelocityForward;
BDSlider SliderVelocityBackward;
int sLastSliderVelocityValue = 0;
int sLastMotorValue = 0;
// stop motor if velocity is less or equal MOTOR_DEAD_BAND_VALUE (max velocity value is 255)
#define MOTOR_DEAD_BAND_VALUE 80

/*
 * Direction
 */
BDSlider SliderRight;
BDSlider SliderLeft;
int sLastLeftRightValue = 0;

/*
 * Timing
 */
uint32_t sMillisOfLastReveivedEvent = 0;
#define SENSOR_RECEIVE_TIMEOUT_MILLIS 500
uint32_t sMillisOfLastVCCInfo = 0;
#define VCC_INFO_PERIOD_MILLIS 1000

/*
 * Layout
 */
int sActualDisplayWidth;
int sActualDisplayHeight;
int sSliderSize;
#define VALUE_X_SLIDER_DEAD_BAND (sSliderSize/2)
int sSliderHeight;
int sSliderWidth;
#define SLIDER_LEFT_RIGHT_THRESHOLD (sSliderWidth/4)
int sTextSize;
int sTextSizeVCC;

// a string buffer for any purpose...
char StringBuffer[128];

void doSensorChange(uint8_t aSensorType, struct SensorCallback * aSensorCallbackInfo);
void printVCCAndTemperature(void);

/*******************************************************************************************
 * Program code starts here
 *******************************************************************************************/

void drawGui(void) {
    BlueDisplay1.clearDisplay(COLOR_WHITE);
    SliderVelocityForward.drawSlider();
    SliderVelocityBackward.drawSlider();
    SliderRight.drawSlider();
    SliderLeft.drawSlider();
    TouchButtonSetZero.drawButton();
    TouchButtonStartStop.drawButton();
}

void initDisplay(void) {
    /*
     * handle display size
     */
    sActualDisplayWidth = BlueDisplay1.getMaxDisplayWidth();
    sActualDisplayHeight = BlueDisplay1.getMaxDisplayHeight();
    if (sActualDisplayWidth < sActualDisplayHeight) {
        // Portrait -> change to landscape 3/2 format
        sActualDisplayHeight = (sActualDisplayWidth / 3) * 2;
    }
    /*
     * compute layout values
     */
    sSliderSize = sActualDisplayWidth / 16;
    sSliderWidth = sActualDisplayHeight / 4;
    // 3/8 of sActualDisplayHeight
    sSliderHeight = ((sActualDisplayHeight / 2) + sSliderWidth) / 2;
    int tSliderThresholdVelocity = (sSliderHeight * (MOTOR_DEAD_BAND_VALUE + 1)) / 255;
    sTextSize = sActualDisplayHeight / 16;
    sTextSizeVCC = sActualDisplayHeight / 8;

    BlueDisplay1.setFlagsAndSize(BD_FLAG_FIRST_RESET_ALL | BD_FLAG_TOUCH_BASIC_DISABLE, sActualDisplayWidth, sActualDisplayHeight);

    sYZeroValueAdded = 0;
    tSensorChangeCallCount = 0;
    registerSensorChangeCallback(FLAG_SENSOR_TYPE_ACCELEROMETER, FLAG_SENSOR_DELAY_UI, FLAG_SENSOR_NO_FILTER, &doSensorChange);
    BlueDisplay1.setScreenOrientationLock(FLAG_SCREEN_ORIENTATION_LOCK_ACTUAL);

    /*
     * 4 Slider
     */
// Position Slider at middle of screen
    SliderVelocityForward.init((sActualDisplayWidth - sSliderSize) / 2, (sActualDisplayHeight / 2) - sSliderHeight, sSliderSize,
            sSliderHeight, tSliderThresholdVelocity, 0, COLOR_WHITE, SLIDER_BAR_COLOR, FLAG_SLIDER_IS_ONLY_OUTPUT,
            NULL);
    SliderVelocityForward.setBarThresholdColor(SLIDER_THRESHOLD_COLOR);
    SliderVelocityForward.setBarBackgroundColor(SLIDER_BACKGROUND_COLOR);

    SliderVelocityBackward.init((sActualDisplayWidth - sSliderSize) / 2, sActualDisplayHeight / 2, sSliderSize, -(sSliderHeight),
            tSliderThresholdVelocity, 0, COLOR_WHITE, SLIDER_BAR_COLOR, FLAG_SLIDER_IS_ONLY_OUTPUT, NULL);
    SliderVelocityBackward.setBarThresholdColor(SLIDER_THRESHOLD_COLOR);
    SliderVelocityBackward.setBarBackgroundColor(SLIDER_BACKGROUND_COLOR);

// Position slider right from velocity at middle of screen
    SliderRight.init((sActualDisplayWidth + sSliderSize) / 2, (sActualDisplayHeight - sSliderSize) / 2, sSliderSize, sSliderWidth,
    SLIDER_LEFT_RIGHT_THRESHOLD, 0, COLOR_WHITE, SLIDER_BAR_COLOR, FLAG_SLIDER_IS_HORIZONTAL | FLAG_SLIDER_IS_ONLY_OUTPUT, NULL);
    SliderRight.setBarThresholdColor( SLIDER_THRESHOLD_COLOR);
    SliderRight.setBarBackgroundColor(SLIDER_BACKGROUND_COLOR);

// Position inverse slider left from Velocity at middle of screen
    SliderLeft.init(((sActualDisplayWidth - sSliderSize) / 2) - sSliderWidth, (sActualDisplayHeight - sSliderSize) / 2, sSliderSize,
            -(sSliderWidth), SLIDER_LEFT_RIGHT_THRESHOLD, 0, COLOR_WHITE, SLIDER_BAR_COLOR,
            FLAG_SLIDER_IS_HORIZONTAL | FLAG_SLIDER_IS_ONLY_OUTPUT, NULL);
    SliderLeft.setBarThresholdColor(SLIDER_THRESHOLD_COLOR);
    SliderLeft.setBarBackgroundColor(SLIDER_BACKGROUND_COLOR);

    /*
     * Buttons
     */
    const char* tCaptionPtr = "Start";
    if (doRun) {
        tCaptionPtr = "Stop";
    }
    TouchButtonStartStop.init(0, sActualDisplayHeight - sActualDisplayHeight / 4, sActualDisplayWidth / 3, sActualDisplayHeight / 4,
    COLOR_BLUE, tCaptionPtr, sTextSize * 2, BUTTON_FLAG_DO_BEEP_ON_TOUCH | BUTTON_FLAG_TYPE_AUTO_RED_GREEN, doRun, &doStartStop);

    TouchButtonSetZero.init(sActualDisplayWidth - sActualDisplayWidth / 3, sActualDisplayHeight - sActualDisplayHeight / 4,
            sActualDisplayWidth / 3, sActualDisplayHeight / 4, COLOR_RED, "Zero", sTextSize * 2, BUTTON_FLAG_DO_BEEP_ON_TOUCH, 0,
            &doSetZero);
}

void setup() {
// initialize the digital pin as an output.
    pinMode(LED_PIN, OUTPUT);
    pinMode(FORWARD_MOTOR_PWM_PIN, OUTPUT);
    pinMode(BACKWARD_MOTOR_PWM_PIN, OUTPUT);
    pinMode(RIGHT_PIN, OUTPUT);
    pinMode(LEFT_PIN, OUTPUT);

    initSimpleSerial(HC_05_BAUD_RATE, false);

// Register callback handler
    registerConnectCallback(&initDisplay);
    registerRedrawCallback(&drawGui);
    // This in turn initializes the display
    registerReorientationCallback(&initDisplay);
    BlueDisplay1.requestMaxCanvasSize(); // this results in a reorientation event
    // clean up old sent data
    checkAndHandleEvents();
    for (uint8_t i = 0; i < 30; ++i) {
        // wait for size to be sent back. Time measured is between 50 and 150 ms
        delayMillisWithCheckAndHandleEvents(10);
    }
}

void loop() {
    uint32_t tMillis = millis();

    /*
     * Stop output if connection lost
     */
    if ((tMillis - sMillisOfLastReveivedEvent) > SENSOR_RECEIVE_TIMEOUT_MILLIS) {
        stopOutputs();
    }

    /*
     * Print VCC and temperature each second
     */
    if ((tMillis - sMillisOfLastVCCInfo) > VCC_INFO_PERIOD_MILLIS) {
        sMillisOfLastVCCInfo = tMillis;
        printVCCAndTemperature();
    }

    /*
     * Check if receive buffer contains an event
     */
    checkAndHandleEvents();
}

/*
 * Handle Start/Stop
 */
void doStartStop(BDButton * aTheTouchedButton, int16_t aValue) {
    doRun = !doRun;
    if (doRun) {
        registerSensorChangeCallback(FLAG_SENSOR_TYPE_ACCELEROMETER, FLAG_SENSOR_DELAY_UI, FLAG_SENSOR_NO_FILTER, &doSensorChange);
        // green stop button
        aTheTouchedButton->setCaption("Stop");
    } else {
        registerSensorChangeCallback(FLAG_SENSOR_TYPE_ACCELEROMETER, FLAG_SENSOR_DELAY_UI, FLAG_SENSOR_NO_FILTER, NULL);
        // red start button
        aTheTouchedButton->setCaption("Start");
        stopOutputs();
    }
    aTheTouchedButton->setValueAndDraw(doRun);
}

/*
 * Stop output signals
 */
void stopOutputs(void) {
    analogWrite(FORWARD_MOTOR_PWM_PIN, 0);
    analogWrite(BACKWARD_MOTOR_PWM_PIN, 0);
    digitalWrite(RIGHT_PIN, LOW);
    digitalWrite(LEFT_PIN, LOW);
}

void doSetZero(BDButton * aTheTouchedButton, int16_t aValue) {
// wait for end of touch vibration
    delay(10);
    sYZeroValueAdded = 0;
    tSensorChangeCallCount = 0;
}

/*
 * Values are between +10 at 90 degree canvas top is up and -10 (canvas bottom is up)
 */
void processYSensorValue(float tSensorValue) {

    // Scale value for full speed = 0xFF at 30 degree
    int tMotorValue = -((tSensorValue - sYZeroValue) * ((255 * 3) / 10));

    // forward backward handling
    uint8_t tActiveMotorPin;
    uint8_t tInactiveMotorPin;
    BDSlider tActiveSlider;
    BDSlider tInactiveSlider;
    if (tMotorValue >= 0) {
        tActiveMotorPin = FORWARD_MOTOR_PWM_PIN;
        tInactiveMotorPin = BACKWARD_MOTOR_PWM_PIN;
        tActiveSlider = SliderVelocityForward;
        tInactiveSlider = SliderVelocityBackward;
    } else {
        tActiveMotorPin = BACKWARD_MOTOR_PWM_PIN;
        tInactiveMotorPin = FORWARD_MOTOR_PWM_PIN;
        tActiveSlider = SliderVelocityBackward;
        tInactiveSlider = SliderVelocityForward;
        tMotorValue = -tMotorValue;
    }

    // dead band handling
    if (tMotorValue <= MOTOR_DEAD_BAND_VALUE) {
        tMotorValue = 0;
    }

    // overflow handling since analogWrite only accepts byte values
    if (tMotorValue > 0xFF) {
        tMotorValue = 0xFF;
    }

    analogWrite(tInactiveMotorPin, 0);
    // use this as delay between deactivating one channel and activating the other
    int tSliderValue = -((tSensorValue - sYZeroValue) * ((sSliderHeight * 3) / 10));
    if (tSliderValue < 0) {
        tSliderValue = -tSliderValue;
    }
    if (sLastSliderVelocityValue != tSliderValue) {
        sLastSliderVelocityValue = tSliderValue;
        tActiveSlider.setActualValueAndDrawBar(tSliderValue);
        tInactiveSlider.setActualValueAndDrawBar(0);
        if (sLastMotorValue != tMotorValue) {
            sLastMotorValue = tMotorValue;
            sprintf(StringBuffer, "%3d", tMotorValue);
            SliderVelocityBackward.printValue(StringBuffer);
            analogWrite(tActiveMotorPin, tMotorValue);
        }
    }
}

/*
 * Values are between +10 at 90 degree canvas right is up and -10 (canvas left is up)
 */
void processXSensorValue(float tSensorValue) {

    // scale value for full scale =SLIDER_WIDTH at at 30 degree
    int tLeftRightValue = tSensorValue * ((sSliderWidth * 3) / 10);

    // left right handling
    uint8_t tActivePin;
    uint8_t tInactivePin;
    BDSlider tActiveSlider;
    BDSlider tInactiveSlider;
    if (tLeftRightValue >= 0) {
        tActivePin = LEFT_PIN;
        tInactivePin = RIGHT_PIN;
        tActiveSlider = SliderLeft;
        tInactiveSlider = SliderRight;
    } else {
        tActivePin = RIGHT_PIN;
        tInactivePin = LEFT_PIN;
        tActiveSlider = SliderRight;
        tInactiveSlider = SliderLeft;
        tLeftRightValue = -tLeftRightValue;
    }

    // dead band handling for slider
    uint8_t tActiveValue = HIGH;
    if (tLeftRightValue > VALUE_X_SLIDER_DEAD_BAND) {
        tLeftRightValue = tLeftRightValue - VALUE_X_SLIDER_DEAD_BAND;
    } else {
        tLeftRightValue = 0;
    }

    // dead band handling for steering synchronous to slider threshold
    if (tLeftRightValue < SLIDER_LEFT_RIGHT_THRESHOLD) {
        tActiveValue = LOW;
    }

    // overflow handling
    if (tLeftRightValue > sSliderWidth) {
        tLeftRightValue = sSliderWidth;
    }

    digitalWrite(tInactivePin, LOW);
    // use this as delay between deactivating one pin and activating the other
    if (sLastLeftRightValue != tLeftRightValue) {
        sLastLeftRightValue = tLeftRightValue;
        tActiveSlider.setActualValueAndDrawBar(tLeftRightValue);
        tInactiveSlider.setActualValueAndDrawBar(0);
    }

    digitalWrite(tActivePin, tActiveValue);
}

/*
 * Not used yet
 */
void printSensorInfo(struct SensorCallback* aSensorCallbackInfo) {
    dtostrf(aSensorCallbackInfo->ValueX, 7, 4, &StringBuffer[50]);
    dtostrf(aSensorCallbackInfo->ValueY, 7, 4, &StringBuffer[60]);
    dtostrf(aSensorCallbackInfo->ValueZ, 7, 4, &StringBuffer[70]);
    dtostrf(sYZeroValue, 7, 4, &StringBuffer[80]);
    snprintf(StringBuffer, sizeof StringBuffer, "X=%s Y=%s Z=%s Zero=%s", &StringBuffer[50], &StringBuffer[60], &StringBuffer[70],
            &StringBuffer[80]);
    BlueDisplay1.drawText(0, sTextSize, StringBuffer, sTextSize, COLOR_BLACK, COLOR_GREEN);
}

/*
 * Sensor callback handler
 */
void doSensorChange(uint8_t aSensorType, struct SensorCallback * aSensorCallbackInfo) {
    if (tSensorChangeCallCount < CALLS_FOR_ZERO_ADJUSTMENT) {
        sYZeroValueAdded += aSensorCallbackInfo->ValueY;
    } else if (tSensorChangeCallCount == CALLS_FOR_ZERO_ADJUSTMENT) {
        // compute zero value
        sYZeroValue = sYZeroValueAdded / CALLS_FOR_ZERO_ADJUSTMENT;
        BlueDisplay1.playTone(24);
    } else {
        tSensorChangeCallCount = CALLS_FOR_ZERO_ADJUSTMENT + 1; // to prevent overflow
//        printSensorInfo(aSensorCallbackInfo);
        if (doRun) {
            processYSensorValue(aSensorCallbackInfo->ValueY);
            processXSensorValue(aSensorCallbackInfo->ValueX);
        }
    }
    sMillisOfLastReveivedEvent = millis();
    tSensorChangeCallCount++;
}

/***************************************
 * ADC Section for VCC and temperature
 ***************************************/
#define PRESCALE128  7
#define ADC_TEMPERATURE_CHANNEL 8
#define ADC_1_1_VOLT_CHANNEL 0x0E
/*
 * take 64 samples with prescaler 128 from channel
 * This takes 13 ms (+ 10 ms optional delay)
 */
uint16_t getADCValue(uint8_t aChannel, uint8_t aReference) {
    uint8_t tOldADMUX = ADMUX;
    ADMUX = aChannel | (aReference << REFS0);
// Temperature channel also seem to need an initial delay
    delay(10);
    uint16_t tValue = 0;
    uint16_t tSum = 0; // uint16_t is sufficient for 64 samples
    uint8_t tOldADCSRA = ADCSRA;
    ADCSRA = (1 << ADEN) | (1 << ADSC) | (1 << ADATE) | (1 << ADIF) | (0 << ADIE) | PRESCALE128;
    for (int i = 0; i < 64; ++i) {
        // wait for free running conversion to finish
        while (bit_is_clear(ADCSRA, ADIF)) {
            ;
        }
        tValue = ADCL;
        tValue |= ADCH << 8;
        tSum += tValue;
    }
    ADCSRA = tOldADCSRA;
    ADMUX = tOldADMUX;

    tSum = (tSum + 32) >> 6;
    return tSum;
}

float getVCCValue(void) {
    float tVCC = getADCValue(ADC_1_1_VOLT_CHANNEL, DEFAULT);
    return ((1024 * 1.1) / tVCC);
}

float getTemperature(void) {
    float tTemp = (getADCValue(ADC_TEMPERATURE_CHANNEL, INTERNAL) - 317);
    return (tTemp / 1.22);
}

/*
 * Show temperature and VCC voltage
 */
void printVCCAndTemperature(void) {
    float tVCCVoltage = getVCCValue();
    dtostrf(tVCCVoltage, 4, 2, &StringBuffer[30]);
    float tTemp = getTemperature();
    dtostrf(tTemp, 4, 1, &StringBuffer[40]);
    sprintf(StringBuffer, "%s Volt\n%s\xB0" "C", &StringBuffer[30], &StringBuffer[40]);
    BlueDisplay1.drawText(sTextSize / 2, sTextSizeVCC, StringBuffer, sTextSizeVCC, COLOR_BLACK, COLOR_NO_BACKGROUND_EXTEND);
}
