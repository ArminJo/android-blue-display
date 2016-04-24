#include <Arduino.h>

#include "BlueDisplay.h"

// Change this if you have reprogrammed the hc05 module for higher baud rate
//#define HC_05_BAUD_RATE BAUD_9600
#define HC_05_BAUD_RATE BAUD_115200

#define TRIGGER 2
#define ECHO 3
#define TONE_PIN 4
#define MEASUREMENT_INTERVAL_MS 50

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
BDButton TouchButtonOffset;
bool doTone = true;
int sOffset = 0;

BDSlider SliderShowDistance;

char sDataBuffer[100];
/*
 * Change doTone flag as well as color and caption of the button.
 */
void doStartStop(BDButton * aTheTouchedButton, int16_t aValue) {
    doTone = !doTone;
    if (doTone) {
        // green stop button
        aTheTouchedButton->setCaption("Stop tone");
    } else {
        // red start button
        aTheTouchedButton->setCaption("Start tone");
    }
    aTheTouchedButton->setValueAndDraw(doTone);
}

/*
 * Handler for number receive event - set delay to value
 */
void doSetOffset(float aValue) {
// clip value
    if (aValue > 20) {
        aValue = 20;
    } else if (aValue < -20) {
        aValue = -20;
    }
    sOffset = aValue;
}
/*
 * Request delay value as number
 */
void doGetOffset(BDButton * aTheTouchedButton, int16_t aValue) {
    BlueDisplay1.getNumberWithShortPrompt(&doSetOffset, "Offset distance [cm]");
}

/*
 * Handle change from landscape to portrait
 */
void handleConnectAndReorientation(void) {
//    tone(TONE_PIN, 1000, 50);
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

    sprintf(sDataBuffer, "sActualDisplayWidth=%d", sActualDisplayWidth);
    BlueDisplay1.debugMessage(sDataBuffer);

    if (sCaptionStartX < 0) {
        sCaptionStartX = 0;
    }

    sValueStartY = getTextAscend(sCaptionTextSize * 2) + sCaptionTextSize + sCaptionTextSize / 4;
    BlueDisplay1.setFlagsAndSize(BD_FLAG_FIRST_RESET_ALL | BD_FLAG_TOUCH_BASIC_DISABLE, sActualDisplayWidth, sActualDisplayHeight);

    SliderShowDistance.init(0, sCaptionTextSize * 3, sCaptionTextSize / 4, sActualDisplayWidth, 199, 0, COLOR_BLUE,
    COLOR_GREEN, FLAG_SLIDER_IS_HORIZONTAL | FLAG_SLIDER_IS_ONLY_OUTPUT, NULL);
    SliderShowDistance.setValueScaleFactor(sActualDisplayWidth / 200);

    // Initialize button position, size, colors etc.
    TouchButtonStartStop.init(0, BUTTON_HEIGHT_5_DYN_LINE_5, BUTTON_WIDTH_3_DYN, BUTTON_HEIGHT_5_DYN, COLOR_BLUE, "Stop Tone",
            sCaptionTextSize / 3, BUTTON_FLAG_DO_BEEP_ON_TOUCH | BUTTON_FLAG_TYPE_AUTO_RED_GREEN, doTone, &doStartStop);

    TouchButtonOffset.init(BUTTON_WIDTH_3_DYN_POS_3, BUTTON_HEIGHT_5_DYN_LINE_5, BUTTON_WIDTH_3_DYN, BUTTON_HEIGHT_5_DYN, COLOR_RED,
            "Offset", sCaptionTextSize / 3, BUTTON_FLAG_DO_BEEP_ON_TOUCH, 0, &doGetOffset);
}

/*
 * Function used as callback handler for redraw event
 */
void drawGui(void) {
    BlueDisplay1.clearDisplay(COLOR_BLUE);
    BlueDisplay1.drawText(sCaptionStartX, sCaptionTextSize, "Distance", sCaptionTextSize, COLOR_WHITE, COLOR_BLUE);
    SliderShowDistance.drawSlider();
    TouchButtonStartStop.drawButton();
    TouchButtonOffset.drawButton();
}

void setup(void) {
    pinMode(TRIGGER, OUTPUT);
    pinMode(TONE_PIN, OUTPUT);
    pinMode(ECHO, INPUT);

    initSimpleSerial(HC_05_BAUD_RATE, false);

    // Register callback handler and check for connection
    BlueDisplay1.initCommunication(&handleConnectAndReorientation, &drawGui);

    /*
     * on double tone, we received max canvas size. Otherwise no connection is available.
     */
    if (BlueDisplay1.mConnectionEstablished) {
        tone(TONE_PIN, 3000, 50);
        delay(100);
    }
    tone(TONE_PIN, 3000, 50);
    delay(100);
}

int sCentimeterNew = 0;
int sCentimeterOld = 50;
bool sToneIsOff = true;

void loop(void) {
    // need minimum 10 usec Trigger Pulse
    digitalWrite(TRIGGER, HIGH);
    delay(2); // to see it on scope
    // falling edge starts measurement
    digitalWrite(TRIGGER, LOW);

    /*
     * Get echo length. 58,48 us per centimeter
     * => 50cm gives 2900 us, 2m gives 11900 us
     */
    unsigned long tPulseLength = pulseIn(ECHO, HIGH, 20000);
    if (tPulseLength == 0) {
        // timeout happened
        tone(TONE_PIN, 1000, 50);
        delay(100);
        tone(TONE_PIN, 2000, 50);
        delay((100 - MEASUREMENT_INTERVAL_MS) - 20);

    } else {
        if (doTone && tPulseLength < (58 * 40)) {
            /*
             * local feedback for distances < 40 cm
             */
            tone(TONE_PIN, tPulseLength, MEASUREMENT_INTERVAL_MS + 20);
        }
        // +1cm was measured at working device
        sCentimeterNew = (tPulseLength / 58) + 1 - sOffset;
        if (sCentimeterNew != sCentimeterOld) {
            if (sActualDisplayHeight > 0) {
                uint16_t tCmXPosition = BlueDisplay1.drawUnsignedByte(getTextWidth(sCaptionTextSize * 2), sValueStartY,
                        sCentimeterNew, sCaptionTextSize * 2, COLOR_YELLOW,
                        COLOR_BLUE);
                BlueDisplay1.drawText(tCmXPosition, sValueStartY, "cm", sCaptionTextSize, COLOR_WHITE, COLOR_BLUE);
                SliderShowDistance.setActualValueAndDrawBar(sCentimeterNew);
            }
            if (sCentimeterNew >= 40 || !doTone) {
                /*
                 * Silence here
                 */
                if (!sToneIsOff) {
                    sToneIsOff = true;
                    // Stop tone
                    BlueDisplay1.playTone(TONE_SILENCE);
                }
            } else {
                sToneIsOff = false;
                /*
                 * Switch tones only if range changes
                 */
                if (sCentimeterNew < 40 && sCentimeterNew > 30 && (sCentimeterOld >= 40 || sCentimeterOld <= 30)) {
                    BlueDisplay1.playTone(22);
                } else if (sCentimeterNew <= 30 && sCentimeterNew > 20 && (sCentimeterOld >= 30 || sCentimeterOld <= 20)) {
                    BlueDisplay1.playTone(17);
                } else if (sCentimeterNew <= 20 && sCentimeterNew > 10 && (sCentimeterOld > 20 || sCentimeterOld <= 10)) {
                    BlueDisplay1.playTone(18);
                } else if (sCentimeterNew <= 10 && sCentimeterNew > 3 && (sCentimeterOld > 10 || sCentimeterOld <= 3)) {
                    BlueDisplay1.playTone(16);
                } else if (sCentimeterNew <= 3 && sCentimeterOld > 3) {
                    BlueDisplay1.playTone(36);
                }
            }
        }
    }
    checkAndHandleEvents();
    sCentimeterOld = sCentimeterNew;
    delay(MEASUREMENT_INTERVAL_MS); // < 200
}
