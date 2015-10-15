#include <Arduino.h>

#include "BlueDisplay.h"

// Change this if you have reprogrammed the hc05 module for higher baud rate
//#define HC_05_BAUD_RATE BAUD_9600
#define HC_05_BAUD_RATE BAUD_115200

#define TRIGGER 2
#define ECHO 3
#define TONE_PIN 4
#define MEASUREMENT_INTERVAL_MS 100

int sActualDisplayWidth;
int sActualDisplayHeight;
int sCaptionTextSize;
int sCaptionStartX;
int sValueStartY;
int sCount;
/*
 * The Start Stop button
 */
BDButton TouchButtonStartStop;
bool doTone = true;

char StringBuffer[100];
/*
 * Change doBlink flag as well as color and caption of the button.
 */
void doStartStop(BDButton * aTheTouchedButton, int16_t aValue) {
    doTone = !doTone;
    if (doTone) {
        // green stop button
        aTheTouchedButton->setCaption("Stop");
    } else {
        // red start button
        aTheTouchedButton->setCaption("Start");
    }
    aTheTouchedButton->setValueAndDraw(doTone);
}

/*
 * Function used as callback handler for redraw too
 */
void drawGui(void) {
    BlueDisplay1.clearDisplay(COLOR_BLUE);
    BlueDisplay1.drawText(sCaptionStartX, sCaptionTextSize, "Distance", sCaptionTextSize, COLOR_WHITE, COLOR_BLUE);
    TouchButtonStartStop.drawButton();
}

/*
 * Handle change from landscape to portrait or response from requestMaxCanvasSize() or initial connects
 */
void handleReorientation(void) {
    tone(TONE_PIN, 1000, 50);
    // manage positions according to actual display size
    sActualDisplayWidth = BlueDisplay1.getMaxDisplayWidth();
    sActualDisplayHeight = BlueDisplay1.getMaxDisplayHeight();
    if (sActualDisplayWidth < sActualDisplayHeight) {
        // Portrait -> change to landscape 3/2 format
        sActualDisplayHeight = (sActualDisplayWidth / 3) * 2;
    }
    sCaptionTextSize = sActualDisplayHeight / 4;
    // Position Caption at middle of screen
    sCaptionStartX = (sActualDisplayWidth - (getTextWidth(sCaptionTextSize) * strlen("Distance"))) / 2;

    sprintf(StringBuffer, "sActualDisplayWidth=%d sCount=%d", sActualDisplayWidth, sCount);
    sCount = 100; // break loop
    BlueDisplay1.debugMessage(StringBuffer);

    if (sCaptionStartX < 0) {
        sCaptionStartX = 0;
    }

    sValueStartY = sActualDisplayHeight - (sActualDisplayHeight / 4);
    BlueDisplay1.setFlagsAndSize(BD_FLAG_TOUCH_BASIC_DISABLE, sActualDisplayWidth, sActualDisplayHeight);

    // Initialize button position, size, colors etc.
    TouchButtonStartStop.init(0, sActualDisplayHeight - (sActualDisplayHeight / 7), sActualDisplayWidth, sActualDisplayHeight / 7,
    COLOR_BLUE, "Stop", sCaptionTextSize / 3, BUTTON_FLAG_DO_BEEP_ON_TOUCH | BUTTON_FLAG_TYPE_AUTO_RED_GREEN, doTone, &doStartStop);
}

void setup(void) {
    pinMode(TRIGGER, OUTPUT);
    pinMode(TONE_PIN, OUTPUT);
    pinMode(ECHO, INPUT);

    initSimpleSerial(HC_05_BAUD_RATE, false);

    // Register callback handlers
    registerConnectCallback(&handleReorientation);
    registerRedrawCallback(&drawGui);
    // This in turn initializes the display
    registerReorientationCallback(&handleReorientation);
    BlueDisplay1.requestMaxCanvasSize(); // this results in a reorientation event
    // clean up old sent data
    checkAndHandleEvents();
    for (sCount = 0; sCount < 30; ++sCount) {
        // wait for size to be sent back. Time measured between 50 and 150 ms
        delayMillisWithCheckAndHandleEvents(10);
    }

    // on double tone, we received max canvas size. Otherwise no connection is available.
    delay(100);
    tone(TONE_PIN, 3000, 50);
}

int tCentimeterOld = 50;
bool sToneIsOff = true;

void loop(void) {
    // need 10 usec Trigger Pulse
    digitalWrite(TRIGGER, HIGH);
    //delayMicroseconds(20);
    delay(2); // to see it on scope
    digitalWrite(TRIGGER, LOW);

    /*
     * Get echo length. Formula is: uS / 58 = centimeters
     */
    uint16_t tCounter = 1; // timeout
    // Wait for echo to go HIGH
    while (digitalRead(ECHO) == LOW && tCounter != 0) {
        tCounter++;
    }
    if (tCounter == 0) {
        // timeout happened
        tone(TONE_PIN, 2000, 50);
        delay(200);
        tone(TONE_PIN, 1000, 50);
        delay(200);
    }
    // Count time echo is HIGH
    tCounter = 1;
    int tCentimeterNew = 50;
    while (digitalRead(ECHO) == HIGH && tCounter != 0) {
        // one loop is approximately 5.9 microseconds
        tCounter = tCounter + 1;
    }
    if (tCounter == 0) {
        // timeout (300 ms) happened
        tone(TONE_PIN, 1000, 50);
        delay(200);
        tone(TONE_PIN, 2000, 50);
    } else {
        //   tone(TONE_PIN, tCounter, MEASUREMENT_INTERVAL_MS + 20);

        tCentimeterNew = ((tCounter + 5) * 10) / 98;
        if (tCentimeterNew != tCentimeterOld) {
            if (sActualDisplayHeight > 0) {
                uint16_t tPosition = BlueDisplay1.drawUnsignedByte(getTextWidth(sCaptionTextSize * 2), sValueStartY, tCentimeterNew,
                        sCaptionTextSize * 2, COLOR_YELLOW,
                        COLOR_BLUE);
                BlueDisplay1.drawText(tPosition, sValueStartY, "cm", sCaptionTextSize, COLOR_WHITE, COLOR_BLUE);
            }
            if (tCentimeterNew > 30 || !doTone) {
                if (!sToneIsOff) {
                    sToneIsOff = true;
                    // Stop tone
                    BlueDisplay1.playTone(98);
                }
            } else {
                sToneIsOff = false;
                if (tCentimeterNew < 30 && tCentimeterNew > 20 && (tCentimeterOld >= 30 || tCentimeterOld <= 20)) {
                    BlueDisplay1.playTone(17);
                }
                if (tCentimeterNew <= 20 && tCentimeterNew > 10 && (tCentimeterOld > 20 || tCentimeterOld <= 10)) {
                    BlueDisplay1.playTone(18);
                }
                if (tCentimeterNew <= 10 && tCentimeterNew > 3 && (tCentimeterOld > 10 || tCentimeterOld <= 3)) {
                    BlueDisplay1.playTone(16);
                }
                if (tCentimeterNew <= 3 && tCentimeterOld > 3) {
                    BlueDisplay1.playTone(36);
                }
            }
        }

    }
    checkAndHandleEvents();
    tCentimeterOld = tCentimeterNew;
    delay(MEASUREMENT_INTERVAL_MS); // < 200
}
