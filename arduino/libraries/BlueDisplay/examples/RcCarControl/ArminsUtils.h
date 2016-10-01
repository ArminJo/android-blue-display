#include <avr/sleep.h>
#include <avr/wdt.h>

// Timeout of 20000L is 3.4 meter
int getUSDistanceAsCentiMeter(unsigned long aTimeoutMicros = 20000L);
int getUSDistanceAsCentiMeterWithCentimeterTimeout(uint8_t aTimeoutCentimeter);
extern int sLastDistance;

void initSleep(uint8_t tSleepMode);
void sleepWithWatchdog(uint8_t aWatchdogPrescaler);
extern volatile uint16_t sNumberOfSleeps;

void blinkLed(uint8_t aLedPin, uint8_t aNumberOfBlinks, uint16_t aBlinkDelay);
