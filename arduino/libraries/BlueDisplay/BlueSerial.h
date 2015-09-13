/*
 * BlueSerial.h
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
 *
 *
 * SEND PROTOCOL USED:
 * Message:
 * 1. Sync byte A5
 * 2. Byte function token
 * 3. Short length (in short units) of parameters
 * 4. Short n parameters
 *
 * Optional Data:
 * 1. Sync Byte A5
 * 2. Byte Data_Size_Type token (byte, short etc.)
 * 3. Short length of data
 * 4. Length data values
 *
 *
 * RECEIVE PROTOCOL USED:
 *
 * Touch/size message has 7 bytes:
 * 1 - Gross message length in bytes
 * 2 - Function code
 * 3 - X Position LSB
 * 4 - X Position MSB
 * 5 - Y Position LSB
 * 6 - Y Position MSB
 * 7 - Sync token
 *
 * Callback message has 15 bytes:
 * 1 - Gross message length in bytes
 * 2 - Function code
 * 16 bit button index
 * 16 bit filler
 * 32 bit callback address
 * 32 bit value
 * 13 - Sync token
 */

#ifndef BLUE_SERIAL_H_
#define BLUE_SERIAL_H_

#define BAUD_STRING_4800 "4800"
#define BAUD_STRING_9600 "9600"
#define BAUD_STRING_19200 "19200"
#define BAUD_STRING_38400 "38400"
#define BAUD_STRING_57600 "57600"
#define BAUD_STRING_115200 "115200"
#define BAUD_STRING_230400 "230400"
#define BAUD_STRING_460800 "460800"
#define BAUD_STRING_921600 " 921600"
#define BAUD_STRING_1382400 "1382400"

#define BAUD_4800 (4800)
#define BAUD_9600 (9600)
#define BAUD_19200 (19200)
#define BAUD_38400 (38400)
#define BAUD_57600 (57600)
#define BAUD_115200 (115200)
#define BAUD_230400 (230400)
#define BAUD_460800 (460800)
#define BAUD_921600 ( 921600)
#define BAUD_1382400 (1382400)

#define PAIRED_PIN 5

#define SYNC_TOKEN 0xA5
extern const int DATAFIELD_TAG_BYTE;
extern const int LAST_FUNCTION_TAG_DATAFIELD;

void sendUSARTArgs(uint8_t aFunctionTag, int aNumberOfArgs, ...);
void sendUSARTArgsAndByteBuffer(uint8_t aFunctionTag, int aNumberOfArgs, ...);
void USART3_sendBuffer(uint8_t * aBytePtr, int aLengthOfDataToSend);
void sendUSART5Args(uint8_t aFunctionTag, uint16_t aXStart, uint16_t aYStart, uint16_t aXEnd, uint16_t aYEnd, uint16_t aColor);
void sendUSART5ArgsAndByteBuffer(uint8_t aFunctionTag, uint16_t aXStart, uint16_t aYStart, uint16_t aXEnd, uint16_t aYEnd,
        uint16_t aColor, uint16_t aBufferLength, uint8_t * aBuffer);

#ifdef LOCAL_DISPLAY_EXISTS
bool USART_isBluetoothPaired(void);
#else
#define USART_isBluetoothPaired() (true)
#endif

extern bool allowTouchInterrupts;
void initSimpleSerial(uint32_t aBaudRate, bool aUsePairedPin);
void USART3_send(char aChar);

void serialEvent();

#endif /* BLUE_SERIAL_H_ */
