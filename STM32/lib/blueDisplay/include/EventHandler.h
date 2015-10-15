/*
 * EventHandler.h
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

#ifndef EVENTHANDLER_H_
#define EVENTHANDLER_H_

#ifndef DO_NOT_NEED_BASIC_TOUCH_EVENTS
//#define DO_NOT_NEED_BASIC_TOUCH_EVENTS // outcommenting or better defining for the compiler with -DDO_NOT_NEED_BASIC_TOUCH_EVENTS saves 620 bytes FLASH and 36 bytes RAM
#endif

#include "BlueDisplay.h" // for struct XYSize
#include "BlueDisplayProtocol.h"

//#ifdef LOCAL_DISPLAY_EXISTS
//#include "ADS7846.h"
//extern ADS7846 TouchPanel;
//#endif

#define TOUCH_STANDARD_CALLBACK_PERIOD_MILLIS 20 // Period between callbacks while touched (a swipe is app 100 ms)
#define TOUCH_STANDARD_LONG_TOUCH_TIMEOUT_MILLIS 800 // Millis after which a touch is classified as a long touch
//
#define TOUCH_SWIPE_THRESHOLD 10  // threshold for swipe detection to suppress long touch handler calling
#define TOUCH_SWIPE_RESOLUTION_MILLIS 20

struct XYPosition {
    uint16_t PosX;
    uint16_t PosY;
};

struct Swipe {
    bool SwipeMainDirectionIsX; // true if TouchDeltaXAbs >= TouchDeltaYAbs
    uint8_t Filler;
    uint16_t Free;
    uint16_t TouchStartX;
    uint16_t TouchStartY;
    int16_t TouchDeltaX;
    int16_t TouchDeltaY;
    uint16_t TouchDeltaAbsMax; // max of TouchDeltaXAbs and TouchDeltaYAbs to easily decide if swipe is large enough to be accepted
};

struct GuiCallback {
    uint16_t ObjectIndex;
    uint16_t Free;
#if (FLASHEND > 65535 || AVR != 1)
    void * Handler;
#else
    void * Handler;
    void * Handler_upperWord; // not used on 16 bit address cpu
#endif
    union ValueForHandler {
        uint16_t Int16Value;
        uint32_t Int32Value;
        float FloatValue;
    } ValueForHandler;
};

struct SensorCallback {
    float ValueX;
    float ValueY;
    float ValueZ;
};

struct IntegerInfoCallback {
    uint16_t SubFunction;
    uint16_t Special;
    void * Handler;
    uint16_t Int16Value1;
    uint16_t Int16Value2;
};

struct BluetoothEvent {
    uint8_t EventType;
    union EventData {
        unsigned char ByteArray[TOUCH_CALLBACK_DATA_SIZE]; // To copy data from input buffer
        struct XYPosition TouchPosition;
        struct XYSize DisplaySize;
        struct GuiCallback GuiCallbackInfo;
        struct Swipe SwipeInfo;
        struct SensorCallback SensorCallbackInfo;
        struct IntegerInfoCallback IntegerInfoCallbackData;
    } EventData;
};

#ifdef LOCAL_DISPLAY_EXISTS
extern struct BluetoothEvent localTouchEvent;
/*
 * helper variables
 */
extern bool sButtonTouched;
extern bool sAutorepeatButtonTouched;
extern bool sSliderTouched;
extern bool sNothingTouched;
extern bool sSliderIsMoveTarget;
extern bool sDisableTouchUpOnce; // set normally by application if long touch action was made
extern bool sDisableUntilTouchUpIsDone;// Skip all touch move and touch up events until touch is released

void resetTouchFlags(void);
#endif

extern struct BluetoothEvent remoteEvent;
#ifdef AVR
// Second event buffer for simple serial to avoid overwriting fast events
extern struct BluetoothEvent remoteTouchDownEvent;
#endif

#ifndef DO_NOT_NEED_BASIC_TOUCH_EVENTS
extern bool sTouchIsStillDown;
extern struct XYPosition sDownPosition;
extern struct XYPosition sActualPosition;
extern struct XYPosition sUpPosition;
#endif

void delayMillisWithCheckAndHandleEvents(int32_t aTimeMillis);

void checkAndHandleEvents(void);

void registerLongTouchDownCallback(void (*aLongTouchCallback)(struct XYPosition *), uint16_t aLongTouchTimeoutMillis);

void registerSwipeEndCallback(void (*aSwipeEndCallback)(struct Swipe *));
void setSwipeEndCallbackEnabled(bool aSwipeEndCallbackEnabled);

void registerConnectCallback(void (*aConnectCallback)(void));
void registerReorientationCallback(void (*aReorientationCallback)(void));

/*
 * Connect always include a redraw
 */
void registerRedrawCallback(void (*aRedrawCallback)(void));
void (* getRedrawCallback(void))(void);

void registerSensorChangeCallback(uint8_t aSensorType, uint8_t aSensorRate, uint8_t aFilterFlag,
        void (*aSensorChangeCallback)(uint8_t aSensorType, struct SensorCallback * aSensorCallbackInfo));

// defines for backward compatibility
#define registerSimpleConnectCallback(aConnectCallback) registerConnectCallback(aConnectCallback)
#define registerSimpleResizeAndReconnectCallback(aRedrawCallback) registerRedrawCallback(aRedrawCallback)
#define registerSimpleResizeAndConnectCallback registerRedrawCallback(aRedrawCallback)
#define registerSimpleResizeCallback(aRedrawCallback) registerRedrawCallback(aRedrawCallback)
#define getSimpleResizeAndConnectCallback() getRedrawCallback()

#ifndef DO_NOT_NEED_BASIC_TOUCH_EVENTS
void registerTouchDownCallback(void (*aTouchDownCallback)(struct XYPosition * aActualPositionPtr));
void registerTouchMoveCallback(void (*aTouchMoveCallback)(struct XYPosition * aActualPositionPtr));
void registerTouchUpCallback(void (*aTouchUpCallback)(struct XYPosition * aActualPositionPtr));
void setTouchUpCallbackEnabled(bool aTouchUpCallbackEnabled);
void (* getTouchUpCallback(void))(struct XYPosition * );
#endif

#ifdef LOCAL_DISPLAY_EXISTS
void handleLocalTouchUp(void);
void callbackLongTouchDownTimeout(void);
void simpleTouchDownHandler(struct XYPosition * aActualPositionPtr);
void simpleTouchHandlerOnlyForButtons(struct XYPosition * aActualPositionPtr);
void simpleTouchDownHandlerOnlyForSlider(struct XYPosition * aActualPositionPtr);
void simpleTouchDownHandlerForSlider(struct XYPosition * aActualPositionPtr);
void simpleTouchMoveHandlerForSlider(struct XYPosition * aActualPositionPtr);

// for local autorepeat button
void registerPeriodicTouchCallback(bool (*aPeriodicTouchCallback)(int, int), uint32_t aCallbackPeriodMillis);
void setPeriodicTouchCallbackPeriod(uint32_t aCallbackPeriod);

bool getDisplayXYValuesFlag(void);
void setDisplayXYValuesFlag(bool aEnableDisplay);
void printTPData(int x, int y, Color_t aColor, Color_t aBackColor);
#endif

#ifdef __cplusplus
extern"C" {
#endif
    void handleEvent(struct BluetoothEvent * aEvent);
#ifdef __cplusplus
}
#endif

#endif /* EVENTHANDLER_H_ */
