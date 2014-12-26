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

#define TOUCHBUTTON_DEFAULT_COLOR 			RGB( 180, 180, 180)
#define TOUCHBUTTON_DEFAULT_CAPTION_COLOR 	COLOR_BLACK

// Error codes
#define TOUCHBUTTON_ERROR_X_RIGHT 			-1
#define TOUCHBUTTON_ERROR_Y_BOTTOM 			-2
#define TOUCHBUTTON_ERROR_CAPTION_TOO_LONG	-3
#define TOUCHBUTTON_ERROR_CAPTION_TOO_HIGH	-4
#define TOUCHBUTTON_ERROR_NOT_INITIALIZED   -64

// return values for checkAllButtons()
#define NOT_TOUCHED false
#define BUTTON_TOUCHED 1
#define BUTTON_TOUCHED_AUTOREPEAT 2 // an autorepeat button was touched
class TouchButton {
public:

    TouchButton();

    static void setDefaultTouchBorder(const uint8_t aDefaultTouchBorder);
    static void setDefaultButtonColor(const uint16_t aDefaultButtonColor);
    static void setDefaultCaptionColor(const uint16_t aDefaultCaptionColor);
    static bool checkAllButtons(const uint16_t aTouchPositionX, const uint16_t aTouchPositionY);
    static void activateAllButtons();
    static void deactivateAllButtons();

    int8_t initSimpleButton(const uint16_t aPositionX, const uint16_t aPositionY, const uint16_t aWidthX, const uint16_t aHeightY,
            const char *aCaption, const uint8_t aCaptionSize, const int16_t aValue,
            void (*aOnTouchHandler)(TouchButton* const, int16_t));
    int8_t initSimpleButtonPGM(const uint16_t aPositionX, const uint16_t aPositionY, const uint16_t aWidthX,
            const uint16_t aHeightY, PGM_P aCaption, const uint8_t aCaptionSize, const int16_t aValue,
            void (*aOnTouchHandler)(TouchButton * const, int16_t));
            int8_t initButton(const uint16_t aPositionX, const uint16_t aPositionY, const uint16_t aWidthX,
                    const uint16_t aHeightY, const char *aCaption, const uint8_t aCaptionSize,
                    const uint16_t aButtonColor, const uint16_t aCaptionColor, const int16_t aValue,
                    void (*aOnTouchHandler)(TouchButton* const, int16_t));
            bool checkButton(const uint16_t aTouchPositionX, const uint16_t aTouchPositionY);
            int8_t drawButton(void);
            void removeButton(const uint16_t aBackgroundColor);
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

        private:

            static TouchButton *sListStart;
            static uint16_t sDefaultButtonColor;
            static uint16_t sDefaultCaptionColor;

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
            int16_t mValue;
            bool mIsActive;
            TouchButton *mNextObject;

        protected:
            uint8_t getCaptionLength(char * aCaptionPointer) const;
            void (*mOnTouchHandler)(TouchButton * const, int16_t);

        };

#endif /* TOUCHBUTTON_H_ */
