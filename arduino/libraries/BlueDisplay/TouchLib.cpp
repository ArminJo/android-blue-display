/*
 * TouchLib.cpp
 *
 * @date 01.09.2014
 * @author Armin Joachimsmeyer
 *      Email:   armin.joachimsmeyer@gmail.com
 *      License: GPL v3 (http://www.gnu.org/licenses/gpl.html)
 * @version 1.0.0
 */

#include <Arduino.h>
#include "TouchLib.h"
#include "BlueDisplay.h"
#include "BlueSerial.h"

#ifdef LOCAL_DISPLAY_EXISTS
#include "ADS7846.h"
ADS7846 TouchPanel;
#endif

struct TouchPosition sEventPosition;
uint8_t sEventType;

struct TouchPosition sActualPosition;
bool sTouchIsStillDown = false;
bool sTouchWasDownButNotProcessed = false; // to ensure only one true per touch

/**
 * Interprets the event type and manage the flags
 *
 * (Touch) Message has 7 bytes:
 * Gross message length in bytes
 * Function code
 * X Position LSB
 * X Position MSB
 * Y Position LSB
 * Y Position MSB
 * Sync Token
 */
void handleReceiveEvent(uint8_t aReceiveBuffer[]) {
    uint8_t sEventType = aReceiveBuffer[1];
    if (sEventType == EVENT_TAG_RESIZE_ACTION || sEventType == EVENT_TAG_CONNECTION_BUILD_UP) {
        sEventPosition.PosX = aReceiveBuffer[2] + (aReceiveBuffer[3] << 8);
        sEventPosition.PosY = aReceiveBuffer[4] + (aReceiveBuffer[5] << 8);
        if (sEventType == EVENT_TAG_CONNECTION_BUILD_UP) {
            BlueDisplay1.setMaxDisplaySize(sEventPosition.PosX, sEventPosition.PosY);
            BlueDisplay1.setConnectionJustBuildUp();
        }
        BlueDisplay1.setNeedsRefresh();
    } else {
        sActualPosition.PosX = aReceiveBuffer[2] + (aReceiveBuffer[3] << 8);
        sActualPosition.PosY = aReceiveBuffer[4] + (aReceiveBuffer[5] << 8);
        if (sEventType == EVENT_TAG_TOUCH_ACTION_DOWN) {
            if (!sTouchIsStillDown) {
                // first touch here
                sTouchIsStillDown = true;
                sTouchWasDownButNotProcessed = true;
            }
        } else if (sEventType == EVENT_TAG_TOUCH_ACTION_UP) {
            // last touch
            sTouchIsStillDown = false;
        }
    }
}

bool isTouchStillDown(void) {
    return sTouchIsStillDown;
}

/**
 * Is called by main loops
 * Returns "true" only one times per touch
 */
//
bool wasTouched(void) {
#ifdef LOCAL_DISPLAY_EXISTS
    TouchPanel.service();
    if (TouchPanel.getPressure() >= MIN_PRESSURE) {
        if (!sTouchIsStillDown) {
            // first down event
            sTouchIsStillDown = true;
            sTouchWasDownButNotProcessed = true;
        }
    } else {
        sTouchIsStillDown = false;
    }
#endif

#ifndef USE_SIMPLE_SERIAL
    checkForReceivedMessage();
#endif
    /*
     * check touch flags
     */
    if (sTouchWasDownButNotProcessed) {
        // reset => return only one true per touch
        sTouchWasDownButNotProcessed = false;
        return true;
    }
    return false;
}

uint16_t getXActual(void) {
    return sActualPosition.PosX;
}

uint16_t getYActual(void) {
    return sActualPosition.PosY;
}
