/*
 * HC-05Initialization.cpp
 *
 * 0. Modify the lines 51 and 52.
 * 1. Load this sketch to your Arduino.
 * 2. Disconnect Arduino from power.
 * 3. Connect Arduino rx/tx with HC-05 module tx/rx (crossover!) and do not forget to attach 5V to the module.
 * 4. Connect key pin of HC-05 module with 3.3V line (of Arduino).
      On my kind of board (a single sided one) it is sufficient to press the tiny button while powering up.
 * 5. Apply power to Arduino and module.
 * 6. Wait for LED to blink continously (This could take up to 10 seconds).
      If it only blinks twice (after 8 seconds for boot flickering), check your wiring.
 * 7. Disconnect Arduino and module from power.
 * 8. Disconnect key pin of HC-05 module from 3.3V line of Arduino.
 * 9. Congratulation you're done.
 *
 * If you see " ... stk500_getsync(): not in sync .." while reprogramming the Arduino
 * it may help to disconnect the Arduino RX from the Hc-05 module TX pin temporarily.
 *
 *  Created on: 15.12.2014
 *      Author: Armin Joachimsmeyer
 */

/*
 * Baud rates supported by the HC-05 module
 */
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

/************************************
** MODIFY THESE VALUES IF YOU NEED **
************************************/
#define MY_HC05_NAME "HC-05-DSO"
#define MY_HC05_BAUDRATE BAUD_STRING_115200

int LED = 13;   // LED pin
bool signalSucess = false;

void setup() {
    pinMode(LED, OUTPUT);
    Serial.begin(BAUD_38400);  // HC-05 default speed in AT command mode

    delay(3000);
    digitalWrite(LED, HIGH);
    delay(1000);
    digitalWrite(LED, LOW);
    delay(300);
    Serial.println("AT");
    delay(300);
    int tReturnedBytes = Serial.available();
    /*
     * Check if "OK\n\r" returned
     */
    if (tReturnedBytes == 4) {
        if (Serial.read() == 'O') {
            if (Serial.read() == 'K') {
                /**
                 * program HC05 Module
                 */
                // reset to original state
                Serial.println("AT+ORGL");
                delay(300);
                // Set name
                Serial.println("AT+NAME=" MY_HC05_NAME);
                delay(300);
                // Set baud / 1 stop bit / no parity
                Serial.println("AT+UART=" MY_HC05_BAUDRATE ",0,0");
                signalSucess = true;
            }
        }
    } else {
      Serial.print("No response to AT command, # of returned bytes are:");
      Serial.println(tReturnedBytes);
    }
    digitalWrite(LED, HIGH);
    delay(1000);
    digitalWrite(LED, LOW);
    delay(300);
}

void loop() {
    digitalWrite(LED, HIGH);
    if (signalSucess) {
        delay(300);
        digitalWrite(LED, LOW);
        delay(300);
    }
}
