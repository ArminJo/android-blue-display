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

struct XYValue {
    uint16_t X;
    uint16_t Y;
};

#define DISPLAY_DEFAULT_HEIGHT 240 // value to use if not connected
#define DISPLAY_DEFAULT_WIDTH 320

/*
 * Basic colors
 */
// RGB to 16 bit 565 schema - 5 red | 6 green | 5 blue
#define COLOR_WHITE     0xFFFF
#define COLOR_BLACK     0X0001 // 16 because 0 is used as flag (e.g. in touch button for default color)
#define COLOR_RED       0xF800
#define COLOR_GREEN     0X03E0
#define COLOR_BLUE      0x001F
#define COLOR_DARK_BLUE 0x0014
#define COLOR_YELLOW    0XFFE0
#define COLOR_MAGENTA   0xF81F
#define COLOR_CYAN      0x03FF

// If used as background color for char or text, the background will not filled
#define COLOR_NO_BACKGROUND   0XFFFE

#define BLUEMASK 0x1F
#define RGB(r,g,b)   (((r&0xF8)<<8)|((g&0xFC)<<3)|((b&0xF8)>>3)) //5 red | 6 green | 5 blue
/*
 * Android Text sizes which are closest to the 8*12 font used locally
 */
#define TEXT_SIZE_11 11
#define TEXT_SIZE_22 22 // for factor 2 of 8*12 font
#define TEXT_SIZE_33 33 // for factor 3 of 8*12 font
// TextSize * 0.6
#define TEXT_SIZE_11_WIDTH 7
#define TEXT_SIZE_22_WIDTH 13

// TextSize * 0.93
#define TEXT_SIZE_11_HEIGHT 12
#define TEXT_SIZE_22_HEIGHT 24

// TextSize * 0.93
#define TEXT_SIZE_11_ASCEND 8
#define TEXT_SIZE_22_ASCEND 17

// TextSize * 0.24
#define TEXT_SIZE_11_DECEND 3
#define TEXT_SIZE_22_DECEND 5

uint8_t getTextWidth(uint8_t aTextSize);
uint8_t getTextAscend(uint8_t aTextSize);
uint16_t getTextAscendMinusDescend(uint8_t aTextSize);
uint8_t getTextMiddle(uint8_t aTextSize);
uint8_t getLocalTextSize(uint8_t aTextSize);

/*
 * Function tags for Bluetooth serial communication
 */
extern const int FUNCTION_TAG_SET_FLAGS;
extern const int BD_FLAG_COMPATIBILITY_MODE_ENABLE;
extern const int BD_FLAG_TOUCH_DISABLE;
extern const int BD_FLAG_TOUCH_MOVE_DISABLE;
extern const int BD_FLAG_USE_MAX_SIZE;

extern const int FUNCTION_TAG_SET_FLAGS_AND_SIZE;
extern const int FUNCTION_TAG_SET_CODEPAGE;
extern const int FUNCTION_TAG_SET_CHARACTER_CODE_MAPPING;

extern const int FUNCTION_TAG_CLEAR_DISPLAY;

//3 parameter
extern const int FUNCTION_TAG_DRAW_PIXEL;

// 5 parameter
extern const int FUNCTION_TAG_DRAW_LINE;
extern const int FUNCTION_TAG_DRAW_RECT;
extern const int FUNCTION_TAG_FILL_RECT;

extern const int FUNCTION_TAG_DRAW_CIRCLE;
extern const int FUNCTION_TAG_FILL_CIRCLE;

// Parameter + Data
extern const int FUNCTION_TAG_DRAW_CHAR;
extern const int FUNCTION_TAG_DRAW_STRING;
extern const int FUNCTION_TAG_DRAW_CHART;
extern const int FUNCTION_TAG_DRAW_PATH;
extern const int FUNCTION_TAG_FILL_PATH;

#ifdef __cplusplus
class BlueDisplay {
public:
    BlueDisplay();
    void setFlags(uint16_t aFlags);
    void setFlagsAndSize(uint16_t aFlags, uint16_t aWidth, uint16_t aHeight);
    void setCharacterMapping(uint8_t aChar, uint16_t aUnicodeChar);

    void clearDisplay(uint16_t aColor);

    void drawPixel(uint16_t aXPos, uint16_t aYPos, uint16_t aColor);
    void drawCircle(uint16_t aXCenter, uint16_t aYCenter, uint16_t aRadius, uint16_t aColor, uint16_t aStrokeWidth);
    void fillCircle(uint16_t aXCenter, uint16_t aYCenter, uint16_t aRadius, uint16_t aColor);
    void drawRect(uint16_t aXStart, uint16_t aYStart, uint16_t aXEnd, uint16_t aYEnd, uint16_t aColor, uint16_t aStrokeWidth);
    void fillRect(uint16_t aXStart, uint16_t aYStart, uint16_t aXEnd, uint16_t aYEnd, uint16_t aColor);

    uint16_t drawChar(uint16_t aPosX, uint16_t aPosY, char aChar, uint8_t aCharSize, uint16_t aFGColor, uint16_t aBGColor);
    uint16_t drawText(uint16_t aXStart, uint16_t aYStart, const char *aStringPtr, uint8_t aFontSize, uint16_t aColor,
            uint16_t aBGColor);

    void drawLine(uint16_t aXStart, uint16_t aYStart, uint16_t aXEnd, uint16_t aYEnd, uint16_t aColor);
    void drawLineFastOneX(uint16_t x0, uint16_t y0, uint16_t y1, uint16_t color);
    void drawLineWithThickness(uint16_t aXStart, uint16_t aYStart, uint16_t aXEnd, uint16_t aYEnd, int16_t aThickness,
            uint8_t aThicknessMode, uint16_t aColor);

    void drawChartByteBuffer(uint16_t aXOffset, uint16_t aYOffset, uint16_t aColor, uint16_t aClearBeforeColor,
            uint8_t *aByteBuffer, uint16_t aByteBufferLength);

    void setMaxDisplaySize(int aMaxDisplayWidth, int aMaxDisplayHeight);
    uint16_t getDisplayWidth(void);
    uint16_t getDisplayHeight(void);

    void setNeedsRefresh(void);
    bool needsRefresh(void);
    void setConnectionJustBuildUp(void);
    bool isConnectionJustBuildUp(void);

    uint16_t drawTextPGM(uint16_t aXStart, uint16_t aYStart, PGM_P aPGMString, uint8_t aFontSize, uint16_t aColor,
    uint16_t aBGColor);

private:
    bool mNeedsRefresh; /* true if resize happens */
    bool mConnectionBuildUp; /* true if new connection happens */
    uint16_t mDisplayHeight;
    uint16_t mDisplayWidth;
    uint16_t mMaxDisplayHeight;
    uint16_t mMaxDisplayWidth;

};
// The instance provided by the class itself
extern BlueDisplay BlueDisplay1;
#endif

#ifdef LOCAL_DISPLAY_EXISTS
#include <MI0283QT2.h>
/*
 * MI0283QT2 TFTDisplay - must provided by main program
 * external declaration saves ROM (210 Bytes) and RAM ( 20 Bytes)
 * and avoids missing initialization :-)
 */
extern MI0283QT2 LocalDisplay;
#endif

uint16_t drawMLText(uint16_t aPosX, uint16_t aPosY, const char *aStringPtr, uint8_t aTextSize, uint16_t aColor, uint16_t aBGColor);

#endif /* BLUEDISPLAY_H_ */
