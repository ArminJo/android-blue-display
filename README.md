# BlueDisplay
Convert your smartphone into an Android remote touch display for your Arduino or ARM projects.

##SUMMARY
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
- Hex und ASCII output of received Bluetooth data at log level verbose.
- Debug messages as toasts.
- C++ Libraries for [Arduino](https://github.com/ArminJo/android-blue-display/tree/master/arduino/libraries/BlueDisplay/BlueDisplay.zip).
 and [ARM (STM)](https://github.com/ArminJo/android-blue-display/tree/master/STM32/lib).

## Version Info:
3.0 Android sensor accessible by Arduino.

3.1 Local display of received and sent commands for debug purposes.

3.2 Improved tone und fullscreen handling. Internal refactoring. Bugfixes and minor improvements.

3.3 Fixed silent tone bug for Lollipop and other bugs.Multiline text /r /n handling.
Android time accessible on Arduino. Debug messages as toasts. Changed create button.
Slider values scalable. GUI multi touch.Hex and ASCII output of received Bluetooth data at log level verbose.

3.4 Timeout for data messages. Get number initial value fixed.
Bug autorepeat button in conjunction with UseUpEventForButtons fixed.

3.5 Slider scaling changed and unit value added.

3.6 connect, reconnect and autoconnect improved/added. Improved debug() command. Simplified Red/Green button handling.


### Example for Hex + ASCII output:
```
V BTSerial RawData=00 4C 13 A5 01 08 00 53 65 74 74 69 6E 67 73 A5 |  L      Settings
V BTSerial RawData=70 12 00 04 00 00 00 00 00 60 00 34 00 00 F8 0B | p             `
V BTSerial RawData=03 00 00 30 21 A5 01 07 00 48 69 73 74 6F 72 79 |   0!     History
```

## Hint
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

