/*
 * BlueDisplay.h
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
 */

#ifndef BLUEDISPLAY_H_
#define BLUEDISPLAY_H_

#include <stdint.h>
#include <stdbool.h>

typedef uint16_t Color_t;

#ifdef __cplusplus
#include "BDButton.h" // for BDButtonHandle_t
#include "BDSlider.h" // for BDSliderHandle_t
#endif

#define DISPLAY_DEFAULT_HEIGHT 240 // value to use if not connected
#define DISPLAY_DEFAULT_WIDTH 320

/*
 * Basic colors
 */
// RGB to 16 bit 565 schema - 5 red | 6 green | 5 blue
#define COLOR_WHITE     ((Color_t)0xFFFF)
// 01 because 0 is used as flag (e.g. in touch button for default color)
#define COLOR_BLACK     ((Color_t)0X0001)
#define COLOR_RED       ((Color_t)0xF800)
#define COLOR_GREEN     ((Color_t)0X07E0)
#define COLOR_BLUE      ((Color_t)0x001F)
#define COLOR_DARK_BLUE ((Color_t)0x0014)
#define COLOR_YELLOW    ((Color_t)0XFFE0)
#define COLOR_MAGENTA   ((Color_t)0xF81F)
#define COLOR_CYAN      ((Color_t)0x07FF)

// If used as background color for char or text, the background will not filled
#define COLOR_NO_BACKGROUND   ((Color_t)0XFFFE)

#define BLUEMASK 0x1F
#define GET_RED(rgb) ((rgb & 0xF800) >> 8)
#define GET_GREEN(rgb) ((rgb & 0x07E0) >> 3)
#define GET_BLUE(rgb) ((rgb & 0x001F) << 3)
#define RGB(r,g,b)   ((Color_t)(((r&0xF8)<<8)|((g&0xFC)<<3)|((b&0xF8)>>3))) //5 red | 6 green | 5 blue

/*
 * Android system tones
 */
#define TONE_CDMA_KEYPAD_VOLUME_KEY_LITE 89
#define TONE_PROP_BEEP 27
#define TONE_PROP_BEEP2 28
#define TONE_CDMA_ONE_MIN_BEEP 88
#define TONE_DEFAULT TONE_CDMA_KEYPAD_VOLUME_KEY_LITE
/*
 * Android Text sizes which are closest to the 8*12 font used locally
 */
#define TEXT_SIZE_11 11
#define TEXT_SIZE_13 13
#define TEXT_SIZE_14 14
#define TEXT_SIZE_16 16
#define TEXT_SIZE_18 18
#define TEXT_SIZE_11 11
// for factor 2 of 8*12 font
#define TEXT_SIZE_22 22
// for factor 3 of 8*12 font
#define TEXT_SIZE_33 33
#define TEXT_SIZE_44 44 // for factor 4 of 8*12 font
// TextSize * 0.6
#ifdef LOCAL_DISPLAY_EXISTS
// 8/16 instead of 7/13 to be compatible with 8*12 font
#define TEXT_SIZE_11_WIDTH 8
#define TEXT_SIZE_22_WIDTH 16
#else
#define TEXT_SIZE_11_WIDTH 7
#define TEXT_SIZE_13_WIDTH 8
#define TEXT_SIZE_14_WIDTH 8
#define TEXT_SIZE_16_WIDTH 10
#define TEXT_SIZE_18_WIDTH 11
#define TEXT_SIZE_22_WIDTH 13
#endif

// TextSize * 0.93
// 12 instead of 11 to be compatible with 8*12 font and have a margin
#define TEXT_SIZE_11_HEIGHT 12
#define TEXT_SIZE_22_HEIGHT 24

// TextSize * 0.93
// 9 instead of 8 to have ASCEND + DECEND = HEIGHT
#define TEXT_SIZE_11_ASCEND 9
#define TEXT_SIZE_13_ASCEND 10
#define TEXT_SIZE_14_ASCEND 11
#define TEXT_SIZE_16_ASCEND 12
#define TEXT_SIZE_18_ASCEND 14
// 18 instead of 17 to have ASCEND + DECEND = HEIGHT
#define TEXT_SIZE_22_ASCEND 18

// TextSize * 0.24
#define TEXT_SIZE_11_DECEND 3
// 6 instead of 5 to have ASCEND + DECEND = HEIGHT
#define TEXT_SIZE_22_DECEND 6

uint8_t getTextHeight(uint8_t aTextSize);
uint8_t getTextWidth(uint8_t aTextSize);
uint8_t getTextAscend(uint8_t aTextSize);
uint16_t getTextAscendMinusDescend(uint8_t aTextSize);
uint8_t getTextMiddle(uint8_t aTextSize);
uint8_t getLocalTextSize(uint8_t aTextSize);

/*
 * BUTTONS
 */
#define BUTTON_AUTO_RED_GREEN_FALSE_COLOR COLOR_RED
#define BUTTON_AUTO_RED_GREEN_TRUE_COLOR COLOR_GREEN
#define BUTTON_DEFAULT_SPACING 16
#define BUTTON_DEFAULT_SPACING_THREE_QUARTER 12
#define BUTTON_DEFAULT_SPACING_HALF 8
#define BUTTON_DEFAULT_SPACING_QUARTER 4
/*
 * Layout for 320 x 240 screen size
 */
#define LAYOUT_320_WIDTH 320
#define LAYOUT_240_HEIGHT 240
#define LAYOUT_256_HEIGHT 256
/*
 * WIDTHS
 */
// for 2 buttons horizontal - 19 characters
#define BUTTON_WIDTH_2 152
#define BUTTON_WIDTH_2_POS_2 (BUTTON_WIDTH_2 + BUTTON_DEFAULT_SPACING)
//
// for 3 buttons horizontal - 12 characters
#define BUTTON_WIDTH_3 96
#define BUTTON_WIDTH_3_POS_2 (BUTTON_WIDTH_3 + BUTTON_DEFAULT_SPACING)
#define BUTTON_WIDTH_3_POS_3 (LAYOUT_320_WIDTH - BUTTON_WIDTH_3)
//
// for 4 buttons horizontal - 8 characters
#define BUTTON_WIDTH_4 68
#define BUTTON_WIDTH_4_POS_2 (BUTTON_WIDTH_4 + BUTTON_DEFAULT_SPACING)
#define BUTTON_WIDTH_4_POS_3 (2*(BUTTON_WIDTH_4 + BUTTON_DEFAULT_SPACING))
#define BUTTON_WIDTH_4_POS_4 (LAYOUT_320_WIDTH - BUTTON_WIDTH_4)
//
// for 5 buttons horizontal 51,2  - 6 characters
#define BUTTON_WIDTH_5 51
#define BUTTON_WIDTH_5_POS_2 (BUTTON_WIDTH_5 + BUTTON_DEFAULT_SPACING)
#define BUTTON_WIDTH_5_POS_3 (2*(BUTTON_WIDTH_5 + BUTTON_DEFAULT_SPACING))
#define BUTTON_WIDTH_5_POS_4 (3*(BUTTON_WIDTH_5 + BUTTON_DEFAULT_SPACING))
#define BUTTON_WIDTH_5_POS_5 (LAYOUT_320_WIDTH - BUTTON_WIDTH_5)
//
//  for 2 buttons horizontal plus one small with BUTTON_WIDTH_5 (118,5)- 15 characters
#define BUTTON_WIDTH_2_5 120
#define BUTTON_WIDTH_2_5_POS_2   (BUTTON_WIDTH_2_5 + BUTTON_DEFAULT_SPACING -1)
#define BUTTON_WIDTH_2_5_POS_2_5 (LAYOUT_320_WIDTH - BUTTON_WIDTH_5)
//
// for 6 buttons horizontal
#define BUTTON_WIDTH_6 40
#define BUTTON_WIDTH_6_POS_2 (BUTTON_WIDTH_6 + BUTTON_DEFAULT_SPACING)
#define BUTTON_WIDTH_6_POS_3 (2*(BUTTON_WIDTH_6 + BUTTON_DEFAULT_SPACING))
#define BUTTON_WIDTH_6_POS_4 (3*(BUTTON_WIDTH_6 + BUTTON_DEFAULT_SPACING))
#define BUTTON_WIDTH_6_POS_5 (4*(BUTTON_WIDTH_6 + BUTTON_DEFAULT_SPACING))
#define BUTTON_WIDTH_6_POS_6 (LAYOUT_320_WIDTH - BUTTON_WIDTH_6)
//
// for 8 buttons horizontal
#define BUTTON_WIDTH_8 33
// for 10 buttons horizontal
#define BUTTON_WIDTH_10 28
/*
 * HEIGHTS
 */
// for 4 buttons vertical
#define BUTTON_HEIGHT_4 48
#define BUTTON_HEIGHT_4_LINE_2 (BUTTON_HEIGHT_4 + BUTTON_DEFAULT_SPACING)
#define BUTTON_HEIGHT_4_LINE_3 (2*(BUTTON_HEIGHT_4 + BUTTON_DEFAULT_SPACING))
#define BUTTON_HEIGHT_4_LINE_4 (LAYOUT_240_HEIGHT - BUTTON_HEIGHT_4)
//
// for 4 buttons vertical and DISPLAY_HEIGHT 256
#define BUTTON_HEIGHT_4_256 52
#define BUTTON_HEIGHT_4_256_LINE_2 (BUTTON_HEIGHT_4_256 + BUTTON_DEFAULT_SPACING)
#define BUTTON_HEIGHT_4_256_LINE_3 (2*(BUTTON_HEIGHT_4_256 + BUTTON_DEFAULT_SPACING))
#define BUTTON_HEIGHT_4_256_LINE_4 (LAYOUT_256_HEIGHT - BUTTON_HEIGHT_4_256)
//
// for 5 buttons vertical
#define BUTTON_HEIGHT_5 38
#define BUTTON_HEIGHT_5_LINE_2 (BUTTON_HEIGHT_5 + BUTTON_DEFAULT_SPACING_THREE_QUARTER)
#define BUTTON_HEIGHT_5_LINE_3 (2*(BUTTON_HEIGHT_5 + BUTTON_DEFAULT_SPACING_THREE_QUARTER))
#define BUTTON_HEIGHT_5_LINE_4 (3*(BUTTON_HEIGHT_5 + BUTTON_DEFAULT_SPACING_THREE_QUARTER))
#define BUTTON_HEIGHT_5_LINE_5 (LAYOUT_240_HEIGHT - BUTTON_HEIGHT_5)
//
// for 5 buttons vertical and DISPLAY_HEIGHT 256
#define BUTTON_HEIGHT_5_256 39
#define BUTTON_HEIGHT_5_256_LINE_2 (BUTTON_HEIGHT_5 + BUTTON_DEFAULT_SPACING)
#define BUTTON_HEIGHT_5_256_LINE_3 (2*(BUTTON_HEIGHT_5 + BUTTON_DEFAULT_SPACING))
#define BUTTON_HEIGHT_5_256_LINE_4 (3*(BUTTON_HEIGHT_5 + BUTTON_DEFAULT_SPACING))
#define BUTTON_HEIGHT_5_256_LINE_5 (LAYOUT_256_HEIGHT - BUTTON_HEIGHT_5)
//
// for 6 buttons vertical 26,66..
#define BUTTON_HEIGHT_6 26

/*
 * Function tags for Bluetooth serial communication
 */

// Sub functions for SET_FLAGS_AND_SIZE
static const int BD_FLAG_FIRST_RESET_ALL = 0x01;
static const int BD_FLAG_TOUCH_BASIC_DISABLE = 0x02;
static const int BD_FLAG_TOUCH_MOVE_DISABLE = 0x04;
static const int BD_FLAG_LONG_TOUCH_ENABLE = 0x08;
static const int BD_FLAG_USE_MAX_SIZE = 0x10;

// see android.hardware.Sensor
static const int TYPE_ACCELEROMETER = 1;
static const int TYPE_GYROSCOPE = 4;

// rate of sensor callbacks - see android.hardware.SensorManager
static const int SENSOR_DELAY_NORMAL = 3; // 200 ms
static const int SENSOR_DELAY_UI = 2; // 60 ms
static const int SENSOR_DELAY_GAME = 1; // 20 ms
static const int SENSOR_DELAY_FASTEST = 0;

// Flags for BUTTON_GLOBAL_SETTINGS
static const int USE_UP_EVENTS_FOR_BUTTONS = 0x01;
static const int BUTTONS_SET_BEEP_TONE = 0x02;

// Values for local button flag
static const int BUTTON_FLAG_NO_BEEP_ON_TOUCH = 0x00;
static const int BUTTON_FLAG_DO_BEEP_ON_TOUCH = 0X01;
static const int BUTTON_FLAG_TYPE_AUTO_RED_GREEN = 0x02;
static const int BUTTON_FLAG_TYPE_AUTOREPEAT = 0x04;

// Flags for slider options
static const int SLIDER_VERTICAL_SHOW_NOTHING = 0x00;
static const int SLIDER_SHOW_BORDER = 0x01;
static const int SLIDER_SHOW_VALUE = 0x02;
static const int SLIDER_IS_HORIZONTAL = 0x04;
static const int SLIDER_VALUE_BY_CALLBACK = 0x10; // if set bar and print value will be set by callback handler and not by slider itself

// Flags for slider caption position
static const int SLIDER_VALUE_CAPTION_ALIGN_LEFT = 0x00;
static const int SLIDER_VALUE_CAPTION_ALIGN_RIGHT = 0x01;
static const int SLIDER_VALUE_CAPTION_ALIGN_MIDDLE = 0x02;
static const int SLIDER_VALUE_CAPTION_BELOW = 0x00;
static const int SLIDER_VALUE_CAPTION_ABOVE = 0x04;

// no valid button number
#define NO_BUTTON 0xFF
#define NO_SLIDER 0xFF

struct XYSize {
    uint16_t XWidth;
    uint16_t YHeight;
};

struct ThickLine {
    int16_t StartX;
    int16_t StartY;
    int16_t EndX;
    int16_t EndY;
    int16_t Thickness;
    Color_t Color;
    Color_t BackgroundColor;
};

#ifdef __cplusplus
class BlueDisplay {
public:
    BlueDisplay();
    void setFlagsAndSize(uint16_t aFlags, uint16_t aWidth, uint16_t aHeight);
    void setCodePage(uint16_t aCodePageNumber);
    void setCharacterMapping(uint8_t aChar, uint16_t aUnicodeChar);

    void playTone(uint8_t aToneIndex);
    void playTone(void);
    void playFeedbackTone(bool isError);
    void setLongTouchDownTimeout(uint16_t aLongTouchDownTimeoutMillis);

    void clearDisplay(Color_t aColor);
    void drawDisplayDirect(void);
    void setScreenOrientationLock(bool doLock);

    void drawPixel(uint16_t aXPos, uint16_t aYPos, Color_t aColor);
    void drawCircle(uint16_t aXCenter, uint16_t aYCenter, uint16_t aRadius, Color_t aColor, uint16_t aStrokeWidth);
    void fillCircle(uint16_t aXCenter, uint16_t aYCenter, uint16_t aRadius, Color_t aColor);
    void drawRect(uint16_t aXStart, uint16_t aYStart, uint16_t aXEnd, uint16_t aYEnd, Color_t aColor,
            uint16_t aStrokeWidth);
    void drawRectRel(uint16_t aXStart, uint16_t aYStart, uint16_t aWidth, uint16_t aHeight, Color_t aColor,
            uint16_t aStrokeWidth);
    void fillRect(uint16_t aXStart, uint16_t aYStart, uint16_t aXEnd, uint16_t aYEnd, Color_t aColor);
    void fillRectRel(uint16_t aXStart, uint16_t aYStart, uint16_t aWidth, uint16_t aHeight, Color_t aColor);
    uint16_t drawChar(uint16_t aPosX, uint16_t aPosY, char aChar, uint8_t aCharSize, Color_t aFGColor,
            Color_t aBGColor);
    uint16_t drawText(uint16_t aXStart, uint16_t aYStart, const char *aStringPtr, uint8_t aFontSize, Color_t aFGColor,
            Color_t aBGColor);
    void drawMLText(uint16_t aPosX, uint16_t aPosY, const char *aStringPtr, uint8_t aTextSize, Color_t aFGColor,
            Color_t aBGColor);

    void setPrintfSizeAndColorAndFlag(int aPrintSize, Color_t aPrintColor, Color_t aPrintBackgroundColor,
    bool aClearOnNewScreen);
    void setPrintfPosition(int aPosX, int aPosY);
    void setPrintfPositionColumnLine(int aColumnNumber, int aLineNumber);
    void writeString(const char *aStringPtr, uint8_t aStringLength);

    void debugMessage(const char *aStringPtr);

    void drawLine(uint16_t aXStart, uint16_t aYStart, uint16_t aXEnd, uint16_t aYEnd, Color_t aColor);
    void drawLineRel(uint16_t aXStart, uint16_t aYStart, uint16_t aXDelta, uint16_t aYDelta, Color_t aColor);
    void drawLineFastOneX(uint16_t x0, uint16_t y0, uint16_t y1, Color_t aColor);
    void drawLineWithThickness(uint16_t aXStart, uint16_t aYStart, uint16_t aXEnd, uint16_t aYEnd, int16_t aThickness,
            Color_t aColor);

    void drawChartByteBuffer(uint16_t aXOffset, uint16_t aYOffset, Color_t aColor, Color_t aClearBeforeColor,
            uint8_t *aByteBuffer, uint16_t aByteBufferLength);
    void drawChartByteBuffer(uint16_t aXOffset, uint16_t aYOffset, Color_t aColor, Color_t aClearBeforeColor,
            uint8_t aChartIndex, bool aDoDrawDirect, uint8_t *aByteBuffer, uint16_t aByteBufferLength);

    void setMaxDisplaySize(struct XYSize * aMaxDisplaySizePtr);
    void setActualDisplaySize(struct XYSize * aActualDisplaySizePtr);
    // returns requested size
    uint16_t getDisplayWidth(void);
    uint16_t getDisplayHeight(void);

    void refreshVector(struct ThickLine * aLine, int16_t aNewRelEndX, int16_t aNewRelEndY);

    void getNumber(void (*aNumberHandler)(float));
    void getNumberWithShortPrompt(void (*aNumberHandler)(float), const char *aShortPromptString);
    void getNumberWithShortPrompt(void (*aNumberHandler)(float), const char *aShortPromptString, float aInitialValue);
    void getText(void (*aTextHandler)(const char *));

    void setSensor(uint8_t aSensorType, bool aDoActivate, uint8_t aSensorRate);

    void generateColorSpectrum(void);

#ifdef LOCAL_DISPLAY_EXISTS
    void testDisplay(void);
#endif

    /*
     * Button stuff
     */
    BDButtonHandle_t createButton(uint16_t aPositionX, uint16_t aPositionY, uint16_t aWidthX, uint16_t aHeightY,
            Color_t aButtonColor, const char * aCaption, uint8_t aCaptionSize, uint8_t aFlags, int16_t aValue,
            void (*aOnTouchHandler)(BDButton *, int16_t));
    void drawButton(BDButtonHandle_t aButtonNumber);
    void removeButton(BDButtonHandle_t aButtonNumber, Color_t aBackgroundColor);
    void drawButtonCaption(BDButtonHandle_t aButtonNumber);
    void setButtonCaption(BDButtonHandle_t aButtonNumber, const char * aCaption, bool doDrawButton);
    void setButtonValue(BDButtonHandle_t aButtonNumber, int16_t aValue);
    void setButtonValueAndDraw(BDButtonHandle_t aButtonNumber, int16_t aValue);
    void setButtonColor(BDButtonHandle_t aButtonNumber, Color_t aButtonColor);
    void setButtonColorAndDraw(BDButtonHandle_t aButtonNumber, Color_t aButtonColor);
    void setButtonPosition(BDButtonHandle_t aButtonNumber, int16_t aPositionX, int16_t aPositionY);
    void setButtonAutorepeatTiming(BDButtonHandle_t aButtonNumber, uint16_t aMillisFirstDelay,
            uint16_t aMillisFirstRate, uint16_t aFirstCount, uint16_t aMillisSecondRate);

    void activateButton(BDButtonHandle_t aButtonNumber);
    void deactivateButton(BDButtonHandle_t aButtonNumber);
    void activateAllButtons(void);
    void deactivateAllButtons(void);
    void setButtonsGlobalFlags(uint16_t aFlags);
    void setButtonsTouchTone(uint8_t aToneIndex, uint8_t aToneVolume);
    /*
     * Slider stuff
     */
    BDSliderHandle_t createSlider(uint16_t aPositionX, uint16_t aPositionY, uint8_t aBarWidth, uint16_t aBarLength,
            uint16_t aThresholdValue, int16_t aInitalValue, Color_t aSliderColor, Color_t aBarColor, uint8_t aOptions,
            void (*aOnChangeHandler)(BDSliderHandle_t *, int16_t));
    void drawSlider(BDSliderHandle_t aSliderNumber);
    void drawSliderBorder(BDSliderHandle_t aSliderNumber);
    void setSliderActualValueAndDrawBar(BDSliderHandle_t aSliderNumber, int16_t aActualValue);
    void setSliderColorBarThreshold(BDSliderHandle_t aSliderNumber, uint16_t aBarThresholdColor);
    void setSliderColorBarBackground(BDSliderHandle_t aSliderNumber, uint16_t aBarBackgroundColor);

    void setSliderCaptionProperties(BDSliderHandle_t aSliderNumber, uint8_t aCaptionSize, uint8_t aCaptionPosition,
            uint8_t aCaptionMargin, Color_t aCaptionColor, Color_t aCaptionBackgroundColor);
    void setSliderCaption(BDSliderHandle_t aSliderNumber, const char * aCaption);

    void activateSlider(BDSliderHandle_t aSliderNumber);
    void deactivateSlider(BDSliderHandle_t aSliderNumber);
    void activateAllSliders(void);
    void deactivateAllSliders(void);

private:
    struct XYSize mReferenceDisplaySize; // contains requested display size
    struct XYSize mActualDisplaySize;
    struct XYSize mMaxDisplaySize;
    uint16_t mActualDisplayHeight;
    uint16_t mActualDisplayWidth;

    /* for tests */
    void drawGreyscale(uint16_t aXPos, uint16_t tYPos, uint16_t aHeight);
    void drawStar(int aXPos, int aYPos, int tOffsetCenter, int tLength, int tOffsetDiagonal, int tLengthDiagonal,
            Color_t aColor);

};
// The instance provided by the class itself
extern BlueDisplay BlueDisplay1;
#endif

extern bool isLocalDisplayAvailable;

#ifdef __cplusplus
#ifdef LOCAL_DISPLAY_EXISTS
#include <MI0283QT2.h>
#include "EventHandler.h"

/*
 * MI0283QT2 TFTDisplay - must provided by main program
 * external declaration saves ROM (210 Bytes) and RAM ( 20 Bytes)
 * and avoids missing initialization :-)
 */
extern MI0283QT2 LocalDisplay;
// to be provided by local display library
extern const int LOCAL_DISPLAY_HEIGHT;
extern const int LOCAL_DISPLAY_WIDTH;
#endif
// to be provided by another source (main.cpp)
extern const int DISPLAY_HEIGHT;
extern const int DISPLAY_WIDTH;

extern "C" {
#endif
// for use in syscalls.c
uint16_t drawTextC(uint16_t aXStart, uint16_t aYStart, const char *aStringPtr, uint8_t aFontSize, Color_t aFGColor,
        Color_t aBGColor);
void writeStringC(const char *aStringPtr, uint8_t aStringLength);
#ifdef __cplusplus
}
#endif

#endif /* BLUEDISPLAY_H_ */
