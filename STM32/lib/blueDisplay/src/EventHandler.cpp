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

#include "EventHandler.h"
#include "BlueSerial.h"
#include "timing.h"

#ifdef LOCAL_DISPLAY_EXISTS
#include "TouchButton.h"
#include "TouchSlider.h"
#include "ADS7846.h"
#endif

#include <stdio.h> // for printf
#ifdef USE_STM32F3_DISCO
#include "stm32f3_discovery.h"  // For LEDx
#endif
#include "stm32fx0xPeripherals.h" // For Watchdog_reload()
#include <stdlib.h> // for NULL

#ifndef DO_NOT_NEED_BASIC_TOUCH_EVENTS
struct XYPosition sDownPosition;
struct XYPosition sActualPosition;
struct XYPosition sUpPosition;
#endif

#ifdef LOCAL_DISPLAY_EXISTS
/*
 * helper variables
 */
//
bool sButtonTouched = false;// flag if autorepeat button was touched - to influence long button press handling
bool sAutorepeatButtonTouched = false;// flag if autorepeat button was touched - to influence long button press handling
bool sNothingTouched = false;// = !(sSliderTouched || sButtonTouched || sAutorepeatButtonTouched)
bool sSliderIsMoveTarget = false;// true if slider was touched by DOWN event

uint32_t sLongTouchDownTimeoutMillis;
/*
 * timer related callbacks
 */bool (*sPeriodicTouchCallback)(int const, int const) = NULL;// return parameter not yet used
uint32_t sPeriodicCallbackPeriodMillis;

struct BluetoothEvent localTouchEvent;
#endif

bool sTouchIsStillDown = false;
bool sDisableTouchUpOnce = false;
bool sDisableUntilTouchUpIsDone = false;

struct BluetoothEvent remoteTouchEvent;

void (*sTouchDownCallback)(struct XYPosition * const) = NULL;
void (*sLongTouchDownCallback)(struct XYPosition * const) = NULL;
void (*sTouchMoveCallback)(struct XYPosition * const) = NULL;
void (*sTouchUpCallback)(struct XYPosition * const) = NULL;
bool sTouchUpCallbackEnabled = false;

void (*sSwipeEndCallback)(struct Swipe * const) = NULL;
bool sSwipeEndCallbackEnabled = false;

void (*sConnectCallback)(struct XYSize * const) = NULL;
void (*sSimpleConnectCallback)(void) = NULL;
void (*sResizeAndConnectCallback)(struct XYSize * const) = NULL;
void (*sSimpleResizeAndConnectCallback)(void) = NULL;

void (*sSensorChangeCallback)(uint8_t aEventType, struct SensorCallback * aSensorCallbackInfo) = NULL;

bool sDisplayXYValuesEnabled = false;  // displays touch values on screen

#ifdef LOCAL_DISPLAY_EXISTS
/**
 * Callback routine for SysTick handler
 */
void callbackPeriodicTouch(void) {
    if (sTouchIsStillDown) {
        if (sPeriodicTouchCallback != NULL) {
            // do "normal" callback for autorepeat buttons
            sPeriodicTouchCallback(sActualPosition.PosX, sActualPosition.PosY);
        }
        if (sTouchIsStillDown) {
            // renew systic callback request
            registerDelayCallback(&callbackPeriodicTouch, sPeriodicCallbackPeriodMillis);
        }
    }
}

/**
 * Register a callback routine which is called every CallbackPeriod milliseconds while screen is touched
 */
void registerPeriodicTouchCallback(bool (*aPeriodicTouchCallback)(int const, int const),
        const uint32_t aCallbackPeriodMillis) {
    sPeriodicTouchCallback = aPeriodicTouchCallback;
    sPeriodicCallbackPeriodMillis = aCallbackPeriodMillis;
    changeDelayCallback(&callbackPeriodicTouch, aCallbackPeriodMillis);
}

/**
 * set CallbackPeriod
 */
void setPeriodicTouchCallbackPeriod(const uint32_t aCallbackPeriod) {
    sPeriodicCallbackPeriodMillis = aCallbackPeriod;
}

/**
 * Callback routine for SysTick handler
 * Creates event if no Slider was touched and no swipe gesture was started
 * Disabling of touch up handling  (sDisableTouchUpOnce = false) must be done by called handler!!!
 */
void callbackLongTouchDownTimeout(void) {
    assert_param(sLongTouchDownCallback != NULL);
// No long touch if swipe is made or slider touched
    if (!sSliderIsMoveTarget) {
        /*
         * Check if a swipe is intended (position has moved over threshold).
         * If not, call long touch callback
         */
        if (abs(sDownPosition.PosX - sActualPosition.PosX) < TOUCH_SWIPE_THRESHOLD
                && abs(sDownPosition.PosY - sActualPosition.PosY) < TOUCH_SWIPE_THRESHOLD) {
            // fill up event
            localTouchEvent.EventData.TouchPosition = TouchPanel.mTouchLastPosition;
            localTouchEvent.EventType = EVENT_TAG_LONG_TOUCH_DOWN_CALLBACK_ACTION;
        }
    }
}
#endif

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

/**
 * Register a callback routine which is called when touch goes up
 */
void registerTouchUpCallback(void (*aTouchUpCallback)(struct XYPosition * const aActualPositionPtr)) {
    sTouchUpCallback = aTouchUpCallback;
    // disable next end touch since we are already in a touch handler and don't want the end of this touch to be interpreted
    if (sTouchIsStillDown) {
        sDisableTouchUpOnce = true;
    }
    sTouchUpCallbackEnabled = (aTouchUpCallback != NULL);
}

/**
 * disable or enable touch up callback
 * used by numberpad
 * @param aTouchUpCallbackEnabled
 */
void setTouchUpCallbackEnabled(bool aTouchUpCallbackEnabled) {
    if (aTouchUpCallbackEnabled && sTouchUpCallback != NULL) {
        sTouchUpCallbackEnabled = true;
    } else {
        sTouchUpCallbackEnabled = false;
    }
}
#endif

/**
 * Register a callback routine which is only called after a timeout if screen is still touched
 */
void registerLongTouchDownCallback(void (*aLongTouchDownCallback)(struct XYPosition * const),
        const uint16_t aLongTouchDownTimeoutMillis) {
    sLongTouchDownCallback = aLongTouchDownCallback;
#ifdef LOCAL_DISPLAY_EXISTS
    sLongTouchDownTimeoutMillis = aLongTouchDownTimeoutMillis;
    if (aLongTouchDownCallback == NULL) {
        changeDelayCallback(&callbackLongTouchDownTimeout, DISABLE_TIMER_DELAY_VALUE); // housekeeping - disable timeout
    }
#endif
    BlueDisplay1.setLongTouchDownTimeout(aLongTouchDownTimeoutMillis);
}

/**
 * Register a callback routine which is called when touch goes up and swipe detected
 */
void registerSwipeEndCallback(void (*aSwipeEndCallback)(struct Swipe * const)) {
    sSwipeEndCallback = aSwipeEndCallback;
    // disable next end touch since we are already in a touch handler and don't want the end of this touch to be interpreted
    if (sTouchIsStillDown) {
        sDisableTouchUpOnce = true;
    }
    sSwipeEndCallbackEnabled = (aSwipeEndCallback != NULL);
}

void setSwipeEndCallbackEnabled(bool aSwipeEndCallbackEnabled) {
    if (aSwipeEndCallbackEnabled && sSwipeEndCallback != NULL) {
        sSwipeEndCallbackEnabled = true;
    } else {
        sSwipeEndCallbackEnabled = false;
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

/*
 * Delay, which also checks for events
 */
void delayMillisWithCheckAndHandleEvents(int32_t aTimeMillis) {
    aTimeMillis /= 16;
    for (int32_t i = 0; i < aTimeMillis; ++i) {
        delayMillis(16);
        checkAndHandleEvents();
    }
}

/**
 * Is called by thread main loops
 * Checks also for (re)connect and resize events
 */
void checkAndHandleEvents(void) {
#ifdef HAL_WWDG_MODULE_ENABLED
    Watchdog_reload();
#endif
#ifdef LOCAL_DISPLAY_EXISTS
    resetTouchFlags();
    if (localTouchEvent.EventType != EVENT_TAG_NO_EVENT) {
        handleEvent(&localTouchEvent);
    }
#endif
    /*
     * check USART buffer, which in turn calls handleEvent() if event was received
     */
    checkAndHandleMessageReceived();
}

/**
 * Interprets the event type and manage the callbacks and flags
 * is indirectly called by thread in main loop
 */
extern "C" void handleEvent(struct BluetoothEvent * aEvent) {
    uint8_t tEventType = aEvent->EventType;
    // avoid using event twice
    aEvent->EventType = EVENT_TAG_NO_EVENT;
#ifndef DO_NOT_NEED_BASIC_TOUCH_EVENTS
#ifdef  LOCAL_DISPLAY_EXISTS
    if (tEventType <= EVENT_TAG_TOUCH_ACTION_MOVE && sDisplayXYValuesEnabled) {
        printTPData(30, 2 + TEXT_SIZE_11_ASCEND, COLOR_BLACK, COLOR_WHITE);
    }
#endif
    if (tEventType == EVENT_TAG_TOUCH_ACTION_DOWN) {
        // must initialize all positions here!
        sDownPosition = aEvent->EventData.TouchPosition;
        sActualPosition = aEvent->EventData.TouchPosition;
#ifdef USE_STM32F3_DISCO
        BSP_LED_On(LED_BLUE_2); // BLUE Front
#endif
        sTouchIsStillDown = true;
#ifdef LOCAL_DISPLAY_EXISTS
        // start timeout for long touch if it is local event
        if (sLongTouchDownCallback != NULL && aEvent != &remoteTouchEvent) {
            changeDelayCallback(&callbackLongTouchDownTimeout, sLongTouchDownTimeoutMillis); // enable timeout
        }
#endif
        if (sTouchDownCallback != NULL) {
            sTouchDownCallback(&aEvent->EventData.TouchPosition);
        }

    } else if (tEventType == EVENT_TAG_TOUCH_ACTION_MOVE) {
        if (sDisableUntilTouchUpIsDone) {
            return;
        }
        if (sTouchMoveCallback != NULL) {
            sTouchMoveCallback(&aEvent->EventData.TouchPosition);
        }
        sActualPosition = aEvent->EventData.TouchPosition;

    } else if (tEventType == EVENT_TAG_TOUCH_ACTION_UP) {
        sUpPosition = aEvent->EventData.TouchPosition;
#ifdef USE_STM32F3_DISCO
        BSP_LED_Off(LED9); // BLUE Front
#endif
        sTouchIsStillDown = false;
#ifdef LOCAL_DISPLAY_EXISTS
        // may set sDisableTouchUpOnce
        handleLocalTouchUp();
#endif
        if (sDisableTouchUpOnce || sDisableUntilTouchUpIsDone) {
            sDisableTouchUpOnce = false;
            sDisableUntilTouchUpIsDone = false;
            return;
        }
        if (sTouchUpCallback != NULL) {
            sTouchUpCallback(&aEvent->EventData.TouchPosition);
        }

    } else if (tEventType == EVENT_TAG_TOUCH_ACTION_ERROR) {
        // try to reset touch state
#ifdef USE_STM32F3_DISCO
        BSP_LED_Off(LED9); // BLUE Front
#endif
        sUpPosition = aEvent->EventData.TouchPosition;
        sTouchIsStillDown = false;
    } else
#endif

    if (tEventType == EVENT_TAG_BUTTON_CALLBACK_ACTION) {
        sTouchIsStillDown = false; // to disable local touch up detection

#ifdef LOCAL_DISPLAY_EXISTS
        void (*tCallback)(BDButton*,
                int16_t) = (void (*)(BDButton*, int16_t)) aEvent->EventData.GuiCallbackInfo.Handler;
        BDButton tTempButton = BDButton(aEvent->EventData.GuiCallbackInfo.ObjectIndex,
                TouchButton::getLocalButtonFromBDButtonHandle(aEvent->EventData.GuiCallbackInfo.ObjectIndex));
        tCallback(&tTempButton, aEvent->EventData.GuiCallbackInfo.ValueForHandler.Int16Value);
#else
        //BDButton * is the same as BDButtonHandle_t * since BDButton only has one BDButtonHandle_t element
        void (*tCallback)(BDButtonHandle_t*,
                int16_t) = (void (*)(BDButtonHandle_t*, int16_t)) aEvent->EventData.GuiCallbackInfo.Handler;
        tCallback(&aEvent->EventData.GuiCallbackInfo.ObjectIndex,
                aEvent->EventData.GuiCallbackInfo.ValueForHandler.Int16Value);
#endif

    } else if (tEventType == EVENT_TAG_SLIDER_CALLBACK_ACTION) {
        sTouchIsStillDown = false; // to disable local touch up detection
#ifdef LOCAL_DISPLAY_EXISTS
                void (*tCallback)(BDSlider *,
                        int16_t) = (void (*)(BDSlider *, int16_t))aEvent->EventData.GuiCallbackInfo.Handler;
                TouchSlider * tLocalSlider = TouchSlider::getLocalSliderFromBDSliderHandle(
                        aEvent->EventData.GuiCallbackInfo.ObjectIndex);
                BDSlider tTempSlider = BDSlider(aEvent->EventData.GuiCallbackInfo.ObjectIndex, tLocalSlider);
                tCallback(&tTempSlider, aEvent->EventData.GuiCallbackInfo.ValueForHandler.Int16Value);
                // synchronize local slider - remote one is synchronized by local slider itself
                if (aEvent != &localTouchEvent) {
                    tLocalSlider->setActualValueAndDrawBar(aEvent->EventData.GuiCallbackInfo.ValueForHandler.Int16Value);
                }
#else
        void (*tCallback)(BDSliderHandle_t *,
                int16_t) = (void (*)(BDSliderHandle_t *, int16_t))aEvent->EventData.GuiCallbackInfo.Handler;
        tCallback(&aEvent->EventData.GuiCallbackInfo.ObjectIndex,
                aEvent->EventData.GuiCallbackInfo.ValueForHandler.Int16Value);
#endif
    } else if (tEventType == EVENT_TAG_NUMBER_CALLBACK) {
        void (*tCallback)(float) = (void (*)(float))aEvent->EventData.GuiCallbackInfo.Handler;
        tCallback(aEvent->EventData.GuiCallbackInfo.ValueForHandler.FloatValue);

        // check for sSensorChangeCallback != NULL since we can still have a few events for sensors even if they are just disabled
    } else if (tEventType >= EVENT_TAG_FIRST_SENSOR_ACTION_CODE && tEventType <= EVENT_TAG_LAST_SENSOR_ACTION_CODE
            && sSensorChangeCallback != NULL) {
        sSensorChangeCallback(tEventType - EVENT_TAG_FIRST_SENSOR_ACTION_CODE, &aEvent->EventData.SensorCallbackInfo);

    } else if (tEventType == EVENT_TAG_SWIPE_CALLBACK_ACTION) {
        sTouchIsStillDown = false;
        if (sSwipeEndCallback != NULL) {
            // compute it locally - no need to send it over the line
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

#ifdef LOCAL_DISPLAY_EXISTS
#include "myStrings.h"
void resetTouchFlags(void) {
    sButtonTouched = false;
    sAutorepeatButtonTouched = false;
    sNothingTouched = false;
}

/**
 * Called at Touch Up
 * Handle long callback delay, check for slider and compute swipe info.
 */
void handleLocalTouchUp(void) {
    if (sLongTouchDownCallback != NULL) {
        //disable local long touch callback
        changeDelayCallback(&callbackLongTouchDownTimeout, DISABLE_TIMER_DELAY_VALUE);
    }
    if (sSliderIsMoveTarget) {
        sSliderIsMoveTarget = false;
        sDisableTouchUpOnce = true; // Do not call the touch up callback in handleEvent() since slider does not need one
    } else if (sSwipeEndCallbackEnabled) {
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
            uint16_t tTouchDeltaXAbs = abs(tSwipeInfo.TouchDeltaX);
            tSwipeInfo.TouchDeltaY = sUpPosition.PosY - sDownPosition.PosY;
            uint16_t tTouchDeltaYAbs = abs(tSwipeInfo.TouchDeltaY);
            if (tTouchDeltaXAbs >= tTouchDeltaYAbs) {
                // X direction
                tSwipeInfo.SwipeMainDirectionIsX = true;
                tSwipeInfo.TouchDeltaAbsMax = tTouchDeltaXAbs;
            } else {
                tSwipeInfo.SwipeMainDirectionIsX = false;
                tSwipeInfo.TouchDeltaAbsMax = tTouchDeltaYAbs;
            }
            sSwipeEndCallback(&tSwipeInfo);
            sDisableTouchUpOnce = true; // Do not call the touch up callback in handleEvent() since we already called a callback above
        }
    }
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
        if (!TouchButton::checkAllButtons(aActualPositionPtr->PosX, aActualPositionPtr->PosY, true)) {
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
    TouchSlider::checkAllSliders(aActualPositionPtr->PosX, aActualPositionPtr->PosY);
}

/**
 * flag for show touchpanel data on screen
 */
void setDisplayXYValuesFlag(bool aEnableDisplay) {
    sDisplayXYValuesEnabled = aEnableDisplay;
}

bool getDisplayXYValuesFlag(void) {
    return sDisplayXYValuesEnabled;
}

/**
 * show touchpanel data on screen
 */
void printTPData(int x, int y, Color_t aColor, Color_t aBackColor) {
    snprintf(StringBuffer, sizeof StringBuffer, "X:%03i Y:%03i", sActualPosition.PosX, sActualPosition.PosY);
    BlueDisplay1.drawText(x, y, StringBuffer, TEXT_SIZE_11, aColor, aBackColor);
}
#endif //LOCAL_DISPLAY_EXISTS

/**
 * return pointer to end touch callback function
 */

void (*
        getTouchUpCallback(void))(struct XYPosition * const) {
            return sTouchUpCallback;
        }
