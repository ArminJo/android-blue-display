/*
 * EventHandler.h
 *
 * @date 01.09.2014
 * @author Armin Joachimsmeyer
 *      Email:   armin.joachimsmeyer@gmail.com
 *      License: GPL v3 (http://www.gnu.org/licenses/gpl.html)
 * @version 1.5.0
 */

#ifndef EVENTHANDLER_H_
#define EVENTHANDLER_H_

#ifndef DO_NOT_NEED_BASIC_TOUCH_EVENTS
//#define DO_NOT_NEED_BASIC_TOUCH_EVENTS // outcommenting or better defining for the compiler with -DDO_NOT_NEED_BASIC_TOUCH_EVENTS saves 620 bytes FLASH and 36 bytes RAM
#endif

#include "BlueDisplay.h"

#ifdef LOCAL_DISPLAY_EXISTS
#include "ADS7846.h"
extern ADS7846 TouchPanel;
#endif

#define TOUCH_STANDARD_CALLBACK_PERIOD_MILLIS 20 // Period between callbacks while touched (a swipe is app 100 ms)
#define TOUCH_STANDARD_LONG_TOUCH_TIMEOUT_MILLIS 800 // Millis after which a touch is classified as a long touch
//
#define TOUCH_SWIPE_THRESHOLD 10  // threshold for swipe detection to suppress long touch handler calling
#define TOUCH_SWIPE_RESOLUTION_MILLIS 20

// eventType can be one of the following:
//see also android.view.MotionEvent
#define EVENT_TAG_TOUCH_ACTION_DOWN 0x00
#define EVENT_TAG_TOUCH_ACTION_UP   0x01
#define EVENT_TAG_TOUCH_ACTION_MOVE 0x02
#define EVENT_TAG_TOUCH_ACTION_ERROR 0xFF

//connection + resize handling
#define EVENT_TAG_CONNECTION_BUILD_UP 0x10
#define EVENT_TAG_RESIZE_ACTION 0x11
// Must be below 0x20 since it only sends 4 bytes data
#define EVENT_TAG_LONG_TOUCH_DOWN_CALLBACK_ACTION  0x18

// command sizes
#define RECEIVE_TOUCH_OR_DISPLAY_DATA_SIZE 4
#define TOUCH_CALLBACK_DATA_SIZE  12 // 15 - command, length and sync token
#define RECEIVE_CALLBACK_DATA_SIZE TOUCH_CALLBACK_DATA_SIZE
// events with a lower number have RECEIVE_TOUCH_OR_DISPLAY_DATA_SIZE
// events with a greater number have RECEIVE_CALLBACK_DATA_SIZE
#define EVENT_TAG_FIRST_CALLBACK_ACTION_CODE 0x20

// GUI elements (button, slider, get number) callback codes
#define EVENT_TAG_BUTTON_CALLBACK_ACTION  0x20
#define EVENT_TAG_SLIDER_CALLBACK_ACTION  0x21
#define EVENT_TAG_SWIPE_CALLBACK_ACTION  0x22
#define EVENT_TAG_NUMBER_CALLBACK 0x28

// NOP used for synchronizing
#define EVENT_TAG_NOP_ACTION 0x2F

// Sensor callback codes
// Tag number is 0x30 + sensor type constant from android.hardware.Sensor
#define EVENT_TAG_FIRST_SENSOR_ACTION_CODE 0x30
#define EVENT_TAG_LAST_SENSOR_ACTION_CODE 0x3F

#define EVENT_TAG_NO_EVENT 0xFF

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
#if (FLASHEND > 65535)
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

struct BluetoothEvent {
    uint8_t EventType;
    union EventData {
        unsigned char ByteArray[TOUCH_CALLBACK_DATA_SIZE]; // To copy data from input buffer
        struct XYPosition TouchPosition;
        struct XYSize DisplaySize;
        struct GuiCallback GuiCallbackInfo;
        struct SensorCallback SensorCallbackInfo;
        struct Swipe SwipeInfo;
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
extern volatile bool sDisableTouchUpOnce; // set normally by application if long touch action was made

void resetTouchFlags(void);
#endif

extern struct BluetoothEvent remoteTouchEvent;

#ifndef DO_NOT_NEED_BASIC_TOUCH_EVENTS
extern struct BluetoothEvent remoteTouchDownEvent;
extern bool sTouchIsStillDown;
extern struct XYPosition sDownPosition;
extern struct XYPosition sActualPosition;
extern struct XYPosition sUpPosition;
#endif

void checkAndHandleEvents(void);

void registerPeriodicTouchCallback(bool (*aPeriodicTouchCallback)(int const, int const), const uint32_t aCallbackPeriodMillis);
void setPeriodicTouchCallbackPeriod(const uint32_t aCallbackPeriod);

void registerLongTouchDownCallback(void (*aLongTouchCallback)(struct XYPosition * const), const uint16_t aLongTouchTimeoutMillis);

void registerSwipeEndCallback(void (*aSwipeEndCallback)(struct Swipe * const));

void registerConnectCallback(void (*aConnectCallback)(struct XYSize * const aMaxSize));
void registerSimpleConnectCallback(void (*aConnectCallback)(void));

void registerResizeAndConnectCallback(void (*aResizeAndConnectCallback)(struct XYSize * const aActualSize));
void registerSimpleResizeAndConnectCallback(void (*aSimpleResizeAndConnectCallback)(void));

void registerSensorChangeCallback(uint8_t aSensorType, uint8_t aSensorRate,
        void (*aSensorChangeCallback)(uint8_t aSensorType, struct SensorCallback * aSensorCallbackInfo));

#ifndef DO_NOT_NEED_BASIC_TOUCH_EVENTS
void registerTouchDownCallback(void (*aTouchDownCallback)(struct XYPosition * const aActualPositionPtr));
void registerTouchMoveCallback(void (*aTouchMoveCallback)(struct XYPosition * const aActualPositionPtr));
void registerTouchUpCallback(void (*aTouchUpCallback)(struct XYPosition * const aActualPositionPtr));
#endif

#ifdef LOCAL_DISPLAY_EXISTS
void handleLocalTouchUp(void);
void simpleTouchDownHandler(struct XYPosition * const aActualPositionPtr);
void simpleTouchHandlerOnlyForButtons(struct XYPosition * const aActualPositionPtr);
void simpleTouchDownHandlerOnlyForSlider(struct XYPosition * const aActualPositionPtr);
void simpleTouchMoveHandlerForSlider(struct XYPosition * const aActualPositionPtr);
#endif

void handleEvent(struct BluetoothEvent * aEvent);

#endif /* EVENTHANDLER_H_ */
