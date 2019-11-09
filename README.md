# [BlueDisplay App](https://play.google.com/store/apps/details?id=de.joachimsmeyer.android.bluedisplay) - convert your smartphone into an Android remote touch display for your Arduino or ARM projects.
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Hit Counter](https://hitcounter.pythonanywhere.com/count/tag.svg?url=https%3A%2F%2Fgithub.com%2FArminJo%2Fandroid-blue-display)](https://github.com/brentvollebregt/hit-counter)

## SUMMARY
Let the Arduino sketch create a GUI with Graphics, Buttons and Sliders on your smartphone by simply connecting a HC-05 to the rx/tx pins of your Arduino.
It receives draw requests from Arduino over Bluetooth and renders it.
GUI callback, touch and sensor events are sent back to Arduino.
No Android programming needed!

## Features
- Graphic + text output as well as printf implementation.
- Draw chart from byte or short values. Enables clearing of last drawn chart.
- Play system tones.
- Touch button + slider objects with tone feedback.
- Button and slider callback as well as touch and sensor events are sent back to Arduino.
- Automatic and manually scaling of display region.
- Easy mapping of UTF-8 characters like Ohm, Celsius etc..
- Up to 115200 Baud using HC-05 modules.
- Local display of received and sent commands for debug purposes.
- Hex and ASCII output of received Bluetooth data at log level verbose.
- Debug messages as toasts.
- C++ Libraries for [Arduino](https://github.com/ArminJo/Arduino-BlueDisplay).
 and [ARM (STM)](https://github.com/ArminJo/android-blue-display/tree/master/STM32/lib).

# Revision History
### Version 4.1
- Improved startup. New message if no data received after connect and part of screen is inactive/black, to access the log.

### Version 4.0 
- Connection with  USB OTG cable now also possible. In this case no Bluetooth adapter is needed.
- Handling of no input for getNumber.
- Slider setScaleFactor() does not scale the actual value, which is delivered as initial value at init().
- Improved tone volume setting - can be adjusted at the smartphone too. trim() for all button caption strings.

### Version 3.6
- connect, reconnect and autoconnect improved/added. Improved debug() command. Simplified Red/Green button handling.

### Version 3.5
- Slider scaling changed and unit value added.

### Version 3.4
- Timeout for data messages. Get number initial value fixed.
- Bug autorepeat button in conjunction with UseUpEventForButtons fixed.

### Version 3.3
- Fixed silent tone bug for Lollipop and other bugs.Multiline text /r /n handling.
- Android time accessible on Arduino. Debug messages as toasts. Changed create button.
- Slider values scalable. GUI multi touch.Hex and ASCII output of received Bluetooth data at log level verbose.

### Version 3.2
- Improved tone and fullscreen handling. Internal refactoring. Bugfixes and minor improvements.

### Version 3.1
- Local display of received and sent commands for debug purposes.

### Version 3.0
- Android sensor accessible by Arduino.


## Example for Hex + ASCII output (on log level verbose):
```
V Hex= 00 4C 13 A5 01 08 00 53 65 74 74 69 6E 67 73 A5
V Asc=     L                 S  e  t  t  i  n  g  s
V Hex= 70 12 00 04 00 00 00 00 00 60 00 34 00 00 F8 0B
V Asc=  p                          `     4
V Hex= 03 00 00 30 21 A5 01 07 00 48 69 73 74 6F 72 79
V Asc=           0  !              H  i  s  t  o  r  y
```

# Hints
If you need debugging with print() you must use the debug() functions since using Serial.print() etc. gives errors (we have only one serial port on the Arduino) . E.g.
```
BlueDisplay1.debug("\r\nDoBlink=", (uint8_t) doBlink);
```

To enable programming of the Arduino while the HC-05 module is connected, use a diode to connect Arduino rx and HC-05 tx.
On Arduino MEGA 2560 TX1 is used, so no diode is needed.
```
                 |\ |
   Arduino-rx ___| \|___ HC-05-tx
                 | /|
                 |/ |
```


BlueDisplay example breadboard picture
![Breadboard picture](https://github.com/ArminJo/android-blue-display/blob/gh-pages/pictures/Blink1.jpg)
Fritzing schematic for BlueDisplay example
![Fritzing schematics](https://github.com/ArminJo/android-blue-display/blob/gh-pages/schematics/BlueDisplayBlink_Steckplatine.png)
DSO with passive attenuator on breadboard
![DSO with passive attenuator on breadboard](https://github.com/ArminJo/android-blue-display/blob/gh-pages/pictures/ArduinoDSO.jpg)
At work
![DSO at work](https://github.com/ArminJo/android-blue-display/blob/gh-pages/pictures/DSO+Tablet.jpg)
Fritzing
![DSO Fritzing](https://github.com/ArminJo/Arduino-Simple-DSO/blob/master/fritzing/Arduino_Nano_DSO_Steckplatine.png)
Schematic
![DSO Schematic](https://github.com/ArminJo/Arduino-Simple-DSO/blob/master/fritzing/Arduino_Nano_DSO_Schaltplan.png)
DSO settings menu
![DSO settings menu](https://github.com/ArminJo/android-blue-display/blob/gh-pages/screenshots/DSOSettings.png)
DSO frequency generator menu
![Frequency generator menu](https://github.com/ArminJo/android-blue-display/blob/gh-pages/screenshots/Frequency.png)
Hacked RC car
![Hacked RC car](https://github.com/ArminJo/android-blue-display/blob/gh-pages/pictures/RCCar+Tablet.jpg)

RC car control display
![RC car control display](https://github.com/ArminJo/android-blue-display/blob/gh-pages/screenshots/RCCarControl.png)

# Credits
The USB driver library used in this project is [Kai Morichs fork of usb-serial-for-android](https://github.com/kai-morich/usb-serial-for-android)