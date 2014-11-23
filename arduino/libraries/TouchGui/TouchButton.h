/*
 * TouchButton.h
 *
 * Renders touch buttons for lcd
 * A button can be a simple clickable text
 * or a box with or without text
 *
 *  Created on:  30.01.2012
 *      Author:  Armin Joachimsmeyer
 *      Email:   armin.joachimsmeyer@gmx.de
 *      License: GPL v3 (http://www.gnu.org/licenses/gpl.html)
 *      Version: 1.0.0
 */

#ifndef TOUCHBUTTON_H_
#define TOUCHBUTTON_H_

#include <TouchLib.h>

#define BUTTON_DEFAULT_SPACING 16
#define BUTTON_WIDTH_2 152 // for 2 buttons horizontal - 19 characters
#define BUTTON_WIDTH_2_POS_2 (BUTTON_WIDTH_2 + BUTTON_DEFAULT_SPACING)
#define BUTTON_WIDTH_3 96 // for 3 buttons horizontal - 12 characters
#define BUTTON_WIDTH_3_POS_2 (BUTTON_WIDTH_3 + BUTTON_DEFAULT_SPACING)
#define BUTTON_WIDTH_3_POS_3 (DISPLAY_WIDTH - BUTTON_WIDTH_3)
#define BUTTON_WIDTH_4 68 // for 4 buttons horizontal - 8 characters
#define BUTTON_WIDTH_5 51 // for 5 buttons horizontal 51,2  - 6 characters
#define BUTTON_WIDTH_5_POS_2 (BUTTON_WIDTH_5 + BUTTON_DEFAULT_SPACING)
#define BUTTON_WIDTH_5_POS_3 (2*(BUTTON_WIDTH_5 + BUTTON_DEFAULT_SPACING))
#define BUTTON_WIDTH_6 40 // for 6 buttons horizontal
#define BUTTON_HEIGHT_4 48 // for 4 buttons vertical
#define BUTTON_HEIGHT_4_LINE_2 (BUTTON_HEIGHT_4 + BUTTON_DEFAULT_SPACING)
#define BUTTON_HEIGHT_4_LINE_3 (2*(BUTTON_HEIGHT_4 + BUTTON_DEFAULT_SPACING))
#define BUTTON_HEIGHT_4_LINE_4 (DISPLAY_HEIGHT - BUTTON_HEIGHT_4)
#define BUTTON_HEIGHT_5 35 // for 5 buttons vertical
#define BUTTON_HEIGHT_5_LINE_2 (BUTTON_HEIGHT_5 + BUTTON_DEFAULT_SPACING)
#define BUTTON_HEIGHT_5_LINE_3 (2*(BUTTON_HEIGHT_5 + BUTTON_DEFAULT_SPACING))
#define BUTTON_HEIGHT_5_LINE_4 (3*(BUTTON_HEIGHT_5 + BUTTON_DEFAULT_SPACING))
#define BUTTON_HEIGHT_5_LINE_5 (DISPLAY_HEIGHT - BUTTON_HEIGHT_5)
#define BUTTON_HEIGHT_6 26 // for 6 buttons vertical 26,66..
#define TOUCHBUTTON_DEFAULT_COLOR 			RGB( 180, 180, 180)
#define TOUCHBUTTON_DEFAULT_CAPTION_COLOR 	COLOR_BLACK
#define TOUCHBUTTON_DEFAULT_TOUCH_BORDER 	2 // extension of touch region
// Error codes
#define TOUCHBUTTON_ERROR_X_RIGHT 			-1
#define TOUCHBUTTON_ERROR_Y_BOTTOM 			-2
#define TOUCHBUTTON_ERROR_CAPTION_TOO_LONG	-3
#define TOUCHBUTTON_ERROR_CAPTION_TOO_HIGH	-4
#define TOUCHBUTTON_ERROR_NOT_INITIALIZED   -64

class TouchButton {
public:

    TouchButton();

    static void setDefaultTouchBorder(const uint8_t aDefaultTouchBorder);
    static void setDefaultButtonColor(const uint16_t aDefaultButtonColor);
    static void setDefaultCaptionColor(const uint16_t aDefaultCaptionColor);
    static bool checkAllButtons(const uint16_t aTouchPositionX, const uint16_t aTouchPositionY);
    static void activateAllButtons();
    static void deactivateAllButtons();

    int8_t initSimpleButton(const uint16_t aPositionX, const uint16_t aPositionY, const uint16_t aWidthX,
            const uint16_t aHeightY, const char *aCaption, const uint8_t aCaptionSize, const int16_t aValue,
            void (*aOnTouchHandler)(TouchButton* const, int16_t));
    int8_t initSimpleButtonPGM(const uint16_t aPositionX, const uint16_t aPositionY, const uint16_t aWidthX,
            const uint16_t aHeightY, PGM_P aCaption, const uint8_t aCaptionSize, const int16_t aValue,
            void (*aOnTouchHandler)(TouchButton * const, int16_t));
            int8_t initButton(const uint16_t aPositionX, const uint16_t aPositionY, const uint16_t aWidthX,
                    const uint16_t aHeightY, const char *aCaption, const uint8_t aCaptionSize, const uint8_t aTouchBorder,
            const uint16_t aButtonColor, const uint16_t aCaptionColor, const int16_t aValue,
            void (*aOnTouchHandler)(TouchButton* const, int16_t));
    bool checkButton(const uint16_t aTouchPositionX, const uint16_t aTouchPositionY);
    int8_t drawButton(void);
    int8_t drawCaption(void);
    int8_t setPosition(const uint16_t aPositionX, const uint16_t aPositionY);
    void setColor(const uint16_t aColor);
    void setCaption(const char *aCaption);
    void setCaptionPGM(PGM_P aCaption);
    void setCaptionColor(const uint16_t aColor);
    void setValue(const int16_t aValue);
    const char *getCaption(void) const;
    uint16_t getPositionX(void) const;
    uint16_t getPositionY(void) const;
    uint16_t getPositionXRight(void) const;
    uint16_t getPositionYBottom(void) const;
    void activate();
    void deactivate();
    void toString(char *aStringBuffer) const;
    uint8_t getTouchBorder() const;
    void setTouchBorder(uint8_t const touchBorder);
private:

    static TouchButton *sListStart;
    static uint16_t sDefaultButtonColor;
    static uint16_t sDefaultCaptionColor;
    static uint8_t sDefaultTouchBorder;

    BlueDisplay * mDisplay; // The Display to use

    uint16_t mButtonColor;
    uint16_t mCaptionColor;
    uint16_t mPositionX;
    uint16_t mPositionY;
    uint16_t mWidth;
    uint16_t mHeight;
    bool mPGMCaption;
    const char *mCaption;
    uint8_t mCaptionSize;
    uint8_t mTouchBorder;
    int16_t mValue;
    bool mIsActive;
    TouchButton *mNextObject;

protected:
    uint8_t getCaptionLength(char * aCaptionPointer) const;
    void (*mOnTouchHandler)(TouchButton * const, int16_t);

};

#endif /* TOUCHBUTTON_H_ */
