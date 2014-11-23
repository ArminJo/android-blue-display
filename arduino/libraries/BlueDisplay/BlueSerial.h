/*
 * BlueSerial.h
 *
 * @date 01.09.2014
 * @author Armin Joachimsmeyer
 *      Email:   armin.joachimsmeyer@gmail.com
 * @copyright GPL v3 (http://www.gnu.org/licenses/gpl.html)
 * @version 1.0.0
 */

#ifndef BLUE_SERIAL_H_
#define BLUE_SERIAL_H_

#define PAIRED_PIN 5

#define SYNC_TOKEN 0xA5
extern const int DATAFIELD_TAG_BYTE;
extern const int LAST_FUNCTION_TAG_DATAFIELD;


// simple blocking functions
void USART3_sendBuffer(uint8_t * aBytePtr, int aLengthOfDataToSend);
void sendUSART5Args(uint8_t aFunctionTag, uint16_t aXStart, uint16_t aYStart, uint16_t aXEnd, uint16_t aYEnd, uint16_t aColor);
void sendUSARTArgs(uint8_t aFunctionTag, int aNumberOfArgs, ...);
void sendUSART5ArgsAndByteBuffer(uint8_t aFunctionTag, uint16_t aXStart, uint16_t aYStart, uint16_t aXEnd, uint16_t aYEnd,
        uint16_t aColor, uint8_t * aBuffer, int aBufferLength);

#ifndef USE_SIMPLE_SERIAL
void checkForReceivedMessage(void);
#endif

bool USART_isBluetoothPaired(void);

// Use simple serial version without receive buffer and other overhead
// Remove comment if you want to use it instead of Serial....
//#define USE_SIMPLE_SERIAL

#ifdef USE_SIMPLE_SERIAL
void initSimpleSerial(uint32_t aBaudRate, bool aUsePairedPin);
void USART3_send(char aChar);
#else
void checkForReceivedMessage(void);
#endif

#endif /* BLUE_SERIAL_H_ */
