<div align = center>

# [BlueDisplay App](https://play.google.com/store/apps/details?id=de.joachimsmeyer.android.bluedisplay)
Convert your smartphone into an Android remote touch display for your Arduino or ARM projects.

[![Badge License: GPLv3](https://img.shields.io/badge/License-GPLv3-brightgreen.svg)](https://www.gnu.org/licenses/gpl-3.0)
 &nbsp; &nbsp;
[![Badge Version](https://img.shields.io/github/v/release/ArminJo/android-blue-display?include_prereleases&color=yellow&logo=DocuSign&logoColor=white)](https://github.com/ArminJo/android-blue-display/releases/latest)
 &nbsp; &nbsp;
[![Badge Commits since latest](https://img.shields.io/github/commits-since/ArminJo/android-blue-display/latest?color=yellow)](https://github.com/ArminJo/android-blue-display/commits/master)
 &nbsp; &nbsp;
![Badge Hit Counter](https://visitor-badge.laobi.icu/badge?page_id=ArminJo_android-blue-display)
<br/>
<br/>
[![Stand With Ukraine](https://raw.githubusercontent.com/vshymanskyy/StandWithUkraine/main/badges/StandWithUkraine.svg)](https://stand-with-ukraine.pp.ua)


[![Button Changelog](https://img.shields.io/badge/Changelog-blue?logoColor=white&logo=AzureArtifacts)](https://github.com/ArminJo/android-blue-display?tab=readme-ov-file#revision-history)

#### If you find this library useful, please give it a star.

&#x1F30E; [Google Translate](https://translate.google.com/translate?sl=en&u=https://github.com/ArminJo/android-blue-display)

</div>


# SUMMARY
Let the Arduino sketch create a GUI with Graphics, Buttons and Sliders on your smartphone by simply connecting a HC-05 to the rx/tx pins of your Arduino.
Directly connecting the Arduino with an USB cable and an USB-OTG adapter to your smartphone is also supported.<br/>
It receives draw requests from Arduino over Bluetooth and renders it.
GUI callback, touch and sensor events are sent back to Arduino.
**No Android programming needed!**

<br/>

# Features
- **Graphic + text** output as well as **printf implementation**.
- **Touch button + slider** objects with tone feedback and 16 bit values.
- Draw **chart** from byte or short values. Enables clearing of last drawn chart.
- **Voice output** with Android TextToSpeech for Android > 5.0 (Lollipop).
- **Touch and sensor events** are sent to Arduino.
- Automatic and manually **scaling of display region**.
- Sliders can have arbitrary start and end values.
- Buttons can be **Red/Green toggle** button with different text for both states.
- Buttons can be **autorepeat buttons** with 2 different repeat rates.
- Easy mapping of any UTF-8 characters like Ohm, Celsius etc..
- Up to **115200 Baud** using **HC-05** modules or** USB OTG**.
- **USB OTG connection** can be used instead of Bluetooth.
- Local display of received and sent commands for debugging purposes.
- Hex and ASCII output of received Bluetooth data at **log level** verbose.
- **Debug messages as toasts**
- Swipe from the left border opens the **options menu**.
- **C++ libraries** for [Arduino and ARM (STM)](https://github.com/ArminJo/Arduino-BlueDisplay).

<br/>

# The Arduino library with lot of examples can be found [here](https://github.com/ArminJo/Arduino-BlueDisplay).
You can load the library with *Tools -> Manage Libraries...* or *Ctrl+Shift+I*. Use "BlueDisplay" as filter string.
The library includes examples for easy initializing a HC-05 and for a simple DSO with 0.3 mega samples/sec.

<br/>

# Usage
Before using the examples, take care that the BT-module (e.g. the the HC-05 module) is connected to your Android device and is visible in the Bluetooth Settings.
For full screen applications, the menu is called by swiping from the left edge of the screen. Otherwise, you can call it by touching the area not occupied by the client application (black display area).

## Menu option "show touch position"
The current touch position is shown at the upper left corner in the following format:<br/>
<IndexOfTouchPointer>|<ActionCode>  <XPositionOnScreen>/<YPositionOnScreen> -> <XPositionForApplication>/<YPositionForApplication>.<br/>
<IndexOfTouchPointer> is 0 for touch with one pointer and 1 for second touch of multitouch etc.<br/>
<ActionCode> is 0 for DOWN, 1 for UP and 2 for MOVE.

<br/>

# Baudrate
All examples initially use the baudrate of 9600. Especially the SimpleTouchScreenDSO example will run smoother with a baudrate of 115200.
For this, change the example baudrate by deactivating the line `#define HC_05_BAUD_RATE BAUD_9600` and activating `#define HC_05_BAUD_RATE BAUD_115200`.
AND change the BT-Module baudrate e.g. by using the BTModuleProgrammer.ino example.

<br/>

# Sensor axis for an Arduino application
Android axis are [defined for **natural screen orientation**](https://source.android.com/devices/sensors/sensor-types), which is portrait for my devices:
- When the device lies flat on a table and its left side is down and right side is up or pushed on its left side toward the right, the X acceleration value is positive.
- When the device lies flat on a table and its bottom side is down and top side is up or pushed on its bottom side toward the top, the Y acceleration value is positive.
- When the device lies flat on a table, the acceleration value along Z is +9.81 (m/s^2).

**The BlueDisplay application converts the axis, so that this definition holds for each screen orientation.**

<br/>

# Example for Hex + ASCII output (on log level verbose):
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
<br/>

# Pictures and screenshots

BlueDisplay example breadboard picture
![Breadboard picture](https://github.com/ArminJo/android-blue-display/blob/gh-pages/pictures/Blink1.jpg)
Fritzing schematic for BlueDisplay example
![Fritzing schematics](https://github.com/ArminJo/android-blue-display/blob/gh-pages/schematics/BlueDisplayBlink_Steckplatine.png)
DSO with passive attenuator on breadboard
![DSO with passive attenuator on breadboard](https://github.com/ArminJo/android-blue-display/blob/gh-pages/pictures/ArduinoDSO.jpg)
At work
![DSO at work](https://github.com/ArminJo/android-blue-display/blob/gh-pages/pictures/DSO+Tablet.jpg)
Fritzing
![DSO Fritzing](https://github.com/ArminJo/Arduino-Simple-DSO/blob/master/extras/Arduino_Nano_DSO_BT_full_Steckplatine.png)
Schematic
![DSO Schematic](https://github.com/ArminJo/Arduino-Simple-DSO/blob/master/extras/Arduino_Nano_DSO_BT_full_Schaltplan.png)
DSO settings menu
![DSO settings menu](https://github.com/ArminJo/android-blue-display/blob/gh-pages/screenshots/DSOSettings.png)
DSO frequency generator menu
![Frequency generator menu](https://github.com/ArminJo/android-blue-display/blob/gh-pages/screenshots/Frequency.png)
Hacked RC car
![Hacked RC car](https://github.com/ArminJo/android-blue-display/blob/gh-pages/pictures/RCCar+Tablet.jpg)

RC car control display
![RC car control display](https://github.com/ArminJo/android-blue-display/blob/gh-pages/screenshots/RCCarControl.png)

# Revision History
### Version 5.0.0 / 22
- Voice output with Android TextToSpeech for Android > 5.0 (Lollipop).
- Text Y and X position is upper left corner of character.
- Screen orientation flags now also possible in setFlagsAndSize().

### Version 4.4.1 / 21
- Targeted Android 14 / 34
- Improved Chart and Slider support, minor bug fixes and improvements.

### Version 4.3.3 / 20
- Targeted Android 13 / 33

### Version 4.3.2 / 19
- Targeted Android 12 / 32
- Support for new function disableAutorepeatUntilEndOfTouch().
- Added Flag `FLAG_SLIDER_VALUE_CAPTION_TAKE_DEFAULT_MARGIN`. Margin is set to RequestedCanvasHeight / 60.
- Inproved color handling for Red/Green toggle button.

### Version 4.3.1 / 18
- Fixed Permission bug for Andoid 12 / 32

### Version 4.3 / 17 - First version build with Android Studio
- New command `FUNCTION_CLEAR_DISPLAY_OPTIONAL` to enable resynchronization of slow displays.
- Bluetooth random delay detection.
- Fixed bug for micro-swipe suppressing.
- Added Slider command `SUBFUNCTION_SLIDER_SET_DEFAULT_COLOR_THRESHOLD`.
- Opening options menu by swipe now not restricted on full screen and connected.
- Strings printed with Serial.print() are not interpreted, but stored in the log for debug purposes.
- Fixed bug in FUNCTION_BUTTON_REMOVE.
- Fixed bug in SUBFUNCTION_SLIDER_SET_POSITION.

### Version 4.2 / 16
- Swipe from the left border in application full screen mode opens the options menu.
- Removed unnecessary message on no data received under certain circumstances.
- Added parameter values `*LOCK_SENSOR_LANDSCAPE` and `*LOCK_SENSOR_PORTRAIT` for function `setScreenOrientationLock()`.
- Slider caption handling improved.
- Added short `drawText` functions.

### Version 4.1
- Improved startup. New message if no data received after connect and part of screen is inactive/black, to access the options menu.

### Version 4.0
- Connection with USB OTG cable now also possible. In this case no Bluetooth adapter is needed.
- Handling of no input for getNumber.
- Slider `setScaleFactor()` does not scale the current value, which is delivered as initial value at `init()`.
- Improved tone volume setting - can be adjusted at the smartphone too. `trim()` for all button caption strings.

### Version 3.6
- connect, reconnect and autoconnect improved/added. Improved `debug()` command. Simplified Red/Green button handling.

### Version 3.5
- Slider scaling changed and unit value added.

### Version 3.4
- Timeout for data messages. Get number initial value fixed.
- Bug autorepeat button in conjunction with UseUpEventForButtons fixed.

### Version 3.3
- Fixed silent tone bug for Lollipop and other bugs.Multiline text /r /n handling.
- Android time accessible on Arduino. Debug messages as toasts. Changed create button.
- Slider values scalable. GUI multi touch. Hex and ASCII output of received Bluetooth data at log level verbose.

### Version 3.2
- Improved tone and fullscreen handling. Internal refactoring. Bugfixes and minor improvements.

### Version 3.1
- Local display of received and sent commands for debug purposes.

### Version 3.0
- Android sensor accessible by Arduino.

# Credits
The USB driver library used in this project is [Kai Morichs fork of usb-serial-for-android](https://github.com/kai-morich/usb-serial-for-android)