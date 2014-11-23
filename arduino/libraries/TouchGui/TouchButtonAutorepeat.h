/*
 * TouchButtonAutorepeat.h
 *
 * Extension of ToucButton
 * implements autorepeat feature for touchbuttons
 *
 *  Created on:  30.02.2012
 *      Author:  Armin Joachimsmeyer
 *      Email:   armin.joachimsmeyer@gmx.de
 *      License: GPL v3 (http://www.gnu.org/licenses/gpl.html)
 *      Version: 1.0.0
 */

#ifndef TOUCHBUTTON_AUTOREPEAT_H_
#define TOUCHBUTTON_AUTOREPEAT_H_

#include <TouchButton.h>
#include <Arduino.h>

#define TOUCHBUTTON_AUTOREPEAT_STATE_START 			0
#define TOUCHBUTTON_AUTOREPEAT_STATE_AFTER_FIRST 	1
#define TOUCHBUTTON_AUTOREPEAT_STATE_AFTER_SECOND 	3

class TouchButtonAutorepeat: public TouchButton {
public:

// static because only one touch possible (no multitouch supported)
	static unsigned long sMillisOfLastCall;
	static unsigned long sMillisSinceFirstTouch;
	static unsigned long sMillisSinceLastCallback;
	static uint8_t sState; //see TOUCHBUTTON_AUTOREPEAT_MODE_..
	static void autorepeatTouchHandler(TouchButtonAutorepeat * const aTheTouchedButton, int16_t aButtonValue);
	void setButtonAutorepeatTiming(const uint16_t aMillisFirstDelay, const uint16_t aMillisFirstRate,
			const uint16_t aMillisSecondDelay, const uint16_t aMillisSecondRate, bool * const aAddrOfStartNewTouchFlag);
	static uint8_t getState();

	TouchButtonAutorepeat();


private:
	uint16_t mMillisFirstDelay;
	uint16_t mMillisFirstRate;
	uint16_t mMillisSecondDelay;
	uint16_t mMillisSecondRate;
	bool * mAddrOfStartNewTouchFlag;
	void (*mOnTouchHandlerAutorepeat)(TouchButton * const, int16_t);

};

#endif /* TOUCHBUTTON_AUTOREPEAT_H_ */
