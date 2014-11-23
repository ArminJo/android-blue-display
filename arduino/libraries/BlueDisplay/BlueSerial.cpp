/*
 * BlueSerial.cpp
 *
 * @date 01.09.2014
 * @author Armin Joachimsmeyer
 *      Email:   armin.joachimsmeyer@gmail.com
 * @copyright GPL v3 (http://www.gnu.org/licenses/gpl.html)
 * @version 1.0.0
 */

#include <Arduino.h>
#include "BlueSerial.h"
#include "TouchLib.h"
#include "digitalWriteFast.h"

// Data field types
const int DATAFIELD_TAG_BYTE = 0x01;
const int DATAFIELD_TAG_SHORT = 0x02;
const int DATAFIELD_TAG_INT = 0x03;
const int DATAFIELD_TAG_LONG = 0x04;
const int DATAFIELD_TAG_FLOAT = 0x05;
const int DATAFIELD_TAG_DOUBLE = 0x06;
const int LAST_FUNCTION_TAG_DATAFIELD = 0x07;

/**
 * (Touch) Message has 7 bytes:
 * 1 - Gross message length in bytes
 * 2 - Function code
 * 3 - X Position LSB
 * 4 - X Position MSB
 * 5 - Y Position LSB
 * 6 - Y Position MSB
 * 7 - Sync Token
 */
#define TOUCH_COMMAND_SIZE_BYTE  7

// definitions from <wiring_private.h>
#ifndef cbi
#define cbi(sfr, bit) (_SFR_BYTE(sfr) &= ~_BV(bit))
#endif
#ifndef sbi
#define sbi(sfr, bit) (_SFR_BYTE(sfr) |= _BV(bit))
#endif

bool usePairedPin = false;

void setUsePairedPin(bool aUsePairedPin) {
    usePairedPin = aUsePairedPin;
}

bool USART_isBluetoothPaired(void) {
    if (!usePairedPin) {
        return true;
    }
    // use tVal to produce optimal code with the compiler
    uint8_t tVal = digitalReadFast(PAIRED_PIN);
    if (tVal != 0) {
        return true;
    }
    return false;
}

#ifdef USE_SIMPLE_SERIAL
void initSimpleSerial(uint32_t aBaudRate, bool aUsePairedPin) {
    usePairedPin = aUsePairedPin;
    if (aUsePairedPin) {
        pinMode(PAIRED_PIN, INPUT);
    }
    uint16_t baud_setting;

    UCSR0A = 1 << U2X0; // Double Speed Mode
    // Exact value = 17,3611 (- 1) for 115200  2,1%
    // 8,68 (- 1) for 230400 8,5% for 8, 3.7% for 9
    // 4,34 (- 1) for 460800 8,5%
    // HC-05 Specified Max Total Error (%) for 8 bit= +3.90/-4.00
    baud_setting = (((F_CPU / 4) / aBaudRate) - 1) / 2;    // /2 after -1 because of better rounding

    // assign the baud_setting, a.k.a. ubbr (USART Baud Rate Register)
    UBRR0H = baud_setting >> 8;
    UBRR0L = baud_setting;

    // enable: TX, RX, RX Complete Interrupt
    UCSR0B = (1 << RXEN0) | (1 << TXEN0) | (1 << RXCIE0);
}

/**
 * ultra simple blocking USART send routine - works 100%!
 */
void USART3_send(char aChar) {
// wait for buffer to become empty
    while (!((UCSR0A ) & (1 << UDRE0))) {
        ;
    }
    UDR0 = aChar;
}
#endif

/**
 * TX of USART0 is port D1
 * RX is port D0
 */
/*
 * RECEIVE BUFFER
 */
//Ring buffer for 6 bytes since no need for SYNC_TOKEN be stored
#define RECEIVE_BUFFER_SIZE 6
uint8_t sReceiveBuffer[RECEIVE_BUFFER_SIZE];
uint8_t sReceiveBufferIndex = 0; // Index of first free position in buffer
bool sReceiveBufferOutOfSync = false;

/**
 * very simple blocking USART send routine - works 100%!
 */
void sendUSARTBufferNoSizeCheck(uint8_t * aParameterBufferPointer, int aParameterBufferLength, uint8_t * aDataBufferPointer,
        int aDataBufferLength) {
#ifdef USE_SIMPLE_SERIAL
    while (aParameterBufferLength > 0) {
        // wait for USART send buffer to become empty
        while (!((UCSR0A ) & (1 << UDRE0))) {
            ;
        }
        //USART_SendData(USART3, *aBytePtr);
        UDR0 = *aParameterBufferPointer;
        aParameterBufferPointer++;
        aParameterBufferLength--;
    }
    while (aDataBufferLength > 0) {
        // wait for USART send buffer to become empty
        while (!((UCSR0A ) & (1 << UDRE0))) {
            ;
        }
        //USART_SendData(USART3, *aBytePtr);
        UDR0 = *aDataBufferPointer;
        aDataBufferPointer++;
        aDataBufferLength--;
    }
#else
    Serial.write(aParameterBufferPointer, aParameterBufferLength);
    Serial.write(aDataBufferPointer, aDataBufferLength);
#endif
}

/**
 * PROTOCOL USED:
 * Message:
 * 1. Sync Byte A5
 * 2. Byte Function token
 * 3. Short length (in short units) of parameters
 * 4. Short n parameters
 *
 * Optional:
 * 1. Sync Byte A5
 * 2. Byte Data_Size_Type token (byte, short etc.)
 * 3. Short length of data
 * 4. length data values
 *
 */

/**
 * send:
 * 1. Sync Byte A5
 * 2. Byte Function token
 * 3. Short length of parameters (here 5*2)
 * 4. Short n parameters
 */
void sendUSART5Args(uint8_t aFunctionTag, uint16_t aXStart, uint16_t aYStart, uint16_t aXEnd, uint16_t aYEnd, uint16_t aColor) {
    uint16_t tParamBuffer[7];

    uint16_t * tBufferPointer = &tParamBuffer[0];
    *tBufferPointer++ = aFunctionTag << 8 | SYNC_TOKEN; // add sync token
    *tBufferPointer++ = 10; // parameter length
    *tBufferPointer++ = aXStart;
    *tBufferPointer++ = aYStart;
    *tBufferPointer++ = aXEnd;
    *tBufferPointer++ = aYEnd;
    *tBufferPointer++ = aColor;
    sendUSARTBufferNoSizeCheck((uint8_t*) &tParamBuffer[0], 14, NULL, 0);
}

/**
 *
 * @param aFunctionTag
 * @param aNumberOfArgs currently not more than 7 args are supported
 */
void sendUSARTArgs(uint8_t aFunctionTag, int aNumberOfArgs, ...) {
    if (aNumberOfArgs > 7) {
        return;
    }

    uint16_t tParamBuffer[9];
    va_list argp;
    uint16_t * tBufferPointer = &tParamBuffer[0];
    *tBufferPointer++ = aFunctionTag << 8 | SYNC_TOKEN; // add sync token
    va_start(argp, aNumberOfArgs);

    *tBufferPointer++ = aNumberOfArgs * 2;
    for (uint8_t i = 0; i < aNumberOfArgs; ++i) {
        *tBufferPointer++ = va_arg(argp, int);
    }
    va_end(argp);
    sendUSARTBufferNoSizeCheck((uint8_t*) &tParamBuffer[0], aNumberOfArgs * 2 + 4, NULL, 0);
}

/**
 * Assembles parameter header and appends header for data field
 */
void sendUSART5ArgsAndByteBuffer(uint8_t aFunctionTag, uint16_t aXStart, uint16_t aYStart, uint16_t aXEnd, uint16_t aYEnd,
        uint16_t aColor, uint8_t * aBuffer, int aBufferLength) {

    uint16_t tParamBuffer[9];

    uint16_t * tBufferPointer = &tParamBuffer[0];
    *tBufferPointer++ = aFunctionTag << 8 | SYNC_TOKEN; // add sync token
    *tBufferPointer++ = 10; // length
    *tBufferPointer++ = aXStart;
    *tBufferPointer++ = aYStart;
    *tBufferPointer++ = aXEnd;
    *tBufferPointer++ = aYEnd;
    *tBufferPointer++ = aColor;

    // add data field header
    *tBufferPointer++ = DATAFIELD_TAG_BYTE << 8 | SYNC_TOKEN; // start new transmission block
    *tBufferPointer++ = aBufferLength; // length in byte
    sendUSARTBufferNoSizeCheck((uint8_t*) &tParamBuffer[0], 18, aBuffer, aBufferLength);
}

/**
 * Read message in buffer for one Touch event.
 * After RECEIVE_BUFFER_SIZE bytes check if SYNC_TOKEN was sent.
 * If OK then interpret content and reset buffer.
 */
#ifdef USE_SIMPLE_SERIAL
ISR(USART_RX_vect) {
    uint8_t tByte = UDR0;
    if (sReceiveBufferOutOfSync) {
        // just wait for next sync token and reset buffer
        if (tByte == SYNC_TOKEN) {
            sReceiveBufferOutOfSync = false;
            sReceiveBufferIndex = 0;
        }
    } else {
        if (sReceiveBufferIndex == RECEIVE_BUFFER_SIZE) {
            // now we expect a sync token
            if (tByte == SYNC_TOKEN) {
                // touch event complete received
                handleReceiveEvent(sReceiveBuffer);
                sReceiveBufferIndex = 0;
            } else {
                // reset buffer since we had an overflow or glitch
                sReceiveBufferOutOfSync = true;
                sReceiveBufferIndex = 0;
            }
        } else {
            // plain message byte
            sReceiveBuffer[sReceiveBufferIndex++] = tByte;
        }
    }
}
#else
void checkForReceivedMessage(void) {
    if (sReceiveBufferOutOfSync) {
        // just wait for next sync token
        while (Serial.available() > 0) {
            if (Serial.read() == SYNC_TOKEN) {
                sReceiveBufferOutOfSync = false;
                break;
            }
        }
    } else {
        while (Serial.available() > RECEIVE_BUFFER_SIZE && !sReceiveBufferOutOfSync) {
            Serial.readBytes((char *) sReceiveBuffer, RECEIVE_BUFFER_SIZE);
            if (Serial.read() == SYNC_TOKEN) {
                handleReceiveEvent(sReceiveBuffer);
            } else {
                sReceiveBufferOutOfSync = true;
            }
        }
    }
}
#endif
