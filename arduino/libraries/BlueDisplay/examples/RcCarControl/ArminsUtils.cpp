/*
 *  ArminsUtils.cpp
 *
 *  Copyright (C) 2016  Armin Joachimsmeyer
 *  armin.joachimsmeyer@gmail.com
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.

 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/gpl.html>.
 *
 */

#include <Arduino.h>
#include "ArminsUtils.h"

// must not be constant, since then we get an undefined reference error at link time
extern uint8_t TRIGGER_OUT_PIN;
extern uint8_t ECHO_IN_PIN;

int sLastDistance;
int getUSDistanceAsCentiMeterWithCentimeterTimeout(uint8_t aTimeoutCentimeter) {
    // 58,48 us per centimeter (forth and back)
    long tTimeoutMicros = aTimeoutCentimeter * 59;
    return getUSDistanceAsCentiMeter(tTimeoutMicros);
}
/*
 * returns -1 if timeout happens
 * timeout of 5850 is equivalent to 1m
 */
int getUSDistanceAsCentiMeter(unsigned long aTimeoutMicros) {
// need minimum 10 usec Trigger Pulse
    digitalWrite(TRIGGER_OUT_PIN, HIGH);
#ifdef DEBUG
    delay(2); // to see it on scope
#else
    delayMicroseconds(10);
#endif
// falling edge starts measurement
    digitalWrite(TRIGGER_OUT_PIN, LOW);

    /*
     * Get echo length. 58,48 us per centimeter (forth and back)
     * => 50cm gives 2900 us, 2m gives 11900 us
     */
    unsigned long tPulseLength = pulseIn(ECHO_IN_PIN, HIGH, aTimeoutMicros);
    if (tPulseLength == 0) {
        // timeout happened
        return -1;
    }
// +1cm was measured at working device
    int tDistance = (tPulseLength / 58) + 1;
    sLastDistance = tDistance;
    return tDistance;
}

void blinkLed(uint8_t aLedPin, uint8_t aNumberOfBlinks, uint16_t aBlinkDelay) {
    for (int i = 0; i < aNumberOfBlinks; i++) {
        digitalWrite(aLedPin, HIGH);
        delay(aBlinkDelay);
        digitalWrite(aLedPin, LOW);
        delay(aBlinkDelay);
    }
}

/*
 * For sleep modes see sleep.h
 * SLEEP_MODE_IDLE
 * SLEEP_MODE_ADC
 * SLEEP_MODE_PWR_DOWN
 * SLEEP_MODE_PWR_SAVE
 * SLEEP_MODE_STANDBY
 * SLEEP_MODE_EXT_STANDBY
 */
void initSleep(uint8_t tSleepMode) {
    sleep_enable()
    ;
    set_sleep_mode(SLEEP_MODE_PWR_DOWN);
}

/*
 * aWatchdogPrescaler (see wdt.h) can be one of
 * WDTO_15MS, 30, 60, 120, 250, WDTO_500MS
 * WDTO_1S to WDTO_8S
 * (value 0-9)
 */
void sleepWithWatchdog(uint8_t aWatchdogPrescaler) {
    MCUSR &= ~(1 << WDRF); // Clear WDRF in MCUSR
    cli();
    wdt_reset();
    WDTCSR |= (1 << WDCE) | (1 << WDE); // Bit 3+4 to unlock WDTCSR
    WDTCSR = aWatchdogPrescaler | (1 << WDIE) | (1 << WDIF); // Watchdog prescaler + interrupt enable + reset interrupt flag
    sei();
    sleep_cpu()
    ;
    wdt_disable();
}

volatile uint16_t sNumberOfSleeps = 0;

ISR(WDT_vect) {
    sNumberOfSleeps++;
}
