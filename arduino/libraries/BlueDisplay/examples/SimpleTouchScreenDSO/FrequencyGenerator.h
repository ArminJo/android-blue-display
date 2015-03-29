/*
 * FrequencyGenerator.h
 *
 *
 *  Created on:  01.01.2015
 *      Author:  Armin Joachimsmeyer
 *      Email:   armin.joachimsmeyer@gmx.de
 *      License: GPL v3 (http://www.gnu.org/licenses/gpl.html)
 *      Version: 1.0.0
 */

#ifndef FREQUENCYGENERATOR_H_
#define FREQUENCYGENERATOR_H_

#include <inttypes.h>
#include <avr/pgmspace.h>

void initFrequencyGenerator(void);
void initFrequencyGeneratorPage(void);
void drawFrequencyGeneratorPage(void);

extern uint8_t TouchButtonFrequencyPage;
extern const char StringStop[] PROGMEM; // "Stop"

#endif //FREQUENCYGENERATOR_H_
