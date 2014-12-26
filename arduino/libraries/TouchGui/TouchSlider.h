/*
 * TouchSlider.h
 *
 *  Created on: 31.01.2012
 *      Author: Armin Joachimsmeyer
 *      Email:   armin.joachimsmeyer@gmx.de
 *      License: GPL v3 (http://www.gnu.org/licenses/gpl.html)
 *      Version: 1.0.0
 *
 */

#ifndef TOUCHSLIDER_H_
#define TOUCHSLIDER_H_

#include <TouchLib.h>

#define TOUCHSLIDER_VERTICAL_SHOW_NOTHING 0x00
#define TOUCHSLIDER_SHOW_BORDER 0x01
#define TOUCHSLIDER_SHOW_VALUE 0x02
#define TOUCHSLIDER_IS_HORIZONTAL 0x04
#define TOUCHSLIDER_HORIZONTAL_VALUE_BELOW_TITLE 0x08

#define TOUCHSLIDER_DEFAULT_SLIDER_COLOR 		RGB( 180, 180, 180)
#define TOUCHSLIDER_DEFAULT_BAR_COLOR 			COLOR_GREEN
#define TOUCHSLIDER_DEFAULT_BAR_THRESHOLD_COLOR COLOR_RED
#define TOUCHSLIDER_DEFAULT_BAR_BACK_COLOR 		COLOR_WHITE
#define TOUCHSLIDER_DEFAULT_CAPTION_COLOR 		COLOR_RED
#define TOUCHSLIDER_DEFAULT_VALUE_COLOR 		COLOR_BLUE
#define TOUCHSLIDER_DEFAULT_CAPTION_VALUE_BACK_COLOR 	COLOR_NO_BACKGROUND
#define TOUCHSLIDER_SIZE_FACTOR 				2 // Factor for right and left border size
#define TOUCHSLIDER_OVERALL_SIZE_FACTOR 		6 // mSize * OVERALL_SIZE_FACTOR = width in pixel
#define TOUCHSLIDER_DEFAULT_SIZE 				4
#define TOUCHSLIDER_DEFAULT_TOUCH_BORDER 		4 // extension of touch region
#define TOUCHSLIDER_DEFAULT_SHOW_CAPTION		true
#define TOUCHSLIDER_DEFAULT_SHOW_VALUE			true
#define TOUCHSLIDER_DEFAULT_MAX_VALUE           160
#define TOUCHSLIDER_DEFAULT_THRESHOLD_VALUE     100

// Error codes
#define TOUCHSLIDER_ERROR_SIZE_ZERO 	-1
#define TOUCHSLIDER_ERROR_SIZE 			-2
#define TOUCHSLIDER_ERROR_MAX_VALUE		-4
#define TOUCHSLIDER_ERROR_ACTUAL_VALUE 	-8
#define TOUCHSLIDER_ERROR_VALUE_TOO_HIGH -16

// return values for checkAllSliders()
#ifndef NOT_TOUCHED
#define NOT_TOUCHED 0
#endif
#define SLIDER_TOUCHED 4

class TouchSlider {
public:
    // Start of list of touchslider
    static TouchSlider * sListStart;

    /*
     * Static functions
     */
    TouchSlider();
    static void setDefaults(const int8_t aDefaultTouchBorder, const uint16_t aDefaultSliderColor,
            const uint16_t aDefaultBarColor, const uint16_t aDefaultBarThresholdColor,
            const uint16_t aDefaultBarBackgroundColor, const uint16_t aDefaultCaptionColor,
            const uint16_t aDefaultValueColor, const uint16_t aDefaultValueCaptionBackgroundColor);
    static void setDefaultSliderColor(const uint16_t aDefaultSliderColor);
    static void setDefaultBarColor(const uint16_t aDefaultBarColor);
    static bool checkAllSliders(const uint16_t aTouchPositionX, const uint16_t aTouchPositionY);
    static void deactivateAllSliders();
    static void activateAllSliders();
    /*
     * Member functions
     */
    void initSimpleSlider(const uint16_t aPositionX, const uint16_t aPositionY, const uint8_t aSizeX,
            const char * aCaption, const uint8_t aOptions,
            int16_t (*aOnChangeHandler)(TouchSlider * const, const int16_t), const char * (*aValueHandler)(int16_t));
    void initSlider(const uint16_t aPositionX, const uint16_t aPositionY, const uint16_t aSize, const uint16_t aMaxValue,
            const uint16_t aThresholdValue, const int16_t aInitalValue, const char * aCaption, const int8_t aTouchBorder,
            const uint8_t aOptions,
            int16_t (*aOnChangeHandler)(TouchSlider * const, const int16_t), const char * (*aValueHandler)(int16_t));
    void initSliderColors(const uint16_t aSliderColor, const uint16_t aBarColor, const uint16_t aBarThresholdColor,
            const uint16_t aBarBackgroundColor, const uint16_t aCaptionColor, const uint16_t aValueColor,
            const uint16_t aValueCaptionBackgroundColor);
    void setValueAndCaptionBackgroundColor(const uint16_t aValueCaptionBackgroundColor);
    void setValueColor(const uint16_t aValueColor);
    void drawSlider();
    bool checkSlider(const uint16_t aPositionX, const uint16_t aPositionY);
    void drawBar();
    void drawBorder();
    int16_t getActualValue() const;
    void setActualValue(int16_t actualValue);
    void setActualValueAndDraw(int16_t actualValue);
    void setActualValueAndDrawBar(int16_t actualValue);
    uint16_t getPositionXRight() const;
    uint16_t getPositionYBottom() const;
    void activate();
    void deactivate();
    uint16_t getBarColor() const;
    void setSliderColor(uint16_t sliderColor);
    void setBarColor(uint16_t barColor);
    void setBarThresholdColor(uint16_t barThresholdColor);

private:
    /*
     * Defaults
     */
    static uint16_t sDefaultSliderColor;
    static uint16_t sDefaultBarColor;
    static uint16_t sDefaultBarThresholdColor;
    static uint16_t sDefaultBarBackgroundColor;
    static uint16_t sDefaultCaptionColor;
    static uint16_t sDefaultValueColor;
    static uint16_t sDefaultValueCaptionBackgroundColor;
    static int8_t sDefaultTouchBorder;

    BlueDisplay * mDisplay; // The Display to use

    uint8_t mActualTouchValue;
    // This value can be different from mActualTouchValue and is provided by callback handler
    int16_t mActualValue;
    /*
     * The Slider
     */
    uint16_t mPositionX;
    uint16_t mPositionXRight;
    uint16_t mPositionY;
    uint16_t mPositionYBottom;
    int16_t mBarLength;
    int16_t mThresholdValue; // Value for color change
    uint8_t mBarWidth;
    const char* mCaption;
    uint8_t mTouchBorder; // extension of touch region
    // Colors
    uint16_t mSliderColor;
    uint16_t mBarColor;
    uint16_t mBarThresholdColor;
    uint16_t mBarBackgroundColor;
    uint16_t mCaptionColor;
    uint16_t mValueColor;
    uint16_t mValueCaptionBackgroundColor;
    uint8_t mOptions;
    bool mIsActive;
    TouchSlider* mNextObject;
    int16_t (*mOnChangeHandler)(TouchSlider* const, int16_t);
    const char* (*mValueHandler)(int16_t);
    int8_t checkParameterValues();
    void printCaption();
    int8_t printValue();
};

#endif /* TOUCHSLIDER_H_ */
