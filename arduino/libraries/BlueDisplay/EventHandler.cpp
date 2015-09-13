/*
 * EventHandler.cpp
 *
 *   SUMMARY
 *  Blue Display is an Open Source Android remote Display for Arduino etc.
 *  It receives basic draw requests from Arduino etc. over Bluetooth and renders it.
 *  It also implements basic GUI elements as buttons and sliders.
 *  GUI callback, touch and sensor events are sent back to Arduino.
 *
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
#include "EventHandler.h"
#include "BlueDisplay.h"
#include "BlueSerial.h"

#ifdef LOCAL_DISPLAY_EXISTS
#include "ADS7846.h"
ADS7846 TouchPanel;
#endif

#ifndef DO_NOT_NEED_BASIC_TOUCH_EVENTS
struct BluetoothEvent remoteTouchDownEvent; // to avoid overwriting of touch down events if CPU is busy and interrupt in not enabled
struct XYPosition sDownPosition;
struct XYPosition sActualPosition;
struct XYPosition sUpPosition;
#endif

#ifdef LOCAL_DISPLAY_EXISTS
/*
 * helper variables
 */bool sButtonTouched = false; // flag if autorepeat button was touched - to influence long button press handling
bool sAutorepeatButtonTouched = false;// flag if autorepeat button was touched - to influence long button press handling
bool sNothingTouched = false;// = !(sSliderTouched || sButtonTouched || sAutorepeatButtonTouched)
bool sSliderIsMoveTarget = false;// true if slider was touched by DOWN event

struct BluetoothEvent localTouchEvent;
#endif

bool sTouchIsStillDown = false;
bool sDisableTouchUpOnce = false;

struct BluetoothEvent remoteTouchEvent;

void (*sTouchDownCallback)(struct XYPosition * const) = NULL;
void (*sLongTouchDownCallback)(struct XYPosition * const) = NULL;
void (*sTouchMoveCallback)(struct XYPosition * const) = NULL;
void (*sTouchUpCallback)(struct XYPosition * const) = NULL;

void (*sSwipeEndCallback)(struct Swipe * const) = NULL;

void (*sConnectCallback)(struct XYSize * const) = NULL;
void (*sSimpleConnectCallback)(void) = NULL;
void (*sResizeAndConnectCallback)(struct XYSize * const) = NULL;
void (*sSimpleResizeAndConnectCallback)(void) = NULL;

void (*sSensorChangeCallback)(uint8_t aEventType, struct SensorCallback * aSensorCallbackInfo) = NULL;

void registerConnectCallback(void (*aConnectCallback)(struct XYSize * const aMaxSizePtr)) {
    sConnectCallback = aConnectCallback;
}

void registerSimpleConnectCallback(void (*aConnectCallback)(void)) {
    sSimpleConnectCallback = aConnectCallback;
}

void registerResizeAndConnectCallback(void (*aResizeAndConnectCallback)(struct XYSize * const aActualSizePtr)) {
    sResizeAndConnectCallback = aResizeAndConnectCallback;
}

void registerSimpleResizeAndConnectCallback(void (*aSimpleResizeAndConnectCallback)(void)) {
    sSimpleResizeAndConnectCallback = aSimpleResizeAndConnectCallback;
}

void (* getSimpleResizeAndConnectCallback(void))(void)  {
    return sSimpleResizeAndConnectCallback;
}

#ifndef DO_NOT_NEED_BASIC_TOUCH_EVENTS
void registerTouchDownCallback(void (*aTouchDownCallback)(struct XYPosition * const aActualPositionPtr)) {
    sTouchDownCallback = aTouchDownCallback;
}

void registerTouchMoveCallback(void (*aTouchMoveCallback)(struct XYPosition * const aActualPositionPtr)) {
    sTouchMoveCallback = aTouchMoveCallback;
}

void registerTouchUpCallback(void (*aTouchUpCallback)(struct XYPosition * const aActualPositionPtr)) {
    sTouchUpCallback = aTouchUpCallback;
}
#endif

/**
 * Register a callback routine which is only called after a timeout if screen is still touched
 */
void registerLongTouchDownCallback(void (*aLongTouchDownCallback)(struct XYPosition * const),
        const uint16_t aLongTouchDownTimeoutMillis) {
    sLongTouchDownCallback = aLongTouchDownCallback;
    BlueDisplay1.setLongTouchDownTimeout(aLongTouchDownTimeoutMillis);
}

/**
 * Register a callback routine which is called when touch goes up and swipe detected
 */
void registerSwipeEndCallback(void (*aSwipeEndCallback)(struct Swipe * const)) {
    sSwipeEndCallback = aSwipeEndCallback;
    // disable next end touch since we are already in a touch handler and don't want the end of this touch to be propagated
    if (sTouchIsStillDown) {
        sDisableTouchUpOnce = true;
    }
}

/**
 *
 * @param aSensorType see see android.hardware.Sensor
 * @param aSensorRate see android.hardware.SensorManager (0-3) or in milli seconds
 * @param aSensorChangeCallback one callback for all sensors types
 */
void registerSensorChangeCallback(uint8_t aSensorType, uint8_t aSensorRate,
        void (*aSensorChangeCallback)(uint8_t aSensorType, struct SensorCallback * aSensorCallbackInfo)) {
    bool tSensorEnable = true;
    if (aSensorChangeCallback == NULL) {
        tSensorEnable = false;
    }
    BlueDisplay1.setSensor(aSensorType, tSensorEnable, aSensorRate);
    sSensorChangeCallback = aSensorChangeCallback;
}

/**
 * Is called by thread main loops
 */
void checkAndHandleEvents(void) {
#ifndef DO_NOT_NEED_BASIC_TOUCH_EVENTS
#ifdef LOCAL_DISPLAY_EXISTS
    resetTouchFlags();
    if (localTouchEvent.EventType != EVENT_TAG_NO_EVENT) {
        handleEvent(&localTouchEvent);
    }
#endif
    if (remoteTouchDownEvent.EventType != EVENT_TAG_NO_EVENT) {
        handleEvent(&remoteTouchDownEvent);
    }
#endif
    if (remoteTouchEvent.EventType != EVENT_TAG_NO_EVENT) {
        handleEvent(&remoteTouchEvent);
    }

}

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
void handleEvent(struct BluetoothEvent * aEvent) {
    uint8_t tEventType = aEvent->EventType;
    // avoid using event twice
    aEvent->EventType = EVENT_TAG_NO_EVENT;
#ifndef DO_NOT_NEED_BASIC_TOUCH_EVENTS
    if (tEventType == EVENT_TAG_TOUCH_ACTION_DOWN) {
        // must initialize all positions here!
        sDownPosition = aEvent->EventData.TouchPosition;
        sActualPosition = aEvent->EventData.TouchPosition;
        sTouchIsStillDown = true;
        if (sTouchDownCallback != NULL) {
            sTouchDownCallback(&aEvent->EventData.TouchPosition);
        }

    } else if (tEventType == EVENT_TAG_TOUCH_ACTION_MOVE) {
        if (sTouchMoveCallback != NULL) {
            sTouchMoveCallback(&aEvent->EventData.TouchPosition);
        }
        sActualPosition = aEvent->EventData.TouchPosition;

    } else if (tEventType == EVENT_TAG_TOUCH_ACTION_UP) {
        sUpPosition = aEvent->EventData.TouchPosition;
        sTouchIsStillDown = false;
#ifdef LOCAL_DISPLAY_EXISTS
        // may set sDisableTouchUpOnce
        handleLocalSwipeDetection();
#endif
        if (sDisableTouchUpOnce) {
            sDisableTouchUpOnce = false;
            return;
        }
        if (sTouchUpCallback != NULL) {
            sTouchUpCallback(&aEvent->EventData.TouchPosition);
        }

    } else if (tEventType == EVENT_TAG_TOUCH_ACTION_ERROR) {
        // try to reset touch state
        sUpPosition = aEvent->EventData.TouchPosition;
        sTouchIsStillDown = false;
    } else
#endif

    if (tEventType == EVENT_TAG_BUTTON_CALLBACK_ACTION) {
        sTouchIsStillDown = false; // to disable local touch up detection
        //BDButton * is the same as BDButtonHandle_t * since BDButton only has one BDButtonHandle_t element
        void (*tCallback)(BDButtonHandle_t*,
                int16_t) = (void (*)(BDButtonHandle_t*, int16_t)) aEvent->EventData.GuiCallbackInfo.Handler;
        tCallback((BDButtonHandle_t*) &aEvent->EventData.GuiCallbackInfo.ObjectIndex,
                aEvent->EventData.GuiCallbackInfo.ValueForHandler.Int16Value);

    } else if (tEventType == EVENT_TAG_SLIDER_CALLBACK_ACTION) {
        sTouchIsStillDown = false; // to disable local touch up detection
        void (*tCallback)(BDSliderHandle_t *,
                int16_t) = (void (*)(BDSliderHandle_t *, int16_t))aEvent->EventData.GuiCallbackInfo.Handler;
        tCallback(&aEvent->EventData.GuiCallbackInfo.ObjectIndex, aEvent->EventData.GuiCallbackInfo.ValueForHandler.Int16Value);

    } else if (tEventType == EVENT_TAG_NUMBER_CALLBACK) {
        void (*tCallback)(float) = (void (*)(float))aEvent->EventData.GuiCallbackInfo.Handler;
        tCallback(aEvent->EventData.GuiCallbackInfo.ValueForHandler.FloatValue);

        // check for sSensorChangeCallback != NULL since we can still have a few events for sensors even if they are just disabled
    } else if (tEventType >= EVENT_TAG_FIRST_SENSOR_ACTION_CODE && tEventType <= EVENT_TAG_LAST_SENSOR_ACTION_CODE
            && sSensorChangeCallback != NULL) {
        sSensorChangeCallback(tEventType, &aEvent->EventData.SensorCallbackInfo);

    } else if (tEventType == EVENT_TAG_SWIPE_CALLBACK_ACTION) {
        sTouchIsStillDown = false;
        if (sSwipeEndCallback != NULL) {
            if (aEvent->EventData.SwipeInfo.SwipeMainDirectionIsX) {
                aEvent->EventData.SwipeInfo.TouchDeltaAbsMax = abs(aEvent->EventData.SwipeInfo.TouchDeltaX);
            } else {
                aEvent->EventData.SwipeInfo.TouchDeltaAbsMax = abs(aEvent->EventData.SwipeInfo.TouchDeltaY);
            }
            sSwipeEndCallback(&(aEvent->EventData.SwipeInfo));
        }

    } else if (tEventType == EVENT_TAG_LONG_TOUCH_DOWN_CALLBACK_ACTION) {
        if (sLongTouchDownCallback != NULL) {
            sLongTouchDownCallback(&(aEvent->EventData.TouchPosition));
        }
        sDisableTouchUpOnce = true;

    } else if (tEventType == EVENT_TAG_CONNECTION_BUILD_UP) {
        BlueDisplay1.setMaxDisplaySize(&aEvent->EventData.DisplaySize);
        if (sSimpleConnectCallback != NULL) {
            sSimpleConnectCallback();
        } else if (sConnectCallback != NULL) {
            sConnectCallback(&aEvent->EventData.DisplaySize);
        }
// also handle as resize
        tEventType = EVENT_TAG_RESIZE_ACTION;
    }
    if (tEventType == EVENT_TAG_RESIZE_ACTION) {
        BlueDisplay1.setActualDisplaySize(&aEvent->EventData.DisplaySize);
        if (sSimpleResizeAndConnectCallback != NULL) {
            sSimpleResizeAndConnectCallback();
        } else if (sResizeAndConnectCallback != NULL) {
            sResizeAndConnectCallback(&aEvent->EventData.DisplaySize);
        }
    }

}

#ifndef DO_NOT_NEED_BASIC_TOUCH_EVENTS
bool isTouchStillDown(void) {
    return sTouchIsStillDown;
}
#endif

#ifdef LOCAL_DISPLAY_EXISTS
void resetTouchFlags(void) {
    sButtonTouched = false;
    sAutorepeatButtonTouched = false;
    sNothingTouched = false;
}
/**
 * Called at Touch Up
 * Handle long callback delay and compute swipe info
 */
void handleLocalSwipeDetection(void) {
    if (sSwipeEndCallback != NULL && !sSliderIsMoveTarget) {
        if (abs(sDownPosition.PosX - sActualPosition.PosX) >= TOUCH_SWIPE_THRESHOLD
                || abs(sDownPosition.PosY - sActualPosition.PosY) >= TOUCH_SWIPE_THRESHOLD) {
            /*
             * Swipe recognized here
             * compute SWIPE data and call callback handler
             */
            struct Swipe tSwipeInfo;
            tSwipeInfo.TouchStartX = sDownPosition.PosX;
            tSwipeInfo.TouchStartY = sDownPosition.PosY;
            tSwipeInfo.TouchDeltaX = sUpPosition.PosX - sDownPosition.PosX;
            uint16_t tTouchDeltaXAbs = abs(tSwipeInfo.TouchDeltaX );
            tSwipeInfo.TouchDeltaY = sUpPosition.PosY - sDownPosition.PosY;
            uint16_t tTouchDeltaYAbs = abs(tSwipeInfo.TouchDeltaY );
            if (tTouchDeltaXAbs >= tTouchDeltaYAbs) {
                // X direction
                tSwipeInfo.SwipeMainDirectionIsX = true;
                tSwipeInfo.TouchDeltaAbsMax = tTouchDeltaXAbs;
            } else {
                tSwipeInfo.SwipeMainDirectionIsX = false;
                tSwipeInfo.TouchDeltaAbsMax = tTouchDeltaYAbs;
            }
            sSwipeEndCallback(&tSwipeInfo);
            sDisableTouchUpOnce = true;
        }
    }
    sSliderIsMoveTarget = false;
}

/**
 *
 * @param aActualPositionPtr
 * @return
 */
void simpleTouchDownHandler(struct XYPosition * const aActualPositionPtr) {
    if (TouchSlider::checkAllSliders(aActualPositionPtr->PosX, aActualPositionPtr->PosY)) {
        sSliderIsMoveTarget = true;
    } else {
        if (!TouchButton::checkAllButtons(aActualPositionPtr->PosX, aActualPositionPtr->PosY)) {
            sNothingTouched = true;
        }
    }
}

void simpleTouchHandlerOnlyForButtons(struct XYPosition * const aActualPositionPtr) {
    if (!TouchButton::checkAllButtons(aActualPositionPtr->PosX, aActualPositionPtr->PosY, true)) {
        sNothingTouched = true;
    }
}

void simpleTouchDownHandlerOnlyForSlider(struct XYPosition * const aActualPositionPtr) {
    if (TouchSlider::checkAllSliders(aActualPositionPtr->PosX, aActualPositionPtr->PosY)) {
        sSliderIsMoveTarget = true;
    } else {
        sNothingTouched = true;
    }
}

void simpleTouchMoveHandlerForSlider(struct XYPosition * const aActualPositionPtr) {
    if (sSliderIsMoveTarget) {
        TouchSlider::checkAllSliders(aActualPositionPtr->PosX, aActualPositionPtr->PosY);
    }
}
#endif