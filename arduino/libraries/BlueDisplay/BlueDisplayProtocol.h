/*
 * BlueDisplayProtocol.h
 *
 *  Created on: 14.08.2015
 * @author Armin Joachimsmeyer
 *      Email:   armin.joachimsmeyer@gmail.com
 *      License: GPL v3 (http://www.gnu.org/licenses/gpl.html)
 * @version 1.0.0
 *
 *
 * SEND PROTOCOL USED:
 * Message:
 * 1. Sync byte A5
 * 2. Byte function token
 * 3. Short length (in short units) of parameters
 * 4. Short n parameters
 *
 * Optional Data:
 * 1. Sync Byte A5
 * 2. Byte Data_Size_Type token (byte, short etc.)
 * 3. Short length of data
 * 4. Length data values
 *
 *
 * RECEIVE PROTOCOL USED:
 *
 * Touch/size message has 7 bytes:
 * 1 - Gross message length in bytes
 * 2 - Function code
 * 3 - X Position LSB
 * 4 - X Position MSB
 * 5 - Y Position LSB
 * 6 - Y Position MSB
 * 7 - Sync token
 *
 * Callback message has 15 bytes:
 * 1 - Gross message length in bytes
 * 2 - Function code
 * 16 bit button index
 * 16 bit filler for 32 bit alignment of next values
 * 32 bit callback address
 * 32 bit value
 * 13 - Sync token
 *
 */

#ifndef BLUEDISPLAYPROTOCOL_H_
#define BLUEDISPLAYPROTOCOL_H_

/*
 * Internal functions
 */
static const int FUNCTION_TAG_GLOBAL_SETTINGS = 0x08;
// Sub functions for GLOBAL_SETTINGS
static const int SET_FLAGS_AND_SIZE = 0x00;
static const int SET_CODEPAGE = 0x01;
static const int SET_CHARACTER_CODE_MAPPING = 0x02;
static const int SET_LONG_TOUCH_DOWN_TIMEOUT = 0x08;
static const int SET_SCREEN_ORIENTATION_LOCK = 0x0C;

/*
 * Sensors
 */
static const int FUNCTION_TAG_SENSOR_SETTINGS = 0x0A;

/*
 * Miscellaneous functions
 */
static const int FUNCTION_TAG_GET_NUMBER = 0x0C;
static const int FUNCTION_TAG_GET_TEXT = 0x0D;
static const int FUNCTION_TAG_PLAY_TONE = 0x0E;

/*
 * Display functions
 */
const int FUNCTION_TAG_CLEAR_DISPLAY = 0x10;
const int FUNCTION_TAG_DRAW_DISPLAY = 0x11;
// 3 parameter
const int FUNCTION_TAG_DRAW_PIXEL = 0x14;
// 6 parameter
const int FUNCTION_TAG_DRAW_CHAR = 0x16;
// 5 parameter
const int FUNCTION_TAG_DRAW_LINE_REL = 0x20;
const int FUNCTION_TAG_DRAW_LINE = 0x21;
const int FUNCTION_TAG_DRAW_RECT_REL = 0x24;
const int FUNCTION_TAG_FILL_RECT_REL = 0x25;
const int FUNCTION_TAG_DRAW_RECT = 0x26;
const int FUNCTION_TAG_FILL_RECT = 0x27;

const int FUNCTION_TAG_DRAW_CIRCLE = 0x28;
const int FUNCTION_TAG_FILL_CIRCLE = 0x29;

const int FUNCTION_TAG_WRITE_SETTINGS = 0x34;
// Flags for WRITE_SETTINGS
const int WRITE_FLAG_SET_SIZE_AND_COLORS_AND_FLAGS = 0x00;
const int WRITE_FLAG_SET_POSITION = 0x01;
const int WRITE_FLAG_SET_LINE_COLUMN = 0x02;

const int LAST_FUNCTION_TAG_WITHOUT_DATA = 0x5F;
// Function with variable data size
const int FUNCTION_TAG_DRAW_STRING = 0x60;
const int FUNCTION_TAG_DEBUG_STRING = 0x61;
const int FUNCTION_TAG_WRITE_STRING = 0x62;

const int FUNCTION_TAG_GET_NUMBER_WITH_SHORT_PROMPT = 0x64;
const int FUNCTION_TAG_GET_NUMBER_WITH_SHORT_PROMPT_AND_INITIAL_VALUE = 0x65;

const int FUNCTION_TAG_DRAW_PATH = 0x68;
const int FUNCTION_TAG_FILL_PATH = 0x69;
const int FUNCTION_TAG_DRAW_CHART = 0x6A;
const int FUNCTION_TAG_DRAW_CHART_WITHOUT_DIRECT_RENDERING = 0x6B;

/*
 * Button functions
 */
const int FUNCTION_TAG_BUTTON_DRAW = 0x40;
const int FUNCTION_TAG_BUTTON_DRAW_CAPTION = 0x41;
const int FUNCTION_TAG_BUTTON_SETTINGS = 0x42;
// Flags for BUTTON_SETTINGS
const int BUTTON_FLAG_SET_BUTTON_COLOR = 0x00;
const int BUTTON_FLAG_SET_BUTTON_COLOR_AND_DRAW = 0x01;
const int BUTTON_FLAG_SET_CAPTION_COLOR = 0x02;
const int BUTTON_FLAG_SET_CAPTION_COLOR_AND_DRAW = 0x03;
const int BUTTON_FLAG_SET_VALUE = 0x04;
const int BUTTON_FLAG_SET_VALUE_AND_DRAW = 0x05;
const int BUTTON_FLAG_SET_COLOR_AND_VALUE = 0x06;
const int BUTTON_FLAG_SET_COLOR_AND_VALUE_AND_DRAW = 0x07;
const int BUTTON_FLAG_SET_POSITION = 0x08;
const int BUTTON_FLAG_SET_POSITION_AND_DRAW = 0x09;
const int BUTTON_FLAG_SET_ACTIVE = 0x10;
const int BUTTON_FLAG_RESET_ACTIVE = 0x11;
const int BUTTON_FLAG_SET_AUTOREPEAT_TIMING = 0x12;

const int FUNCTION_TAG_BUTTON_REMOVE = 0x43;

// static functions
const int FUNCTION_TAG_BUTTON_ACTIVATE_ALL = 0x48;
const int FUNCTION_TAG_BUTTON_DEACTIVATE_ALL = 0x49;
const int FUNCTION_TAG_BUTTON_GLOBAL_SETTINGS = 0x4A;

// Function with variable data size
const int FUNCTION_TAG_BUTTON_CREATE = 0x70;
const int FUNCTION_TAG_BUTTON_CREATE_32 = 0x71;
const int FUNCTION_TAG_BUTTON_SET_CAPTION = 0x72;
const int FUNCTION_TAG_BUTTON_SET_CAPTION_AND_DRAW_BUTTON = 0x73;

/*
 * Slider functions
 */
static const int FUNCTION_TAG_SLIDER_CREATE = 0x50;
static const int FUNCTION_TAG_SLIDER_DRAW = 0x51;
static const int FUNCTION_TAG_SLIDER_SETTINGS = 0x52;
static const int FUNCTION_TAG_SLIDER_DRAW_BORDER = 0x53;

// Flags for SLIDER_SETTINGS
static const int SLIDER_FLAG_SET_COLOR_THRESHOLD = 0x00;
static const int SLIDER_FLAG_SET_COLOR_BAR_BACKGROUND = 0x01;
static const int SLIDER_FLAG_SET_COLOR_BAR = 0x02;
static const int SLIDER_FLAG_SET_VALUE_AND_DRAW_BAR = 0x03;
static const int SLIDER_FLAG_SET_POSITION = 0x04;
static const int SLIDER_FLAG_SET_ACTIVE = 0x05;
static const int SLIDER_FLAG_RESET_ACTIVE = 0x06;

static const int SLIDER_FLAG_SET_CAPTION_PROPERTIES = 0x08;
static const int SLIDER_FLAG_SET_VALUE_STRING_PROPERTIES = 0x09;


// static slider functions
static const int FUNCTION_TAG_SLIDER_ACTIVATE_ALL = 0x58;
static const int FUNCTION_TAG_SLIDER_DEACTIVATE_ALL = 0x59;
static const int FUNCTION_TAG_SLIDER_GLOBAL_SETTINGS = 0x5A;

// Function with variable data size
const int FUNCTION_TAG_SLIDER_SET_CAPTION = 0x78;
const int FUNCTION_TAG_SLIDER_PRINT_VALUE = 0x79;

#endif /* BLUEDISPLAYPROTOCOL_H_ */
