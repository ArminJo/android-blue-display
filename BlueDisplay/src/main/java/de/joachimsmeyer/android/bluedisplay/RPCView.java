/*
 * 	SUMMARY
 * 	Blue Display is an Open Source Android remote Display for Arduino etc.
 * 	It receives basic draw requests from Arduino etc. over Bluetooth and renders it.
 * 	It also implements basic GUI elements as buttons and sliders.
 * 	It sends touch or GUI callback events over Bluetooth back to Arduino.
 *
 *  Copyright (C) 2014-2020  Armin Joachimsmeyer
 *  armin.joachimsmeyer@gmail.com
 *
 * 	This file is part of BlueDisplay.
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
 *  This is the view which interprets the global and graphic commands received by serial service.
 *  It also handles touch events and swipe as well as long touch detection.
 *
 */
package de.joachimsmeyer.android.bluedisplay;

import static android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED;
import static android.speech.tts.TextToSpeech.SUCCESS;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.util.Log;
import android.util.SparseArray;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

class LineInfo {
    public Paint mPaint;
    public float Xoffset;
    public float Yoffset;
    public float XScaleFactor;
    public float YScaleFactor;
}

@SuppressLint("HandlerLeak")
public class RPCView extends View {

    public static final String LOG_TAG = "RPCView";

    BlueDisplay mBlueDisplayContext;

    protected int mRequestedCanvasWidth; // Of course without scale factor.
    protected int mRequestedCanvasHeight; // Of course without scale factor.
    protected int mCurrentCanvasPixelWidth; // The value used for drawing
    protected int mCurrentCanvasPixelHeight;
    // the maximum display area available for this orientation
    protected int mCurrentViewPixelHeight; // Display Height - StatusBar - TitleBar
    protected int mCurrentViewPixelWidth; // Display Width
    boolean mSendPendingConnectMessage = false;
    ToneGenerator mToneGeneratorForAbsoluteVolumes;
    ToneGenerator mToneGenerator;
    int mLastSystemVolume;
    int mLastRequestedToneVolume;

    TextToSpeech mTextToSpeech;
    boolean mTextToSpeechIsInitialized;

    private static final int MAX_CHART_LINE_WIDTH = 1800;

    // 4 values for one line - 16 lines possible
    public static float[][] mChartScreenBuffer = new float[16][MAX_CHART_LINE_WIDTH * 4];
    public static int[] mChartScreenBufferValidDataLength = new int[16];
    public static Bitmap mBitmap;
    private final Paint mBitmapPaint; // only used for onDraw() to draw bitmap
    private final Paint mInfoPaint; // for internal info text like touch coordinates

    static final float TEXT_ASCEND_FACTOR = 0.76f;
    static final float TEXT_DESCEND_FACTOR = 0.24f;
    static final float TEXT_WIDTH_FACTOR = 0.6f;

    private static final int TEXT_SIZE_INFO_PAINT = 22;
    private static final int TEXT_WIDTH_INFO_PAINT = (int) ((TEXT_SIZE_INFO_PAINT * TEXT_WIDTH_FACTOR) + 0.5);

    private final Paint mTextPaint; // To avoid garbage collection. For all scaled text
    private final Paint mTextBackgroundStroke1Fill; // for all scaled text background
    private final Paint mPaintStroke1Fill; // for circle, rectangles and path
    private final Paint mPaintStrokeScaleFactorColorSettable; // Fixed stroke scaleFactor for pixel, line and chart
    private final Paint mPaintStrokeAndColorSettable; // for all lines with thickness. Stroke and color must be set, before used
    private final Paint mPaintStrokeAndColorSettableAntiAliased; // Only for lines with thickness. Stroke and color must be set, before used
    private static final int NUMBER_OF_SUPPORTED_LINES = 16;
    // For future use
    private static final LineInfo[] mDrawLineInfoArray = new LineInfo[NUMBER_OF_SUPPORTED_LINES];


    /*
     * All values are input values (for scale factor = 1.0)
     */
    private int mTextPrintTextStartPosX; // Position after \n or \r for printf implementation
    private int mTextPrintTextCurrentPosX; // for printf implementation
    private int mTextPrintTextCurrentPosY; // for printf implementation
    private int mTextPrintTextSize = 12; // Unscaled value, for printf implementation
    private int mTextExpandedPrintColor = Color.BLACK; // for printf implementation

    private final Paint mPrintTextStroke1Fill; // Storage of color and scaled size for printf implementation
    private int mTextExpandedPrintBackgroundColor = Color.BLACK; // for printf implementation
    private boolean mTextPrintDoClearScreenOnWrap = true; // for printf implementation

    private int mLastDrawStringTextSize;
    private int mLastDrawStringColor;
    private int mLastDrawStringBackgroundColor;

    private Canvas mCanvas;
    private final Path mPath = new Path();

    private final Handler mHandler;

    public static char[] sCharsArray = new char[1024];

    /*
     * Scaling
     */
    protected float mScaleFactor = 1;
    protected float mMaxScaleFactor;
    float mTouchScaleFactor = 1;

    long mLastDebugToastMillis = 0;
    static final long DEBUG_TOAST_REFRESH_MILLIS = 500;

    /*
     * Flags which can be set by client
     */
    private boolean mUseMaxSize; // true after reset
    protected boolean mTouchBasicEnable; // send down, (move) and up events
    protected boolean mTouchMoveEnable; // can be used to suppress only the move events if mTouchBasicEnable is true
    private boolean mIsLongTouchEnabled;
    boolean mUseUpEventForButtons;

    long mLongTouchDownTimeoutMillis = 800;

    /*
     * Flags for touch event handler
     */
    protected boolean mShowTouchCoordinates = false;
    protected int mShowTouchCoordinatesLastStringLength = 19;

    public static boolean mDeviceListActivityLaunched = false; // to prevent multiple launches of DeviceListActivity()
    private final ScaleGestureDetector mScaleDetector;

    // Global flags
    // True if switching of mUseUpEventForButtons to true just occurs while button was down (mTouchIsActive == true)
    boolean mDisableButtonUpOnce = false;
    // Only one long touch per multitouch
    private boolean mLongTouchMessageWasSent;
    private boolean mLongTouchEventWasSent;
    private int mLongTouchPointerIndex;

    // Flags per touch pointer
    private final int MAX_POINTER = 5; // 5 different touch pointers supported on most devices
    boolean[] mTouchIsActive;
    // Positions contain raw (unscaled) values
    private final float[] mTouchDownPositionX;
    private final float[] mTouchDownPositionY;
    private final float[] mLastTouchPositionX;
    private final float[] mLastTouchPositionY;

    int[] mTouchStartsOnButtonNumber; // number of button for last touch_down event. -1 if touch does not start on a button
    int[] mTouchStartsOnSliderNumber; // number of slider if touch starts on a slider, used to distinguish between slider and swipe
    // moves
    boolean[] mSkipProcessingUntilTouchUpForButton; // true if touch down already sends an event (eg. a button down event)

    // To avoid multiple sending of effectively zero moving
    int[] mLastSentMoveXValue;
    int[] mLastSentMoveYValue;

    /*
     * region description of tags
     */
    // Tags for data buffer 0-7
    /*
     * Data buffer tags for future use (means never)
     */
    private static final int DATAFIELD_TAG_BYTE = 0x01;
    public static final int LAST_DATAFIELD_TAG = DATAFIELD_TAG_BYTE;

    // Internal functions 8-F
    public static final int INDEX_LAST_FUNCTION_INTERNAL = 0x0F; // not used yet

    // Display (draw) functions 10-3F
    public static final int INDEX_LAST_FUNCTION_DISPLAY = 0x3F; // not used yet

    // Button functions 40-4F
    public static final int INDEX_FIRST_FUNCTION_BUTTON = 0x40;
    public static final int INDEX_LAST_FUNCTION_BUTTON = 0x4F;

    // Slider functions 50-5F
    public static final int INDEX_FIRST_FUNCTION_SLIDER = 0x50;
    public static final int INDEX_LAST_FUNCTION_SLIDER = 0x5F;

    // Display (draw) functions with variable data 60-
    public static final int INDEX_FIRST_FUNCTION_WITH_DATA = 0x60;

    // Button functions with variable data 70-77
    public static final int INDEX_FIRST_FUNCTION_BUTTON_WITH_DATA = 0x70;
    public static final int INDEX_LAST_FUNCTION_BUTTON_WITH_DATA = 0x77;

    // Slider functions with variable data 78-7E
    // 0x7F is NOP
    public static final int INDEX_FIRST_FUNCTION_SLIDER_WITH_DATA = 0x78;
    public static final int INDEX_LAST_FUNCTION_SLIDER_WITH_DATA = 0x7E;

    /*
     * Constants used in Protocol
     */
    // If used as background color for char or text, the background will not filled. Must sign extend constant to 32 bit, since
    // parameter is also sign extended.
    public static final int COLOR32_NO_BACKGROUND = 0XFFFFFFFE;
    public static final int COLOR16_NO_BACKGROUND = 0XFFFE;

    public static final int COLOR16_NO_DELETE = 0X0001;

    public static final float NUMBER_INITIAL_VALUE_DO_NOT_SHOW = 1e-40f;

    /*
     * Internal functions
     */
    private final static int FUNCTION_GLOBAL_SETTINGS = 0x08;
    // Sub functions for GLOBAL_SETTINGS
    private final static int SUBFUNCTION_GLOBAL_SET_FLAGS_AND_SIZE = 0x00;
    // Flags for SUBFUNCTION_GLOBAL_SET_FLAGS_AND_SIZE value
    private final static int BD_FLAG_FIRST_RESET_ALL = 0x01;
    private final static int BD_FLAG_TOUCH_BASIC_DISABLE = 0x02;
    private final static int BD_FLAG_TOUCH_MOVE_DISABLE = 0x04;
    private final static int BD_FLAG_LONG_TOUCH_ENABLE = 0x08;
    private final static int BD_FLAG_USE_MAX_SIZE = 0x10;

    private final static int SUBFUNCTION_GLOBAL_SET_CODEPAGE = 0x01;
    private final static int SUBFUNCTION_GLOBAL_SET_CHARACTER_CODE_MAPPING = 0x02;
    private final static int SUBFUNCTION_GLOBAL_SET_LONG_TOUCH_DOWN_TIMEOUT = 0x08;
    private final static int SUBFUNCTION_GLOBAL_SET_SCREEN_ORIENTATION_LOCK = 0x0C;
    private final static int SUBFUNCTION_GLOBAL_SET_SCREEN_BRIGHTNESS = 0x0D;

    // 2 codes which are different from Android enumerations
    private final static int FLAG_SCREEN_ORIENTATION_LOCK_UNLOCK = 0x00;
    private final static int FLAG_SCREEN_ORIENTATION_LOCK_LANDSCAPE = 0x03;

    // results in a redraw callback
    private final static int FUNCTION_REQUEST_MAX_CANVAS_SIZE = 0x09;
    private final static int FUNCTION_SENSOR_SETTINGS = 0x0A;

    /*
     * Miscellaneous functions
     */
    private final static int FUNCTION_GET_NUMBER = 0x0C;
    private final static int FUNCTION_GET_TEXT = 0x0D;
    private final static int FUNCTION_GET_INFO = 0x0E;
    // Sub functions for FUNCTION_GET_INFO
    private final static int SUBFUNCTION_GET_INFO_LOCAL_TIME = 0x00;
    private final static int SUBFUNCTION_GET_INFO_UTC_TIME = 0x01;

    private final static int FUNCTION_PLAY_TONE = 0x0F;
    private final static int FUNCTION_SPEAK_SET_LOCALE = 0x80;
    private final static int FUNCTION_SPEAK_SET_VOICE = 0x81; // One of the Voice strings printed in log at level Info at BD application startup
    private final static int FUNCTION_SPEAK_STRING_FLUSH = 0x88;
    private final static int FUNCTION_SPEAK_STRING_ADD = 0x89;

    // used for Sync
    private final static int FUNCTION_NOP = 0x7F;

    /*
     * Display functions
     */
    public final static int FUNCTION_CLEAR_DISPLAY = 0x10;
    public final static int FUNCTION_DRAW_DISPLAY = 0x11;
    public final static int FUNCTION_CLEAR_DISPLAY_OPTIONAL = 0x12; // used for skipping commands in buffer
    // with 3 parameter
    private final static int FUNCTION_DRAW_PIXEL = 0x14;
    // 6 parameter
    public final static int FUNCTION_DRAW_CHAR = 0x16;

    // with 5 parameter
    private final static int FUNCTION_DRAW_LINE_REL = 0x20;
    private final static int FUNCTION_DRAW_LINE = 0x21;
    private final static int FUNCTION_DRAW_RECT_REL = 0x24;
    private final static int FUNCTION_FILL_RECT_REL = 0x25;
    private final static int FUNCTION_DRAW_RECT = 0x26;
    private final static int FUNCTION_FILL_RECT = 0x27;

    private final static int FUNCTION_DRAW_CIRCLE = 0x28;
    private final static int FUNCTION_FILL_CIRCLE = 0x29;

    private final static int FUNCTION_DRAW_VECTOR_DEGREE = 0x2C;
    private final static int FUNCTION_DRAW_VECTOR_RADIAN = 0x2D;

    private final static int FUNCTION_LINE_SETTINGS = 0x30;
    private final static int FUNCTION_WRITE_SETTINGS = 0x34;
    // Flags for WRITE_SETTINGS
    private final static int FLAG_WRITE_SETTINGS_SET_SIZE_AND_COLORS_AND_FLAGS = 0x00;
    private final static int FLAG_WRITE_SETTINGS_SET_POSITION = 0x01;
    private final static int FLAG_WRITE_SETTINGS_SET_LINE_COLUMN = 0x02;

    /*
     * Functions with variable parameter length
     */
    private final static int FUNCTION_DRAW_STRING = 0x60;
    private final static int STRING_ALIGN_RIGHT_XPOS = -1; // 0xFFFF
    private final static int STRING_ALIGN_MIDDLE_XPOS = -2; // 0xFFFE
    private final static int FUNCTION_DEBUG_STRING = 0x61;
    private final static int FUNCTION_WRITE_STRING = 0x62;

    private final static int FUNCTION_GET_NUMBER_WITH_SHORT_PROMPT = 0x64;
    private final static int FUNCTION_GET_TEXT_WITH_SHORT_PROMPT = 0x65;

    private final static int FUNCTION_DRAW_PATH = 0x68;
    private final static int FUNCTION_FILL_PATH = 0x69;
    final static int FUNCTION_DRAW_CHART = 0x6A;
    final static int FUNCTION_DRAW_CHART_WITHOUT_DIRECT_RENDERING = 0x6B;

    final static int CHART_MODE_PIXEL = 0;
    final static int CHART_MODE_LINE = 1;
    final static int CHART_MODE_AREA = 2; // not yet supported
    final static int CHART_X_AXIS_SCALE_FACTOR_1 = 0; // identity is code with 0
    final static int CHART_X_AXIS_SCALE_FACTOR_EXPANSION_1_5 = 1; // expansion by 1.5
    final static int CHART_X_AXIS_SCALE_FACTOR_EXPANSION_2 = 2; // expansion by factor 2
    final static int CHART_X_AXIS_SCALE_FACTOR_COMPRESSION_1_5 = -1; // compression by 1.5
    final static int CHART_X_AXIS_SCALE_FACTOR_COMPRESSION_2 = -2; // compression by factor 2
    final static int FUNCTION_DRAW_SCALED_CHART = 0x6C; // For chart implementation
    final static int FUNCTION_DRAW_SCALED_CHART_WITHOUT_DIRECT_RENDERING = 0x6D;

    private static final int LONG_TOUCH_DOWN = 0;

    /*
     * Action code to action string mappings for log output
     */
    public static final SparseArray<String> sActionMappings = new SparseArray<>(20);

    static {
        sActionMappings.put(MotionEvent.ACTION_DOWN, "down");
        sActionMappings.put(MotionEvent.ACTION_UP, "up");
        sActionMappings.put(MotionEvent.ACTION_MOVE, "move");
        sActionMappings.put(MotionEvent.ACTION_CANCEL, "cancel");
        sActionMappings.put(SerialService.EVENT_CONNECTION_BUILD_UP, "connection build up");
        sActionMappings.put(SerialService.EVENT_REDRAW, "redraw");
        sActionMappings.put(SerialService.EVENT_REORIENTATION, "reorientation");
        sActionMappings.put(SerialService.EVENT_DISCONNECT, "disconnect");

        sActionMappings.put(SerialService.EVENT_LONG_TOUCH_DOWN_CALLBACK, "long down");
        sActionMappings.put(SerialService.EVENT_FIRST_CALLBACK, "first");
        sActionMappings.put(SerialService.EVENT_BUTTON_CALLBACK, "button");
        sActionMappings.put(SerialService.EVENT_SLIDER_CALLBACK, "slider");

        sActionMappings.put(SerialService.EVENT_SWIPE_CALLBACK, "swipe");
        sActionMappings.put(SerialService.EVENT_NUMBER_CALLBACK, "number");
        sActionMappings.put(SerialService.EVENT_INFO_CALLBACK, "info");
        sActionMappings.put(SerialService.EVENT_FIRST_SENSOR_ACTION_CODE + Sensor.TYPE_ACCELEROMETER, "Accelerometer");
        sActionMappings.put(SerialService.EVENT_FIRST_SENSOR_ACTION_CODE + Sensor.TYPE_GRAVITY, "Gravity");
        sActionMappings.put(SerialService.EVENT_FIRST_SENSOR_ACTION_CODE + Sensor.TYPE_GYROSCOPE, "Gyroscope");
        sActionMappings.put(SerialService.EVENT_FIRST_SENSOR_ACTION_CODE + Sensor.TYPE_LINEAR_ACCELERATION, "LinAcceleration");
        sActionMappings.put(SerialService.EVENT_FIRST_SENSOR_ACTION_CODE + Sensor.TYPE_MAGNETIC_FIELD, "Magnetic");
        sActionMappings.put(SerialService.EVENT_NOP, "nop (for sync)");
        sActionMappings.put(SerialService.EVENT_SPEAKING_DONE, "speaking done");
        sActionMappings.put(SerialService.EVENT_REQUESTED_DATA_CANVAS_SIZE, "return canvas size and timestamp");
    }

    protected void setMaxScaleFactor() {
        float tMaxHeightFactor = (float) mCurrentViewPixelHeight / mRequestedCanvasHeight;
        float tMaxWidthFactor = (float) mCurrentViewPixelWidth / mRequestedCanvasWidth;
        mMaxScaleFactor = Math.min(tMaxHeightFactor, tMaxWidthFactor);
        if (MyLog.isINFO()) {
            MyLog.i(LOG_TAG, "MaxScaleFactor=" + mMaxScaleFactor);
        }
    }

    /*
     * Not used yet
     */
    protected int reduceIntWithXScaleFactor(int aValue, int aXScaleFactor) {
        if (aXScaleFactor == CHART_X_AXIS_SCALE_FACTOR_1) {
            return aValue;
        }
        int tRetValue;
        if (aXScaleFactor > CHART_X_AXIS_SCALE_FACTOR_EXPANSION_1_5) {
            tRetValue = aValue / aXScaleFactor;
        } else if (aXScaleFactor == CHART_X_AXIS_SCALE_FACTOR_EXPANSION_1_5) {
            // value * 2/3
            tRetValue = (aValue * 2) / 3;
        } else if (aXScaleFactor == CHART_X_AXIS_SCALE_FACTOR_COMPRESSION_1_5) {
            // value * 3/2
            tRetValue = (aValue * 3) / 2;
        } else {
            tRetValue = aValue * -aXScaleFactor;
        }
        return tRetValue;
    }

    /**
     * Enlarge value if scale factor is compression
     * Reduce value, if scale factor is expansion
     * <p>
     * aXScaleFactor > 1 : expansion by factor aXScaleFactor. I.e. value -> (value / factor)
     * aXScaleFactor == 1 : expansion by 1.5
     * aXScaleFactor == 0 : identity
     * aXScaleFactor == -1 : compression by 1.5
     * aXScaleFactor < -1 : compression by factor -aXScaleFactor -> (value * factor)
     * multiplies value with factor if aXScaleFactor is < 0 (compression) or divide if aXScaleFactor is > 0 (expansion)
     */
    protected float reduceFloatWithXScaleFactor(float aValue, int aXScaleFactor) {
        if (aXScaleFactor == CHART_X_AXIS_SCALE_FACTOR_1) {
            return aValue;
        }
        float tRetValue;
        if (aXScaleFactor > CHART_X_AXIS_SCALE_FACTOR_EXPANSION_1_5) {
            tRetValue = aValue / aXScaleFactor;
        } else if (aXScaleFactor == CHART_X_AXIS_SCALE_FACTOR_EXPANSION_1_5) {
            // value * 2/3
            tRetValue = (aValue * 2) / 3;
        } else if (aXScaleFactor == CHART_X_AXIS_SCALE_FACTOR_COMPRESSION_1_5) {
            // value * 3/2
            tRetValue = (aValue * 3) / 2;
        } else {
            tRetValue = aValue * -aXScaleFactor;
        }
        return tRetValue;
    }

    /**
     * Enlarge value if scale factor is expansion
     * Reduce value, if scale factor is compression
     */
    protected float enlargeFloatWithXScaleFactor(float aValue, int aXScaleFactor) {
        return reduceFloatWithXScaleFactor(aValue, -aXScaleFactor);
    }

    @SuppressLint("NewApi")
    public RPCView(Context aContext, Handler aHandler) {
        super(aContext);
        if (MyLog.isINFO()) {
            Log.i(LOG_TAG, "+++ ON CREATE +++");
        }

        mBlueDisplayContext = (BlueDisplay) aContext;
        mHandler = aHandler;

        resetFlags();

        mScaleDetector = new ScaleGestureDetector(aContext, new ScaleListener());

        // to have system sound volume control
        mLastSystemVolume = mBlueDisplayContext.mAudioManager.getStreamVolume(AudioManager.STREAM_SYSTEM);
        mToneGenerator = new ToneGenerator(AudioManager.STREAM_SYSTEM, (mLastSystemVolume * ToneGenerator.MAX_VOLUME) / mBlueDisplayContext.mMaxSystemVolume);


        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            if (MyLog.isINFO()) {
                MyLog.i(LOG_TAG, "TextToSpeech not available for Android " + Build.VERSION.RELEASE + " < 5 (Lollipop)");
            }
        } else {
            // For Text To Speech
            mTextToSpeech = new TextToSpeech(mBlueDisplayContext, new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    if (status == SUCCESS) {
                        List<TextToSpeech.EngineInfo> tEnginesList = mTextToSpeech.getEngines();
                        boolean tIsGoogleTTSAvailable = false;
                        for (TextToSpeech.EngineInfo tEngine : tEnginesList) {
                            if (tEngine.name.equals("com.google.android.tts")) {
                                tIsGoogleTTSAvailable = true;
                                if (MyLog.isINFO()) {
                                    MyLog.i(LOG_TAG, "Google TextToSpeech is available.");
                                }
                            } else {
                                if (MyLog.isDEBUG()) {
                                    MyLog.d(LOG_TAG, "TextToSpeech engine " + tEngine.name + " is available.");
                                }
                            }
                        }
                        if (tIsGoogleTTSAvailable) {
                            // Use Google TTS
                            mTextToSpeech.setEngineByPackageName("com.google.android.tts");
                            mTextToSpeech.setLanguage(Locale.US);
                            mTextToSpeechIsInitialized = true;
                            if (MyLog.isINFO()) {
                                MyLog.i(LOG_TAG, "Default voice is: " + mTextToSpeech.getDefaultVoice().getName());
                                Set<Voice> tVoicesSet = mTextToSpeech.getVoices();
                                MyLog.i(LOG_TAG, "Available voices are:");
                                int i = 1; // I can see 472 voices :-)
                                for (Voice tVoice : tVoicesSet) {
                                    MyLog.i(LOG_TAG, i + " " + tVoice.getName());
                                    i++;
                                }
                            }
//                    } else {
//                        // Fallback to another TTS engine (if available)
//                        fallbackToOtherTTS(mBlueDisplayContext);
                        }
                        mTextToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                            @Override
                            public void onStart(String utteranceId) {
                                // Called when TTS starts speaking
                                Log.i(LOG_TAG, "TextToSpeech started.");
                            }

                            @Override
                            public void onDone(String aUtteranceId) {
                                // Called when TTS finishes speaking
                                if (MyLog.isINFO()) {
                                    MyLog.i(LOG_TAG, "TextToSpeech finished speaking: " + aUtteranceId);
                                }
                                mBlueDisplayContext.mSerialService.writeOneIntegerEvent(SerialService.EVENT_SPEAKING_DONE, SerialService.EVENT_SPEAKING_OK);
                            }

                            @Override
                            public void onError(String utteranceId) {
                                // Called if an error occurs during TTS
                                Log.e(LOG_TAG, "TextToSpeech error occurred.");
                                mBlueDisplayContext.mSerialService.writeOneIntegerEvent(SerialService.EVENT_SPEAKING_DONE, SerialService.EVENT_SPEAKING_ERROR);
                            }
                        });
                    } else {
                        Log.e(LOG_TAG, "TextToSpeech initialization failed. TTS engine \"com.google.android.tts\" not found.");
                    }
                }
            });
        }


        mTextPaint = new Paint();
        mTextPaint.setTypeface(Typeface.MONOSPACE);
        mTextPaint.setStyle(Paint.Style.FILL);

        mPrintTextStroke1Fill = new Paint();
        mPrintTextStroke1Fill.setStrokeWidth(1);
        mPrintTextStroke1Fill.setStyle(Paint.Style.FILL);
        mPrintTextStroke1Fill.setTypeface(Typeface.MONOSPACE);
        // default values
        mPrintTextStroke1Fill.setTextSize(12);
        mPrintTextStroke1Fill.setColor(Color.GRAY);

        // For output of touch coordinates
        mInfoPaint = new Paint();
        mInfoPaint.setTypeface(Typeface.MONOSPACE);
        mInfoPaint.setStyle(Paint.Style.FILL);
        mInfoPaint.setTextSize(TEXT_SIZE_INFO_PAINT);
        mInfoPaint.setColor(Color.BLACK);

        mTextBackgroundStroke1Fill = new Paint();
        mTextBackgroundStroke1Fill.setStrokeWidth(1);
        mTextBackgroundStroke1Fill.setStyle(Paint.Style.FILL);

        // for fillRect, the outline of the form will be filled
        mPaintStroke1Fill = new Paint();
        mPaintStroke1Fill.setStrokeWidth(1);
        mPaintStroke1Fill.setStyle(Paint.Style.FILL);
//        mGraphPaintStroke1Fill.setAntiAlias(true); // with AntiAlias, we get a residual outline at clearing the rectangle

        // For pixel, line and chart. The outline of a form will not be filled
        mPaintStrokeScaleFactorColorSettable = new Paint();
        mPaintStrokeScaleFactorColorSettable.setStyle(Paint.Style.STROKE);
        /*
         * without AntiAlias lines have different thickness depending at their position
         * with AntiAlias, we get a residual outline at clearing the line
         */
        mPaintStrokeScaleFactorColorSettable.setAntiAlias(true);
        // mGraphPaintStrokeFixed.setStrokeCap(Cap.BUTT);

        mPaintStrokeAndColorSettable = new Paint();
        mPaintStrokeAndColorSettable.setStyle(Paint.Style.STROKE);

        mPaintStrokeAndColorSettableAntiAliased = new Paint();
        mPaintStrokeAndColorSettableAntiAliased.setStyle(Paint.Style.STROKE);
        mPaintStrokeAndColorSettableAntiAliased.setAntiAlias(true);


        /*
        For future use
         */
        for (LineInfo tInfo : mDrawLineInfoArray) {
            tInfo = new LineInfo();
            tInfo.mPaint = new Paint();
            tInfo.mPaint.setStyle(Paint.Style.STROKE);
            tInfo.mPaint.setAntiAlias(true);
            tInfo.mPaint.setColor(Color.BLACK); // default setting
            tInfo.Xoffset = 0;
            tInfo.Yoffset = 0;
            tInfo.XScaleFactor = 1;
            tInfo.YScaleFactor = 1;
        }

        /*
         * create start Bitmap
         */
        try {
            Point tDisplaySize = new Point();
            mBlueDisplayContext.getWindowManager().getDefaultDisplay().getSize(tDisplaySize);
            mCurrentCanvasPixelWidth = tDisplaySize.x;
            mCurrentCanvasPixelHeight = tDisplaySize.y;
        } catch (NoSuchMethodError e) {
            mCurrentCanvasPixelWidth = mBlueDisplayContext.getWindowManager().getDefaultDisplay().getWidth();
            mCurrentCanvasPixelHeight = mBlueDisplayContext.getWindowManager().getDefaultDisplay().getHeight();
        }

        /*
         * Let initial bitmap cover only 80 % of display to have space to call the options menu.
         * Nice for devices / android versions without an options button.
         */
        if (mCurrentCanvasPixelWidth > mCurrentCanvasPixelHeight) {
            // Landscape
            mCurrentCanvasPixelHeight = (mCurrentCanvasPixelHeight * 100) / 80;
        } else {
            // Portrait
            mCurrentCanvasPixelWidth = (mCurrentCanvasPixelWidth * 100) / 80;
        }

        mRequestedCanvasWidth = mCurrentCanvasPixelWidth;
        mRequestedCanvasHeight = mCurrentCanvasPixelHeight;

        mBitmap = Bitmap.createBitmap(mCurrentCanvasPixelWidth, mCurrentCanvasPixelHeight, Bitmap.Config.ARGB_8888);
        // mBitmap.setHasAlpha(false);

        mBitmapPaint = new Paint();

        mCanvas = new Canvas(mBitmap);
        mCanvas.drawColor(Color.WHITE); // white background
        initCharMappingArray();

        /*
         * initialize touch event flags
         */
        mTouchStartsOnButtonNumber = new int[MAX_POINTER];
        mTouchStartsOnSliderNumber = new int[MAX_POINTER];
        mLastSentMoveXValue = new int[MAX_POINTER];
        mLastSentMoveYValue = new int[MAX_POINTER];
        mTouchIsActive = new boolean[MAX_POINTER];
        mSkipProcessingUntilTouchUpForButton = new boolean[MAX_POINTER];
        mTouchDownPositionX = new float[MAX_POINTER];
        mTouchDownPositionY = new float[MAX_POINTER];
        mLastTouchPositionX = new float[MAX_POINTER];
        mLastTouchPositionY = new float[MAX_POINTER];
        resetTouchFlags(0);
    }

    @Override
    public void onSizeChanged(int aWidth, int aHeight, int aOldWidth, int aOldHeight) {
        /*
         * Is called on start and on changing orientation from Portrait to Landscape and back
         * but NOT from Landscape to Reverse Landscape!
         */

        /*
         * Correct sizes with system bars insets
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Insets tInsets = null;
            tInsets = mBlueDisplayContext.getWindowManager().getCurrentWindowMetrics().getWindowInsets().getInsets(WindowInsets.Type.systemBars());
            if (MyLog.isDEBUG()) {
                Log.d(LOG_TAG, "systemBarsInsets =" + tInsets); // Only logcat output here
            }
            aWidth -= tInsets.right + tInsets.left;
            aHeight -= tInsets.bottom + tInsets.top;
        }

        mCurrentViewPixelWidth = aWidth;
        mCurrentViewPixelHeight = aHeight;
        if (MyLog.isINFO()) {
            Log.i(LOG_TAG, "++ ON SizeChanged width=" + aWidth + " height=" + aHeight + " old width=" + aOldWidth + " old height=" + aOldHeight); // Only logcat output here
        }

        setMaxScaleFactor();

        // resize canvas
        float tScaleFactor = mScaleFactor;

        // send new max size to client, but only if device is connected (check needed here since we always get an onSizeChanged
        // event at startup)
        if (mSendPendingConnectMessage) {
            mSendPendingConnectMessage = false;
            // Signal connection to Client, now that the mCurrentViewWidth and mCurrentViewHeight are set.
            mBlueDisplayContext.mSerialService.signalBlueDisplayConnection();
            /*
             * Send the event to the UI Activity, which in turn shows the connected toast and sets window to always on
             */
            mBlueDisplayContext.mHandlerForGUIRequests.sendEmptyMessage(BlueDisplay.MESSAGE_USB_CONNECT);
            MyLog.i(LOG_TAG, "onSizeChanged: Send delayed connection build up event");
        } else {
            if (mBlueDisplayContext.mDeviceConnected) {
                mBlueDisplayContext.mSerialService.writeTwoIntegerEventAndTimestamp(SerialService.EVENT_REORIENTATION, aWidth, aHeight);
            }
        }
        // scale and do not send redraw event, since the client generates the redraw event for start and changing orientation
        // by itself in order not to overwrite the small client buffer with the second event.
        setScaleFactor(tScaleFactor, false);
    }

    /**
     * Is called in reaction to invalidate()
     */
    @Override
//    public void onDraw(@NonNull Canvas canvas) { // this give the error : public void onDraw(@NonNull Canvas canvas) {
    public void onDraw(Canvas canvas) {
        if (MyLog.isVERBOSE()) {
            Log.v(LOG_TAG, "+ ON Draw +");
        }
        if (mBlueDisplayContext.mBTSerialSocket != null || mBlueDisplayContext.mUSBSerialSocket != null) {
            int tResult;
            int tSumWaitDelay = 0;
            do {
                tResult = mBlueDisplayContext.mSerialService.searchCommand(this);
                float tCurrentLeftInset = 0;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    tCurrentLeftInset = mBlueDisplayContext.getWindowManager().getCurrentWindowMetrics().getWindowInsets().getInsets(WindowInsets.Type.systemBars()).left;
                }
                canvas.drawBitmap(mBitmap, tCurrentLeftInset, 0, mBitmapPaint); // must be done at every call
                int tBytesInBuffer = mBlueDisplayContext.mSerialService.getBufferBytesAvailable();
                if (tResult == SerialService.RPCVIEW_DO_DRAW_AND_CALL_AGAIN) {
                    // We have more data in buffer, but want to show the bitmap now (between 4 and 20 ms on my Nexus7/6.0.1),
                    // so trigger next call of OnDraw
                    invalidate();
                    if (MyLog.isDEVELOPMENT_TESTING()) {
                        Log.v(LOG_TAG, "Call invalidate() to redraw again. Bytes in buffer=" + tBytesInBuffer);
                    }
                } else {
                    if (tResult == SerialService.RPCVIEW_DO_WAIT) {
                        if (MyLog.isDEVELOPMENT_TESTING()) {
                            Log.v(LOG_TAG, "Wait for 20 ms"); // first wait, to avoid requesting a new trigger every time
                        }
                        try {
                            Thread.sleep(20);
                            tSumWaitDelay += 20;
                            if (tBytesInBuffer == mBlueDisplayContext.mSerialService.getBufferBytesAvailable() && tSumWaitDelay > 1000) {
                                MyLog.e(LOG_TAG, "Read delay > 1000 ms for missing bytes. Maybe android is busy / rendering a lot? Bytes in buffer=" + mBlueDisplayContext.mSerialService.getBufferBytesAvailable());
                                tResult = SerialService.RPCVIEW_DO_NOTHING; // Just request new trigger
                            }
                        } catch (InterruptedException e) {
                            // Just do nothing
                        }
                    }
                    // check again, we may have changed tResult
                    if (tResult != SerialService.RPCVIEW_DO_WAIT) {
                        /*
                         * Request new trigger from BT or USB socket, if new data has arrived
                         */
                        mBlueDisplayContext.mSerialService.mRequireUpdateViewMessage = true;
                        if (MyLog.isDEVELOPMENT_TESTING()) {
                            Log.v(LOG_TAG, "Set mRequireUpdateViewMessage to true. Bytes in buffer=" + mBlueDisplayContext.mSerialService.getBufferBytesAvailable());
                        }
                    }
                }
            } while (tResult == SerialService.RPCVIEW_DO_WAIT);
        } else {
            // safety net...
            canvas.drawBitmap(mBitmap, 0, 0, mBitmapPaint);
        }
    }

    /**
     * Handle touch events:
     * 1. Process event by scale detector
     * 2. Filter out multi touch
     * 3. Handle long touch down recognition
     * 4. Show touch coordinates if locally enabled
     * 5. Detect swipes
     * 6. Send swipe, slider and button callback events
     * 7. Send touch events except if they were outside of canvas or consumed by slider or button or touchUp event for swipe
     * <p>
     * (non-Javadoc)
     *
     * @see android.view.View#onTouchEvent(android.view.MotionEvent)
     */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent aEvent) {
        float tDistanceFromTouchDown;
        boolean tMicroMoveDetected = false; // Needed to disable micro moves canceling long touch down recognition

        int tMaskedAction = aEvent.getActionMasked();

        if (MyLog.isVERBOSE()) {
            MyLog.v(LOG_TAG, "TouchEvent action=" + tMaskedAction);
        }

        int tActionIndex = aEvent.getActionIndex();
        int tPointerCount = aEvent.getPointerCount();

        /*
         * Check which pointer changed on move
         */
        if (tMaskedAction == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < tPointerCount && i < MAX_POINTER; i++) {
                if (mLastTouchPositionX[i] != aEvent.getX(i) || mLastTouchPositionY[i] != aEvent.getY(i)) {
                    mLastTouchPositionX[i] = aEvent.getX(i);
                    mLastTouchPositionY[i] = aEvent.getY(i);
                    // Found new action index
                    tActionIndex = i;
                    break;
                }
            }
        }
        float tCurrentX = aEvent.getX(tActionIndex);
        float tCurrentY = aEvent.getY(tActionIndex);
        int tCurrentXScaled = (int) ((tCurrentX / mScaleFactor) + 0.5);
        int tCurrentYScaled = (int) ((tCurrentY / mScaleFactor) + 0.5);

        if (mTouchStartsOnButtonNumber[0] < 0 && mTouchStartsOnSliderNumber[0] < 0 && mTouchStartsOnButtonNumber[tActionIndex] < 0 && mTouchStartsOnSliderNumber[tActionIndex] < 0) {
            // process event by scale detector if event is started on an empty (no button or slider) space
            mScaleDetector.onTouchEvent(aEvent);
        }

        if (mShowTouchCoordinates) {
            int tXPos = (int) (tCurrentX + 0.5);
            int tYPos = (int) (tCurrentY + 0.5);

            mTextBackgroundStroke1Fill.setColor(Color.WHITE);
            mCanvas.drawRect(0, 0, TEXT_WIDTH_INFO_PAINT * mShowTouchCoordinatesLastStringLength, TEXT_SIZE_INFO_PAINT + 2, mTextBackgroundStroke1Fill);
            String tInfoString = tActionIndex + "|" + tMaskedAction + "  " + tXPos + "/" + tYPos + "->" + tCurrentXScaled + "/" + tCurrentYScaled;
            mShowTouchCoordinatesLastStringLength = tInfoString.length();
            mCanvas.drawText(tInfoString, 0, 20, mInfoPaint);
            invalidate(); // To show the new coordinates
        }

        if (tActionIndex > 0) {
            // convert pointer actions to plain actions for ACTION_POINTER_DOWN + ACTION_POINTER_UP
            if (tMaskedAction == MotionEvent.ACTION_POINTER_DOWN) {
                tMaskedAction = MotionEvent.ACTION_DOWN;
            } else if (tMaskedAction == MotionEvent.ACTION_POINTER_UP) {
                tMaskedAction = MotionEvent.ACTION_UP;
            }
        }

        switch (tMaskedAction) {
            case MotionEvent.ACTION_DOWN:
                mTouchIsActive[tActionIndex] = true;
                mTouchDownPositionX[tActionIndex] = tCurrentX;
                mTouchDownPositionY[tActionIndex] = tCurrentY;
                // mMultiTouchDetected = false;
                break;

            case MotionEvent.ACTION_MOVE:
                tDistanceFromTouchDown = Math.max(Math.abs(mTouchDownPositionX[tActionIndex] - tCurrentX), Math.abs(mTouchDownPositionY[tActionIndex] - tCurrentY));
                // Do not accept pseudo or micro moves as an event to disable long touch down recognition
                if (tDistanceFromTouchDown < mCurrentViewPixelWidth / 100.0) { // (mCurrentViewWidth / 100) -> around 10 to 20 pixel.
                    tMicroMoveDetected = true;
                }
                if (mSkipProcessingUntilTouchUpForButton[tActionIndex]) {
                    // do not process ACTION_MOVE for buttons
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
            default:
                if (mSkipProcessingUntilTouchUpForButton[tActionIndex]) {
                    // Finish processing here
                    resetTouchFlags(tActionIndex);
                    return true;
                }
                break;
        }

        /*
         * Cancel long touch recognition, but not on micro moves.
         */
        if (mLongTouchMessageWasSent && !tMicroMoveDetected && mLongTouchPointerIndex == tActionIndex) {
            // reset long touch recognition by clearing messages
            mLongTouchDownHandler.removeMessages(LONG_TOUCH_DOWN);
            mLongTouchMessageWasSent = false;
        }

        /*
         * Check if event is outside CANVAS, then open option menu
         */
        if (tCurrentX > mCurrentCanvasPixelWidth || tCurrentY > mCurrentCanvasPixelHeight) {
            // open options menu if touch outside of canvas
            if (tMaskedAction == MotionEvent.ACTION_UP) {
                mBlueDisplayContext.openOptionsMenu();
            }
        } else {
            if (mBlueDisplayContext.mBTSerialSocket != null) {
                if (mBlueDisplayContext.mBTSerialSocket.getState() == BluetoothSerialSocket.STATE_NONE && !mDeviceListActivityLaunched) {
                    /*
                     * Launch the DeviceListActivity to choose device if state is "not connected"
                     */
                    mBlueDisplayContext.launchDeviceListActivity();
                    mDeviceListActivityLaunched = true;
                }
            }
            if ((mBlueDisplayContext.mUSBDeviceAttached && mBlueDisplayContext.mUSBSerialSocket.mIsConnected) || (mBlueDisplayContext.mBTSerialSocket != null && mBlueDisplayContext.mBTSerialSocket.getState() == BluetoothSerialSocket.STATE_CONNECTED)) {
                mDeviceListActivityLaunched = false;
                /*
                 * Send EVENT data since we are connected
                 */

                if (tMaskedAction == MotionEvent.ACTION_UP && mTouchStartsOnSliderNumber[tActionIndex] < 0) {
                    /*
                     * Check SWIPE callback only if touch did not start on a slider
                     */
                    float tDeltaX = tCurrentX - mTouchDownPositionX[tActionIndex];
                    float tDeltaY = tCurrentY - mTouchDownPositionY[tActionIndex];
                    tDistanceFromTouchDown = Math.max(Math.abs(tDeltaX), Math.abs(tDeltaY));
                    /*
                     * SWIPE only if move is greater than SWIPE_DETECTION_LOWER_LIMIT. Otherwise touches are often interpreted as
                     * micro-swipes. If SWIPE starts on a button then swipe must be even greater. (mCurrentViewWidth / 100) ->
                     * around 10 to 20 pixel.
                     */
                    if ((mTouchStartsOnButtonNumber[tActionIndex] < 0 && tDistanceFromTouchDown > mCurrentViewPixelWidth / 100.0) || tDistanceFromTouchDown > (mCurrentViewPixelWidth / 25.0)) {
                        int tDeltaXScaled = (int) (tDeltaX / mScaleFactor);
                        int tDeltaYScaled = (int) (tDeltaY / mScaleFactor);
                        int tIsXDirection = 0;
                        if (Math.abs(tDeltaXScaled) >= Math.abs(tDeltaYScaled)) {
                            // horizontal swipe
                            tIsXDirection = 1;
                        }

                        /*
                         * If swipe starts at the left display border and is bigger than 1/10 of display size then open settings
                         * menu. On my Nexus I have the problem, that slides from left are not reliable so I narrowed the valued
                         * needed for successful swipe from left border.
                         */
                        if (MyLog.isDEBUG()) {
                            MyLog.d(LOG_TAG, "Swipe: Fullscreen=" + (mCurrentViewPixelWidth == mCurrentCanvasPixelWidth) + " StartX=" + mTouchDownPositionX[tActionIndex] + " DeltaX=" + tDeltaX + " DeltaY=" + tDeltaY);
                        }
                        if (mTouchDownPositionX[tActionIndex] < mCurrentViewPixelWidth / 100.0 && tDeltaX > mCurrentViewPixelWidth / 50.0 && tDeltaX > (5 * Math.abs(tDeltaYScaled))) {
                            if (MyLog.isINFO()) {
                                Log.i(LOG_TAG, "Swipe from left border detected -> display option menu");
                            }
                            mBlueDisplayContext.openOptionsMenu();

                        } else {
                            // send swipe event and suppress UP-event sending
                            mBlueDisplayContext.mSerialService.writeSwipeCallbackEvent(SerialService.EVENT_SWIPE_CALLBACK, tIsXDirection, (int) (mTouchDownPositionX[tActionIndex] / mScaleFactor), (int) (mTouchDownPositionY[tActionIndex] / mScaleFactor), tDeltaXScaled, tDeltaYScaled);
                        }
                        // (ab)use mTouchStartsOnSliderNumber flag to suppress other checks on ACTION_UP and sending of UP-event
                        mTouchStartsOnSliderNumber[tActionIndex] = 999;
                    }
                }

                /*
                 * Check SLIDERS if ACTION_DOWN
                 */
                if (tMaskedAction == MotionEvent.ACTION_DOWN) {
                    int tSliderNumber = TouchSlider.checkAllSliders(tCurrentXScaled, tCurrentYScaled);
                    if (tSliderNumber >= 0) {
                        mTouchStartsOnSliderNumber[tActionIndex] = tSliderNumber;
                        invalidate(); // Show new local slider bar value
                    }
                } else {
                    /*
                     * Check SLIDER if ACTION_MOVE
                     */
                    if (tMaskedAction == MotionEvent.ACTION_MOVE && mTouchStartsOnSliderNumber[tActionIndex] >= 0) {
                        if (TouchSlider.checkIfTouchInSliderNumber(tCurrentXScaled, tCurrentYScaled, mTouchStartsOnSliderNumber[tActionIndex])) {
                            invalidate(); // Show new local slider bar value
                        }
                    }
                }

                /*
                 * Check BUTTONS if no slider active AND (ACTION_DOWN OR if mUseUpEventForButtons AND ACTION_UP and no swipe
                 * detected / mDisableButtonUpOnce)
                 */

                if (mTouchStartsOnSliderNumber[tActionIndex] < 0) {
                    if ((tMaskedAction == MotionEvent.ACTION_DOWN && !mUseUpEventForButtons) || (tMaskedAction == MotionEvent.ACTION_UP && mUseUpEventForButtons && !mDisableButtonUpOnce)) {
                        mTouchStartsOnButtonNumber[tActionIndex] = TouchButton.checkAllButtons(tCurrentXScaled, tCurrentYScaled, false);
                        if (mTouchStartsOnButtonNumber[tActionIndex] >= 0 && tMaskedAction == MotionEvent.ACTION_DOWN) {
                            // remember that we send an event on touch down and to skip processing until touch up
                            mSkipProcessingUntilTouchUpForButton[tActionIndex] = true;
                        }
                    } else if (tMaskedAction == MotionEvent.ACTION_DOWN) {
                        // Just check if down touch hits a button area
                        mTouchStartsOnButtonNumber[tActionIndex] = TouchButton.checkAllButtons(tCurrentXScaled, tCurrentYScaled, true);
                    }
                }
                /*
                 * check for LONG TOUCH initialization
                 */
                if (tMaskedAction == MotionEvent.ACTION_DOWN && mIsLongTouchEnabled && mTouchStartsOnButtonNumber[tActionIndex] < 0 && mTouchStartsOnSliderNumber[tActionIndex] < 0 && !mLongTouchMessageWasSent && !mLongTouchEventWasSent) {
                    /*
                     * Send delayed message to handler, witch in turn sends the callback. If another event happens before the delay,
                     * the message will be simply deleted. Cannot use aEvent.getDownTime(), since it gives time of first touch for
                     * multitouch.
                     */
                    mLongTouchDownHandler.sendEmptyMessageAtTime(LONG_TOUCH_DOWN, SystemClock.uptimeMillis() + mLongTouchDownTimeoutMillis);
                    mLongTouchMessageWasSent = true;
                    mLongTouchPointerIndex = tActionIndex;
                }

                /*
                 * Evaluate local and global flags before sending basic events
                 */
                if (mTouchStartsOnButtonNumber[tActionIndex] < 0 && mTouchStartsOnSliderNumber[tActionIndex] < 0 && mTouchBasicEnable && (mTouchMoveEnable || tMaskedAction != MotionEvent.ACTION_MOVE)) {
                    // suppress sending of zero moves
                    if (tMaskedAction == MotionEvent.ACTION_MOVE) {
                        if (mLastSentMoveXValue[tActionIndex] != tCurrentXScaled || mLastSentMoveYValue[tActionIndex] != tCurrentYScaled) {
                            mLastSentMoveXValue[tActionIndex] = tCurrentXScaled;
                            mLastSentMoveYValue[tActionIndex] = tCurrentYScaled;
                            mBlueDisplayContext.mSerialService.writeTwoIntegerAndAByteEvent(tMaskedAction, tCurrentXScaled, tCurrentYScaled, tActionIndex);
                        }
                    } else {
                        // no button/slider touched and touch is enabled -> send touch event to client
                        mBlueDisplayContext.mSerialService.writeTwoIntegerAndAByteEvent(tMaskedAction, tCurrentXScaled, tCurrentYScaled, tActionIndex);
                    }
                }

                /*
                 * Cleanup for ACTION_UP + ACTION_CANCEL
                 */
                if (tMaskedAction == MotionEvent.ACTION_UP || tMaskedAction == MotionEvent.ACTION_CANCEL) {
                    resetTouchFlags(tActionIndex);
                }
            } else {
                /*
                 * We are not connected here -> do swipe detection for OptionsMenu
                 *
                 * If swipe starts at the left display border and is bigger than 1/10 of display size then open settings menu. On my
                 * Nexus I have the problem, that slides from left are not reliable so I narrowed the valued needed for successful
                 * swipe from left border.
                 */
                float tDeltaX = tCurrentX - mTouchDownPositionX[tActionIndex];
                float tDeltaY = tCurrentY - mTouchDownPositionY[tActionIndex];
                if (MyLog.isDEBUG()) {
                    MyLog.d(LOG_TAG, "Swipe while not connected: Fullscreen=" + (mCurrentViewPixelWidth == mCurrentCanvasPixelWidth) + " StartX=" + mTouchDownPositionX[tActionIndex] + " DeltaX=" + tDeltaX + " DeltaY=" + tDeltaY);
                }
                if (mTouchDownPositionX[tActionIndex] < mCurrentViewPixelWidth / 100.0 && tDeltaX > mCurrentViewPixelWidth / 50.0 && tDeltaX > (5 * Math.abs(tDeltaY))) {
                    if (MyLog.isINFO()) {
                        Log.i(LOG_TAG, "Swipe from left border detected -> display option menu");
                    }
                    mBlueDisplayContext.openOptionsMenu();
                }

            }
        }
        return true;
    }

    /*
     * Reset touch related flags. called for ACTION_UP or ACTION_CANCEL
     */
    private void resetTouchFlags(int aActionIndex) {
        mTouchStartsOnButtonNumber[aActionIndex] = -1;
        mTouchStartsOnSliderNumber[aActionIndex] = -1;
        mTouchIsActive[aActionIndex] = false;
        mSkipProcessingUntilTouchUpForButton[aActionIndex] = false;

        if (aActionIndex == 0) {
            /*
             * Last up action. Reset all.
             */
            Arrays.fill(mTouchStartsOnButtonNumber, -1);
            Arrays.fill(mTouchStartsOnSliderNumber, -1);

            for (int i = 0; i < mLastSentMoveXValue.length; i++) {
                mLastSentMoveXValue[aActionIndex] = -1;
            }
            for (int i = 0; i < mLastSentMoveYValue.length; i++) {
                mLastSentMoveYValue[aActionIndex] = -1;
            }

            Arrays.fill(mTouchIsActive, false);
            for (int i = 0; i < mSkipProcessingUntilTouchUpForButton.length; i++) {
                mSkipProcessingUntilTouchUpForButton[aActionIndex] = false;
            }

            mLongTouchEventWasSent = false;
            mDisableButtonUpOnce = false;
        }

        // Cancel long touch. Duplicated here, since routine is also called in switch statement above, which in turn returns
        // directly.
        if (mLongTouchMessageWasSent && (mLongTouchPointerIndex == aActionIndex || aActionIndex == 0)) {
            // reset long touch recognition by clearing messages
            mLongTouchDownHandler.removeMessages(LONG_TOUCH_DOWN);
            mLongTouchMessageWasSent = false;
        }
    }

    private final Handler mLongTouchDownHandler = new Handler(Looper.myLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == LONG_TOUCH_DOWN) {
                mDisableButtonUpOnce = true;
                mBlueDisplayContext.mSerialService.writeTwoIntegerEvent(SerialService.EVENT_LONG_TOUCH_DOWN_CALLBACK, (int) (mTouchDownPositionX[mLongTouchPointerIndex] / mScaleFactor + 0.5), (int) (mTouchDownPositionY[mLongTouchPointerIndex] / mScaleFactor + 0.5));
                mLongTouchEventWasSent = true;
            } else {
                Log.e(LOG_TAG, "Unknown message " + msg);
            }
        }
    };

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            if (mTouchScaleFactor < 1) {
                /*
                 * Let mTouchScaleFactor be below 1 if it is still below e.g. from setting of onSizeChanged() on rotating screen.
                 */
                return true;
            }
            float tTempScaleFactor = detector.getScaleFactor();
            // reduce sensitivity by factor 3
            tTempScaleFactor -= 1.0F;
            tTempScaleFactor /= 3;
            tTempScaleFactor += 1.0F;

            // clip mTouchScaleFactor to 1
            mTouchScaleFactor *= tTempScaleFactor;
            if (mTouchScaleFactor < 1) {
                mTouchScaleFactor = 1;
            } else if (mTouchScaleFactor > mMaxScaleFactor) {
                mTouchScaleFactor = mMaxScaleFactor;
            }

            if (MyLog.isVERBOSE()) {
                MyLog.v(LOG_TAG, "TouchScaleFactor=" + mTouchScaleFactor + " detector factor=" + tTempScaleFactor + " MaxScaleFactor=" + mMaxScaleFactor);
            }

            // snap to 0.05, 5%
            float tScaleFactorSnapped = mTouchScaleFactor;
            tScaleFactorSnapped *= 20;
            tScaleFactorSnapped = Math.round(tScaleFactorSnapped);
            tScaleFactorSnapped /= 20;

            // save and restore mTouchScaleFactor since setScaleFactor will
            // overwrite it with tScaleFactor
            tTempScaleFactor = mTouchScaleFactor;
            boolean tRetvalue = !setScaleFactor(tScaleFactorSnapped, true);
            mTouchScaleFactor = tTempScaleFactor;
            // return true if event was handled
            return tRetvalue;
        }
    }

    /*
     * For future use?
     */
    @SuppressWarnings("unused")
    private static class GestureListener extends SimpleOnGestureListener {

        @Override
        public boolean onDown(MotionEvent e) {
            if (MyLog.isINFO()) {
                Log.i(LOG_TAG, "onDown");
            }
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (MyLog.isINFO()) {
                Log.i(LOG_TAG, "onScroll distanceX=" + distanceX + " Y=" + distanceY);
            }
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            if (MyLog.isINFO()) {
                Log.i(LOG_TAG, "onLongPress");
            }
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            if (MyLog.isINFO()) {
                Log.i(LOG_TAG, "onSingleTapUp");
            }
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if (MyLog.isINFO()) {
                Log.i(LOG_TAG, "onSingleTapConfirmed");
            }
            // true if the event is consumed, else false
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (MyLog.isINFO()) {
                Log.i(LOG_TAG, "onDoubleTap");
            }
            // true if the event is consumed, else false
            return true;
        }

    }

    /*
     *  To be used for ASCII values between 0x80 and 0xFF to be mapped (aka codepage)
     *  Only entries != 0x0000 are used, otherwise the local codepage is taken
     */
    private char[] mCharMappingArray = new char[128];

    public void myConvertChars(byte[] aInputData, char[] aOutputChars, int aDataLength) {
        for (int i = 0; i < aDataLength; i++) {
            aOutputChars[i] = myConvertChar(aInputData[i]);
        }
    }

    private char myConvertChar(byte aData) {
        char tChar;
        // we have signed arithmetic here, so 0x80 is negative
        if (aData > 0) {
            tChar = (char) aData;
        } else {
            // mask highest bit
            int tHighChar = (aData + 0x80);
            // get mapping
            tChar = mCharMappingArray[tHighChar & 0x7F];
            if (tChar == 0x0000) {
                // no mapping found, use local codepage
                tChar = (char) (tHighChar + 0x80);
            }
        }
        return tChar;
    }

    /**
     * Creates a new canvas with new scale factor. Clips scale factor to value between 1 and maximum possible scale factor
     *
     * @return true if canvas size was changed
     */
    public boolean setScaleFactor(float aScaleFactor, boolean aSendToClient) {

        float tOldFactor = mScaleFactor;
        if (mUseMaxSize) { // true after reset
            aScaleFactor = mMaxScaleFactor;
            mScaleFactor = mMaxScaleFactor;
        } else if (aScaleFactor <= 1) {
            mScaleFactor = 1;
        } else {
            /*
             * Use maximum possible size if scale factor is too big (mUseMaxSize == true)
             */
            mScaleFactor = Math.min(aScaleFactor, mMaxScaleFactor);
        }
        if (tOldFactor != mScaleFactor) {
            mCurrentCanvasPixelWidth = (int) (mRequestedCanvasWidth * mScaleFactor);
            mCurrentCanvasPixelHeight = (int) (mRequestedCanvasHeight * mScaleFactor);

            Bitmap tOldBitmap = mBitmap;
            mBitmap = Bitmap.createScaledBitmap(mBitmap, mCurrentCanvasPixelWidth, mCurrentCanvasPixelHeight, false);
            mCanvas = new Canvas(mBitmap);
            tOldBitmap.recycle();

            mTouchScaleFactor = mScaleFactor;
            mPaintStrokeScaleFactorColorSettable.setStrokeWidth(mScaleFactor);

            if (MyLog.isINFO()) {
                MyLog.i(LOG_TAG, "setScaleFactor(" + aScaleFactor + ") UseMaxSize=" + mUseMaxSize + " old factor=" + tOldFactor + " resulting factor=" + mScaleFactor);
            }
            invalidate(); // Show resized bitmap

            // send new size to client
            if (mBlueDisplayContext.mSerialService != null && aSendToClient) {
                mBlueDisplayContext.mSerialService.writeTwoIntegerEvent(SerialService.EVENT_REDRAW, mCurrentCanvasPixelWidth, mCurrentCanvasPixelHeight);
            }
            // show new Values
            if (mBlueDisplayContext.mMyToast != null) {
                mBlueDisplayContext.mMyToast.cancel();
            }
            mBlueDisplayContext.mMyToast = Toast.makeText(mBlueDisplayContext, String.format("Scale factor=%5.1f%% ", (mScaleFactor * 100)) + " -> " + mCurrentCanvasPixelWidth + "*" + mCurrentCanvasPixelHeight, Toast.LENGTH_SHORT);
            mBlueDisplayContext.mMyToast.show();
            return true;
        }

        if (MyLog.isINFO()) {
            MyLog.i(LOG_TAG, "setScaleFactor(" + aScaleFactor + ") resulting factor=" + mScaleFactor + " not changed");
        }
        return false;
    }

    // 5 red | 6 green | 5 blue
    public static int shortToLongColor(int aShortColor) {
        int tBlue = (aShortColor & 0x1F) << 3;
        if (tBlue > 0x80) {
            // to get real 0xFF
            tBlue += 0x07;
        }
        int tGreen = (aShortColor & 0x07E0) << 5;
        if (tGreen > 0x8000) {
            // to get real 0xFF
            tGreen += 0x0300;
        }
        int tRed = aShortColor & 0xF800;
        if (tRed > 0x8000) {
            // to get real 0xFF
            tRed += 0x0700;
        }
        tRed = tRed << 8;
        return (tRed | tGreen | tBlue | 0xFF000000);
    }

    public static String shortToColorString(int aShortColor) {
        int tBlue = (aShortColor & 0x1F) << 3;
        if (tBlue > 0x80) {
            // to get real 0xFF
            tBlue += 0x07;
        }
        int tGreen = (aShortColor & 0x07E0) >> 3;
        if (tGreen > 0x80) {
            // to get real 0xFF
            tGreen += 0x03;
        }
        int tRed = (aShortColor & 0xF800) >> 8;
        if (tRed > 0x80) {
            // to get real 0xFF
            tRed += 0x07;
        }
        return "R:" + tRed + " G:" + tGreen + " B:" + tBlue;
    }

    public static int convertByteToInt(byte aByte) {
        if (aByte < 0) {
            return aByte + 256;
        }
        return aByte;
    }

    int printNewline() {
        int tPrintY = mTextPrintTextCurrentPosY + mTextPrintTextSize + 1; // for space between lines otherwise we see "g" truncated
        if (tPrintY >= mRequestedCanvasHeight) {
            // wrap around to top of screen
            tPrintY = 0;
            if (mTextPrintDoClearScreenOnWrap) {
                mCanvas.drawColor(mTextExpandedPrintBackgroundColor);
            }
        }
        mTextPrintTextCurrentPosX = 0;
        return tPrintY;
    }

    public void interpretCommand(int aCommand, int[] aParameters, int aParamsLength, byte[] aDataBytes, int[] aDataInts, int aDataLength) {

        if (MyLog.isVERBOSE()) {
            StringBuilder tParam = new StringBuilder("cmd=0x" + Integer.toHexString(aCommand) + " / " + aCommand);
            for (int i = 0; i < aParamsLength; i++) {
                if (aParameters[i] > 1023 || aParameters[i] < 0) {
                    tParam.append(" p").append(i).append("=0x").append(Integer.toHexString(aParameters[i] & 0xFFFF));
                } else {
                    tParam.append(" p").append(i).append("=").append(aParameters[i]);
                }
            }
            tParam.append(" data length=").append(aDataLength);
            MyLog.v(LOG_TAG, tParam.toString());
        }

        // Disable message, which triggers the toast, that no data was received.
        resetWaitMessage();

        if ((aCommand >= INDEX_FIRST_FUNCTION_BUTTON && aCommand <= INDEX_LAST_FUNCTION_BUTTON) || aCommand >= INDEX_FIRST_FUNCTION_BUTTON_WITH_DATA && aCommand <= INDEX_LAST_FUNCTION_BUTTON_WITH_DATA) {
            TouchButton.interpretCommand(this, aCommand, aParameters, aParamsLength, aDataBytes, aDataLength);
            return;
        } else if ((aCommand >= INDEX_FIRST_FUNCTION_SLIDER && aCommand <= INDEX_LAST_FUNCTION_SLIDER) || aCommand >= INDEX_FIRST_FUNCTION_SLIDER_WITH_DATA && aCommand <= INDEX_LAST_FUNCTION_SLIDER_WITH_DATA) {
            TouchSlider.interpretCommand(this, aCommand, aParameters, aParamsLength, aDataBytes, aDataLength);
            return;
        }

        Paint tResultingPaint;

        float tXStartScaled = 0;
        float tYStartScaled = 0;
        float tXEndScaled;
        float tYEndScaled;
        if (aParamsLength >= 2) {
            tXStartScaled = aParameters[0] * mScaleFactor;
            tYStartScaled = aParameters[1] * mScaleFactor;
        }
        float tScaledTextSize;
        int tColor;
        int tIndex;
        String tFunctionName;
        String tAdditionalInfo = "";
        String tStringParameter = "";
        String tCallbackAddressStringAdjustedForClientDebugging = "";

        float tDescend;
        float tAscend;

        int tSubcommand;
        int tCallbackAddress;

        try {
            switch (aCommand) {
                case FUNCTION_PLAY_TONE:
                    int tToneIndex = ToneGenerator.TONE_CDMA_KEYPAD_VOLUME_KEY_LITE;
                    int tDurationMillis = -1;
                    ToneGenerator tToneGenerator = mToneGenerator;
                    if (aParamsLength > 0) {
                        if (aParameters[0] > 0 && aParameters[0] <= ToneGenerator.TONE_CDMA_SIGNAL_OFF) {
                            tToneIndex = aParameters[0];
                        }
                        if (aParamsLength > 1) {
                            /*
                             * set duration in ms
                             */
                            tDurationMillis = aParameters[1];
                            // Only duration -1 means forever, -2 gives 65534
                            if (tDurationMillis < -1) {
                                tDurationMillis = 0x10000 + tDurationMillis;
                            }
                            if (aParamsLength > 2) {
                                /*
                                 * set volume to absolute values between 0% and 100%
                                 */
                                int tVolume = aParameters[2];
                                if (tVolume > ToneGenerator.MAX_VOLUME) {
                                    tVolume = ToneGenerator.MAX_VOLUME;
                                }
                                if (tVolume >= 0) {
                                    tToneGenerator = mToneGeneratorForAbsoluteVolumes;
                                    if (tVolume != mLastRequestedToneVolume) {
                                        /*
                                         * change absolute value of volume
                                         */
                                        mLastRequestedToneVolume = tVolume;
                                        mToneGeneratorForAbsoluteVolumes = new ToneGenerator(AudioManager.STREAM_SYSTEM, tVolume);
                                    }
                                }
                            }
                        }
                    }
                    /*
                     * check if user changed volume
                     */
                    int tCurrentSystemVolume = mBlueDisplayContext.mAudioManager.getStreamVolume(AudioManager.STREAM_SYSTEM);
                    if (mLastSystemVolume != tCurrentSystemVolume) {
                        mLastSystemVolume = tCurrentSystemVolume;
                        mToneGenerator = new ToneGenerator(AudioManager.STREAM_SYSTEM, (tCurrentSystemVolume * ToneGenerator.MAX_VOLUME) / mBlueDisplayContext.mMaxSystemVolume);
                    }

                    tToneGenerator.startTone(tToneIndex, tDurationMillis);
                    if (MyLog.isINFO()) {
                        MyLog.i(LOG_TAG, "Play tone index=" + tToneIndex + " duration=" + tDurationMillis);
                    }
                    break;

                case FUNCTION_SPEAK_STRING_FLUSH:
                case FUNCTION_SPEAK_STRING_ADD:
                    myConvertChars(aDataBytes, sCharsArray, aDataLength);
                    tStringParameter = new String(sCharsArray, 0, aDataLength);
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                        if (MyLog.isINFO()) {
                            MyLog.i(LOG_TAG, "talkString \"" + tStringParameter + "\" - not available for Android version " + Build.VERSION.RELEASE + " < 5.0 (Lollipop)");
                            mBlueDisplayContext.mSerialService.writeOneIntegerEvent(SerialService.EVENT_SPEAKING_DONE, SerialService.EVENT_SPEAKING_NOT_AVAILABLE);
                        }
                        return;
                    }
                    if (!mTextToSpeechIsInitialized) {
                        MyLog.e(LOG_TAG, "TextToSpeech engine \"com.google.android.tts\" not available. String=" + tStringParameter);
                        mBlueDisplayContext.mSerialService.writeOneIntegerEvent(SerialService.EVENT_SPEAKING_DONE, SerialService.EVENT_SPEAKING_NOT_AVAILABLE);
                        return;
                    }

                    if (MyLog.isINFO()) {
                        MyLog.i(LOG_TAG, "speakString \"" + tStringParameter + "\"");
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        int tQueueMode = TextToSpeech.QUEUE_FLUSH;
                        if (aCommand == FUNCTION_SPEAK_STRING_ADD) {
                            tQueueMode = TextToSpeech.QUEUE_ADD;
                        }
                        if (mTextToSpeech.speak(tStringParameter, tQueueMode, null, "BlueDisplaySpeak") != SUCCESS) {
                            mBlueDisplayContext.mSerialService.writeOneIntegerEvent(SerialService.EVENT_SPEAKING_DONE, SerialService.EVENT_SPEAKING_ERROR);
                        }
                    }
                    break;

                case FUNCTION_SPEAK_SET_LOCALE:
                    myConvertChars(aDataBytes, sCharsArray, aDataLength);
                    tStringParameter = new String(sCharsArray, 0, aDataLength);
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                        if (MyLog.isINFO()) {
                            MyLog.i(LOG_TAG, "speakSetLocale: \"" + tStringParameter + "\" - not available for Android version " + Build.VERSION.RELEASE + " < 5.0 (Lollipop)");
                        }
                        return;
                    } else {
                        Locale tLocale = Locale.forLanguageTag(tStringParameter);
                        if (mTextToSpeech.isLanguageAvailable(tLocale) == LANG_NOT_SUPPORTED) {
                            MyLog.w(LOG_TAG, "speakSetLocale: the locale \"" + tStringParameter + "\" is not supported");
                        } else {
                            mTextToSpeech.setLanguage(tLocale);
                            if (MyLog.isINFO()) {
                                MyLog.i(LOG_TAG, "Set Locale to \"" + tStringParameter + "\", Voice is \"" + mTextToSpeech.getVoice().getName() + "\"");
                            }
                        }
                    }
                    break;

                /*
                 * One of the Voice strings printed in log at level Info at BD application startup
                 */
                case FUNCTION_SPEAK_SET_VOICE:
                    myConvertChars(aDataBytes, sCharsArray, aDataLength);
                    tStringParameter = new String(sCharsArray, 0, aDataLength);
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                        if (MyLog.isINFO()) {
                            MyLog.i(LOG_TAG, "speakSetVoice \"" + tStringParameter + "\" - not available for Android version " + Build.VERSION.RELEASE + " < 5.0 (Lollipop)");
                        }
                        return;
                    } else {
                        Set<Voice> tVoicesSet = mTextToSpeech.getVoices();
                        boolean tFoundVoice = false;
                        for (Voice tVoice : tVoicesSet) {
                            if (tVoice.getName().equals(tStringParameter)) {
                                mTextToSpeech.setVoice(tVoice);
                                tFoundVoice = true;
                                if (MyLog.isINFO()) {
                                    MyLog.i(LOG_TAG, "Set voice to \"" + tStringParameter + "\"");
                                }
                                break;
                            }
                        }
                        if (!tFoundVoice) {
                            MyLog.w(LOG_TAG, "Voice \"" + tStringParameter + "\" not found");
                        }
                    }
                    break;


                case FUNCTION_GET_NUMBER:
                case FUNCTION_GET_NUMBER_WITH_SHORT_PROMPT:
                case FUNCTION_GET_TEXT:
                case FUNCTION_GET_TEXT_WITH_SHORT_PROMPT:

                    boolean tDoNumber = true;
                    tFunctionName = "number";

                    if (aCommand == FUNCTION_GET_TEXT || aCommand == FUNCTION_GET_TEXT_WITH_SHORT_PROMPT) {
                        tFunctionName = "text";
                        tDoNumber = false;
                    }
                    String tInitialInfo = "";
                    // Integer.MIN_VALUE is used as flag not to show value
                    float tInitialValue = NUMBER_INITIAL_VALUE_DO_NOT_SHOW;
                    tCallbackAddress = aParameters[0] & 0x0000FFFF;
                    if (aParamsLength == 2 || aParamsLength == 4) {
                        // 32 bit callback address
                        tCallbackAddress = tCallbackAddress | (aParameters[1] << 16);
                    } else {
                        tCallbackAddressStringAdjustedForClientDebugging = "/0x" + Integer.toHexString(tCallbackAddress << 1);
                    }

                    if (aParamsLength > 2) {
                        int ValueStartIndex = 1;
                        if (aParamsLength == 4) {
                            // 32 bit callback address + initial value
                            ValueStartIndex = 2;
                        }
                        // With initial value
                        int tIntValue = (aParameters[ValueStartIndex] & 0x0000FFFF) | (aParameters[ValueStartIndex + 1] << 16);
                        tInitialValue = Float.intBitsToFloat(tIntValue);
                        tInitialInfo = " initial value=" + tInitialValue;
                    }

                    if (aDataLength > 0) {
                        myConvertChars(aDataBytes, sCharsArray, aDataLength);
                        tStringParameter = new String(sCharsArray, 0, aDataLength);
                    }
                    if (MyLog.isINFO()) {
                        MyLog.i(LOG_TAG, "Get " + tFunctionName + " callback=0x" + Integer.toHexString(tCallbackAddress) + tCallbackAddressStringAdjustedForClientDebugging + " prompt=\"" + tStringParameter + "\"" + tInitialInfo);
                    }

                    /*
                     * Send request for number input to the UI Activity Ends up in showInputDialog() If cancelled nothing is sent back
                     */
                    Message msg = mHandler.obtainMessage(BlueDisplay.REQUEST_INPUT_DATA);
                    Bundle bundle = new Bundle();
                    bundle.putInt(BlueDisplay.CALLBACK_ADDRESS, tCallbackAddress);
                    bundle.putString(BlueDisplay.DIALOG_PROMPT, tStringParameter);
                    bundle.putFloat(BlueDisplay.NUMBER_INITIAL_VALUE, tInitialValue);
                    bundle.putBoolean(BlueDisplay.NUMBER_FLAG, tDoNumber);
                    msg.setData(bundle);
                    mHandler.sendMessage(msg);
                    break;

                case FUNCTION_REQUEST_MAX_CANVAS_SIZE:
                    if (MyLog.isINFO()) {
                        MyLog.i(LOG_TAG, "Request max canvas size. Result=" + mCurrentViewPixelWidth + "/" + mCurrentViewPixelHeight);
                    }
                    if (mBlueDisplayContext.mSerialService != null) {
                        mBlueDisplayContext.mSerialService.writeTwoIntegerEventAndTimestamp(SerialService.EVENT_REQUESTED_DATA_CANVAS_SIZE, mCurrentViewPixelWidth, mCurrentViewPixelHeight);
                    }
                    break;

                case FUNCTION_GET_INFO:
                    // get timestamps
                    // For future use
                    tSubcommand = aParameters[0];
                    tCallbackAddress = aParameters[1] & 0x0000FFFF;
                    if (aParamsLength == 3) {
                        // 32 bit callback address
                        tCallbackAddress = tCallbackAddress | (aParameters[2] << 16);
                    } else {
                        tCallbackAddressStringAdjustedForClientDebugging = "/0x" + Integer.toHexString(tCallbackAddress << 1);
                    }

                    switch (tSubcommand) {
                        case SUBFUNCTION_GET_INFO_LOCAL_TIME:
                        case SUBFUNCTION_GET_INFO_UTC_TIME:
                            /*
                             * send useDaylightTime flag, distance to UTC and requested timestamp
                             */
                            tFunctionName = "UTC time";
                            if (tSubcommand == SUBFUNCTION_GET_INFO_LOCAL_TIME) {
                                tFunctionName = "local time";
                            }
                            int tUseDaylightTime = 0;
                            if (TimeZone.getDefault().useDaylightTime()) {
                                tUseDaylightTime = 1;
                            }
                            int tGmtOffset = TimeZone.getDefault().getRawOffset(); // + TimeZone.getDefault().getDSTSavings(); // 1 hour if DST enabled, which is always true :-(
                            long tTimestamp = System.currentTimeMillis();
                            if (tSubcommand == SUBFUNCTION_GET_INFO_LOCAL_TIME) {
                                tTimestamp += tGmtOffset;
                            }
                            long tTimestampSeconds = tTimestamp / 1000L;
                            if (MyLog.isINFO()) {
                                MyLog.i(LOG_TAG, "Get " + tFunctionName + " callback=0x" + Integer.toHexString(tCallbackAddress) + tCallbackAddressStringAdjustedForClientDebugging);
                            }
                            mBlueDisplayContext.mSerialService.writeInfoCallbackEvent(SerialService.EVENT_INFO_CALLBACK, tSubcommand, tUseDaylightTime, tGmtOffset, tCallbackAddress, tTimestampSeconds);

                            break;
                    }
                    break;

                case FUNCTION_GLOBAL_SETTINGS:
                    tSubcommand = aParameters[0];
                    switch (tSubcommand) {
                        case SUBFUNCTION_GLOBAL_SET_FLAGS_AND_SIZE:
                            if (aParameters[2] < 10 || aParameters[3] < 10) {
                                MyLog.e(LOG_TAG, "Set flags=0x" + Integer.toHexString(aParameters[1]) + " and canvas size W x H =" + aParameters[2] + " x " + aParameters[3] + ". Size parameter values to small -> return.");
                                return;
                            }
                            // set canvas size
                            if (MyLog.isINFO()) {
                                MyLog.i(LOG_TAG, "Set flags=0x" + Integer.toHexString(aParameters[1]) + " and canvas size W x H =" + aParameters[2] + " x " + aParameters[3]);
                            }
                            mRequestedCanvasWidth = aParameters[2];
                            mRequestedCanvasHeight = aParameters[3];
                            setMaxScaleFactor();
                            setFlags(aParameters[1]);
                            handleScreenOrientationFlags(aParameters[1] >> 8); // These flags are contained in upper byte
                            break;

                        case SUBFUNCTION_GLOBAL_SET_CODEPAGE:
                            if (MyLog.isINFO()) {
                                MyLog.i(LOG_TAG, "Set codepage=ISO_8859_" + aParameters[1]);
                            }
                            String tCharsetName = "ISO_8859_" + aParameters[1];
                            Charset tCharset = Charset.forName(tCharsetName);
                            byte[] tCodepage = new byte[mCharMappingArray.length];
                            for (int i = 0; i < mCharMappingArray.length; i++) {
                                tCodepage[i] = (byte) (0x0080 + i);
                            }
                            ByteBuffer tBytebuffer = ByteBuffer.wrap(tCodepage);
                            CharBuffer tCharBuffer = tCharset.decode(tBytebuffer); // decode selected code page to char buffer
                            mCharMappingArray = tCharBuffer.array();
                            break;

                        case SUBFUNCTION_GLOBAL_SET_CHARACTER_CODE_MAPPING:
                            tIndex = aParameters[1] - 0x80;
                            if (tIndex >= 0 && tIndex < mCharMappingArray.length) {
                                if (MyLog.isINFO()) {
                                    MyLog.i(LOG_TAG, "Set character mapping=" + mCharMappingArray[tIndex] + "->" + (char) aParameters[2] + " / 0x" + Integer.toHexString(aParameters[1]) + "-> 0x" + Integer.toHexString(aParameters[2]));
                                }
                                mCharMappingArray[tIndex] = (char) aParameters[2];
                            } else {
                                MyLog.e(LOG_TAG, "Character mapping index=0x" + Integer.toHexString(aParameters[1]) + "+ must be between 0x80 and 0xFF");
                            }
                            break;

                        case SUBFUNCTION_GLOBAL_SET_LONG_TOUCH_DOWN_TIMEOUT:
                            if (aParameters[1] <= 0) {
                                mLongTouchDownTimeoutMillis = 0;
                                mIsLongTouchEnabled = false;
                            } else {
                                mLongTouchDownTimeoutMillis = aParameters[1];
                                mIsLongTouchEnabled = true;
                            }
                            if (MyLog.isINFO()) {
                                MyLog.i(LOG_TAG, "Long touch-down timeout=" + mLongTouchDownTimeoutMillis);
                            }
                            break;

                        case SUBFUNCTION_GLOBAL_SET_SCREEN_ORIENTATION_LOCK:
                            handleScreenOrientationFlags(aParameters[1]);
                            break;

                        case SUBFUNCTION_GLOBAL_SET_SCREEN_BRIGHTNESS:
                            Window window = mBlueDisplayContext.getWindow();
                            WindowManager.LayoutParams layoutParams = window.getAttributes();
                            // 0 is dark and 100 is full bright, others are user default
                            // Android: A value of less than 0, the default, means to use the preferred screen brightness.
                            // 0 to 1 adjusts the brightness from dark to full bright
                            if (aParameters[1] >= 0 && aParameters[1] <= 100) {
                                layoutParams.screenBrightness = (float) (aParameters[1] / 100.0);
                            } else {
                                layoutParams.screenBrightness = -1;
                            }
                            window.setAttributes(layoutParams);
                            if (MyLog.isINFO()) {
                                MyLog.i(LOG_TAG, "Set screen brightness 0x" + Integer.toHexString(aParameters[1]) + " -> " + layoutParams.screenBrightness);
                            }
                            break;

                        default:
                            MyLog.e(LOG_TAG, "Global settings: unknown subcommand 0x" + Integer.toHexString(tSubcommand) + " received. paramsLength=" + aParamsLength + " dataLength=" + aDataLength);
                            break;

                    }
                    break;

                case FUNCTION_SENSOR_SETTINGS:
                    boolean tDoActivate = aParameters[1] != 0;
                    int tFilterFlag = Sensors.FLAG_SENSOR_NO_FILTER;
                    if (aParamsLength == 4) {
                        tFilterFlag = aParameters[3];
                    }
                    mBlueDisplayContext.mSensorEventListener.setSensor(aParameters[0], tDoActivate, aParameters[2], tFilterFlag);
                    break;

                case FUNCTION_CLEAR_DISPLAY_OPTIONAL:
                    // Do nothing, it is interpreted directly at SearchCommand as sync point for skipping command buffer
                    break;

                case FUNCTION_CLEAR_DISPLAY:
                    // clear screen
                    if (MyLog.isINFO()) {
                        MyLog.i(LOG_TAG, "Clear screen color=" + shortToColorString(aParameters[0]));
                    }
                    mCanvas.drawColor(shortToLongColor(aParameters[0]));
                    break;

                case FUNCTION_DRAW_PIXEL:
                    mPaintStrokeScaleFactorColorSettable.setColor(shortToLongColor(aParameters[2]));
                    if (MyLog.isDEBUG()) {
                        MyLog.d(LOG_TAG, "drawPixel(" + aParameters[0] + ", " + aParameters[1] + ") color= " + shortToColorString(aParameters[2]));
                    }
                    mCanvas.drawPoint(tXStartScaled, tYStartScaled, mPaintStrokeScaleFactorColorSettable);
                    break;

                case FUNCTION_LINE_SETTINGS:
                    if (MyLog.isINFO()) {
                        MyLog.i(LOG_TAG, "setPaint[" + aParameters[0] + "] stroke=" + aParameters[1] + "] color= " + shortToColorString(aParameters[2]));
                    }
                    int tLineArrayIndex = aParameters[0];
                    if (tLineArrayIndex >= NUMBER_OF_SUPPORTED_LINES) {
                        tLineArrayIndex = 0;
                    }
                    mDrawLineInfoArray[tLineArrayIndex].mPaint.setStrokeWidth(Math.round(aParameters[1] * mScaleFactor));
                    mDrawLineInfoArray[tLineArrayIndex].mPaint.setColor(shortToLongColor(aParameters[2]));
                    break;

                case FUNCTION_DRAW_LINE_REL:
                case FUNCTION_DRAW_LINE:
                case FUNCTION_DRAW_VECTOR_DEGREE:
                    /*
                     * If the highest bit in aParameters[1] (YStart) is set,
                     * use normal (aliased) Paint, which can be cleared without residual
                     */
                    if ((aParameters[1] & 0x00008000) != 0) {
                        tYStartScaled = (aParameters[1] & 0x00007FFF) * mScaleFactor; // remove highest bitCOLOR16_NO_BACKGROUND
                        tResultingPaint = mPaintStrokeAndColorSettable;
                    } else {
                        tResultingPaint = mPaintStrokeAndColorSettableAntiAliased;
                    }

                    if (aCommand == FUNCTION_DRAW_LINE_REL) {
                        tFunctionName = "drawLineRel";
                        tXEndScaled = tXStartScaled + aParameters[2] * mScaleFactor;
                        tYEndScaled = tYStartScaled + aParameters[3] * mScaleFactor;
                    } else if (aCommand == FUNCTION_DRAW_VECTOR_DEGREE) {
                        tFunctionName = "drawVectorDegree";
                        float tLength = aParameters[2];
                        float tDegree = aParameters[3];
                        double tRadianOfDegree = tDegree * (Math.PI / 180);
                        tXEndScaled = (float) (tXStartScaled + (Math.cos(tRadianOfDegree) * tLength) * mScaleFactor);
                        tYEndScaled = (float) (tYStartScaled - (Math.sin(tRadianOfDegree) * tLength) * mScaleFactor);
                    } else {
                        tFunctionName = "drawLine";
                        tXEndScaled = aParameters[2] * mScaleFactor;
                        tYEndScaled = aParameters[3] * mScaleFactor;
                    }

                    float tLineStroke = mScaleFactor;

                    tXStartScaled = Math.round(tXStartScaled);
                    tYStartScaled = Math.round(tYStartScaled);
                    tXEndScaled = Math.round(tXEndScaled);
                    tYEndScaled = Math.round(tYEndScaled);
                    if (tXStartScaled == tXEndScaled || tYStartScaled == tYEndScaled) {
                        // Use NON anti aliased Paint for horizontal or vertical lines, these can be cleared without residual
                        tResultingPaint = mPaintStrokeAndColorSettable;
                    }

                    tColor = shortToLongColor(aParameters[4]);
                    if (aParamsLength > 5) {
                        // Stroke / thickness parameter
                        tLineStroke = aParameters[5] * mScaleFactor;
                        if (MyLog.isDEBUG()) {
                            tAdditionalInfo = " strokeWidth=" + aParameters[5];
                        }
                    }
                    tResultingPaint.setStrokeWidth(Math.round(tLineStroke));

                    tResultingPaint.setColor(tColor);
                    if (tXStartScaled == tXEndScaled && tYStartScaled == tYEndScaled) {
                        mCanvas.drawPoint(tXStartScaled, tYStartScaled, tResultingPaint);
                    } else {
                        mCanvas.drawLine(tXStartScaled, tYStartScaled, tXEndScaled, tYEndScaled, tResultingPaint);
                    }
                    if (MyLog.isDEBUG()) {
                        MyLog.d(LOG_TAG, tFunctionName + "(" + aParameters[0] + ", " + aParameters[1] + ", " + aParameters[2] + ", " + aParameters[3] + ") color=" + shortToColorString(aParameters[4]) + tAdditionalInfo);
                    }
                    break;

                case FUNCTION_DRAW_VECTOR_RADIAN:
                    tFunctionName = "drawVectorRadian";
                    float tLength = aParameters[2];
                    float tRadian = Float.intBitsToFloat((aParameters[3] & 0x0000FFFF) | (aParameters[4] << 16));
                    tXEndScaled = (float) (tXStartScaled + (Math.cos(tRadian) * tLength) * mScaleFactor);
                    tYEndScaled = (float) (tYStartScaled - (Math.sin(tRadian) * tLength) * mScaleFactor);

                    tColor = shortToLongColor(aParameters[5]);

                    if (aParamsLength > 6) {
                        // Stroke parameter
                        mPaintStrokeAndColorSettableAntiAliased.setStrokeWidth(Math.round(aParameters[6] * mScaleFactor));
                        tResultingPaint = mPaintStrokeAndColorSettableAntiAliased;
                        if (MyLog.isDEBUG()) {
                            tAdditionalInfo = " strokeWidth=" + aParameters[6];
                        }
                    } else {
                        tResultingPaint = mPaintStrokeScaleFactorColorSettable;
                    }

                    tResultingPaint.setColor(tColor);
                    if (tXStartScaled == tXEndScaled && tYStartScaled == tYEndScaled) {
                        mCanvas.drawPoint(tXStartScaled, tYStartScaled, tResultingPaint);
                    } else {
                        mCanvas.drawLine(tXStartScaled, tYStartScaled, tXEndScaled, tYEndScaled, tResultingPaint);
                    }
                    if (MyLog.isDEBUG()) {
                        MyLog.d(LOG_TAG, tFunctionName + "(" + aParameters[0] + ", " + aParameters[1] + ", " + aParameters[2] + ", " + aParameters[3] + ") color=" + shortToColorString(aParameters[5]) + tAdditionalInfo);
                    }
                    break;

                case FUNCTION_DRAW_CHART:
                case FUNCTION_DRAW_CHART_WITHOUT_DIRECT_RENDERING:
                case FUNCTION_DRAW_SCALED_CHART:
                case FUNCTION_DRAW_SCALED_CHART_WITHOUT_DIRECT_RENDERING:
                    /*
                     * Use NON anti aliased Paint for easy removing of old chart
                     * Chart index is coded in the upper 4 bits of Y start position
                     */
                    int tChartIndex = (aParameters[1] >> 12) & 0x0F; // Otherwise we may get -1
                    if (tChartIndex > 0) {
                        tYStartScaled = (aParameters[1] & 0x0FFF) * mScaleFactor;
                    }

                    boolean tDeleteOldLine = false;
                    int tDeleteColor;
                    float tYScaleFactor = 1.0F;
                    float tAdjustedXScaleFactor = mScaleFactor;
                    int tChartMode = CHART_MODE_LINE;

                    if (aParamsLength > 6) {
                        /*
                         * FUNCTION_DRAW_SCALED_CHART and FUNCTION_DRAW_SCALED_CHART_WITHOUT_DIRECT_RENDERING here
                         * get float YScale value and adjust effective data length to XScale
                         */
                        tYScaleFactor = Float.intBitsToFloat((aParameters[3] & 0x0000FFFF) | (aParameters[4] << 16));
                        // Use NON anti aliased Paint for easy removing of old chart
                        mPaintStrokeAndColorSettable.setStrokeWidth(Math.round(aParameters[5] * mScaleFactor));
                        tChartMode = aParameters[6];
                        tColor = shortToLongColor(aParameters[7]);
                        tDeleteColor = shortToLongColor(aParameters[8]);
                        if (aParameters[8] != COLOR16_NO_DELETE) {
                            tDeleteOldLine = true;
                        }
                        // Adjust values to XScaleFactor
                        tAdjustedXScaleFactor = enlargeFloatWithXScaleFactor(mScaleFactor, aParameters[2]);
                        if (MyLog.isINFO()) {
                            if (aCommand == FUNCTION_DRAW_SCALED_CHART) {
                                tFunctionName = "drawScaledChart";
                            } else {
                                tFunctionName = "drawScaledChartWithoutDirectRendering";
                            }
                            MyLog.i(LOG_TAG, tFunctionName + " X=" + aParameters[0] + " Y=" + (aParameters[1] & 0x0FFF) + " XFactor=" + enlargeFloatWithXScaleFactor(1, aParameters[2]) + " YFactor=" + tYScaleFactor + " lineSize=" + aParameters[5] + " mode=" + tChartMode + " color=" + shortToColorString(aParameters[7]) + " deleteColor=" + shortToColorString(aParameters[8]) + " length=" + aDataLength + " chartIndex=" + tChartIndex + " | 0x" + Integer.toHexString(aDataBytes[0] & 0xFF) + " 0X" + Integer.toHexString(aDataBytes[1] & 0xFF) + " 0x" + Integer.toHexString(aDataBytes[2] & 0xFF) + " 0X" + Integer.toHexString(aDataBytes[3] & 0xFF));
                        }

                    } else {
                        tColor = shortToLongColor(aParameters[2]);
                        tDeleteColor = shortToLongColor(aParameters[3]);
                        if (aParameters[3] != COLOR16_NO_DELETE) {
                            tDeleteOldLine = true;
                        }
                        mPaintStrokeAndColorSettable.setStrokeWidth(Math.round(mScaleFactor));
                        if (MyLog.isINFO()) {
                            if (aCommand == FUNCTION_DRAW_CHART) {
                                tFunctionName = "drawChart";
                            } else {
                                tFunctionName = "drawChartWithoutDirectRendering";
                            }
                            MyLog.i(LOG_TAG, tFunctionName + " X=" + aParameters[0] + " Y=" + (aParameters[1] & 0x0FFF) + " color=" + shortToColorString(aParameters[2]) + " deleteColor=" + shortToColorString(aParameters[3]) + " length=" + aDataLength + " chartIndex=" + tChartIndex + " | 0x" + Integer.toHexString(aDataBytes[0] & 0xFF) + " 0X" + Integer.toHexString(aDataBytes[1] & 0xFF) + " 0x" + Integer.toHexString(aDataBytes[2] & 0xFF) + " 0X" + Integer.toHexString(aDataBytes[3] & 0xFF));

                        }
                    }

                    // can not use tDeleteColor here, because it is a converted value
                    if (tDeleteOldLine) {
                        /*
                         * delete old chart line
                         */
                        mPaintStrokeAndColorSettable.setColor(tDeleteColor); // set delete color
                        if (tChartMode == CHART_MODE_LINE) {
                            mCanvas.drawLines(mChartScreenBuffer[tChartIndex], 0, mChartScreenBufferValidDataLength[tChartIndex], mPaintStrokeAndColorSettable);
                        } else {
                            mCanvas.drawPoints(mChartScreenBuffer[tChartIndex], 0, mChartScreenBufferValidDataLength[tChartIndex], mPaintStrokeAndColorSettable);
                        }
                    }

                    mPaintStrokeAndColorSettable.setColor(tColor); // now set draw color

                    /*
                     * draw new chart.
                     * Origin is at upper left and therefore Y values are inverse!
                     * After returning to searchCommand() this will break to enable rendering
                     * or continue to receive the next draw command until no data gets in and rendering may happen.
                     */
                    if (aDataLength > MAX_CHART_LINE_WIDTH) {
                        aDataLength = MAX_CHART_LINE_WIDTH;
                        MyLog.w(LOG_TAG, "aDataLength of " + aDataLength + " is bigger than maximum allowed data length of " + MAX_CHART_LINE_WIDTH);
                    }

                    /*
                     * Fill draw buffer with points for chart to draw at the end
                     */
                    int tSourceIndex = 0;
                    int tDestinationIndex = 0;
                    float tYValue = (SerialService.convertByteToFloat(aDataBytes[tSourceIndex++]) * mScaleFactor * tYScaleFactor) + tYStartScaled;
                    for (int i = 0; i < aDataLength - 1; i++) {
                        // Each line is taken from 4 consecutive values in the pts array
                        // Start of line / pixel
                        mChartScreenBuffer[tChartIndex][tDestinationIndex++] = tXStartScaled;
                        mChartScreenBuffer[tChartIndex][tDestinationIndex++] = tYValue;
                        /*
                         * Get values of next point
                         */
                        tYValue = (SerialService.convertByteToFloat(aDataBytes[tSourceIndex++]) * mScaleFactor * tYScaleFactor) + tYStartScaled;
                        tXStartScaled += tAdjustedXScaleFactor;
                        if (tChartMode == CHART_MODE_LINE) {
                            // Write next point as end of current line
                            mChartScreenBuffer[tChartIndex][tDestinationIndex++] = tXStartScaled;
                            mChartScreenBuffer[tChartIndex][tDestinationIndex++] = tYValue;
                        }
                    }
                    if (tChartMode == CHART_MODE_LINE) {
                        // For n points we have n-1 lines
                        mCanvas.drawLines(mChartScreenBuffer[tChartIndex], 0, (aDataLength - 1) * 4, mPaintStrokeAndColorSettable);
                        mChartScreenBufferValidDataLength[tChartIndex] = (aDataLength - 1) * 4; // for optional deletion of this line
                    } else {
                        // CHART_MODE_PIXEL here. Store last point
                        mChartScreenBuffer[tChartIndex][tDestinationIndex++] = tXStartScaled;
                        mChartScreenBuffer[tChartIndex][tDestinationIndex] = tYValue;
                        mCanvas.drawPoints(mChartScreenBuffer[tChartIndex], 0, aDataLength * 2, mPaintStrokeAndColorSettable);
                        mChartScreenBufferValidDataLength[tChartIndex] = aDataLength * 2; // for optional deletion of this line
                    }
                    break;

                case FUNCTION_DRAW_PATH:
                case FUNCTION_FILL_PATH:
                    tColor = shortToLongColor(aParameters[0]);
                    if (aCommand == FUNCTION_DRAW_PATH) {
                        tFunctionName = "drawPath";
                        mPaintStrokeAndColorSettable.setColor(tColor);
                        mPaintStrokeAndColorSettable.setStrokeWidth(Math.round(aParameters[1] * mScaleFactor));
                        tResultingPaint = mPaintStrokeAndColorSettable;
                        if (MyLog.isDEBUG()) {
                            tAdditionalInfo = " strokeWidth=" + aParameters[1];
                        }
                    } else {
                        tFunctionName = "fillPath";
                        mPaintStroke1Fill.setColor(tColor);
                        tResultingPaint = mPaintStroke1Fill;
                    }
                    if (MyLog.isDEBUG()) {
                        MyLog.d(LOG_TAG, tFunctionName + "(" + tXStartScaled + ", " + tYStartScaled + ") color=" + shortToColorString(aParameters[0]) + " length=" + aDataLength + tAdditionalInfo);
                    }

                    /*
                     * Data to path
                     */
                    mPath.incReserve(aDataLength + 1);
                    mPath.moveTo(aDataInts[0] * mScaleFactor, aDataInts[1] * mScaleFactor);
                    int i = 2;
                    while (i < aDataLength) {
                        mPath.lineTo(aDataInts[i] * mScaleFactor, aDataInts[i + 1] * mScaleFactor);
                        i += 2;
                    }
                    mPath.close();

                    mCanvas.drawPath(mPath, tResultingPaint);

                    // Path only consists of lines
                    mPath.rewind();

                    break;

                case FUNCTION_DRAW_RECT_REL:
                case FUNCTION_FILL_RECT_REL:
                case FUNCTION_DRAW_RECT:
                case FUNCTION_FILL_RECT:
                    if (aCommand == FUNCTION_DRAW_RECT_REL || aCommand == FUNCTION_FILL_RECT_REL) {
                        tXEndScaled = tXStartScaled + aParameters[2] * mScaleFactor; // is width, i.e. 0 -> rect is not rendered
                        tYEndScaled = tYStartScaled + aParameters[3] * mScaleFactor; // is height, i.e. 0 -> rect is not rendered
                    } else {
                        tXEndScaled = aParameters[2] * mScaleFactor;
                        tYEndScaled = aParameters[3] * mScaleFactor;
                        // sort parameters
                        float tmp;
                        if (tXStartScaled > tXEndScaled) {
                            tmp = tXStartScaled;
                            tXStartScaled = tXEndScaled;
                            tXEndScaled = tmp;
                        }
                        if (tYStartScaled > tYEndScaled) {
                            tmp = tYStartScaled;
                            tYStartScaled = tYEndScaled;
                            tYEndScaled = tmp;
                        }
                    }

                    tColor = shortToLongColor(aParameters[4]);

                    if (aCommand == FUNCTION_DRAW_RECT || aCommand == FUNCTION_DRAW_RECT_REL) {
                        if (aCommand == FUNCTION_DRAW_RECT_REL) {
                            tFunctionName = "drawRectRel";
                        } else {
                            tFunctionName = "drawRect";
                        }
                        mPaintStrokeAndColorSettable.setColor(tColor);
                        mPaintStrokeAndColorSettable.setStrokeWidth(Math.round(aParameters[5] * mScaleFactor));
                        tResultingPaint = mPaintStrokeAndColorSettable;
                        if (MyLog.isDEBUG()) {
                            tAdditionalInfo = " strokeWidth=" + aParameters[5];
                        }
                    } else {
                        if (aCommand == FUNCTION_FILL_RECT_REL) {
                            tFunctionName = "fillRectRel";
                        } else {
                            tFunctionName = "fillRect";
                        }
                        mPaintStroke1Fill.setColor(tColor);
                        tResultingPaint = mPaintStroke1Fill;
                    }

                    if (MyLog.isDEBUG()) {
                        MyLog.d(LOG_TAG, tFunctionName + "(" + aParameters[0] + ", " + aParameters[1] + ", " + aParameters[2] + ", " + aParameters[3] + ") , color=" + shortToColorString(aParameters[4]) + tAdditionalInfo);
                    }
                    mCanvas.drawRect(tXStartScaled, tYStartScaled, tXEndScaled, tYEndScaled, tResultingPaint);
                    break;

                case FUNCTION_DRAW_CIRCLE:
                case FUNCTION_FILL_CIRCLE:
                    tColor = shortToLongColor(aParameters[3]);
                    float tRadius = aParameters[2] * mScaleFactor;
                    if (aCommand == FUNCTION_DRAW_CIRCLE) {
                        tFunctionName = "drawCircle";
                        mPaintStrokeAndColorSettable.setColor(tColor);
                        mPaintStrokeAndColorSettable.setStrokeWidth(Math.round(aParameters[4] * mScaleFactor));
                        tResultingPaint = mPaintStrokeAndColorSettable;
                        if (MyLog.isDEBUG()) {
                            tAdditionalInfo = " strokeWidth=" + aParameters[4];
                        }
                    } else {
                        tFunctionName = "fillCircle";
                        mPaintStroke1Fill.setColor(tColor);
                        tResultingPaint = mPaintStroke1Fill;
                    }

                    if (MyLog.isDEBUG()) {
                        MyLog.d(LOG_TAG, tFunctionName + "(" + aParameters[0] + ", " + aParameters[1] + ", r=" + aParameters[2] + ") ,color=" + shortToColorString(aParameters[3]) + tAdditionalInfo);
                    }
                    mCanvas.drawCircle(tXStartScaled, tYStartScaled, tRadius, tResultingPaint);
                    break;

                case FUNCTION_DEBUG_STRING:
                    tStringParameter = new String(aDataBytes, 0, aDataLength);
                    // Show new values as toast for at least 500 ms, i.e. subsequent debugs are suppressed during 500 ms
                    showAsDebugToast(tStringParameter);
                    // Output as warning in order to enable easier finding and filtering the message in log
                    MyLog.w(LOG_TAG, "DebugString=\"" + tStringParameter + "\"");
                    break;


                case FUNCTION_WRITE_SETTINGS:
                    tSubcommand = aParameters[0];
                    switch (tSubcommand) {
                        case FLAG_WRITE_SETTINGS_SET_SIZE_AND_COLORS_AND_FLAGS:
                            mTextPrintTextSize = aParameters[1];
                            mTextExpandedPrintColor = shortToLongColor(aParameters[2]);
                            mPrintTextStroke1Fill.setTextSize(mTextPrintTextSize * mScaleFactor);
                            mPrintTextStroke1Fill.setColor(shortToLongColor(aParameters[2]));
                            mTextExpandedPrintBackgroundColor = shortToLongColor(aParameters[3]);
                            mTextPrintDoClearScreenOnWrap = aParameters[4] > 0;
                            if (MyLog.isINFO()) {
                                MyLog.i(LOG_TAG, "Set printf size=" + aParameters[1] + " color=" + shortToColorString(aParameters[2]) + " backgroundcolor=" + shortToColorString(aParameters[3]) + " clearOnWrap=" + mTextPrintDoClearScreenOnWrap);
                            }
                            break;

                        case FLAG_WRITE_SETTINGS_SET_POSITION:
                            /*
                             * Sets the Y position and the X start position after a newline
                             * Positions are in pixel
                             */
                            mTextPrintTextCurrentPosX = aParameters[1];
                            mTextPrintTextStartPosX = mTextPrintTextCurrentPosX;
                            mTextPrintTextCurrentPosY = aParameters[2];
                            if (MyLog.isINFO()) {
                                MyLog.i(LOG_TAG, "Set printf start position to: " + mTextPrintTextCurrentPosX + " / " + mTextPrintTextCurrentPosY);
                            }
                            break;

                        case FLAG_WRITE_SETTINGS_SET_LINE_COLUMN:
                            /*
                             * Sets the Y position and the X start position after a newline
                             * Positions are in character units :-)
                             */
                            mTextPrintTextCurrentPosX = (int) ((aParameters[1] * mTextPrintTextSize * TEXT_WIDTH_FACTOR) + 0.5);
                            mTextPrintTextStartPosX = mTextPrintTextCurrentPosX;
                            mTextPrintTextCurrentPosY = aParameters[2] * mTextPrintTextSize;
                            if (MyLog.isINFO()) {
                                MyLog.i(LOG_TAG, "Set printf start position to: " + aParameters[1] + " / " + aParameters[2] + " = " + mTextPrintTextCurrentPosX + " / " + mTextPrintTextCurrentPosY);
                            }
                            break;

                        default:
                            MyLog.e(LOG_TAG, "Write settings: unknown subcommand 0x" + Integer.toHexString(tSubcommand) + " received. paramsLength=" + aParamsLength + " dataLength=" + aDataLength);
                    }
                    break;

                /*
                 * Writes string at previously set position and do line and page wrapping for print emulation
                 * Do an automatic break before a new word, which will not fit on the remainder of line!
                 * \r is interpreted as a space
                 */
                case FUNCTION_WRITE_STRING:
                    myConvertChars(aDataBytes, sCharsArray, aDataLength);
                    tStringParameter = new String(sCharsArray, 0, aDataLength);

                    if (MyLog.isINFO()) {
                        MyLog.i(LOG_TAG, "writeString(\"" + tStringParameter.replaceAll("\n", "\\\\n") + "\") at " + mTextPrintTextCurrentPosX + " / " + mTextPrintTextCurrentPosY);
                    }
                    char tChar;
                    int tCurrentCharacterIndex = 0;
                    int tWordStartIndex = 0;
                    int tPrintBufferStartIndex = 0;
                    float tScaledTextPrintTextSize = mTextPrintTextSize * mScaleFactor;
                    int tTextUnscaledWidth = (int) ((mTextPrintTextSize * TEXT_WIDTH_FACTOR) + 0.5);
                    int tLineLengthInChars = mRequestedCanvasWidth / tTextUnscaledWidth;
                    boolean doFlushAndCarriageReturn = false;
                    boolean doFlushAndNewline = false;
                    int tColumn = mTextPrintTextCurrentPosX / tTextUnscaledWidth;
                    int tStartColumn = mTextPrintTextStartPosX / tTextUnscaledWidth; // after \n or \r
                    // ascend for background color.
                    tAscend = tScaledTextPrintTextSize * TEXT_ASCEND_FACTOR;
                    while (true) {
                        // check for terminate condition
                        if (tCurrentCharacterIndex >= aDataLength) {
                            // check if last character was newline and string was already printed
                            if (tPrintBufferStartIndex < aDataLength) {
                                /*
                                 * Draw last word
                                 */
                                tYStartScaled = mTextPrintTextCurrentPosY * mScaleFactor;
                                tXStartScaled = mTextPrintTextCurrentPosX * mScaleFactor;
                                int tIntegerTextSize = (int) (tScaledTextPrintTextSize + 0.5);
                                tIntegerTextSize = (int) ((tIntegerTextSize * TEXT_WIDTH_FACTOR) + 0.5);
                                float tTextLength = (aDataLength - tPrintBufferStartIndex) * tIntegerTextSize;

                                // draw background
                                mTextBackgroundStroke1Fill.setColor(mTextExpandedPrintBackgroundColor);
                                mCanvas.drawRect(tXStartScaled, tYStartScaled, tXStartScaled + tTextLength, tYStartScaled + tScaledTextPrintTextSize, mTextBackgroundStroke1Fill);
                                // draw char / string
                                drawText(tStringParameter, tPrintBufferStartIndex, aDataLength, tXStartScaled, tYStartScaled + tAscend, tScaledTextPrintTextSize, mTextExpandedPrintColor);
                                mTextPrintTextCurrentPosX += Math.round(tTextLength / mScaleFactor); // Advance to start position for next write
                            }
                            break;
                        }
                        // get character and interpret special characters and space as word separator
                        tChar = sCharsArray[tCurrentCharacterIndex++];

                        if (tChar == '\n') {
                            // new line -> is also start of a new word
                            tWordStartIndex = tCurrentCharacterIndex;
                            // signal flush and newline
                            doFlushAndNewline = true;
                        } else if (tChar == '\r') {
                            // Reset x position to start but do not advance to next line
                            tWordStartIndex = tCurrentCharacterIndex;
                            doFlushAndCarriageReturn = true;
                        } else if (tChar == ' ') {
                            // start of a new word
                            tWordStartIndex = tCurrentCharacterIndex;
                            if (tColumn == tStartColumn) {
                                // skip from printing the space if first character in line
                                tPrintBufferStartIndex = tCurrentCharacterIndex;
                            }
                        } else {
                            if (tColumn >= tLineLengthInChars) {
                                /*
                                 * character does not fit in line -> print word at next line
                                 */
                                doFlushAndNewline = true;
                                int tWordLength = (tCurrentCharacterIndex - tWordStartIndex);
                                if (tWordLength > tLineLengthInChars) {
                                    // word too long for a line just print char on next line
                                    // just draw "buffer" to old line, make newline and process character again
                                    tCurrentCharacterIndex--;
                                } else {
                                    // draw buffer till word start, print a newline and process word again
                                    tCurrentCharacterIndex = tWordStartIndex;
                                }
                            }
                        }
                        if (doFlushAndNewline || doFlushAndCarriageReturn) {
                            tXStartScaled = mTextPrintTextCurrentPosX * mScaleFactor;
                            tYStartScaled = mTextPrintTextCurrentPosY * mScaleFactor;
                            int tIntegerScaledTextWidth = (int) (tScaledTextPrintTextSize + 0.5);
                            // ??? is this integer computation stuff required to find the text length Andoid takes internally to get real size for drawing background???
                            tIntegerScaledTextWidth = (int) ((tIntegerScaledTextWidth * TEXT_WIDTH_FACTOR) + 0.5);
                            // do not count the newline or space
                            float tTextLength = ((tCurrentCharacterIndex - 1) - tPrintBufferStartIndex) * tIntegerScaledTextWidth;
                            if (tTextLength > 0) {
                                // do not print trailing \r or \n
                                mTextBackgroundStroke1Fill.setColor(mTextExpandedPrintBackgroundColor);
                                mCanvas.drawRect(tXStartScaled, tYStartScaled, tXStartScaled + tTextLength, tYStartScaled + tScaledTextPrintTextSize, mTextBackgroundStroke1Fill);
                                // Draw char / string which has to be flushed
                                drawText(tStringParameter, tPrintBufferStartIndex, tCurrentCharacterIndex - 1, tXStartScaled, tYStartScaled + tAscend, tScaledTextPrintTextSize, mTextExpandedPrintColor);
                            }
                            tPrintBufferStartIndex = tCurrentCharacterIndex;
                            if (doFlushAndNewline) {
                                mTextPrintTextCurrentPosY = printNewline();
                            }
                            // set to user specified start position
                            mTextPrintTextCurrentPosX = mTextPrintTextStartPosX; // set it explicitly since compiler may hold mTextPrintTextCurrentPosX in register
                            tColumn = tStartColumn;
                            doFlushAndNewline = false;
                            doFlushAndCarriageReturn = false;
                        } else {
                            // Just increment columns as long as characters do not trigger a flush i.e overflow screen, or are \n or \r
                            tColumn++;
                        }
                    } // while true
                    break;

                case FUNCTION_DRAW_CHAR:
                case FUNCTION_DRAW_STRING:
                    tYStartScaled = aParameters[1] * mScaleFactor;
                    int tBackgroundColor;

                    if (aParamsLength <= 2) {
                        /*
                         * Get the last 3 parameters from preceding command
                         */
                        tScaledTextSize = mLastDrawStringTextSize * mScaleFactor;
                        tColor = mLastDrawStringColor;
                        tBackgroundColor = mLastDrawStringBackgroundColor;
                    } else {
                        if (aCommand != FUNCTION_DRAW_CHAR) {
                            /*
                             * Store the last 3 parameters for next command
                             */
                            mLastDrawStringTextSize = aParameters[2];
                            mLastDrawStringColor = aParameters[3];
                            mLastDrawStringBackgroundColor = aParameters[4];
                        }
                        tScaledTextSize = aParameters[2] * mScaleFactor;
                        tColor = aParameters[3];
                        tBackgroundColor = aParameters[4];
                    }

                    mTextPaint.setTextSize(tScaledTextSize); // will be used below
                    int tExpandedColor = shortToLongColor(tColor);

                    // ascend for draw
                    tAscend = tScaledTextSize * TEXT_ASCEND_FACTOR;

                    int tDataLength = aDataLength;
                    if (aCommand == FUNCTION_DRAW_CHAR) {
                        tFunctionName = "drawChar";
                        sCharsArray[0] = myConvertChar((byte) aParameters[5]);
                        tDataLength = 1;
                    } else {
                        tFunctionName = "drawString";
                        myConvertChars(aDataBytes, sCharsArray, tDataLength);
                    }
                    tStringParameter = new String(sCharsArray, 0, tDataLength);

                    /*
                     * Handle special alignments coded in aParameters[0]
                     */
                    if (aParameters[0] == STRING_ALIGN_RIGHT_XPOS || aParameters[0] == STRING_ALIGN_MIDDLE_XPOS) {
                        int tTextPixelLength = (int) ((RPCView.TEXT_WIDTH_FACTOR * tScaledTextSize * tStringParameter.length()) + 0.5);
                        if (aParameters[0] == STRING_ALIGN_RIGHT_XPOS) {
                            tXStartScaled = mCurrentCanvasPixelWidth - tTextPixelLength;
                        } else {
                            // tXStartScaled == STRING_ALIGN_MIDDLE_XPOS
                            tXStartScaled = (mCurrentCanvasPixelWidth - tTextPixelLength) / 2;
                        }
                    }

                    if (MyLog.isDEBUG()) {
                        MyLog.d(LOG_TAG, tFunctionName + "(\"" + tStringParameter + "\", " + aParameters[0] + ", " + aParameters[1] + ", size=" + mLastDrawStringTextSize + ") color=" + shortToColorString(tColor) + " bg=" + shortToColorString(tBackgroundColor));
                    }

                    /*
                     * Handle background modes
                     */
                    int tNewlineIndex = tStringParameter.indexOf('\n');
                    boolean tDrawBackgroundExtend = false; // true -> draw background for whole rest of line
                    int tCRIndex = tStringParameter.indexOf('\r');
                    if (tCRIndex >= 0 && (tCRIndex < tNewlineIndex || tNewlineIndex < 0)) {
                        tNewlineIndex = tCRIndex;
                        tDrawBackgroundExtend = true;
                    }

                    boolean tDrawBackground;
                    if (tBackgroundColor == COLOR16_NO_BACKGROUND) {
                        tDrawBackground = false;
                        tDrawBackgroundExtend = false;
                    } else {
                        mTextBackgroundStroke1Fill.setColor(shortToLongColor(tBackgroundColor));
                        tDrawBackground = true;
                    }

                    if (tNewlineIndex > 0) {
                        int tStartIndex = 0;

                        while (tNewlineIndex > 0) {
                            /*
                             * Multiline text
                             */
                            if (tDrawBackgroundExtend) {
                                // draw background for whole rest of line. mScaleFactor for lower margin
                                mCanvas.drawRect(tXStartScaled, tYStartScaled, mCurrentCanvasPixelWidth, tYStartScaled + tScaledTextSize + mScaleFactor, mTextBackgroundStroke1Fill);
                            } else if (tDrawBackground) {
                                // draw background only for string except for single newline
                                if (tStartIndex != tNewlineIndex) {
                                    float tTextLength = mTextPaint.measureText(tStringParameter, tStartIndex, tNewlineIndex);
                                    // draw background. mScaleFactor for lower margin
                                    mCanvas.drawRect(tXStartScaled, tYStartScaled, tXStartScaled + tTextLength, tYStartScaled + tScaledTextSize + mScaleFactor, mTextBackgroundStroke1Fill);

                                }
                            }
                            // check for single newline
                            if (tStartIndex != tNewlineIndex) {
                                // no single newline, draw string
                                drawText(tStringParameter, tStartIndex, tNewlineIndex, tXStartScaled, tYStartScaled + tAscend, tScaledTextSize, tExpandedColor);
                                tYStartScaled += tScaledTextSize + mScaleFactor; // + Margin between lines
                            }
                            // search for next newline
                            tStartIndex = tNewlineIndex + 1;
                            if (tNewlineIndex + 1 <= tStringParameter.length()) {
                                tNewlineIndex = tStringParameter.indexOf('\n', tStartIndex);
                                tDrawBackgroundExtend = false;
                                tCRIndex = tStringParameter.indexOf('\r', tStartIndex);
                                if (tCRIndex >= 0 && (tCRIndex < tNewlineIndex || tNewlineIndex < 0)) {
                                    tNewlineIndex = tCRIndex;
                                    tDrawBackgroundExtend = true;
                                }

                                if (tNewlineIndex < 0) {
                                    tNewlineIndex = tStringParameter.length();
                                }
                            } else {
                                tNewlineIndex = 0;
                            }
                        }
                    } else {
                        /*
                         * Single line text
                         */
                        if (tDrawBackground) {
                            float tTextLength = mTextPaint.measureText(tStringParameter);
                            // draw background. mScaleFactor for lower margin
                            mCanvas.drawRect(tXStartScaled, tYStartScaled, tXStartScaled + tTextLength, tYStartScaled + tScaledTextSize + mScaleFactor, mTextBackgroundStroke1Fill);
                        }

                        // draw char / string
                        drawText(tStringParameter, tXStartScaled, tYStartScaled + tAscend, tScaledTextSize, tExpandedColor);
                    }
                    break;

                case FUNCTION_NOP:
                    if (MyLog.isINFO()) {
                        MyLog.i(LOG_TAG, "NOP (for sync) received. ParamsLength=" + aParamsLength + " DataLength=" + aDataLength);
                    }
                    break;

                default:
                    MyLog.e(LOG_TAG, "unknown command 0x" + Integer.toHexString(aCommand) + " received. paramsLength=" + aParamsLength + " dataLength=" + aDataLength);
                    break;
            }
        } catch (Exception e) {
            MyLog.e(LOG_TAG, "Exception caught for command 0x" + Integer.toHexString(aCommand) + ". paramsLength=" + aParamsLength + " dataLength=" + aDataLength + " Exception=" + e);
        }
        // long tEnd = System.nanoTime();
        // Log.i(LOG_TAG, "Interpret=" + (tEnd - tStart));
    }

    /*
     * Handles BlueDisplay orientations, which are a bit different from Android ones
     */
    private void handleScreenOrientationFlags(int aClientRequestedOrientation) {
        if (aClientRequestedOrientation == FLAG_SCREEN_ORIENTATION_LOCK_UNLOCK) {
            // unlock is BlueDisplay code (0x00), set preferred Orientation here
            mBlueDisplayContext.mOrientationisLockedByClient = false;
            mBlueDisplayContext.setScreenOrientation(mBlueDisplayContext.mPreferredScreenOrientation);

            if (MyLog.isINFO()) {
                MyLog.i(LOG_TAG, "Unlocked screen orientation to preferred orientation=" + mBlueDisplayContext.getScreenOrientationRotationString(mBlueDisplayContext.mPreferredScreenOrientation));
            }
        } else {
            mBlueDisplayContext.mOrientationisLockedByClient = true;

            // Convert BlueDisplay enumeration to Android one
            if (aClientRequestedOrientation == FLAG_SCREEN_ORIENTATION_LOCK_LANDSCAPE) {
                aClientRequestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            }
            // Here we have only Android enumerations
            int tNewOrientation = aClientRequestedOrientation;

            // may be overwritten by "current" below
            String tRequestedOrientation = mBlueDisplayContext.getScreenOrientationRotationString(tNewOrientation);

            if (aClientRequestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LOCKED) { // android and BD emumerations are the same
                tRequestedOrientation = "current";
                tNewOrientation = mBlueDisplayContext.getCurrentOrientation(mBlueDisplayContext.getResources().getConfiguration().orientation);
            }

            if (MyLog.isINFO()) {
                MyLog.i(LOG_TAG, "Requested orientation lock=" + tRequestedOrientation);
            }
            mBlueDisplayContext.setScreenOrientation(tNewOrientation);
        }
    }

    /*
     * Show new values as toast for at least 500 ms, i.e. subsequent debugs are suppressed during 500 ms
     */
    public void showAsDebugToast(String tStringParameter) {
        long tMillis = System.currentTimeMillis();
        if (tMillis > (mLastDebugToastMillis + DEBUG_TOAST_REFRESH_MILLIS)) {
            mLastDebugToastMillis = tMillis;
            if (mBlueDisplayContext.mMyToast != null) {
                mBlueDisplayContext.mMyToast.cancel();
            }
            mBlueDisplayContext.mMyToast = Toast.makeText(mBlueDisplayContext, tStringParameter, Toast.LENGTH_SHORT);
            mBlueDisplayContext.mMyToast.show();
        }
    }

    /*
     * Called only by RPCView.interpretCommand()
     */
    public void resetWaitMessage() {
        if (mBlueDisplayContext.mWaitForDataAfterConnect) {
            // reset timeout by clearing messages
            if (MyLog.isINFO()) {
                Log.i(LOG_TAG, "Reset timeout waiting for commands");
            }
            mBlueDisplayContext.mHandlerForGUIRequests.removeMessages(BlueDisplay.MESSAGE_TIMEOUT_AFTER_CONNECT);
            mBlueDisplayContext.mWaitForDataAfterConnect = false;
        }
    }

    public void fillRectRel(float aXStart, float aYStart, float aWidth, float aHeight, int aColor) {
        mPaintStroke1Fill.setColor(aColor);
        mCanvas.drawRect(aXStart * mScaleFactor, aYStart * mScaleFactor, (aXStart + aWidth) * mScaleFactor, (aYStart + aHeight) * mScaleFactor, mPaintStroke1Fill);
    }

    public void fillRect(float aXStart, float aYStart, float aXEnd, float aYEnd, int aColor) {
        mPaintStroke1Fill.setColor(aColor);
        mCanvas.drawRect(aXStart * mScaleFactor, aYStart * mScaleFactor, aXEnd * mScaleFactor, aYEnd * mScaleFactor, mPaintStroke1Fill);
    }

    public void drawText(String aText, float aScaledPosX, float aScaledPosY, float aScaledTextSize, int aColor) {
        mTextPaint.setTextSize(aScaledTextSize);
        mTextPaint.setColor(aColor);

        while (aScaledPosX >= mCurrentCanvasPixelWidth) {
            // Wrap around
            aScaledPosX -= mCurrentCanvasPixelWidth;
        }
        while (aScaledPosY >= mCurrentCanvasPixelHeight) {
            // Wrap around
            aScaledPosY -= mCurrentCanvasPixelHeight;
        }
        mCanvas.drawText(aText, aScaledPosX, aScaledPosY, mTextPaint);
    }

    public void drawText(String aText, int aStartIndex, int aEndIndexNotIncluded, float aScaledPosX, float aScaledPosY, float aScaledTextSize, int aColor) {
        drawText(aText.substring(aStartIndex, aEndIndexNotIncluded), aScaledPosX, aScaledPosY, aScaledTextSize, aColor);
    }

    /*
     * For internal button and slider usage, no ascend compensation for draw position here
     */
    public void drawTextWithBackground(float aPosX, float aPosY, String aText, float aTextSize, int aColor, int aBGColor) {
        aPosX *= mScaleFactor;
        aPosY *= mScaleFactor;
        aTextSize *= mScaleFactor;
        mTextPaint.setTextSize(aTextSize);
        mTextPaint.setColor(aColor);

        // draw background
        // ascend for background color. + mScaleFactor for upper margin
//        float tAscend = (aTextSize * TEXT_ASCEND_FACTOR) + mScaleFactor;
        // ascend for background color
        float tAscend = (aTextSize * TEXT_ASCEND_FACTOR);
        float tDescend = aTextSize * TEXT_DESCEND_FACTOR;
        float tTextLength = mTextPaint.measureText(aText);
        mTextBackgroundStroke1Fill.setColor(aBGColor);
        mCanvas.drawRect(aPosX, aPosY - tAscend, aPosX + tTextLength, aPosY + tDescend, mTextBackgroundStroke1Fill);

        mCanvas.drawText(aText, aPosX, aPosY, mTextPaint);
    }

    void initCharMappingArray() {
        /*
         * initialize mapping array with current codepage chars
         */
        short tUnicodeChar = 0x0080;
        for (int i = 0; i < mCharMappingArray.length; i++) {
            mCharMappingArray[i] = (char) tUnicodeChar;
            tUnicodeChar++;
        }
    }

    protected void resetAll() {
        mBlueDisplayContext.mOrientationisLockedByClient = false;
        TouchButton.resetButtons(this);
        TouchSlider.resetSliders();
        Sensors.disableAllSensors();
        resetFlags();
        initCharMappingArray();
        if (MyLog.isINFO()) {
            MyLog.i(LOG_TAG, "Reset all");
        }
    }

    private void resetFlags() {
        mUseMaxSize = true;
        mTouchBasicEnable = true;
        mTouchMoveEnable = true;
        mIsLongTouchEnabled = false;
        mUseUpEventForButtons = false;
    }

    private void setFlags(int aFlags) {
        String tResetAllString = "";
        if ((aFlags & BD_FLAG_FIRST_RESET_ALL) != 0) {
            resetAll();
            tResetAllString = " after reset_all";
        }

        mTouchBasicEnable = ((aFlags & BD_FLAG_TOUCH_BASIC_DISABLE) == 0);
        mTouchMoveEnable = ((aFlags & BD_FLAG_TOUCH_MOVE_DISABLE) == 0);
        mIsLongTouchEnabled = ((aFlags & BD_FLAG_LONG_TOUCH_ENABLE) != 0);

        mScaleFactor = 0.4711f; // force resize in setScaleFactor()
        if ((aFlags & BD_FLAG_USE_MAX_SIZE) != 0) {
            mUseMaxSize = true;
            // resize canvas
            setScaleFactor(10, false);
        } else {
            mUseMaxSize = false;
            setScaleFactor(1, false);
        }

        if (MyLog.isINFO()) {
            MyLog.i(LOG_TAG, "SetFlags state now " + tResetAllString + ": TouchMoveEnable=" + mTouchMoveEnable + ", LongTouchEnabled=" + mIsLongTouchEnabled + ", UseMaxSize=" + mUseMaxSize);
        }
    }

    /******************************************************************************************
     * TEST METHODS
     ******************************************************************************************/

    private static final int TEST_CANVAS_WIDTH = 320;
    private static final int TEST_CANVAS_HEIGHT = 240;

    /*
     * do length correction for a 1 pixel line; draw 4 lines for tYStart = 2
     */
    public void drawLengthCorrectedLine(float aStartX, float aStartY, float aStopX, float aStopY, int aScaleFactor, Paint aPaint, Canvas aCanvas) {
        if (aStartX > aStopX) {
            // change X direction to positive
            float tTemp = aStartX;
            aStartX = aStopX;
            aStopX = tTemp;
            tTemp = aStartY;
            aStartY = aStopY;
            aStopY = tTemp;
        }
        if (aScaleFactor == 2) {
            if (aStartY == aStopY) {
                // horizontal line
                drawLengthCorrectedLine(aStartX, aStartY, aStopX + 1, aStopY, 1, aPaint, aCanvas);
                drawLengthCorrectedLine(aStartX, aStartY + 1, aStopX + 1, aStopY + 1, 1, aPaint, aCanvas);
            } else if (aStartX == aStopX) {
                // vertical line here
                if (aStartY < aStopY) {
                    ++aStopY;
                } else {
                    ++aStartY;
                }
                drawLengthCorrectedLine(aStartX, aStartY, aStopX, aStopY, 1, aPaint, aCanvas);
                drawLengthCorrectedLine(aStartX + 1, aStartY, aStopX + 1, aStopY, 1, aPaint, aCanvas);
            } else {
                drawLengthCorrectedLine(aStartX, aStartY, aStopX, aStopY, 1, aPaint, aCanvas);
                drawLengthCorrectedLine(aStartX + 1, aStartY, aStopX + 1, aStopY, 1, aPaint, aCanvas);
                drawLengthCorrectedLine(aStartX, aStartY + 1, aStopX, aStopY + 1, 1, aPaint, aCanvas);
                drawLengthCorrectedLine(aStartX + 1, aStartY + 1, aStopX + 1, aStopY + 1, 1, aPaint, aCanvas);
            }
        } else {
            if (aStartX != aStopX) { // no vertical line?
                if (aStartX < aStopX) {
                    ++aStopX; // length correction at end of line
                } else {
                    ++aStartX; // length correction at start of line
                }
            }
            if (aStartY != aStopY) {
                if (aStartY < aStopY) {
                    ++aStopY;
                } else {
                    ++aStartY;
                }
            } else if (aStartX == aStopX) {
                ++aStopX;// draw only one point
            }

            aCanvas.drawLine(aStartX, aStartY, aStopX, aStopY, aPaint);
        }
    }

    public void showTestpage() {

        mCanvas.drawColor(Color.WHITE); // clear screen

        // showGraphTestpage
        MyLog.i(LOG_TAG, "mScaleFactor=" + mScaleFactor);

        int tY = drawGraphTestPattern() + 10;

        tY = (int) ((drawFontTest(tY) / mScaleFactor) + 10); // scale since the next tests run with functions using mScaleFactor

        // The unscaled logo is 450 * 350 and has a left border of 50 and a upper border of 100
        int tScaleDivisor = 5;
        // Place it at the lower right corner
        drawLogo(mRequestedCanvasWidth - (500 / tScaleDivisor) - 2, mRequestedCanvasHeight - (450 / tScaleDivisor) - 2, tScaleDivisor);
        testBDFunctions(5, tY, TEST_CANVAS_HEIGHT, false);

        invalidate(); // Show the testpage
    }

    /*
     * Start at bottom of page with a Box
     */
    void testBDFunctions(int aStartX, int aStartY, int aCanvasHeight, boolean aCompatibilityMode) {

        int tModeCharacter = 0x4E; // Character 'N'
        if (aCompatibilityMode) {
            tModeCharacter = 0x43; // Character 'C'
        }

        byte[] tByteBuffer = "Testy56789".getBytes();
        tByteBuffer[5] = (byte) 0x81; // Omega manually set
        tByteBuffer[6] = (byte) 0x82; // Home
        tByteBuffer[7] = (byte) 0xB1; // +/-
        tByteBuffer[8] = (byte) 0xBB; // >>
        tByteBuffer[9] = (byte) 0xB5; // micro

        int[] tParameters = new int[6];

        // get current canvas for drawing reference lines
        Canvas tCanvas = new Canvas(mBitmap);
        Paint tGraph1Paint = new Paint();
        tGraph1Paint.setColor(Color.BLACK);
        tGraph1Paint.setStyle(Paint.Style.STROKE);
        tGraph1Paint.setStrokeWidth(1);

        /*
         * direct draw reference line at start
         * Here we use scale factor since we use native Android functions
         */
        tCanvas.drawLine(aStartX, mScaleFactor * aStartY, aStartX + 30, mScaleFactor * aStartY, tGraph1Paint);

        int tStartX = aStartX + 12;

        // draw square box
        tParameters[0] = tStartX;
        tParameters[1] = aStartY;
        tParameters[2] = tStartX + 10;
        tParameters[3] = aStartY;
        tParameters[4] = 0; // Color black
        interpretCommand(FUNCTION_DRAW_LINE, tParameters, 5, tByteBuffer, null, 0);
        tParameters[0] = tParameters[2] = tStartX;
        tParameters[1] = aStartY + 1;
        tParameters[3] = aStartY + 10;
        tParameters[4] = 0x7E0; // Color green
        interpretCommand(FUNCTION_DRAW_LINE, tParameters, 5, tByteBuffer, null, 0);
        tParameters[0] = tParameters[2] = tStartX + 10;
        interpretCommand(FUNCTION_DRAW_LINE, tParameters, 5, tByteBuffer, null, 0);
        tParameters[1] = tParameters[3] = aStartY + 10;
        tParameters[0] = tStartX + 1;
        tParameters[2] = tStartX + 9;
        tParameters[4] = 0xF800; // Color red
        // red bottom line as rect
        interpretCommand(FUNCTION_FILL_RECT, tParameters, 6, tByteBuffer, null, 0);

        // draw 2 border points outside box
        tCanvas.drawPoint(mScaleFactor * tStartX - 1, mScaleFactor * aStartY - 1, tGraph1Paint); // 1
        // pixel
        tCanvas.drawPoint(mScaleFactor * tStartX + mScaleFactor * (10 + 1), mScaleFactor * aStartY + mScaleFactor * (10 + 1), tGraph1Paint); // 1
        // pixel

        // draw bigger rect outside
        tParameters[0] = tStartX - 2;
        tParameters[2] = tStartX + 12;
        tParameters[1] = aStartY - 2;
        tParameters[3] = aStartY + 12;
        tParameters[4] = 0xF800; // Color red
        interpretCommand(FUNCTION_DRAW_RECT, tParameters, 6, tByteBuffer, null, 0);

        // fill with red square
        tParameters[0] = tStartX + 2;
        tParameters[2] = tStartX + 8;
        tParameters[1] = aStartY + 2;
        tParameters[3] = aStartY + 8;
        tParameters[4] = 0xF800; // Color red
        interpretCommand(FUNCTION_FILL_RECT, tParameters, 6, tByteBuffer, null, 0);

        // fill with black circle
        tParameters[0] = tStartX + 5;
        tParameters[1] = aStartY + 5;
        tParameters[2] = 3; // radius
        tParameters[3] = 0;
        interpretCommand(FUNCTION_DRAW_CIRCLE, tParameters, 6, tByteBuffer, null, 0);

        // and draw center point
        tCanvas.drawPoint(mScaleFactor * tStartX + mScaleFactor * 5, mScaleFactor * aStartY + mScaleFactor * 5, tGraph1Paint); // 1

        /*
         * Thick line
         */
        tParameters[0] = tStartX + 50;
        tParameters[1] = aStartY + 30;
        tParameters[2] = tStartX + 70;
        tParameters[3] = aStartY + 40;
        tParameters[4] = 0; // Color black
        tParameters[5] = 5; // Stroke width
        interpretCommand(FUNCTION_DRAW_LINE, tParameters, 6, tByteBuffer, null, 0);

        // draw char + string
        tParameters[0] = tStartX + 15;
        tParameters[1] = aStartY;
        tParameters[2] = 11; // Size
        tParameters[3] = 0; // Color black
        tParameters[4] = 0x1F; // Background color blue
        tParameters[5] = tModeCharacter;
        interpretCommand(FUNCTION_DRAW_CHAR, tParameters, 6, tByteBuffer, null, 0);

        // ISO_8859_15
        tParameters[0] = SUBFUNCTION_GLOBAL_SET_CODEPAGE;
        tParameters[1] = 15;
        interpretCommand(FUNCTION_GLOBAL_SETTINGS, tParameters, 1, tByteBuffer, null, 0);

        tParameters[0] = SUBFUNCTION_GLOBAL_SET_CHARACTER_CODE_MAPPING;
        tParameters[1] = 0x81;
        tParameters[2] = 0x03A9; // Omega in UTF16
        interpretCommand(FUNCTION_GLOBAL_SETTINGS, tParameters, 2, tByteBuffer, null, 0);

        tParameters[0] = SUBFUNCTION_GLOBAL_SET_CHARACTER_CODE_MAPPING;
        tParameters[1] = 0x82;
        tParameters[2] = 0x2302; // House in UTF16
        interpretCommand(FUNCTION_GLOBAL_SETTINGS, tParameters, 2, tByteBuffer, null, 0);

        tParameters[0] = tStartX + 30;
        tParameters[1] = aStartY;
        tParameters[2] = 11; // Size
        interpretCommand(FUNCTION_DRAW_STRING, tParameters, 6, tByteBuffer, null, tByteBuffer.length);

        // generate chart data
        byte[] tChartBuffer = new byte[aCanvasHeight / 2];
        for (int i = 0; i < tChartBuffer.length; i++) {
            tChartBuffer[i] = (byte) ((i + aStartY) % (aCanvasHeight / 2));
        }
        // DrawChart
        tParameters[0] = 0;
        tParameters[1] = (aCanvasHeight / 2);
        tParameters[2] = 0; // Color black
        tParameters[3] = 0; // No clearing of old chart
        interpretCommand(FUNCTION_DRAW_CHART, tParameters, 4, tChartBuffer, null, tChartBuffer.length);

        // Draw Z shaped chart
        tChartBuffer[0] = (byte) 3;
        tChartBuffer[1] = (byte) 2;
        tChartBuffer[2] = (byte) 1;
        tChartBuffer[3] = (byte) 0;
        tChartBuffer[4] = (byte) (aCanvasHeight / 2);
        tChartBuffer[5] = (byte) ((aCanvasHeight / 2) - 1);
        tChartBuffer[6] = (byte) ((aCanvasHeight / 2) - 2);
        tChartBuffer[7] = (byte) ((aCanvasHeight / 2) - 3);

        tParameters[0] = (aStartY % 30) + tChartBuffer.length;
        interpretCommand(FUNCTION_DRAW_CHART, tParameters, 4, tChartBuffer, null, 8);

        /*
         * compare chart + lines
         */
        tChartBuffer[0] = (byte) (aCanvasHeight / 2 + (aStartY % 30));
        tChartBuffer[1] = (byte) (aCanvasHeight / 2 + (aStartY % 30) + 2);
        tChartBuffer[2] = (byte) (aCanvasHeight / 2 + (aStartY % 30) + 4);
        tChartBuffer[3] = (byte) (aCanvasHeight / 2 + (aStartY % 30) + 8);
        tChartBuffer[4] = (byte) (aCanvasHeight / 2 + (aStartY % 30) + 16);
        tChartBuffer[5] = (byte) (aCanvasHeight / 2 + (aStartY % 30) + 10);
        tChartBuffer[6] = (byte) (aCanvasHeight / 2 + (aStartY % 30) + 16);
        tChartBuffer[7] = (byte) (aCanvasHeight / 2 + (aStartY % 30) + 10);
        tChartBuffer[8] = (byte) (aCanvasHeight / 2 + (aStartY % 30) + 6);
        tChartBuffer[9] = (byte) (aCanvasHeight / 2 + (aStartY % 30) + 2);
        tChartBuffer[10] = (byte) (aCanvasHeight / 2 + (aStartY % 30));

        tParameters[0] = 90;
        tParameters[1] = aCanvasHeight / 2 + (aStartY % 30);
        tParameters[2] = 11; // Size
        tParameters[3] = 0; // Color black
        tParameters[4] = 0x1F; // Background color blue
        tParameters[5] = tModeCharacter;
        interpretCommand(FUNCTION_DRAW_CHAR, tParameters, 6, tByteBuffer, null, 0);

        tParameters[4] = 0xF800; // Color red
        for (int i = 0; i < 10; i++) {
            tParameters[0] = 100 + i;
            tParameters[1] = convertByteToInt(tChartBuffer[i]);
            tParameters[2] = tParameters[0] + 1;
            tParameters[3] = convertByteToInt(tChartBuffer[i + 1]);
            interpretCommand(FUNCTION_DRAW_LINE, tParameters, 5, tByteBuffer, null, 0);
        }

        // DrawChart
        tParameters[0] = 100;
        tParameters[1] = 0;
        tParameters[2] = 0; // Color black
        tParameters[3] = 0; // No clearing of old chart
        interpretCommand(FUNCTION_DRAW_CHART, tParameters, 4, tChartBuffer, null, 11);
    }

    /*
     * The unscaled logo is 450 * 350 and has a left border of 50 and a upper border of 100
     */
    private void drawLogo(int aStartX, int aStartY, int aScaleDivisor) {
        int[] tParameters = new int[6];
        int[] tPathParameters = new int[6];

        // blue rectangle
        tParameters[0] = 50 / aScaleDivisor + aStartX;
        tParameters[1] = 330 / aScaleDivisor + aStartY;
        tParameters[2] = 450 / aScaleDivisor;
        tParameters[3] = 120 / aScaleDivisor;
        tParameters[4] = 0x1F; // Color blue
        interpretCommand(FUNCTION_FILL_RECT_REL, tParameters, 5, null, null, 0);
        tParameters[4] = 0; // Color black
        tParameters[5] = 1; // Stroke width for Draw...
        interpretCommand(FUNCTION_DRAW_RECT_REL, tParameters, 5, null, null, 0);

        // red triangle
        tParameters[0] = 0xF800; // Color red
        tPathParameters[0] = 50 / aScaleDivisor + aStartX;
        tPathParameters[1] = 100 / aScaleDivisor + aStartY;
        tPathParameters[2] = 230 / aScaleDivisor + aStartX;
        tPathParameters[3] = 440 / aScaleDivisor + aStartY;
        tPathParameters[4] = 450 / aScaleDivisor + aStartX;
        tPathParameters[5] = 330 / aScaleDivisor + aStartY;
        interpretCommand(FUNCTION_FILL_PATH, tParameters, 1, null, tPathParameters, 6);
        tParameters[0] = 0x00; // Color black
        tParameters[1] = 1; // Stroke width for Draw...
        interpretCommand(FUNCTION_DRAW_PATH, tParameters, 2, null, tPathParameters, 6);

        // yellow circle
        tParameters[0] = 160 / aScaleDivisor + aStartX;
        tParameters[1] = 210 / aScaleDivisor + aStartY;
        tParameters[2] = 110 / aScaleDivisor;
        tParameters[3] = 0XFFE0; // Color yellow

        interpretCommand(FUNCTION_FILL_CIRCLE, tParameters, 4, null, tPathParameters, 6);
        tParameters[3] = 0x00; // Color black
        tParameters[4] = 1; // Stroke width for Draw..
        interpretCommand(FUNCTION_DRAW_CIRCLE, tParameters, 2, null, tPathParameters, 6);
    }

    /**
     * @return next free y position
     */
    public int drawGraphTestPattern() {
        int tTextSize = 11;
        Canvas tCanvas = new Canvas(mBitmap);

        Paint tTextPaint = new Paint();
        tTextPaint.setStyle(Paint.Style.FILL);
        tTextPaint.setTypeface(Typeface.MONOSPACE);
        tTextPaint.setTextSize(tTextSize);
        tTextPaint.setColor(Color.RED);

        // for description
        Paint tTextPaintInfo = new Paint();
        tTextPaintInfo.setStyle(Paint.Style.FILL);
        tTextPaintInfo.setTypeface(Typeface.MONOSPACE);
        tTextPaintInfo.setTextSize(11);
        tTextPaintInfo.setColor(Color.BLACK);

        Paint tTextBackgroundPaint = new Paint();
        tTextBackgroundPaint.setStyle(Paint.Style.FILL);
        tTextBackgroundPaint.setColor(Color.GREEN);

        Paint tVariableStrokePaint = new Paint();
        tVariableStrokePaint.setColor(Color.BLACK);
        tVariableStrokePaint.setStyle(Paint.Style.STROKE);
        tVariableStrokePaint.setStrokeWidth(1);
        tVariableStrokePaint.setAntiAlias(true);

        Paint tGraph1FillPaint = new Paint();
        tGraph1FillPaint.setColor(Color.BLACK);
        tGraph1FillPaint.setStyle(Paint.Style.FILL);
        tGraph1FillPaint.setStrokeWidth(1);
        tGraph1FillPaint.setAntiAlias(true);

        Paint tFixedStroke2VariableCapPaint = new Paint();
        tFixedStroke2VariableCapPaint.setColor(Color.BLACK);
        tFixedStroke2VariableCapPaint.setStyle(Paint.Style.STROKE);
        tFixedStroke2VariableCapPaint.setStrokeWidth(2);
        tFixedStroke2VariableCapPaint.setAntiAlias(true);


        tCanvas.drawText("Display Width=" + mCurrentCanvasPixelWidth + " of " + mCurrentViewPixelWidth + " Height=" + mCurrentCanvasPixelHeight + " of " + mCurrentViewPixelHeight, 75, tTextSize, tTextPaint);

        // mark corner
        // upper left
        tCanvas.drawRect(0, 0, 3, 3, tTextPaint); // results in 3*3 rect from 0
        // to 2 incl.
        tCanvas.drawText("0,0", 10, 10, tTextPaint);

        // upper right, only outline
        tCanvas.drawRect(mCurrentCanvasPixelWidth - 3, 0, mCurrentCanvasPixelWidth, 3, tVariableStrokePaint);

        //  3x3 not filled rect in lower left
        tCanvas.drawRect(0, mCurrentCanvasPixelHeight - 3, 3, mCurrentCanvasPixelHeight, tVariableStrokePaint);

        /*
         * lower left, test different horizontal rect offsets of 0, 1, 2, 3
         */
        float tXPosition = 0;
        float tYPosition = mCurrentCanvasPixelHeight - 5;
        tCanvas.drawRect(tXPosition, tYPosition, tXPosition + 10, tYPosition, tGraph1FillPaint); // Height 0 -> is not rendered :-)
        tXPosition += 12;
        tCanvas.drawRect(tXPosition, tYPosition, tXPosition + 10, tYPosition + 1, tGraph1FillPaint); // Height 1
        tXPosition += 12;
        tCanvas.drawRect(tXPosition, tYPosition, tXPosition + 10, tYPosition + 2, tGraph1FillPaint); // Height 2
        tXPosition += 12;
        // Height 3 with 1 pixel gap to bottom of canvas
        tCanvas.drawRect(tXPosition, tYPosition, tXPosition + 10, tYPosition + 3, tGraph1FillPaint);

        tYPosition -= 2;
        tXPosition = 1;
        tCanvas.drawRect(tXPosition, tYPosition, tXPosition, tYPosition - 10, tGraph1FillPaint); // Width 0 -> is not rendered :-)
        tXPosition += 5;
        tCanvas.drawRect(tXPosition, tYPosition, tXPosition + 1, tYPosition - 10, tGraph1FillPaint); // Height 1
        tXPosition += 5;
        tCanvas.drawRect(tXPosition, tYPosition, tXPosition + 2, tYPosition - 10, tGraph1FillPaint); // Height 2
        tXPosition += 5;
        tCanvas.drawRect(tXPosition, tYPosition, tXPosition + 3, tYPosition - 10, tGraph1FillPaint);


        // lower right
        tCanvas.drawRect(TEST_CANVAS_WIDTH - 3, TEST_CANVAS_HEIGHT - 3, TEST_CANVAS_WIDTH, TEST_CANVAS_HEIGHT, tTextPaint);
        if (TEST_CANVAS_WIDTH != mCurrentCanvasPixelWidth) {
            tCanvas.drawText((mCurrentCanvasPixelWidth - 1) + "," + (mCurrentCanvasPixelHeight - 1), mCurrentCanvasPixelWidth - 60, mCurrentCanvasPixelHeight - 2, tTextPaint);
            tCanvas.drawRect(mCurrentCanvasPixelWidth - 3, mCurrentCanvasPixelHeight - 3, mCurrentCanvasPixelWidth, mCurrentCanvasPixelHeight, tTextPaint);
        }

        float tStartX;
        float tStartY;
        float tYPos = 25;
        String tString = "Cap=Butt | 5 times (stroke=1/stroke2 1*1-4*4 | 1*4 4*1) each one + 0.2 in Y";

        /*
         * Draw lines point and rects with stroke 1 and 2 and move by 0.2 in y direction
         */
        for (int i = 0; i < 2; i++) {

            tCanvas.drawText(tString, 0, tYPos, tTextPaintInfo);
            tYPos += 5;
            tStartX = 4;
            tStartY = tYPos;
            for (int j = 0; j < 6; j++) {
                tCanvas.drawPoint(tStartX, tStartY, tVariableStrokePaint); // 1 pixel at 0
                tStartX += 3;
                tCanvas.drawPoint(tStartX, tStartY, tFixedStroke2VariableCapPaint);// 4 pixel at
                // 2/3
                tCanvas.drawPoint(tStartX, tStartY, tGraph1FillPaint); // reference
                // point
                tStartX += 2;
                /*
                 * squares
                 */
                for (int k = 1; k < 5; k++) {
                    tCanvas.drawRect(tStartX, tStartY, tStartX + k, tStartY + k, tVariableStrokePaint);
                    tCanvas.drawPoint(tStartX, tStartY, tGraph1FillPaint);// reference
                    // point
                    tStartX += k + 4;
                    tCanvas.drawRect(tStartX, tStartY, tStartX + k, tStartY + k, tFixedStroke2VariableCapPaint);
                    tCanvas.drawPoint(tStartX, tStartY, tGraph1FillPaint);// reference
                    // point
                    tStartX += k + 2;
                }

                tCanvas.drawLine(tStartX, tStartY, tStartX + 4, tStartY, tVariableStrokePaint); // 4
                tCanvas.drawPoint(tStartX, tStartY, tGraph1FillPaint);// reference
                // point
                tStartX += 7;
                tCanvas.drawLine(tStartX, tStartY, tStartX + 4, tStartY, tFixedStroke2VariableCapPaint); // 4x2
                tCanvas.drawPoint(tStartX, tStartY, tGraph1FillPaint);// reference
                // point
                tStartX += 6;
                tCanvas.drawLine(tStartX, tStartY, tStartX, tStartY + 4, tVariableStrokePaint); // 4
                tCanvas.drawPoint(tStartX, tStartY, tGraph1FillPaint);// reference
                // point
                tStartX += 4;
                tCanvas.drawLine(tStartX, tStartY, tStartX, tStartY + 4, tFixedStroke2VariableCapPaint); // 4x2
                tCanvas.drawPoint(tStartX, tStartY, tGraph1FillPaint);// reference
                // point

                tStartX += 4;
                tStartY += 0.2F;
            }
            tVariableStrokePaint.setStrokeCap(Cap.SQUARE);
            tFixedStroke2VariableCapPaint.setStrokeCap(Cap.SQUARE);
            tGraph1FillPaint.setStrokeCap(Cap.SQUARE);
            tString = "Cap=Square";
            tYPos += 20;
        }

        /*
         * Draw stars at different positions
         */
        tVariableStrokePaint.setStrokeCap(Cap.BUTT);
        tFixedStroke2VariableCapPaint.setStrokeCap(Cap.BUTT);
        tYPos += 4;
        tCanvas.drawText("Stars | 4*stroke=1 Y=0,Y=0.5, Y=0.3,Y=0.7 | 2*stroke=2 | 2* corrected", 0, tYPos, tTextPaintInfo);

        tYPos += 20;
        float tX = 10.0f;
        drawStarForTests(tCanvas, tVariableStrokePaint, tGraph1FillPaint, tX, tYPos, 2, 5, 1, 3);

        // at 0.5 / 0.5
        tX = 25.5f;
        drawStarForTests(tCanvas, tVariableStrokePaint, tGraph1FillPaint, tX, (float) (tYPos + 0.5), 2, 5, 1, 3);

        // at 0.3 / 0.3
        tX = 40.3f;
        drawStarForTests(tCanvas, tVariableStrokePaint, tGraph1FillPaint, tX, (float) (tYPos + 0.3), 2, 5, 1, 3);

        // at 0.7 / 0.7
        tX = 55.7f;
        drawStarForTests(tCanvas, tVariableStrokePaint, tGraph1FillPaint, tX, (float) (tYPos + 0.7), 2, 5, 1, 3);

        tX = 90.0f;
        // without correction of origin and length
        drawStarForTests(tCanvas, tFixedStroke2VariableCapPaint, tGraph1FillPaint, tX, tYPos, 6, 6, 3, 2);

        tX += 30.0f;
        // manual length correction - 7 instead of 6 :-)
        drawStarForTests(tCanvas, tFixedStroke2VariableCapPaint, tGraph1FillPaint, tX, tYPos, 6, 7, 3, 3);

        tYPos += 10;
        /*
         * corrected stars - for compatibility mode
         */
        tX += 40.0f;
        // Zoom = 1
        drawStarCorrectedForTests(tCanvas, tVariableStrokePaint, tGraph1FillPaint, 1, tX, tYPos, 8, 12, 4, 4);

        tX += 40.0f;
        // Zoom = 2
        drawStarCorrectedForTests(tCanvas, tVariableStrokePaint, tGraph1FillPaint, 2, tX, tYPos, 8, 12, 4, 4);

        /*
         * graph test
         */
        tYPos -= 10;
        tX = 250;
        tCanvas.drawText("Graph: first BUTT then SQUARE", tX, tYPos, tTextPaintInfo);
        tYPos += 15;
        // draw baselines
        tCanvas.drawLine(tX, tYPos, tX + 40, tYPos, tVariableStrokePaint);
        tCanvas.drawLine(tX, tYPos - 10, tX + 40, tYPos - 10, tGraph1FillPaint);
        tCanvas.drawLine(tX, tYPos, tX + 2, tYPos - 5, tFixedStroke2VariableCapPaint);
        tX += 2;
        tCanvas.drawLine(tX, tYPos - 5, tX + 2, tYPos, tFixedStroke2VariableCapPaint);

        tX += 4;
        tCanvas.drawLine(tX, tYPos, tX + 2, tYPos - 5, tFixedStroke2VariableCapPaint);
        tX += 2;
        tCanvas.drawLine(tX, tYPos - 5, tX + 2, tYPos - 10, tFixedStroke2VariableCapPaint);
        tX += 2;
        tCanvas.drawLine(tX, tYPos - 10, tX + 2, tYPos - 5, tFixedStroke2VariableCapPaint);
        tX += 2;
        tCanvas.drawLine(tX, tYPos - 5, tX + 2, tYPos, tFixedStroke2VariableCapPaint);
        tX += 4;
        tCanvas.drawLine(tX, tYPos, tX + 2, tYPos - 10, tFixedStroke2VariableCapPaint);
        tX += 2;
        tCanvas.drawLine(tX, tYPos - 10, tX + 2, tYPos, tFixedStroke2VariableCapPaint);
        tX += 2;
        tCanvas.drawLine(tX, tYPos, tX + 2, tYPos - 10, tFixedStroke2VariableCapPaint);
        tX += 2;
        tCanvas.drawLine(tX, tYPos - 10, tX + 2, tYPos, tFixedStroke2VariableCapPaint);

        tFixedStroke2VariableCapPaint.setStrokeCap(Cap.SQUARE);
        tX += 4;
        tCanvas.drawLine(tX, tYPos, tX + 2, tYPos - 10, tFixedStroke2VariableCapPaint);
        tX += 2;
        tCanvas.drawLine(tX, tYPos - 10, tX + 2, tYPos, tFixedStroke2VariableCapPaint);
        tX += 2;
        tCanvas.drawLine(tX, tYPos, tX + 2, tYPos - 10, tFixedStroke2VariableCapPaint);
        tX += 2;
        tCanvas.drawLine(tX, tYPos - 10, tX + 2, tYPos, tFixedStroke2VariableCapPaint);

        tYPos += 20;
        tX = 250;
        tCanvas.drawText("Lines with StrokeWidth 1-5", tX, tYPos, tTextPaintInfo);
        tYPos += 10;
        // draw lines
        tCanvas.drawLine(tX, tYPos, tX + 10, tYPos, tVariableStrokePaint);
        tVariableStrokePaint.setStrokeWidth(2);
        tCanvas.drawLine(tX + 11, tYPos, tX + 20, tYPos, tVariableStrokePaint);
        tVariableStrokePaint.setStrokeWidth(3);
        tCanvas.drawLine(tX + 21, tYPos, tX + 30, tYPos, tVariableStrokePaint);
        tVariableStrokePaint.setStrokeWidth(4);
        tCanvas.drawLine(tX + 31, tYPos, tX + 40, tYPos, tVariableStrokePaint);
        tVariableStrokePaint.setStrokeWidth(5);
        tCanvas.drawLine(tX + 41, tYPos, tX + 50, tYPos, tVariableStrokePaint);


        /*
         * Draw vertical non aliased lines of different stoke sizes to check different sizes depending on position
         */
        tYPos += 10;
        float startXFloat = 10;
        tCanvas.drawText("Non aliased lines at rounded and float positions with rounded and float StrokeWidth += 0.3", startXFloat, tYPos, tTextPaintInfo);
        tYPos += 5;

        tVariableStrokePaint.setAntiAlias(false);

        Paint tRoundedStrokePaint = new Paint();
        tRoundedStrokePaint.setColor(Color.BLACK);
        tRoundedStrokePaint.setStyle(Paint.Style.STROKE);

        float tStrokeWidthFloat = 0.7F;
        for (int i = 0; i < 10; i++) {
            /*
             * Draw 10 Vertical lines at rounded and non rounded X positions
             * If position or stroke is rounded, line size is independent of position
             */
            tStrokeWidthFloat += 0.3F;
            tVariableStrokePaint.setStrokeWidth(tStrokeWidthFloat);
            tRoundedStrokePaint.setStrokeWidth(Math.round(tStrokeWidthFloat));
            for (int j = 0; j < 5; j++) {
                tCanvas.drawLine(Math.round(startXFloat), tYPos, Math.round(startXFloat), tYPos + 10, tRoundedStrokePaint);
                tCanvas.drawLine(Math.round(startXFloat), tYPos + 10, Math.round(startXFloat), tYPos + 20, tVariableStrokePaint);
                tCanvas.drawLine(startXFloat, tYPos + 20, startXFloat, tYPos + 30, tVariableStrokePaint);
                startXFloat += 10.3F;
            }
            startXFloat += 10;
        }
        return (int) (tYPos + 12);
    }

    /**
     * Font size and background test
     *
     * @return next free y position
     */
    @SuppressLint("DefaultLocale")
    public int drawFontTest(float aYStartPosition) {
        float tTextSize = 11;
        float tTextSizeInfo = 15;
        Canvas tCanvas = new Canvas(mBitmap);

        Paint tTextPaint = new Paint();
        tTextPaint.setStyle(Paint.Style.FILL);
        tTextPaint.setTypeface(Typeface.MONOSPACE);
        tTextPaint.setTextSize(tTextSize);

        // for output
        Paint tInfoText = new Paint();
        tInfoText.setStyle(Paint.Style.FILL);
        tInfoText.setTypeface(Typeface.MONOSPACE);
        tInfoText.setTextSize(tTextSizeInfo);

        Paint tTextBackgroundPaint = new Paint();
        tTextBackgroundPaint.setStyle(Paint.Style.FILL);
        tTextBackgroundPaint.setColor(Color.GREEN);

        Paint tStroke1UnaliasedPaint = new Paint();
        tStroke1UnaliasedPaint.setColor(Color.BLACK);
        tStroke1UnaliasedPaint.setStyle(Paint.Style.STROKE);
        tStroke1UnaliasedPaint.setStrokeWidth(1);

        Path tPath = new Path();
        RectF tRect = new RectF();

        tInfoText.setColor(Color.RED);

        float tYPos = aYStartPosition + tTextSize;
        tCanvas.drawText("Font INFO:    SIZE|ASCENT|DESCENT|WIDTH", 100, tYPos, tInfoText);
        tInfoText.setColor(Color.BLACK);

        float startX;
        tYPos += tTextSize + 4;

        String tExampleString = "gti";
        String tString;
        /*
         * Output TextSize-TextWidth for Size 5-75
         */
        MyLog.i(LOG_TAG, "Font metrics:");
        int j = 5;
        for (int k = 0; k < 15; k++) {
            StringBuilder tFontsizes = new StringBuilder(100);
            StringBuilder tFontsizesStringForLog = new StringBuilder(150);
            for (int i = 0; i < 5; i++) {
                tFontsizes.append(j);
                tFontsizesStringForLog.append(j);
                tFontsizes.append('|');
                tFontsizesStringForLog.append(';');

                tTextPaint.setTextSize(j);
                int tFontWidth = (int) (tTextPaint.measureText(tExampleString) / tExampleString.length());
                tString = String.format("%3.1f|", -tTextPaint.ascent());
                tFontsizes.append(tString);
                tString = String.format("%7.4f;", -tTextPaint.ascent());
                tFontsizesStringForLog.append(tString);
                tString = String.format("%3.1f|", tTextPaint.descent());
                tFontsizes.append(tString);
                tString = String.format("%7.4f;", tTextPaint.descent());
                tFontsizesStringForLog.append(tString);
                tFontsizes.append(tFontWidth);
                tFontsizesStringForLog.append(tFontWidth);
                tTextPaint.getTextPath(tExampleString, 0, tExampleString.length(), 10, 10, tPath);
                tPath.computeBounds(tRect, false);
                tFontsizesStringForLog.append(";").append(10 - tRect.top);
                tFontsizesStringForLog.append(";").append(tRect.bottom - 10);
                tFontsizes.append(" - ");
                tFontsizesStringForLog.append("\r\n");
                j++;
            }
            tYPos += tTextSizeInfo;
            tCanvas.drawText(tFontsizes.toString(), 0, tYPos, tInfoText);
            MyLog.i(LOG_TAG, tFontsizesStringForLog.toString());
        }

        tYPos += tTextSizeInfo;
        tYPos += tTextSizeInfo;
        tInfoText.setColor(Color.RED);
        tCanvas.drawText("draw text with background determined by ascent and decent", 0, tYPos, tInfoText);
        tYPos += tTextSizeInfo;

        /*
         * draw text with background determined by ascent and decent.
         */
        float[] tTextSizesArray = {11.0f, 12.0f, 13.0f, 16.0f, 22.0f, 24.0f, 28.0f, 32.0f, 33.0f, 44.0f, 48.0f};
        float tEndX;
        tYPos += 60;
        startX = 0;
        tCanvas.drawLine(startX, tYPos, startX + 20, tYPos, tStroke1UnaliasedPaint); // Base line
        startX += 20;
        for (float v : tTextSizesArray) {
            tTextSize = v;
            tTextPaint.setTextSize(tTextSize);
            tEndX = startX + (3 * ((tTextSize * 6) + 4) / 10);
            tCanvas.drawRect(startX, tYPos - (float) (tTextSize * 0.928), tEndX, tYPos + (float) (tTextSize * 0.235), tTextBackgroundPaint);
            tCanvas.drawText(tExampleString, startX, tYPos, tTextPaint);
            startX = tEndX + 3;
        }
        tCanvas.drawLine(startX + 3, tYPos, startX + 20, tYPos, tStroke1UnaliasedPaint); // Base line
        tYPos += tTextSizeInfo;
        tYPos += tTextSizeInfo;
        tCanvas.drawText("draw text with background determined by real ascent and decent from getTextPath()", 0, tYPos, tInfoText);
        tYPos += tTextSizeInfo;
        /*
         * draw text with background determined by real ascent and decent - derived from getTextPath().
         */

        tYPos += 60;
        startX = 0;
        tCanvas.drawLine(startX, tYPos, startX + 20, tYPos, tStroke1UnaliasedPaint); // Base line
        startX += 20;
        for (float v : tTextSizesArray) {
            tTextSize = v;
            tTextPaint.setTextSize(tTextSize);
            tEndX = startX + (3 * ((tTextSize * 6) + 4) / 10);
            tCanvas.drawRect(startX, tYPos - tTextSize * TEXT_ASCEND_FACTOR, tEndX, tYPos + tTextSize * TEXT_DESCEND_FACTOR, tTextBackgroundPaint);
            tCanvas.drawText(tExampleString, startX, tYPos, tTextPaint);
            startX = tEndX + 3;
        }
        tCanvas.drawLine(startX + 3, tYPos, startX + 20, tYPos, tStroke1UnaliasedPaint); // Base line

        MyLog.i(LOG_TAG, "Font test last tYPos=" + tYPos);
        return (int) (tYPos + tTextSize);
    }

    private void drawStarForTests(Canvas tCanvas, Paint aPaint, Paint aFillPaint, float tX, float tY, int tOffsetCenter, int tLength, int tOffsetDiagonal, int tLengthDiagonal) {

        tLength--;
        tLengthDiagonal += tOffsetDiagonal - 1;

        float X = tX + tOffsetCenter;
        tCanvas.drawLine(X, tY, X + tLength, tY, aPaint);
        tCanvas.drawPoint(X, tY, aFillPaint);
        tCanvas.drawLine(X, tY - tOffsetDiagonal, X + tLength, tY - tLengthDiagonal, aPaint);// <
        // 45
        // degree
        tCanvas.drawPoint(X, tY - tOffsetDiagonal, aFillPaint);
        tCanvas.drawLine(X, tY + tOffsetDiagonal, X + tLength, tY + tLengthDiagonal, aPaint); // <
        // 45
        // degree
        tCanvas.drawPoint(X, tY + tOffsetDiagonal, aFillPaint);
        tCanvas.drawLine(X, tY + tOffsetCenter, X + tLength, tY + tOffsetCenter + tLength, aPaint); // 45
        // degree
        // +
        tCanvas.drawPoint(X, tY + tOffsetCenter, aFillPaint);

        float Y = tY + tOffsetCenter;
        tCanvas.drawLine(tX, Y, tX, Y + tLength, aPaint);
        tCanvas.drawPoint(tX, Y, aFillPaint);
        tCanvas.drawLine(tX - tOffsetDiagonal, Y, tX - tLengthDiagonal, Y + tLength, aPaint);
        tCanvas.drawPoint(tX - tOffsetDiagonal, Y, aFillPaint);
        tCanvas.drawLine(tX + tOffsetDiagonal, Y, tX + tLengthDiagonal, Y + tLength, aPaint);
        tCanvas.drawPoint(tX + tOffsetDiagonal, Y, aFillPaint);
        tCanvas.drawLine(tX - tOffsetCenter, Y, tX - tOffsetCenter - tLength, Y + tLength, aPaint); // 45
        // degree
        // +
        tCanvas.drawPoint(tX - tOffsetCenter, Y, aFillPaint);

        X = tX - tOffsetCenter;
        tCanvas.drawLine(X, tY, X - tLength, tY, aPaint);
        tCanvas.drawPoint(X, tY, aFillPaint);
        tCanvas.drawLine(X, tY - tOffsetDiagonal, X - tLength, tY - tLengthDiagonal, aPaint);
        tCanvas.drawPoint(X, tY - tOffsetDiagonal, aFillPaint);
        tCanvas.drawLine(X, tY + tOffsetDiagonal, X - tLength, tY + tLengthDiagonal, aPaint);
        tCanvas.drawPoint(X, tY + tOffsetDiagonal, aFillPaint);
        tCanvas.drawLine(X, tY - tOffsetCenter, X - tLength, tY - tOffsetCenter - tLength, aPaint); // 45
        // degree
        // +
        tCanvas.drawPoint(X, tY - tOffsetCenter, aFillPaint);

        Y = tY - tOffsetCenter;
        tCanvas.drawLine(tX, Y, tX, Y - tLength, aPaint);
        tCanvas.drawPoint(tX, Y, aFillPaint);
        tCanvas.drawLine(tX - tOffsetDiagonal, Y, tX - tLengthDiagonal, Y - tLength, aPaint);
        tCanvas.drawPoint(tX - tOffsetDiagonal, Y, aFillPaint);
        tCanvas.drawLine(tX + tOffsetDiagonal, Y, tX + tLengthDiagonal, Y - tLength, aPaint);
        tCanvas.drawPoint(tX + tOffsetDiagonal, Y, aFillPaint);
        tCanvas.drawLine(tX + tOffsetCenter, Y, tX + tOffsetCenter + tLength, Y - tLength, aPaint); // 45
        // degree
        // +
        tCanvas.drawPoint(tX + tOffsetCenter, Y, aFillPaint);

        tCanvas.drawPoint(tX, tY, aFillPaint);
    }

    private void drawStarCorrectedForTests(Canvas tCanvas, Paint aPaint, Paint aFillPaint, int aZoom, float aX, float aY, int tOffsetCenter, int tLength, int tOffsetDiagonal, int tLengthDiagonal) {
        int tX = (int) aX;
        int tY = (int) aY;

        tLength--;
        tLengthDiagonal += tOffsetDiagonal - 1;

        int X = tX + tOffsetCenter;
        for (int i = 0; i < 2; i++) {
            drawCorrectedLineWithStartPixelForTests(X, tY, X + tLength, tY, aZoom, aPaint, tCanvas, aFillPaint);
            drawCorrectedLineWithStartPixelForTests(X, tY - tOffsetDiagonal, X + tLength, tY - tLengthDiagonal, aZoom, aPaint, tCanvas, aFillPaint);// < 45
            drawCorrectedLineWithStartPixelForTests(X, tY + tOffsetDiagonal, X + tLength, tY + tLengthDiagonal, aZoom, aPaint, tCanvas, aFillPaint); // < 45
            X = tX - tOffsetCenter;
            tLength = -tLength;
        }

        int Y = tY + tOffsetCenter;
        for (int i = 0; i < 2; i++) {
            drawCorrectedLineWithStartPixelForTests(tX, Y, tX, Y + tLength, aZoom, aPaint, tCanvas, aFillPaint);
            drawCorrectedLineWithStartPixelForTests(tX - tOffsetDiagonal, Y, tX - tLengthDiagonal, Y + tLength, aZoom, aPaint, tCanvas, aFillPaint);
            drawCorrectedLineWithStartPixelForTests(tX + tOffsetDiagonal, Y, tX + tLengthDiagonal, Y + tLength, aZoom, aPaint, tCanvas, aFillPaint);
            Y = tY - tOffsetCenter;
            tLength = -tLength;
        }

        X = tX + tOffsetCenter;
        tLengthDiagonal = tOffsetCenter + tLength;
        for (int i = 0; i < 2; i++) {
            drawCorrectedLineWithStartPixelForTests(X, tY - tOffsetCenter, X + tLength, tY - tLengthDiagonal, aZoom, aPaint, tCanvas, aFillPaint); // 45
            drawCorrectedLineWithStartPixelForTests(X, tY + tOffsetCenter, X + tLength, tY + tLengthDiagonal, aZoom, aPaint, tCanvas, aFillPaint); // 45

            X = tX - tOffsetCenter;
            tLength = -tLength;
        }

        tCanvas.drawPoint(tX, tY, aFillPaint);
    }

    private void drawCorrectedLineWithStartPixelForTests(int aStartX, int aStartY, int aStopX, int aStopY, int aScaleFactor, Paint aPaint, Canvas tCanvas, Paint aFillPaint) {
        drawLengthCorrectedLine(aStartX, aStartY, aStopX, aStopY, aScaleFactor, aPaint, tCanvas);
        tCanvas.drawPoint(aStartX, aStartY, aFillPaint);
    }

}
