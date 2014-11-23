/*
 * TouchButtonAutorepeat.cpp
 *
 * Extension of ToucButton
 * implements autorepeat feature for touch buttons
 *
 *  Created on:  30.02.2012
 *      Author:  Armin Joachimsmeyer
 *      Email:   armin.joachimsmeyer@gmx.de
 *      License: GPL v3 (http://www.gnu.org/licenses/gpl.html)
 *      Version: 1.0.0
 *
 * 	Ram usage:
 * 		12 byte + 11 bytes per button
 *
 * 	Code size:
 * 		? kByte
 *
 */

#include "TouchButtonAutorepeat.h"

TouchButtonAutorepeat::TouchButtonAutorepeat() {

}

unsigned long TouchButtonAutorepeat::sMillisOfLastCall;
unsigned long TouchButtonAutorepeat::sMillisSinceFirstTouch;
unsigned long TouchButtonAutorepeat::sMillisSinceLastCallback;
uint8_t TouchButtonAutorepeat::sState; //see TOUCHBUTTON_AUTOREPEAT_STATE_..

/*
 * Sets timing for autorepeat
 * StartNewTouchFlag must be true only on first touch
 * see code schematic below
 *
 * if (PanelTouched) {
 * 	 if (TouchButton::checkAllButtons(...))
 * 	   StartNewTouch = false;
 * 	 }
 * } else {
 *   StartNewTouch = true;
 * }
 *
 */
void TouchButtonAutorepeat::setButtonAutorepeatTiming(const uint16_t aMillisFirstDelay, const uint16_t aMillisFirstRate,
		const uint16_t aMillisSecondDelay, const uint16_t aMillisSecondRate, bool * const aAddrOfStartNewTouchFlag) {
	mMillisFirstDelay = aMillisFirstDelay;
	mMillisFirstRate = aMillisFirstRate;
	mMillisSecondDelay = aMillisSecondDelay;
	mMillisSecondRate = aMillisSecondRate;
	mAddrOfStartNewTouchFlag = aAddrOfStartNewTouchFlag;
	// replace standard button handler
	if (mOnTouchHandler != mOnTouchHandlerAutorepeat) {
		mOnTouchHandlerAutorepeat = mOnTouchHandler;
		mOnTouchHandler = (void (*)(TouchButton*, int16_t))&autorepeatTouchHandler;
	}
}

/*
 * Static function for autorepeat
 */
void TouchButtonAutorepeat::autorepeatTouchHandler(TouchButtonAutorepeat * const aTheTouchedButton, int16_t aButtonValue) {
	// count milliseconds for loop control
	unsigned long tMillis = millis();
	bool tDoCallback = false;
	if (*aTheTouchedButton->mAddrOfStartNewTouchFlag) {
		// First touch here
		sState = TOUCHBUTTON_AUTOREPEAT_STATE_START;
		sMillisSinceFirstTouch = 0;
		tDoCallback = true;
	} else {
		// subsequent calls for the touch -> do autorepeat
		sMillisSinceFirstTouch += tMillis - sMillisOfLastCall;
		sMillisSinceLastCallback += tMillis - sMillisOfLastCall;
		if (sMillisSinceFirstTouch > aTheTouchedButton->mMillisSecondDelay) {
			sState = TOUCHBUTTON_AUTOREPEAT_STATE_AFTER_SECOND;
			if (sMillisSinceLastCallback > aTheTouchedButton->mMillisSecondRate) {
				tDoCallback = true;
			}
		} else if (sMillisSinceFirstTouch > aTheTouchedButton->mMillisFirstDelay) {
			sState = TOUCHBUTTON_AUTOREPEAT_STATE_AFTER_FIRST;
			if (sMillisSinceLastCallback > aTheTouchedButton->mMillisFirstRate) {
				tDoCallback = true;
			}
		}
	}
	sMillisOfLastCall = tMillis;
	if (tDoCallback) {
		sMillisSinceLastCallback = 0;
		aTheTouchedButton->mOnTouchHandlerAutorepeat(aTheTouchedButton, aButtonValue);
	}
}

uint8_t TouchButtonAutorepeat::getState() {
	return sState;
}

