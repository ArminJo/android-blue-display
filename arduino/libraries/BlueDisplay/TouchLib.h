/*
 * TouchLib.h
 *
 * @date 01.09.2014
 * @author Armin Joachimsmeyer
 *      Email:   armin.joachimsmeyer@gmail.com
 *      License: GPL v3 (http://www.gnu.org/licenses/gpl.html)
 * @version 1.0.0
 */

#ifndef TOUCHLIB_H_
#define TOUCHLIB_H_

#include "BlueDisplay.h"

#ifdef LOCAL_DISPLAY_EXISTS
#include "ADS7846.h"
extern ADS7846 TouchPanel;
#endif

struct TouchPosition {
    uint16_t PosX;
    uint16_t PosY;
};
extern struct TouchPosition sEventPosition;

extern uint8_t sEventType;
// can be one of the following:
//see also android.view.MotionEvent
#define EVENT_TAG_TOUCH_ACTION_DOWN 0x00
#define EVENT_TAG_TOUCH_ACTION_UP   0x01
#define EVENT_TAG_TOUCH_ACTION_MOVE 0x02
#define EVENT_TAG_TOUCH_ACTION_ERROR 0xFF
#define EVENT_TAG_CONNECTION_BUILD_UP 0x10
#define EVENT_TAG_RESIZE_ACTION 0x11

bool wasTouched(void);
void handleReceiveEvent(uint8_t aReceiveBuffer[]);
bool isTouchStillDown(void);
uint16_t getXActual(void);
uint16_t getYActual(void);

extern bool sTouchWasDownButNotProcessed;
extern bool sTouchIsStillDown;

#endif /* TOUCHLIB_H_ */
