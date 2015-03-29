/*
 * BlueDisplay.h
 *
 *  Created on: 12.09.2014
 * @author Armin Joachimsmeyer
 *      Email:   armin.joachimsmeyer@gmail.com
 *      License: GPL v3 (http://www.gnu.org/licenses/gpl.html)
 * @version 1.0.0
 */

#ifndef BLUEDISPLAY_H_
#define BLUEDISPLAY_H_

// Uncomment this if using a local MI0283QT2
//#define LOCAL_DISPLAY_EXISTS

#include <avr/pgmspace.h>

#include <stdint.h>

#define DISPLAY_DEFAULT_HEIGHT 240 // value to use if not connected
#define DISPLAY_DEFAULT_WIDTH 320
#define STRING_BUFFER_STACK_SIZE 20 // Buffer size allocated on stack for ...PGM() functions.

/*
 * Basic colors
 */
typedef uint16_t Color_TypeDef;
// RGB to 16 bit 565 schema - 5 red | 6 green | 5 blue
#define COLOR_WHITE     ((Color_TypeDef)0xFFFF)
#define COLOR_BLACK     ((Color_TypeDef)0X0001) // 16 because 0 is used as flag (e.g. in touch button for default color)
#define COLOR_RED       ((Color_TypeDef)0xF800)
#define COLOR_GREEN     ((Color_TypeDef)0X07E0)
#define COLOR_BLUE      ((Color_TypeDef)0x001F)
#define COLOR_DARK_BLUE ((Color_TypeDef)0x0014)
#define COLOR_YELLOW    ((Color_TypeDef)0XFFE0)
#define COLOR_MAGENTA   ((Color_TypeDef)0xF81F)
#define COLOR_CYAN      ((Color_TypeDef)0x07FF)

// If used as background color for char or text, the background will not filled
#define COLOR_NO_BACKGROUND   ((Color_TypeDef)0XFFFE)

#define BLUEMASK 0x1F
#define GET_RED(rgb) ((rgb & 0xF800) >> 8)
#define GET_GREEN(rgb) ((rgb & 0x07E0) >> 3)
#define GET_BLUE(rgb) ((rgb & 0x001F) << 3)
#define RGB(r,g,b)   ((Color_TypeDef)(((r&0xF8)<<8)|((g&0xFC)<<3)|((b&0xF8)>>3))) //5 red | 6 green | 5 blue

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
#define TEXT_SIZE_22 22 // for factor 2 of 8*12 font
#define TEXT_SIZE_33 33 // for factor 3 of 8*12 font
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

uint8_t getTextWidth(uint8_t aTextSize);
uint8_t getTextAscend(uint8_t aTextSize);
uint16_t getTextAscendMinusDescend(uint8_t aTextSize);
uint8_t getTextMiddle(uint8_t aTextSize);
uint8_t getLocalTextSize(uint8_t aTextSize);

/*
 * Flags for BlueDisplay functions
 */
// Sub functions for SET_FLAGS_AND_SIZE
static const int BD_FLAG_FIRST_RESET_ALL = 0x01;
static const int BD_FLAG_TOUCH_BASIC_DISABLE = 0x02;
static const int BD_FLAG_TOUCH_MOVE_DISABLE = 0x04;
static const int BD_FLAG_LONG_TOUCH_ENABLE = 0x08;
static const int BD_FLAG_USE_MAX_SIZE = 0x10;

// Sensors
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
static const int BUTTON_FLAG_DO_BEEP_ON_TOUCH = 0x01;
static const int BUTTON_FLAG_TYPE_AUTO_RED_GREEN = 0x02;

// Flags for slider options
static const int TOUCHSLIDER_VERTICAL_SHOW_NOTHING = 0x00;
static const int TOUCHSLIDER_SHOW_BORDER = 0x01;
static const int TOUCHSLIDER_VALUE_BY_CALLBACK = 0x02; // if set value will be set by callback handler
static const int TOUCHSLIDER_IS_HORIZONTAL = 0x04;

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
    Color_TypeDef Color;
    Color_TypeDef BackgroundColor;
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

    void clearDisplay(Color_TypeDef aColor);
    void drawDisplayDirect(void);
    void setScreenOrientationLock(bool doLock);

    void drawPixel(uint16_t aXPos, uint16_t aYPos, Color_TypeDef aColor);
    void drawCircle(uint16_t aXCenter, uint16_t aYCenter, uint16_t aRadius, Color_TypeDef aColor, uint16_t aStrokeWidth);
    void fillCircle(uint16_t aXCenter, uint16_t aYCenter, uint16_t aRadius, Color_TypeDef aColor);
    void drawRect(uint16_t aXStart, uint16_t aYStart, uint16_t aXEnd, uint16_t aYEnd, Color_TypeDef aColor, uint16_t aStrokeWidth);
    void drawRectRel(uint16_t aXStart, uint16_t aYStart, uint16_t aWidth, uint16_t aHeight, Color_TypeDef aColor,
            uint16_t aStrokeWidth);
    void fillRect(uint16_t aXStart, uint16_t aYStart, uint16_t aXEnd, uint16_t aYEnd, Color_TypeDef aColor);
    void fillRectRel(uint16_t aXStart, uint16_t aYStart, uint16_t aWidth, uint16_t aHeight, Color_TypeDef aColor);
    uint16_t drawChar(uint16_t aPosX, uint16_t aPosY, char aChar, uint8_t aCharSize, Color_TypeDef aFGColor,
            Color_TypeDef aBGColor);
    uint16_t drawText(uint16_t aXStart, uint16_t aYStart, const char *aStringPtr, uint8_t aFontSize, Color_TypeDef aFGColor,
            Color_TypeDef aBGColor);
    void drawMLText(uint16_t aPosX, uint16_t aPosY, const char *aStringPtr, uint8_t aTextSize, Color_TypeDef aFGColor,
            Color_TypeDef aBGColor);

    void debugMessage(const char *aStringPtr);

    void drawLine(uint16_t aXStart, uint16_t aYStart, uint16_t aXEnd, uint16_t aYEnd, Color_TypeDef aColor);
    void drawLineRel(uint16_t aXStart, uint16_t aYStart, uint16_t aXDelta, uint16_t aYDelta, Color_TypeDef aColor);
    void drawLineFastOneX(uint16_t x0, uint16_t y0, uint16_t y1, Color_TypeDef aColor);
    void drawLineWithThickness(uint16_t aXStart, uint16_t aYStart, uint16_t aXEnd, uint16_t aYEnd, int16_t aThickness,
            Color_TypeDef aColor);

    void drawChartByteBuffer(uint16_t aXOffset, uint16_t aYOffset, Color_TypeDef aColor, Color_TypeDef aClearBeforeColor,
            uint8_t *aByteBuffer, uint16_t aByteBufferLength);
    void drawChartByteBuffer(uint16_t aXOffset, uint16_t aYOffset, Color_TypeDef aColor, Color_TypeDef aClearBeforeColor,
            uint8_t aChartIndex, bool aDoDrawDirect, uint8_t *aByteBuffer, uint16_t aByteBufferLength);

    void setMaxDisplaySize(struct XYSize * const aMaxDisplaySizePtr);
    void setActualDisplaySize(struct XYSize * const aActualDisplaySizePtr);
    // returns requested size
    uint16_t getDisplayWidth(void);
    uint16_t getDisplayHeight(void);

    void refreshVector(struct ThickLine * aLine, int16_t aNewRelEndX, int16_t aNewRelEndY);

    void getNumber(void (*aNumberHandler)(const float));
    void getNumberWithShortPrompt(void (*aNumberHandler)(const float), const char *aShortPromptString);
    void getNumberWithShortPrompt(void (*aNumberHandler)(const float), const char *aShortPromptString, float aInitialValue);
    void getText(void (*aTextHandler)(const char *));

    void setSensor(uint8_t aSensorType, bool aDoActivate, uint8_t aSensorRate);

    uint16_t drawTextPGM(uint16_t aXStart, uint16_t aYStart, PGM_P aPGMString, uint8_t aFontSize, Color_TypeDef aFGColor,
    Color_TypeDef aBGColor);
    void getNumberWithShortPromptPGM(void (*aNumberHandler)(const float),const char *tShortPromptLengtht);

    /*
     * Button stuff
     */
    void resetAllButtons(void);
    uint8_t createButton(const uint16_t aPositionX, const uint16_t aPositionY, const uint16_t aWidthX, const uint16_t aHeightY,
    const Color_TypeDef aButtonColor, const char * aCaption, const uint8_t aCaptionSize, const uint8_t aFlags,
    const int16_t aValue, void (*aOnTouchHandler)(uint8_t, int16_t));
    void drawButton(uint8_t aButtonNumber);
    void drawButtonCaption(uint8_t aButtonNumber);
    void setButtonCaption(uint8_t aButtonNumber, const char * aCaption, bool doDrawButton);
    void setButtonValue(uint8_t aButtonNumber, const int16_t aValue);
    void setButtonValueAndDraw(uint8_t aButtonNumber, const int16_t aValue);
    void setButtonColor(uint8_t aButtonNumber, const Color_TypeDef aButtonColor);
    void setButtonColorAndDraw(uint8_t aButtonNumber, const Color_TypeDef aButtonColor);

    void activateButton(uint8_t aButtonNumber);
    void deactivateButton(uint8_t aButtonNumber);
    void activateAllButtons(void);
    void deactivateAllButtons(void);
    void setButtonsGlobalFlags(uint16_t aFlags);
    void setButtonsTouchTone(uint8_t aToneIndex, uint8_t aToneVolume);

    uint8_t createButtonPGM(const uint16_t aPositionX, const uint16_t aPositionY, const uint16_t aWidthX,
    const uint16_t aHeightY, const Color_TypeDef aButtonColor, PGM_P aPGMCaption, const uint8_t aCaptionSize, const uint8_t aFlags,
    const int16_t aValue, void (*aOnTouchHandler)(uint8_t, int16_t));
    void setButtonCaptionPGM(uint8_t aButtonNumber, PGM_P aPGMCaption, bool doDrawButton);

    /*
     * Slider stuff
     */
    void resetAllSliders(void);
    uint8_t createSlider(const uint16_t aPositionX, const uint16_t aPositionY, const uint8_t aBarWidth, const uint16_t aBarLength,
    const uint16_t aThresholdValue, const int16_t aInitalValue, const Color_TypeDef aSliderColor,
    const Color_TypeDef aBarColor, const uint8_t aOptions, void (*aOnChangeHandler)(const uint8_t, const int16_t));
    void drawSlider(uint8_t aSliderNumber);
    void drawSliderBorder(uint8_t aSliderNumber);
    void setSliderActualValueAndDraw(uint8_t aSliderNumber,int16_t aActualValue);
    void setSliderColorBarThreshold(uint8_t aSliderNumber, uint16_t aBarThresholdColor);
    void setSliderColorBarBackground(uint8_t aSliderNumber, uint16_t aBarBackgroundColor);

    void activateSlider(uint8_t aSliderNumber);
    void deactivateSlider(uint8_t aSliderNumber);
    void activateAllSliders(void);
    void deactivateAllSliders(void);

private:
    struct XYSize mReferenceDisplaySize; // contains requested display size
    struct XYSize mActualDisplaySize;
    struct XYSize mMaxDisplaySize;
    uint16_t mActualDisplayHeight;
    uint16_t mActualDisplayWidth;

};
// The instance provided by the class itself
extern BlueDisplay BlueDisplay1;
#endif

#ifdef LOCAL_DISPLAY_EXISTS
#include <MI0283QT2.h>
#include "EventHandler.h"

/*
 * MI0283QT2 TFTDisplay - must provided by main program
 * external declaration saves ROM (210 Bytes) and RAM ( 20 Bytes)
 * and avoids missing initialization :-)
 */
extern MI0283QT2 LocalDisplay;
#else
// to be provided by another source
extern const uint16_t DISPLAY_HEIGHT;
extern const uint16_t DISPLAY_WIDTH;
#endif

#endif /* BLUEDISPLAY_H_ */
