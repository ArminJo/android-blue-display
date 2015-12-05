/*
 * 	SUMMARY
 * 	Blue Display is an Open Source Android remote Display for Arduino etc.
 * 	It receives basic draw requests from Arduino etc. over Bluetooth and renders it.
 * 	It also implements basic GUI elements as buttons and sliders.
 * 	It sends touch or GUI callback events over Bluetooth back to Arduino.
 * 
 *  Copyright (C) 2014  Armin Joachimsmeyer
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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.SparseArray;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.View;
import android.widget.Toast;

public class RPCView extends View {

	public static final String LOG_TAG = "BD";

	BlueDisplay mBlueDisplayContext;

	private static final int MAX_REFERENCE_CANVAS_WIDTH = 1280;
	// private static final int MAX_REFERENCE_CANVAS_HEIGHT = 800; // not used yet

	protected int mActualCanvasWidth;
	protected int mActualCanvasHeight;
	protected int mRequestedCanvasWidth;
	protected int mRequestedCanvasHeight;
	protected int mActualViewHeight; // Display Height - StatusBar - TitleBar
	protected int mActualViewWidth; // Display Width

	ToneGenerator mToneGenerator;
	int mActualToneVolume;

	// 4 values for one line - 4 lines possible
	public static float[][] mChartScreenBuffer = new float[4][MAX_REFERENCE_CANVAS_WIDTH * 4];

	public static int mChartScreenBufferActualLength = 0;
	public static float mChartScreenBufferXStart = 0;

	public static Bitmap mBitmap;
	private Paint mBitmapPaint; // only used for onDraw() to draw bitmap
	private Paint mInfoPaint; // for internal info text
	private Paint mTextPaint; // for all scaled text
	private Paint mTextBackgroundPaint; // for all scaled text background
	private Paint mGraphPaintStroke1Fill; // for circle, rectangles and path
	private Paint mGraphPaintStrokeScaleFactor; // for pixel, line and chart
	private Paint mGraphPaintStrokeSettable; // user settable for pixel, line and chart

	/*
	 * All values are input values (for scale factor = 1.0)
	 */
	private int mTextPrintTextActualPosX; // for printf implementation
	private int mTextPrintTextActualPosY; // for printf implementation
	private int mTextPrintTextSize = 12; // for printf implementation

	private Paint mTextPrintPaint; // for printf implementation
	private int mTextPrintBackgroundColor = Color.BLACK; // for printf implementation
	private boolean mTextPrintDoClearScreenOnWrap = true; // for printf implementation

	private Canvas mCanvas;
	private Path mPath = new Path();

	private final Handler mHandler;

	public static char[] sCharsArray = new char[1024];

	/*
	 * Scaling
	 */
	protected float mScaleFactor = 1;
	protected float mMaxScaleFactor;
	float mTouchScaleFactor = 1;
	Toast mLastScaleValueToast;

	/*
	 * Flags which can be set by client
	 */
	private boolean mUseMaxSize;
	protected boolean mTouchBasicEnable; // send down, (move) and up events
	protected boolean mTouchMoveEnable; // can be used to suppress only the move events if mTouchBasicEnable is true
	private boolean mIsLongTouchEnabled;
	boolean mUseUpEventForButtons;

	long mLongTouchDownTimeoutMillis = 800;

	/*
	 * Flags for touch event handler
	 */
	protected boolean mShowTouchCoordinates = false;
	private final float SWIPE_LIMIT = 10; // 10 pixel
	private final float MICRO_MOVE_LIMIT = 5; // 5 pixel to avoid killing of long touch recognition
	private boolean mMultiTouchDetected = false;
	boolean mTouchIsActive = true;
	// True if switching of mUseUpEventForButtons to true just occurs while button was down (mTouchIsActive == true)
	boolean mDisableButtonUpOnce = false;
	private float mTouchDownPositionX;
	private float mTouchDownPositionY;
	int mTouchStartsOnButtonNumber = -1; // number of button for last touch_down event
	boolean mTouchStartsOnSlider = false; // true if touch starts on a slider, used to distinguish between slider and swipe moves
	boolean mSkipProcessingUntilTouchUp = false; // true if touch down already sends an event (eg. a button down event)
	public static boolean mDeviceListActivityLaunched = false; // to prevent multiple launches of DeviceListActivity()
	private ScaleGestureDetector mScaleDetector;

	// If used as background color for char or text, the background will not filled. Must sign extend constant to 32 bit, since
	// parameter is also sign extended.
	public static final int COLOR_NO_BACKGROUND = 0XFFFFFFFE;
	// will not clear the rest of the line for multiline text
	public static final int COLOR_NO_BACKGROUND_EXTEND = 0XFFFFFFFD;
	public static final int COLOR_WHITE_PARAMETER = 0XFFFFFFFF;

	/*
	 * region description of tags
	 */
	// Tags for data buffer 0-7
	public static final int INDEX_LAST_FUNCTION_DATAFIELD = 0x07;

	// Internal functions 8-F
	public static final int INDEX_LAST_FUNCTION_INTERNAL = 0x0F;

	// Display (draw) functions 10-3F
	public static final int INDEX_LAST_FUNCTION_DISPLAY = 0x3F;

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
	 * Data buffer tags
	 */
	// private final int DATAFIELD_TAG_BYTE = 0x01;
	// private final int DATAFIELD_TAG_SHORT = 0x02;
	// private final int DATAFIELD_TAG_INT = 0x03;
	// private final int DATAFIELD_TAG_LONG = 0x04;
	// private final int DATAFIELD_TAG_FLOAT = 0x05;
	// private final int DATAFIELD_TAG_DOUBLE = 0x06;

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
	// Flags for SUBFUNCTION_GLOBAL_SET_SCREEN_ORIENTATION_LOCK
	// private final static int FLAG_SCREEN_ORIENTATION_LOCK_LANDSCAPE = 0x00;
	private final static int FLAG_SCREEN_ORIENTATION_LOCK_PORTRAIT = 0x01;
	private final static int FLAG_SCREEN_ORIENTATION_LOCK_ACTUAL = 0x02;
	private final static int FLAG_SCREEN_ORIENTATION_LOCK_UNLOCK = 0x03;

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
	private final static int SUBFUNCTION_GET_INFO_ = 0x00;

	private final static int FUNCTION_PLAY_TONE = 0x0F;

	// used for Sync
	private final static int FUNCTION_NOP = 0x7F;

	/*
	 * Display functions
	 */
	private final static int FUNCTION_CLEAR_DISPLAY = 0x10;
	public final static int FUNCTION_DRAW_DISPLAY = 0x11;
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

	private final static int FUNCTION_WRITE_SETTINGS = 0x34;
	// Flags for WRITE_SETTINGS
	private final static int FLAG_WRITE_SETTINGS_SET_SIZE_AND_COLORS_AND_FLAGS = 0x00;
	private final static int FLAG_WRITE_SETTINGS_SET_POSITION = 0x01;
	private final static int FLAG_WRITE_SETTINGS_SET_LINE_COLUMN = 0x02;

	// Variable parameter length
	private final static int FUNCTION_DRAW_STRING = 0x60;
	private final static int FUNCTION_DEBUG_STRING = 0x61;
	private final static int FUNCTION_WRITE_STRING = 0x62;

	private final static int FUNCTION_GET_NUMBER_WITH_SHORT_PROMPT = 0x64;
	private final static int FUNCTION_GET_TEXT_WITH_SHORT_PROMPT = 0x65;

	private final static int FUNCTION_DRAW_PATH = 0x68;
	private final static int FUNCTION_FILL_PATH = 0x69;
	final static int FUNCTION_DRAW_CHART = 0x6A;
	final static int FUNCTION_DRAW_CHART_WITHOUT_DIRECT_RENDERING = 0x6B;

	private static final int LONG_TOUCH_DOWN = 0;

	/*
	 * Action code to action string mappings for log output
	 */
	public static final SparseArray<String> sActionMappings = new SparseArray<String>(20);

	static {
		sActionMappings.put(MotionEvent.ACTION_DOWN, "down");
		sActionMappings.put(MotionEvent.ACTION_UP, "up");
		sActionMappings.put(MotionEvent.ACTION_MOVE, "move");
		sActionMappings.put(MotionEvent.ACTION_CANCEL, "cancel");
		sActionMappings.put(BluetoothSerialService.EVENT_CONNECTION_BUILD_UP, "connection build up");
		sActionMappings.put(BluetoothSerialService.EVENT_REDRAW_ACTION, "redraw");
		sActionMappings.put(BluetoothSerialService.EVENT_REORIENTATION_ACTION, "reorientation");
		sActionMappings.put(BluetoothSerialService.EVENT_LONG_TOUCH_DOWN_CALLBACK_ACTION, "long down");
		sActionMappings.put(BluetoothSerialService.EVENT_FIRST_CALLBACK_ACTION, "first");
		sActionMappings.put(BluetoothSerialService.EVENT_BUTTON_CALLBACK_ACTION, "button");
		sActionMappings.put(BluetoothSerialService.EVENT_SLIDER_CALLBACK_ACTION, "slider");

		sActionMappings.put(BluetoothSerialService.EVENT_SWIPE_CALLBACK_ACTION, "swipe");
		sActionMappings.put(BluetoothSerialService.EVENT_NUMBER_CALLBACK, "number");
		sActionMappings.put(BluetoothSerialService.EVENT_INFO_CALLBACK, "info");
		sActionMappings.put(BluetoothSerialService.EVENT_FIRST_SENSOR_ACTION_CODE + Sensor.TYPE_ACCELEROMETER, "Accelerometer");
		sActionMappings.put(BluetoothSerialService.EVENT_FIRST_SENSOR_ACTION_CODE + Sensor.TYPE_GRAVITY, "Gravity");
		sActionMappings.put(BluetoothSerialService.EVENT_FIRST_SENSOR_ACTION_CODE + Sensor.TYPE_GYROSCOPE, "Gyroscope");
		sActionMappings.put(BluetoothSerialService.EVENT_FIRST_SENSOR_ACTION_CODE + Sensor.TYPE_LINEAR_ACCELERATION,
				"LinAccelation");
		sActionMappings.put(BluetoothSerialService.EVENT_FIRST_SENSOR_ACTION_CODE + Sensor.TYPE_MAGNETIC_FIELD, "Magnetic");
		sActionMappings.put(BluetoothSerialService.EVENT_NOP_ACTION, "nop (for sync)");
	}

	protected void setMaxScaleFactor() {
		float tMaxHeightFactor = (float) mActualViewHeight / mRequestedCanvasHeight;
		float tMaxWidthFactor = (float) mActualViewWidth / mRequestedCanvasWidth;
		mMaxScaleFactor = Math.min(tMaxHeightFactor, tMaxWidthFactor);
		if (MyLog.isDEBUG()) {
			MyLog.d(LOG_TAG, "MaxScaleFactor=" + mMaxScaleFactor);
		}
	}

	@SuppressWarnings("deprecation")
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
		mToneGenerator = new ToneGenerator(AudioManager.STREAM_SYSTEM, ToneGenerator.MAX_VOLUME);
		mTextPaint = new Paint();
		mTextPaint.setTypeface(Typeface.MONOSPACE);
		mTextPaint.setStyle(Paint.Style.FILL);

		mTextPrintPaint = new Paint();
		mTextPrintPaint.setStrokeWidth(1);
		mTextPrintPaint.setStyle(Paint.Style.FILL);
		mTextPrintPaint.setTypeface(Typeface.MONOSPACE);
		// default values
		mTextPrintPaint.setTextSize(12);
		mTextPrintPaint.setColor(Color.GRAY);

		// For output of touch coordinates
		mInfoPaint = new Paint();
		mInfoPaint.setTypeface(Typeface.MONOSPACE);
		mInfoPaint.setStyle(Paint.Style.FILL);
		mInfoPaint.setTextSize(22);
		mInfoPaint.setColor(Color.BLACK);

		mTextBackgroundPaint = new Paint();
		mTextBackgroundPaint.setStrokeWidth(1);
		mTextBackgroundPaint.setStyle(Paint.Style.FILL);

		// no anti alias or +0.5 for lines with stroke 1 and 1 for lines with
		// stroke 2
		// for fillRect
		mGraphPaintStroke1Fill = new Paint();
		mGraphPaintStroke1Fill.setStrokeWidth(1);
		mGraphPaintStroke1Fill.setStyle(Paint.Style.FILL);

		mGraphPaintStrokeScaleFactor = new Paint();
		mGraphPaintStrokeScaleFactor.setStyle(Paint.Style.STROKE);
		// mGraphPaintStrokeScaleFactor.setStrokeCap(Cap.BUTT);

		mGraphPaintStrokeSettable = new Paint();
		mGraphPaintStrokeSettable.setStyle(Paint.Style.STROKE);

		/*
		 * create start Bitmap
		 */
		// be prepared...
		try {
			Point tDisplaySize = new Point();
			mBlueDisplayContext.getWindowManager().getDefaultDisplay().getSize(tDisplaySize);
			mRequestedCanvasWidth = tDisplaySize.x;
			mRequestedCanvasHeight = tDisplaySize.y;
		} catch (NoSuchMethodError e) {
			mRequestedCanvasWidth = mBlueDisplayContext.getWindowManager().getDefaultDisplay().getWidth();
			mRequestedCanvasHeight = mBlueDisplayContext.getWindowManager().getDefaultDisplay().getHeight();
		}

		mActualCanvasWidth = mRequestedCanvasWidth;
		mActualCanvasHeight = mRequestedCanvasHeight;
		mBitmap = Bitmap.createBitmap(mActualCanvasWidth, mActualCanvasHeight, Bitmap.Config.ARGB_8888);
		// mBitmap.setHasAlpha(false);

		mBitmapPaint = new Paint();

		mCanvas = new Canvas(mBitmap);
		mCanvas.drawColor(Color.WHITE); // background
		initCharMappingArray();
	}

	@Override
	public void onSizeChanged(int aWidth, int aHeight, int aOldWidth, int aOldHeight) {
		// Is called on start and on changing orientation
		if (MyLog.isINFO()) {
			Log.i(LOG_TAG, "++ ON SizeChanged width=" + aWidth + " height=" + aHeight + " old width=" + aOldWidth + " old height="
					+ aOldHeight);
		}

		mActualViewWidth = aWidth;
		mActualViewHeight = aHeight;
		setMaxScaleFactor();

		// resize canvas
		float tScaleFactor = mScaleFactor;
		if (mUseMaxSize) {
			tScaleFactor = 10;
		}

		// send new max size to client
		if (mBlueDisplayContext.mSerialService != null) {
			mBlueDisplayContext.mSerialService.writeTwoIntegerEvent(BluetoothSerialService.EVENT_REORIENTATION_ACTION, aWidth,
					aHeight);
		}
		// scale and do not send redraw event, since it may overwrite the client buffer of former reorientation event
		setScaleFactor(tScaleFactor, true, false);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		if (MyLog.isVERBOSE()) {
			Log.v(LOG_TAG, "+ ON Draw +");
		}
		if (mBlueDisplayContext.mSerialService != null) {
			boolean tRetrigger = mBlueDisplayContext.mSerialService.searchCommand(this);
			canvas.drawBitmap(mBitmap, 0, 0, mBitmapPaint);
			if (tRetrigger) {
				// Trigger next frame
				invalidate();
			}
		} else {
			canvas.drawBitmap(mBitmap, 0, 0, mBitmapPaint);
		}
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	/**
	 * Handle touch events:
	 * 1. Process event by scale detector
	 * 2. Filter out multi touch
	 * 3. Handle long touch down recognition
	 * 4. Show touch coordinates if locally enabled
	 * 5. Detect swipes
	 * 6. Send swipe, slider and button callback events
	 * 7. Send touch events except if they were outside of canvas or consumed by slider or button or touchUp event for swipe
	 * 
	 * (non-Javadoc)
	 * @see android.view.View#onTouchEvent(android.view.MotionEvent)
	 */
	public boolean onTouchEvent(MotionEvent aEvent) {
		float tDistanceFromTouchDown = 0;
		boolean doNotRemoveLongTouchDownMessage = false; // Flag for suppressing micro moves on long touch down

		int tMaskedAction = aEvent.getActionMasked();

		if (MyLog.isVERBOSE()) {
			MyLog.v(LOG_TAG, "TouchEvent action=" + tMaskedAction);
		}
		// process event also by scale detector
		mScaleDetector.onTouchEvent(aEvent);

		if (aEvent.getActionIndex() > 0) {
			// multi touch started
			mMultiTouchDetected = true;
		}

		if (mShowTouchCoordinates) {
			int tXPos = (int) (aEvent.getX() + 0.5);
			int tYPos = (int) (aEvent.getY() + 0.5);
			int tXPosScaled = (int) (aEvent.getX() / mScaleFactor + 0.5);
			int tYPosScaled = (int) (aEvent.getY() / mScaleFactor + 0.5);
			mTextBackgroundPaint.setColor(Color.WHITE);
			mCanvas.drawRect(0, 0, 210, 24, mTextBackgroundPaint);
			mCanvas.drawText(tXPos + "/" + tYPos + "->" + tXPosScaled + "/" + tYPosScaled, 0, 20, mInfoPaint);
			invalidate();
		}

		switch (tMaskedAction) {
		case MotionEvent.ACTION_DOWN:
			mTouchIsActive = true;
			mTouchDownPositionX = aEvent.getX();
			mTouchDownPositionY = aEvent.getY();
			mMultiTouchDetected = false;
			break;

		case MotionEvent.ACTION_MOVE:
			tDistanceFromTouchDown = Math.max(Math.abs(mTouchDownPositionX - aEvent.getX()),
					Math.abs(mTouchDownPositionY - aEvent.getY()));
			// avoid to disable long touch down recognition on pseudo or micro moves
			if (tDistanceFromTouchDown < MICRO_MOVE_LIMIT) {
				doNotRemoveLongTouchDownMessage = true;
			}
			break;

		case MotionEvent.ACTION_UP:
		case MotionEvent.ACTION_CANCEL:
		default:
			mTouchIsActive = false;
			mMultiTouchDetected = false;
			if (mSkipProcessingUntilTouchUp) {
				mSkipProcessingUntilTouchUp = false;
				mTouchStartsOnButtonNumber = -1;
				return true;
			}
			break;
		}

		if (mSkipProcessingUntilTouchUp) {
			// do not process ACTION_MOVE
			return true;
		}

		if (mIsLongTouchEnabled && !doNotRemoveLongTouchDownMessage) {
			// reset long touch recognition by clearing messages
			mLongTouchDownHandler.removeMessages(LONG_TOUCH_DOWN);
		}

		// check if event is on CANVAS
		if (aEvent.getX() > mActualCanvasWidth || aEvent.getY() > mActualCanvasHeight) {
			// open options menu if touch outside of canvas
			if (tMaskedAction == MotionEvent.ACTION_UP) {
				mBlueDisplayContext.openOptionsMenu();
			}
		} else if (!mMultiTouchDetected && mBlueDisplayContext.mSerialService != null) {
			if (mBlueDisplayContext.mSerialService.getState() == BluetoothSerialService.STATE_NONE
					&& mDeviceListActivityLaunched == false) {
				// Launch the DeviceListActivity to choose device
				Intent serverIntent = new Intent(mBlueDisplayContext, DeviceListActivity.class);
				mBlueDisplayContext.startActivityForResult(serverIntent, BlueDisplay.REQUEST_CONNECT_DEVICE);
				mDeviceListActivityLaunched = true;
			}
			if (mBlueDisplayContext.mSerialService.getState() == BluetoothSerialService.STATE_CONNECTED) {
				mDeviceListActivityLaunched = false;
				/*
				 * send data if connected and if no multi-touch is present
				 */

				if (tMaskedAction == MotionEvent.ACTION_UP && !mTouchStartsOnSlider) {
					/*
					 * Check SWIPE callback only if touch did not start on a slider
					 */
					tDistanceFromTouchDown = Math.max(Math.abs(mTouchDownPositionX - aEvent.getX()),
							Math.abs(mTouchDownPositionY - aEvent.getY()));
					/*
					 * SWIPE only if move is greater than SWIPE_LIMIT. If SWIPE starts on a button then swipe must be even greater.
					 */
					if ((mTouchStartsOnButtonNumber < 0 && tDistanceFromTouchDown > SWIPE_LIMIT)
							|| tDistanceFromTouchDown > 4 * SWIPE_LIMIT) {
						int tDeltaX = (int) ((aEvent.getX() - mTouchDownPositionX) / mScaleFactor);
						int tDeltaY = (int) ((aEvent.getY() - mTouchDownPositionY) / mScaleFactor);
						int tIsXDirection = 0;
						if (Math.abs(tDeltaX) >= Math.abs(tDeltaY)) {
							// horizontal swipe
							tIsXDirection = 1;
						}
						// send swipe event and suppress UP-event sending
						mBlueDisplayContext.mSerialService.writeSwipeCallbackEvent(
								BluetoothSerialService.EVENT_SWIPE_CALLBACK_ACTION, tIsXDirection,
								(int) (mTouchDownPositionX / mScaleFactor), (int) (mTouchDownPositionY / mScaleFactor), tDeltaX,
								tDeltaY);
						// (mis)use mTouchStartsOnSlider flag to suppress other checks on ACTION_UP and sending of UP-event
						mTouchStartsOnSlider = true;
					}
				}

				/*
				 * Check SLIDERS if ACTION_DOWN or (ACTION_MOVE and slider active)
				 */
				if (tMaskedAction == MotionEvent.ACTION_DOWN || (tMaskedAction == MotionEvent.ACTION_MOVE && mTouchStartsOnSlider)) {
					boolean tMatch = TouchSlider.checkAllSliders((int) (aEvent.getX() / mScaleFactor + 0.5), (int) (aEvent.getY()
							/ mScaleFactor + 0.5));
					if (tMatch) {
						invalidate(); // show new slider bar value
						if (tMaskedAction == MotionEvent.ACTION_DOWN) {
							mTouchStartsOnSlider = true;
						}
					}
				}

				if (!mTouchStartsOnSlider) {
					/*
					 * Check BUTTONS if no slider active AND (ACTION_DOWN OR if mUseUpEventForButtons AND ACTION_UP and no swipe
					 * detected)
					 */
					if (((tMaskedAction == MotionEvent.ACTION_DOWN && !mUseUpEventForButtons) || (tMaskedAction == MotionEvent.ACTION_UP
							&& mUseUpEventForButtons && !mDisableButtonUpOnce))) {
						mTouchStartsOnButtonNumber = TouchButton.checkAllButtons((int) (aEvent.getX() / mScaleFactor + 0.5),
								(int) (aEvent.getY() / mScaleFactor + 0.5), false);
						if (mTouchStartsOnButtonNumber >= 0 && tMaskedAction == MotionEvent.ACTION_DOWN) {
							// signal that we send an event on touch down and to skip processing until touch up
							mSkipProcessingUntilTouchUp = true;
						}
					} else if (tMaskedAction == MotionEvent.ACTION_DOWN) {
						// Just check if down touch hits a button area
						mTouchStartsOnButtonNumber = TouchButton.checkAllButtons((int) (aEvent.getX() / mScaleFactor + 0.5),
								(int) (aEvent.getY() / mScaleFactor + 0.5), true);
					}

					/*
					 * check for LONG TOUCH initialization
					 */
					if (tMaskedAction == MotionEvent.ACTION_DOWN && mIsLongTouchEnabled && mTouchStartsOnButtonNumber < 0) {
						/*
						 * send delayed message to handler, witch in turn sends the callback. If another event happens, the message
						 * will be simply deleted.
						 */
						mLongTouchDownHandler.sendEmptyMessageAtTime(LONG_TOUCH_DOWN, aEvent.getDownTime()
								+ mLongTouchDownTimeoutMillis);
					}
				}

				// evaluate local and global flags before sending basic events
				if (mTouchStartsOnButtonNumber < 0 && !mTouchStartsOnSlider && mTouchBasicEnable
						&& (mTouchMoveEnable || tMaskedAction != MotionEvent.ACTION_MOVE)) {
					// no button/slider touched and touch is enabled -> send touch event to client
					mBlueDisplayContext.mSerialService.writeTwoIntegerEvent(tMaskedAction,
							(int) (aEvent.getX() / mScaleFactor + 0.5), (int) (aEvent.getY() / mScaleFactor + 0.5));
				}
				if (tMaskedAction == MotionEvent.ACTION_UP || tMaskedAction == MotionEvent.ACTION_CANCEL) {
					mTouchStartsOnButtonNumber = -1;
					mTouchStartsOnSlider = false;
					mDisableButtonUpOnce = false;
				}
			}
		}
		return true;
	}

	@SuppressLint("HandlerLeak")
	private final Handler mLongTouchDownHandler = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {

			case LONG_TOUCH_DOWN:
				mDisableButtonUpOnce = true;
				mBlueDisplayContext.mSerialService.writeTwoIntegerEvent(
						BluetoothSerialService.EVENT_LONG_TOUCH_DOWN_CALLBACK_ACTION,
						(int) (mTouchDownPositionX / mScaleFactor + 0.5), (int) (mTouchDownPositionY / mScaleFactor + 0.5));
				break;

			default:
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
			tTempScaleFactor -= 1.0;
			tTempScaleFactor /= 3;
			tTempScaleFactor += 1.0;

			// clip mTouchScaleFactor to 1
			mTouchScaleFactor *= tTempScaleFactor;
			if (mTouchScaleFactor < 1) {
				mTouchScaleFactor = 1;
			} else if (mTouchScaleFactor > mMaxScaleFactor) {
				mTouchScaleFactor = mMaxScaleFactor;
			}

			if (MyLog.isVERBOSE()) {
				MyLog.v(LOG_TAG, "TouchScaleFactor=" + mTouchScaleFactor + " detector factor=" + tTempScaleFactor
						+ " MaxScaleFactor=" + mMaxScaleFactor);
			}

			// snap to 0.05, 5%
			float tScaleFactorSnapped = mTouchScaleFactor;
			tScaleFactorSnapped *= 20;
			tScaleFactorSnapped = Math.round(tScaleFactorSnapped);
			tScaleFactorSnapped /= 20;

			// save and restore mTouchScaleFactor since setScaleFactor will
			// overwrite it with tScaleFactor
			tTempScaleFactor = mTouchScaleFactor;
			boolean tRetvalue = !setScaleFactor(tScaleFactorSnapped, true, true);
			mTouchScaleFactor = tTempScaleFactor;
			// return true if event was handled
			return tRetvalue;
		}
	}

	/*
	 * For future use?
	 */
	@SuppressWarnings("unused")
	private class GestureListener extends SimpleOnGestureListener {

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

	};

	private char[] mCharMappingArray = new char[128];

	public char[] myConvertChars(byte[] aData, char[] aChars, int aDataLength) {
		for (int i = 0; i < aDataLength; i++) {
			aChars[i] = myConvertChar(aData[i]);
		}
		return aChars;
	}

	private char myConvertChar(byte aData) {
		// TODO help to compute length of string if using special character
		char tChar;
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
	 * @param aScaleFactor
	 * @param aResizeCanvas
	 *            if true also resize canvas (false only for tests)
	 * @return true if canvas size was changed
	 */
	public boolean setScaleFactor(float aScaleFactor, boolean allowFloatFactor, boolean aSendToClient) {

		float tOldFactor = mScaleFactor;
		if (aScaleFactor <= 1) {
			mScaleFactor = 1;
		} else {
			/*
			 * Use maximum possible size if scale factor is too big (mUseMaxSize == true)
			 */
			if (aScaleFactor > mMaxScaleFactor) {
				mScaleFactor = mMaxScaleFactor;
			} else {
				mScaleFactor = aScaleFactor;
			}

			if (!allowFloatFactor) {
				// not really used yet
				int tIntFactor = (int) mScaleFactor;
				mScaleFactor = tIntFactor;
			}
		}
		if (tOldFactor != mScaleFactor) {
			mActualCanvasWidth = (int) (mRequestedCanvasWidth * mScaleFactor);
			mActualCanvasHeight = (int) (mRequestedCanvasHeight * mScaleFactor);
			Bitmap tOldBitmap = mBitmap;
			mBitmap = Bitmap.createScaledBitmap(mBitmap, mActualCanvasWidth, mActualCanvasHeight, false);
			mCanvas = new Canvas(mBitmap);
			tOldBitmap.recycle();

			mTouchScaleFactor = mScaleFactor;
			mGraphPaintStrokeScaleFactor.setStrokeWidth(mScaleFactor);

			if (MyLog.isINFO()) {
				MyLog.i(LOG_TAG, "setScaleFactor(" + aScaleFactor + ") old factor=" + tOldFactor + " resulting factor="
						+ mScaleFactor);
			}
			invalidate();
			// send new size to client
			if (mBlueDisplayContext.mSerialService != null && aSendToClient) {
				mBlueDisplayContext.mSerialService.writeTwoIntegerEvent(BluetoothSerialService.EVENT_REDRAW_ACTION,
						mActualCanvasWidth, mActualCanvasHeight);
			}
			// show new Values
			if (mLastScaleValueToast != null) {
				mLastScaleValueToast.cancel();
			}
			mLastScaleValueToast = Toast.makeText(mBlueDisplayContext, String.format("%5.1f%% ", (mScaleFactor * 100))
					+ mActualCanvasWidth + "*" + mActualCanvasHeight, Toast.LENGTH_SHORT);
			mLastScaleValueToast.show();
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
		int tPrintY = mTextPrintTextActualPosY + mTextPrintTextSize + 1; // for space between lines otherwise we see "g" truncated
		if (tPrintY >= mRequestedCanvasHeight) {
			// wrap around to top of screen
			tPrintY = 0;
			if (mTextPrintDoClearScreenOnWrap) {
				mCanvas.drawColor(mTextPrintBackgroundColor);
			}
		}
		mTextPrintTextActualPosX = 0;
		return tPrintY;
	}

	public void interpretCommand(int aCommand, int[] aParameters, int aParamsLength, byte[] aDataBytes, int[] aDataInts,
			int aDataLength) {

		if (MyLog.isVERBOSE()) {
			StringBuilder tParam = new StringBuilder("cmd=0x" + Integer.toHexString(aCommand) + " / " + aCommand);
			for (int i = 0; i < aParamsLength; i++) {
				tParam.append(" ").append(i).append("=").append(aParameters[i]);
			}
			tParam.append(" data length=" + aDataLength);
			MyLog.v(LOG_TAG, tParam.toString());
		}
		if ((aCommand >= INDEX_FIRST_FUNCTION_BUTTON && aCommand <= INDEX_LAST_FUNCTION_BUTTON)
				|| aCommand >= INDEX_FIRST_FUNCTION_BUTTON_WITH_DATA && aCommand <= INDEX_LAST_FUNCTION_BUTTON_WITH_DATA) {
			TouchButton.interpretCommand(this, aCommand, aParameters, aParamsLength, aDataBytes, aDataInts, aDataLength);
			return;
		} else if ((aCommand >= INDEX_FIRST_FUNCTION_SLIDER && aCommand <= INDEX_LAST_FUNCTION_SLIDER)
				|| aCommand >= INDEX_FIRST_FUNCTION_SLIDER_WITH_DATA && aCommand <= INDEX_LAST_FUNCTION_SLIDER_WITH_DATA) {
			TouchSlider.interpretCommand(this, aCommand, aParameters, aParamsLength, aDataBytes, aDataInts, aDataLength);
			return;
		}

		Paint tResultingPaint;

		float tXStart = 0;
		float tYStart = 0;
		float tXEnd = 0;
		float tYEnd = 0;
		if (aParamsLength >= 2) {
			tXStart = aParameters[0] * mScaleFactor;
			tYStart = aParameters[1] * mScaleFactor;
		}
		int tTextSize;
		int tColor;
		int tIndex;
		String tFunctionName;
		String tAdditionalInfo = "";
		String tStringParameter = "";

		float tDecend;
		float tAscend;

		int tSubcommand;
		int tCallbackAddress;

		try {
			switch (aCommand) {
			case FUNCTION_PLAY_TONE:
				int tTone = ToneGenerator.TONE_CDMA_KEYPAD_VOLUME_KEY_LITE;
				int tDurationMillis = -1;
				if (aParamsLength > 0) {
					if (aParameters[0] > 0 && aParameters[0] <= ToneGenerator.TONE_CDMA_SIGNAL_OFF) {
						tTone = aParameters[0];
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
							 * set volume
							 */
							if ((aParameters[2] >= 0 || aParameters[2] < ToneGenerator.MAX_VOLUME)
									&& aParameters[2] != mActualToneVolume) {
								mActualToneVolume = aParameters[3];
								mToneGenerator = new ToneGenerator(AudioManager.STREAM_SYSTEM, mActualToneVolume);
							}
						}
					}
				}
				mToneGenerator.startTone(tTone, tDurationMillis);
				if (MyLog.isINFO()) {
					MyLog.i(LOG_TAG, "Play tone type=" + tTone + " duration=" + tDurationMillis);
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
				int tInitalValue = Integer.MIN_VALUE;
				int HandlerStartIndex = 0;
				if (aParamsLength == 3) {
					tInitalValue = aParameters[0];
					HandlerStartIndex = 1;
					tInitialInfo = " initial value=" + Integer.toString(tInitalValue);
				}
				tCallbackAddress = aParameters[HandlerStartIndex] & 0x0000FFFF;
				if (aParamsLength >= 2) {
					HandlerStartIndex++;
					// 32 bit callback address
					tCallbackAddress = tCallbackAddress | (aParameters[HandlerStartIndex] << 16);
				}

				if (aDataLength > 0) {
					myConvertChars(aDataBytes, sCharsArray, aDataLength);
					tStringParameter = new String(sCharsArray, 0, aDataLength);
				}
				if (MyLog.isINFO()) {
					MyLog.i(LOG_TAG, "Get " + tFunctionName + " callback=0x" + Integer.toHexString(tCallbackAddress) + " prompt=\""
							+ tStringParameter + "\"" + tInitialInfo);
				}
				// Send request for number input to the UI Activity

				Message msg = mHandler.obtainMessage(BlueDisplay.REQUEST_INPUT_DATA);
				Bundle bundle = new Bundle();
				bundle.putInt(BlueDisplay.CALLBACK_ADDRESS, tCallbackAddress);
				bundle.putString(BlueDisplay.DIALOG_PROMPT, tStringParameter);
				bundle.putInt(BlueDisplay.NUMBER_INITIAL_VALUE, tInitalValue);
				bundle.putBoolean(BlueDisplay.NUMBER_FLAG, tDoNumber);
				msg.setData(bundle);
				mHandler.sendMessage(msg);
				break;

			case FUNCTION_REQUEST_MAX_CANVAS_SIZE:
				if (MyLog.isINFO()) {
					MyLog.i(LOG_TAG, "Request max canvas size=" + mActualCanvasWidth + "/" + mActualCanvasHeight);
				}
				if (mBlueDisplayContext.mSerialService != null) {
					mBlueDisplayContext.mSerialService.writeTwoIntegerEvent(BluetoothSerialService.EVENT_REORIENTATION_ACTION,
							mActualViewWidth, mActualViewHeight);
				}
				break;

			case FUNCTION_GET_INFO:
				tSubcommand = aParameters[0];
				tCallbackAddress = aParameters[1] & 0x0000FFFF;
				if (aParamsLength == 3) {
					// 32 bit callback address
					tCallbackAddress = tCallbackAddress | (aParameters[2] << 16);
				}
				switch (tSubcommand) {
				case SUBFUNCTION_GET_INFO_:
					// For future use
				}
				break;

			case FUNCTION_GLOBAL_SETTINGS:
				tSubcommand = aParameters[0];
				switch (tSubcommand) {
				case SUBFUNCTION_GLOBAL_SET_FLAGS_AND_SIZE:
					if (aParameters[2] < 10 || aParameters[3] < 10) {
						MyLog.e(LOG_TAG, "Set flags=0x" + Integer.toHexString(aParameters[1]) + " and canvas size="
								+ aParameters[2] + "/" + aParameters[3] + ". Size parameter values to small -> return.");
						return;
					}
					// set canvas size
					if (MyLog.isINFO()) {
						MyLog.i(LOG_TAG, "Set flags=0x" + Integer.toHexString(aParameters[1]) + " and canvas size="
								+ aParameters[2] + "/" + aParameters[3]);
					}
					mRequestedCanvasWidth = aParameters[2];
					mRequestedCanvasHeight = aParameters[3];
					setMaxScaleFactor();
					setFlags(aParameters[1]);
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
					CharBuffer tCharBuffer = tCharset.decode(tBytebuffer);
					mCharMappingArray = tCharBuffer.array();
					break;

				case SUBFUNCTION_GLOBAL_SET_CHARACTER_CODE_MAPPING:
					tIndex = aParameters[1] - 0x80;
					if (MyLog.isINFO()) {
						MyLog.i(LOG_TAG, "Set character mapping=" + mCharMappingArray[tIndex] + "->" + (char) aParameters[2]
								+ " / 0x" + Integer.toHexString(aParameters[1]) + "-> 0x" + Integer.toHexString(aParameters[2]));
					}
					if (tIndex >= 0 && tIndex < mCharMappingArray.length) {
						mCharMappingArray[tIndex] = (char) aParameters[2];
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
					if (aParameters[1] == FLAG_SCREEN_ORIENTATION_LOCK_UNLOCK) {
						// unlock
						mBlueDisplayContext.mOrientationisLockedByClient = false;
						mBlueDisplayContext.setActualScreenOrientation(mBlueDisplayContext.mPreferredScreenOrientation);
						if (MyLog.isINFO()) {
							MyLog.i(LOG_TAG, "Unlocked screen orientation to preferred orientation="
									+ mBlueDisplayContext.mActualScreenOrientationRotationString);
						}
					} else {
						mBlueDisplayContext.mOrientationisLockedByClient = true;
						int tNewOrientation = mBlueDisplayContext.mActualScreenOrientation;
						String tTagetOrientation = "";
						if (aParameters[1] == FLAG_SCREEN_ORIENTATION_LOCK_ACTUAL) {
							tTagetOrientation = "actual";
							// Get REAL orientation - 1 for Portrait and 2 for Landscape
							int tActualRealOrientation = mBlueDisplayContext.getResources().getConfiguration().orientation;
							tNewOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
							if (tActualRealOrientation == Configuration.ORIENTATION_PORTRAIT) {
								tNewOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
							}
							if (mBlueDisplayContext.mActualRotation == Surface.ROTATION_180
									|| mBlueDisplayContext.mActualRotation == Surface.ROTATION_270) {
								if (tNewOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
									tNewOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
								} else if (tNewOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
									tNewOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
								} else {
									MyLog.w(LOG_TAG, "Rotation = 180 or 270 and unknown actual orientation="
											+ mBlueDisplayContext.mActualScreenOrientation);
								}
							}
						} else if (aParameters[1] == FLAG_SCREEN_ORIENTATION_LOCK_PORTRAIT) {
							tTagetOrientation = "portrait";
							tNewOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
						} else {
							tTagetOrientation = "landscape";
							tNewOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
						}
						mBlueDisplayContext.setActualScreenOrientation(tNewOrientation);
						if (MyLog.isINFO()) {
							MyLog.i(LOG_TAG, "Locked screen orientation to " + tTagetOrientation + ". New orientation="
									+ mBlueDisplayContext.mActualScreenOrientationRotationString);
						}
					}
					break;

				default:
					MyLog.e(LOG_TAG, "Global settings: unknown subcommand 0x" + Integer.toHexString(tSubcommand)
							+ " received. paramsLength=" + aParamsLength + " dataLenght=" + aDataLength);
					break;

				}
				break;

			case FUNCTION_WRITE_SETTINGS:
				tSubcommand = aParameters[0];
				switch (tSubcommand) {
				case FLAG_WRITE_SETTINGS_SET_SIZE_AND_COLORS_AND_FLAGS:
					mTextPrintTextSize = aParameters[1];
					mTextPrintPaint.setTextSize(mTextPrintTextSize * mScaleFactor);
					mTextPrintPaint.setColor(shortToLongColor(aParameters[2]));
					mTextPrintBackgroundColor = shortToLongColor(aParameters[3]);
					if (aParameters[4] > 0) {
						mTextPrintDoClearScreenOnWrap = true;
					} else {
						mTextPrintDoClearScreenOnWrap = false;
					}
					if (MyLog.isINFO()) {
						MyLog.i(LOG_TAG, "Set printf size=" + aParameters[1] + " color=" + shortToColorString(aParameters[2])
								+ " backgroundcolor=" + shortToColorString(aParameters[3]) + " clearOnWrap="
								+ mTextPrintDoClearScreenOnWrap);
					}
					break;

				case FLAG_WRITE_SETTINGS_SET_POSITION:
					mTextPrintTextActualPosX = aParameters[1];
					mTextPrintTextActualPosY = aParameters[2];
					if (MyLog.isINFO()) {
						MyLog.i(LOG_TAG, "Set printf start position to: " + mTextPrintTextActualPosX + " / "
								+ mTextPrintTextActualPosY);
					}
					break;

				case FLAG_WRITE_SETTINGS_SET_LINE_COLUMN:
					mTextPrintTextActualPosX = (int) (aParameters[1] * mTextPrintTextSize * 0.6);
					mTextPrintTextActualPosY = aParameters[2] * mTextPrintTextSize;
					if (MyLog.isINFO()) {
						MyLog.i(LOG_TAG, "Set printf start position to: " + aParameters[1] + " / " + aParameters[2] + " = "
								+ mTextPrintTextActualPosX + " / " + mTextPrintTextActualPosY);
					}
					break;

				default:
					MyLog.e(LOG_TAG, "Write settings: unknown subcommand 0x" + Integer.toHexString(tSubcommand)
							+ " received. paramsLength=" + aParamsLength + " dataLenght=" + aDataLength);
				}
				break;

			case FUNCTION_SENSOR_SETTINGS:
				boolean tDoActivate = false;
				tFunctionName = "SetSensor";
				if (aParameters[1] != 0) {
					tDoActivate = true;
				}
				int tFilterFlag = Sensors.FLAG_SENSOR_NO_FILTER;
				if (aParamsLength == 4) {
					tFilterFlag = aParameters[3];
				}
				mBlueDisplayContext.mSensorEventListener.setSensor(aParameters[0], tDoActivate, aParameters[2], tFilterFlag);
				break;

			case FUNCTION_CLEAR_DISPLAY:
				// clear screen
				if (MyLog.isDEBUG()) {
					MyLog.d(LOG_TAG, "Clear screen color= " + shortToColorString(aParameters[0]));
				}
				mCanvas.drawColor(shortToLongColor(aParameters[0]));
				// reset screen buffer
				mChartScreenBufferActualLength = 0;
				break;

			case FUNCTION_DRAW_PIXEL:
				mGraphPaintStrokeScaleFactor.setColor(shortToLongColor(aParameters[2]));
				if (MyLog.isDEBUG()) {
					MyLog.d(LOG_TAG, "drawPixel(" + aParameters[0] + ", " + aParameters[1] + ") color= "
							+ shortToColorString(aParameters[2]));
				}
				mCanvas.drawPoint(tXStart, tYStart, mGraphPaintStrokeScaleFactor);
				break;

			case FUNCTION_DRAW_LINE_REL:
			case FUNCTION_DRAW_LINE:
				if (aCommand == FUNCTION_DRAW_LINE_REL) {
					tFunctionName = "drawLineRel";
					tXEnd = tXStart + aParameters[2] * mScaleFactor;
					tYEnd = tYStart + aParameters[3] * mScaleFactor;
				} else {
					tFunctionName = "drawLine";
					tXEnd = aParameters[2] * mScaleFactor;
					tYEnd = aParameters[3] * mScaleFactor;
				}

				tColor = shortToLongColor(aParameters[4]);

				if (aParamsLength > 5) {
					// Stroke parameter
					mGraphPaintStrokeSettable.setStrokeWidth(aParameters[5] * mScaleFactor);
					tResultingPaint = mGraphPaintStrokeSettable;
					if (MyLog.isDEBUG()) {
						tAdditionalInfo = " strokeWidth=" + aParameters[5] * mScaleFactor;
					}
				} else {
					tResultingPaint = mGraphPaintStrokeScaleFactor;
				}

				tResultingPaint.setColor(tColor);
				if (tXStart == tXEnd && tYStart == tYEnd) {
					mCanvas.drawPoint(tXStart, tYStart, tResultingPaint);
				} else {
					mCanvas.drawLine(tXStart, tYStart, tXEnd, tYEnd, tResultingPaint);
				}
				if (MyLog.isDEBUG()) {
					MyLog.d(LOG_TAG, tFunctionName + "(" + aParameters[0] + ", " + aParameters[1] + ", " + aParameters[2] + ", "
							+ aParameters[3] + ") color= " + shortToColorString(aParameters[4]) + tAdditionalInfo);
				}
				break;

			case FUNCTION_DRAW_CHART:
			case FUNCTION_DRAW_CHART_WITHOUT_DIRECT_RENDERING:
				/*
				 * Chart index is coded in the upper 4 bits of Y start position
				 */
				int tChartIndex = aParameters[1] >> 12;
				if (tChartIndex > 0) {
					tYStart = (aParameters[1] & 0x0FFF) * mScaleFactor;
				}
				tColor = shortToLongColor(aParameters[2]);
				int tDeleteColor = shortToLongColor(aParameters[3]);

				if (MyLog.isDEBUG()) {
					if (aCommand == FUNCTION_DRAW_CHART) {
						tFunctionName = "drawChart";
					} else {
						tFunctionName = "drawChartWithoutDirectRendering";
					}
					MyLog.d(LOG_TAG, tFunctionName + "(" + aParameters[0] + ", " + (aParameters[1] & 0x0FFF) + ") color= "
							+ shortToColorString(aParameters[2]) + " ,deleteColor= " + shortToColorString(aParameters[3])
							+ " lenght=" + aDataLength + " ChartIndex=" + tChartIndex);
				}

				if (aParameters[3] != 0) {
					/*
					 * delete old chart
					 */
					// set delete color
					mGraphPaintStrokeScaleFactor.setColor(tDeleteColor);
					mCanvas.drawLines(mChartScreenBuffer[tChartIndex], 0, mChartScreenBufferActualLength * 4,
							mGraphPaintStrokeScaleFactor);
				}

				/*
				 * draw new chart
				 */
				mGraphPaintStrokeScaleFactor.setColor(tColor);

				float tYOffset = tYStart;
				tYStart += BluetoothSerialService.convertByteToFloat(aDataBytes[0]) * mScaleFactor;
				mChartScreenBuffer[tChartIndex][0] = tXStart;
				mChartScreenBuffer[tChartIndex][1] = tYStart;
				mChartScreenBufferActualLength = aDataLength;
				mChartScreenBufferXStart = tXStart;
				int j = 1;
				int i = 2;
				// for points for each line
				while (i < (aDataLength - 2) * 4) {
					tXStart += mScaleFactor;
					tYStart = (BluetoothSerialService.convertByteToFloat(aDataBytes[j++]) * mScaleFactor) + tYOffset;
					// end of first line ...
					mChartScreenBuffer[tChartIndex][i++] = tXStart;
					mChartScreenBuffer[tChartIndex][i++] = tYStart;
					// ... is start of next line
					mChartScreenBuffer[tChartIndex][i++] = tXStart;
					mChartScreenBuffer[tChartIndex][i++] = tYStart;
				}
				mChartScreenBuffer[tChartIndex][i++] = tXStart + mScaleFactor;
				mChartScreenBuffer[tChartIndex][i] = (BluetoothSerialService.convertByteToFloat(aDataBytes[j++]) * mScaleFactor)
						+ tYOffset;

				mCanvas.drawLines(mChartScreenBuffer[tChartIndex], 0, aDataLength * 4, mGraphPaintStrokeScaleFactor);

				break;

			case FUNCTION_DRAW_PATH:
			case FUNCTION_FILL_PATH:
				tColor = shortToLongColor(aParameters[0]);
				if (aCommand == FUNCTION_DRAW_PATH) {
					tFunctionName = "drawPath";
					mGraphPaintStrokeSettable.setColor(tColor);
					mGraphPaintStrokeSettable.setStrokeWidth(aParameters[1] * mScaleFactor);
					tResultingPaint = mGraphPaintStrokeSettable;
					if (MyLog.isDEBUG()) {
						tAdditionalInfo = " strokeWidth=" + aParameters[1] * mScaleFactor;
					}
				} else {
					tFunctionName = "fillPath";
					mGraphPaintStroke1Fill.setColor(tColor);
					tResultingPaint = mGraphPaintStroke1Fill;
				}
				if (MyLog.isDEBUG()) {
					MyLog.d(LOG_TAG, tFunctionName + "(" + tXStart + ", " + tYStart + ") color= "
							+ shortToColorString(aParameters[0]) + " lenght=" + aDataLength + tAdditionalInfo);
				}

				/*
				 * Data to path
				 */
				mPath.incReserve(aDataLength + 1);
				mPath.moveTo(aDataInts[0] * mScaleFactor, aDataInts[1] * mScaleFactor);
				i = 2;
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
					tXEnd = tXStart + aParameters[2] * mScaleFactor;
					tYEnd = tYStart + aParameters[3] * mScaleFactor;
				} else {
					tXEnd = aParameters[2] * mScaleFactor;
					tYEnd = aParameters[3] * mScaleFactor;
					// sort parameters
					float tmp;
					if (tXStart > tXEnd) {
						tmp = tXStart;
						tXStart = tXEnd;
						tXEnd = tmp;
					}
					if (tYStart > tYEnd) {
						tmp = tYStart;
						tYStart = tYEnd;
						tYEnd = tmp;
					}
				}

				tColor = shortToLongColor(aParameters[4]);

				if (aCommand == FUNCTION_DRAW_RECT || aCommand == FUNCTION_DRAW_RECT_REL) {
					if (aCommand == FUNCTION_DRAW_RECT_REL) {
						tFunctionName = "drawRectRel";
					} else {
						tFunctionName = "drawRect";
					}
					mGraphPaintStrokeSettable.setColor(tColor);
					mGraphPaintStrokeSettable.setStrokeWidth(aParameters[5] * mScaleFactor);
					tResultingPaint = mGraphPaintStrokeSettable;
					if (MyLog.isDEBUG()) {
						tAdditionalInfo = " strokeWidth=" + aParameters[5] * mScaleFactor;
					}
				} else {
					if (aCommand == FUNCTION_FILL_RECT_REL) {
						tFunctionName = "fillRectRel";
					} else {
						tFunctionName = "fillRect";
					}
					mGraphPaintStroke1Fill.setColor(tColor);
					tResultingPaint = mGraphPaintStroke1Fill;
				}

				if (MyLog.isDEBUG()) {
					MyLog.d(LOG_TAG, tFunctionName + "(" + aParameters[0] + ", " + aParameters[1] + ", " + aParameters[2] + ", "
							+ aParameters[3] + ") , color= " + shortToColorString(aParameters[4]) + tAdditionalInfo);
				}
				mCanvas.drawRect(tXStart, tYStart, tXEnd, tYEnd, tResultingPaint);
				break;

			case FUNCTION_DRAW_CIRCLE:
			case FUNCTION_FILL_CIRCLE:
				tColor = shortToLongColor(aParameters[3]);
				float tRadius = aParameters[2] * mScaleFactor;
				if (aCommand == FUNCTION_DRAW_CIRCLE) {
					tFunctionName = "drawCircle";
					mGraphPaintStrokeSettable.setColor(tColor);
					mGraphPaintStrokeSettable.setStrokeWidth(aParameters[4] * mScaleFactor);
					tResultingPaint = mGraphPaintStrokeSettable;
					if (MyLog.isDEBUG()) {
						tAdditionalInfo = " strokeWidth=" + aParameters[4] * mScaleFactor;
					}
				} else {
					tFunctionName = "fillCircle";
					mGraphPaintStroke1Fill.setColor(tColor);
					tResultingPaint = mGraphPaintStroke1Fill;
				}

				if (MyLog.isDEBUG()) {
					MyLog.d(LOG_TAG, tFunctionName + "(" + aParameters[0] + ", " + aParameters[1] + ", r=" + aParameters[2]
							+ ") ,color= " + shortToColorString(aParameters[3]) + tAdditionalInfo);
				}
				mCanvas.drawCircle(tXStart, tYStart, tRadius, tResultingPaint);
				break;

			case FUNCTION_DEBUG_STRING:
				tStringParameter = new String(aDataBytes, 0, aDataLength);
				// intentionally without enclosing if MyLog.isINFO()
				MyLog.i(LOG_TAG, "DebugString=" + tStringParameter);
				break;

			case FUNCTION_WRITE_STRING:
				myConvertChars(aDataBytes, sCharsArray, aDataLength);
				tStringParameter = new String(sCharsArray, 0, aDataLength);

				if (MyLog.isINFO()) {
					MyLog.i(LOG_TAG, "writeString(\"" + tStringParameter.replaceAll("\n", "\\n") + "\"");
				}
				char tChar;
				int tActualCharacterIndex = 0;
				int tWordStart = 0;
				int tPrintBufferStart = 0;
				int tPrintBufferEnd = aDataLength;
				float tScaledTextPrintTextSize = mTextPrintTextSize * mScaleFactor;
				mTextPrintPaint.setTextSize(tScaledTextPrintTextSize);
				int tTextUnscaledWidth = (int) (mTextPrintTextSize * 0.6);
				int tLineLengthInChars = (int) (mRequestedCanvasWidth / tTextUnscaledWidth);
				boolean doFlushAndNewline = false;
				int tColumn = (int) (mTextPrintTextActualPosX / tTextUnscaledWidth);
				// ascend for background color.
				tAscend = (float) (tScaledTextPrintTextSize * 0.76);
				while (true) {
					// check for terminate condition
					if (tActualCharacterIndex >= tPrintBufferEnd) {
						// check if last character was newline and string was already printed
						if (tPrintBufferStart < tPrintBufferEnd) {
							tYStart = mTextPrintTextActualPosY * mScaleFactor;
							tXStart = mTextPrintTextActualPosX * mScaleFactor;
							int tIntegerTextSize = (int) (tScaledTextPrintTextSize + 0.5);
							tIntegerTextSize = (int) ((tIntegerTextSize * 0.6) + 0.5);
							float tTextLength = (tPrintBufferEnd - tPrintBufferStart) * tIntegerTextSize;

							// draw background
							mTextBackgroundPaint.setColor(mTextPrintBackgroundColor);
							mCanvas.drawRect(tXStart, tYStart, tXStart + tTextLength, tYStart + tScaledTextPrintTextSize,
									mTextBackgroundPaint);
							// draw char / string
							mCanvas.drawText(tStringParameter, tPrintBufferStart, tPrintBufferEnd - 1, tXStart, tYStart + tAscend,
									mTextPrintPaint);
						}
						break;
					}
					tChar = sCharsArray[tActualCharacterIndex++];

					if (tChar == '\n') {
						// new line -> start of a new word
						tWordStart = tActualCharacterIndex;
						// signal flush and newline
						doFlushAndNewline = true;
					} else if (tChar == '\r') {
						// skip but start of a new word
						tWordStart = tActualCharacterIndex;
					} else if (tChar == ' ') {
						// start of a new word
						tWordStart = tActualCharacterIndex;
						if (tColumn == 0) {
							// skip from printing if first character in line
							tPrintBufferStart = tActualCharacterIndex;
						}
					} else {
						if (tColumn >= tLineLengthInChars) {
							// character does not fit in line -> print it at next line
							doFlushAndNewline = true;
							int tWordlength = (tActualCharacterIndex - tWordStart);
							if (tWordlength > tLineLengthInChars) {
								// word too long for a line just print char on next line
								// just draw "buffer" to old line, make newline and process character again
								tActualCharacterIndex--;
							} else {
								// draw buffer till word start, print a newline and process word again
								tActualCharacterIndex = tWordStart;
							}
						}
					}
					if (doFlushAndNewline) {
						tXStart = mTextPrintTextActualPosX * mScaleFactor;
						tYStart = mTextPrintTextActualPosY * mScaleFactor;
						int tIntegerTextSize = (int) (tScaledTextPrintTextSize + 0.5);
						tIntegerTextSize = (int) ((tIntegerTextSize * 0.6) + 0.5);
						// do not count the newline or space
						float tTextLength = ((tActualCharacterIndex - 1) - tPrintBufferStart) * tIntegerTextSize;

						mTextBackgroundPaint.setColor(mTextPrintBackgroundColor);
						mCanvas.drawRect(tXStart, tYStart, tXStart + tTextLength, tYStart + tScaledTextPrintTextSize,
								mTextBackgroundPaint);
						// draw char / string
						mCanvas.drawText(tStringParameter, tPrintBufferStart, tActualCharacterIndex - 1, tXStart,
								tYStart + tAscend, mTextPrintPaint);

						tPrintBufferStart = tActualCharacterIndex;
						mTextPrintTextActualPosY = printNewline();
						mTextPrintTextActualPosX = 0; // set it explicitly since compiler may hold mTextPrintTextActualPosX in
														// register
						tColumn = 0;
						doFlushAndNewline = false;
					} else {
						tColumn++;
					}
				}
				break;

			case FUNCTION_DRAW_CHAR:
			case FUNCTION_DRAW_STRING:

				tTextSize = (int) (aParameters[2] * mScaleFactor);
				mTextPaint.setTextSize(tTextSize);
				// ascend for background color. + mScaleFactor for upper margin
				tAscend = (float) (tTextSize * 0.76) + mScaleFactor;
				tDecend = (float) (tTextSize * 0.24);

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

				mTextPaint.setColor(shortToLongColor(aParameters[3]));

				if (MyLog.isDEBUG()) {
					MyLog.d(LOG_TAG, tFunctionName + "(\"" + tStringParameter + "\", " + aParameters[0] + ", " + aParameters[1]
							+ ", size=" + aParameters[2] + ") color= " + shortToColorString(aParameters[3]) + " bg= "
							+ shortToColorString(aParameters[4]));
				}

				tIndex = tStringParameter.indexOf('\n');
				boolean tDrawBackgroundExtend = false;
				int tCRIndex = tStringParameter.indexOf('\r');
				if (tCRIndex >= 0 && (tCRIndex < tIndex || tIndex < 0)) {
					tIndex = tCRIndex;
					tDrawBackgroundExtend = true;
					// tStringParameter = tStringParameter.substring(0, tIndex) + "\n" + tStringParameter.substring(tIndex + 1);
				}

				boolean tDrawBackground;
				if (aParameters[4] == COLOR_NO_BACKGROUND) {
					tDrawBackground = false;
					tDrawBackgroundExtend = false;
					aParameters[4] = COLOR_WHITE_PARAMETER;
				} else {
					mTextBackgroundPaint.setColor(shortToLongColor(aParameters[4]));
					tDrawBackground = true;
				}

				if (tIndex > 0) {
					int tStartIndex = 0;

					while (tIndex > 0) {
						/*
						 * Multiline text
						 */
						if (tDrawBackgroundExtend) {
							// draw background for whole rest of line
							mCanvas.drawRect(tXStart, tYStart - tAscend, mActualCanvasWidth, tYStart + tDecend,
									mTextBackgroundPaint);
						} else if (tDrawBackground) {
							// draw background only for string except for single newline
							if (tStartIndex != tIndex) {
								float tTextLength = mTextPaint.measureText(tStringParameter, tStartIndex, tIndex);
								// draw background
								mCanvas.drawRect(tXStart, tYStart - tAscend, tXStart + tTextLength, tYStart + tDecend,
										mTextBackgroundPaint);
							}
						}
						// check for single newline
						if (tStartIndex != tIndex) {
							// draw string
							mCanvas.drawText(tStringParameter, tStartIndex, tIndex, tXStart, tYStart, mTextPaint);
							tYStart += tTextSize + mScaleFactor; // + Margin
						}
						// search for next newline
						tStartIndex = tIndex + 1;
						if (tIndex + 1 <= tStringParameter.length()) {
							tIndex = tStringParameter.indexOf('\n', tIndex + 1);
							tDrawBackgroundExtend = false;
							tCRIndex = tStringParameter.indexOf('\r', tIndex + 1);
							if (tCRIndex >= 0 && (tCRIndex < tIndex || tIndex < 0)) {
								tIndex = tCRIndex;
								tDrawBackgroundExtend = true;
								// tStringParameter = tStringParameter.substring(0, tIndex) + "\n"
								// + tStringParameter.substring(tIndex + 1);
							}

							if (tIndex < 0) {
								tIndex = tStringParameter.length();
							}
						} else {
							tIndex = 0;
						}
					}
				} else {
					/*
					 * Single line text
					 */
					if (tDrawBackground) {
						float tTextLength = mTextPaint.measureText(tStringParameter);
						// draw background
						mCanvas.drawRect(tXStart, tYStart - tAscend, tXStart + tTextLength, tYStart + tDecend, mTextBackgroundPaint);
						// mCanvas.drawRect(tXStart, tYStart - tAscend, tXStart +
						// (tTextWidth * tDataLength) + 1, tYStart + tDecend,
						// mTextBackgroundPaint);
					}

					// draw char / string
					mCanvas.drawText(tStringParameter, tXStart, tYStart, mTextPaint);
				}
				break;

			case FUNCTION_NOP:
				if (MyLog.isINFO()) {
					MyLog.i(LOG_TAG, "NOP (for sync) received. ParamsLength=" + aParamsLength + " DataLength=" + aDataLength);
				}
				break;

			default:
				MyLog.e(LOG_TAG, "unknown command 0x" + Integer.toHexString(aCommand) + " received. paramsLength=" + aParamsLength
						+ " dataLenght=" + aDataLength);
				break;
			}
		} catch (Exception e) {
			MyLog.e(LOG_TAG, "Exception catched for command 0x" + Integer.toHexString(aCommand) + ". paramsLength=" + aParamsLength
					+ " dataLenght=" + aDataLength + " Exception=" + e);
		}
		// long tEnd = System.nanoTime();
		// Log.i(LOG_TAG, "Interpret=" + (tEnd - tStart));
	}

	public void fillRectRel(float aXStart, float aYStart, float aWidth, float aHeight, int aColor) {
		mGraphPaintStroke1Fill.setColor(aColor);
		mCanvas.drawRect(aXStart * mScaleFactor, aYStart * mScaleFactor, (aXStart + aWidth) * mScaleFactor, (aYStart + aHeight)
				* mScaleFactor, mGraphPaintStroke1Fill);
	}

	public void drawText(float aPosX, float aPosY, String aText, float aTextSize, int aColor) {
		mTextPaint.setTextSize(aTextSize * mScaleFactor);
		mTextPaint.setColor(aColor);
		mCanvas.drawText(aText, aPosX * mScaleFactor, aPosY * mScaleFactor, mTextPaint);
	}

	public void drawTextWithBackground(float aPosX, float aPosY, String aText, float aTextSize, int aColor, int aBGColor) {
		aPosX *= mScaleFactor;
		aPosY *= mScaleFactor;
		aTextSize *= mScaleFactor;
		mTextPaint.setTextSize(aTextSize);

		// draw background
		// ascend for background color. + mScaleFactor for upper margin
		float tAscend = (float) (aTextSize * 0.76) + mScaleFactor;
		float tDecend = (float) (aTextSize * 0.24);
		float tTextLength = mTextPaint.measureText(aText);
		mTextBackgroundPaint.setColor(aBGColor);
		mCanvas.drawRect(aPosX, aPosY - tAscend, aPosX + tTextLength, aPosY + tDecend, mTextBackgroundPaint);

		mTextPaint.setColor(aColor);
		mCanvas.drawText(aText, aPosX, aPosY, mTextPaint);
	}

	void initCharMappingArray() {
		/*
		 * initialize mapping array with actual codepage chars
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
		TouchSlider.resetSliders(this);
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
			tResetAllString = "Reset all before, ";
		}

		mTouchBasicEnable = ((aFlags & BD_FLAG_TOUCH_BASIC_DISABLE) == 0);
		mTouchMoveEnable = ((aFlags & BD_FLAG_TOUCH_MOVE_DISABLE) == 0);
		mIsLongTouchEnabled = ((aFlags & BD_FLAG_LONG_TOUCH_ENABLE) != 0);

		mScaleFactor = 0.9f; // force resize in setScaleFactor()
		if ((aFlags & BD_FLAG_USE_MAX_SIZE) != 0) {
			mUseMaxSize = true;
			// resize canvas
			setScaleFactor(10, true, false);
		} else {
			mUseMaxSize = false;
			setScaleFactor(1, true, false);
		}

		if (MyLog.isINFO()) {
			MyLog.i(LOG_TAG, "SetFlags: " + tResetAllString + "TouchMoveEnable=" + mTouchMoveEnable + ", LongTouchEnabled="
					+ mIsLongTouchEnabled + ", UseMaxSize=" + mUseMaxSize);
		}
	}

	/*
	 * do length correction for a 1 pixel line; draw 4 lines for tYStart = 2
	 */
	public void drawLenghtCorrectedLine(float aStartX, float aStartY, float aStopX, float aStopY, int aScaleFactor, Paint aPaint,
			Canvas aCanvas) {
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
				drawLenghtCorrectedLine(aStartX, aStartY, aStopX + 1, aStopY, 1, aPaint, aCanvas);
				drawLenghtCorrectedLine(aStartX, aStartY + 1, aStopX + 1, aStopY + 1, 1, aPaint, aCanvas);
			} else if (aStartX == aStopX) {
				// vertical line
				if (aStartY != aStopY) {
					if (aStartY < aStopY) {
						++aStopY;
					} else {
						++aStartY;
					}
				}
				drawLenghtCorrectedLine(aStartX, aStartY, aStopX, aStopY, 1, aPaint, aCanvas);
				drawLenghtCorrectedLine(aStartX + 1, aStartY, aStopX + 1, aStopY, 1, aPaint, aCanvas);
			} else {
				drawLenghtCorrectedLine(aStartX, aStartY, aStopX, aStopY, 1, aPaint, aCanvas);
				drawLenghtCorrectedLine(aStartX + 1, aStartY, aStopX + 1, aStopY, 1, aPaint, aCanvas);
				drawLenghtCorrectedLine(aStartX, aStartY + 1, aStopX, aStopY + 1, 1, aPaint, aCanvas);
				drawLenghtCorrectedLine(aStartX + 1, aStartY + 1, aStopX + 1, aStopY + 1, 1, aPaint, aCanvas);
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

	/******************************************************************************************
	 * TEST METHODS
	 ******************************************************************************************/

	private static final int TEST_CANVAS_WIDTH = 320;
	private static final int TEST_CANVAS_HEIGHT = 240;

	public void showGraphTestpage() {
		// adjust canvas size to scale factor 2
		mRequestedCanvasWidth = TEST_CANVAS_WIDTH;
		mRequestedCanvasHeight = TEST_CANVAS_HEIGHT;
		mCanvas.drawColor(Color.WHITE); // clear screen

		int tY = (int) (drawGraphTestPattern() / mScaleFactor) + 10;

		drawLogo(200, 120, 5);
		testBDFunctions(5, tY, false);
		invalidate();
	}

	public void showFontTestpage() {
		// adjust canvas size to scale factor 2
		mRequestedCanvasWidth = TEST_CANVAS_WIDTH;
		mRequestedCanvasHeight = TEST_CANVAS_HEIGHT;
		// setScaleFactor(2, false, true);
		mCanvas.drawColor(Color.WHITE); // clear screen
		drawFontTest();
		invalidate();
	}

	/*
	 * Works with mRequestedCanvasHeight / mRequestedCanvasWidth
	 */
	void testBDFunctions(int aStartX, int aStartY, boolean aCompatibilityMode) {

		int tModeCharacter = 0x4E; // Character 'N'
		if (aCompatibilityMode) {
			tModeCharacter = 0x43; // Character 'C'
		}

		byte[] tByteBuffer = "Testy56789".getBytes();
		tByteBuffer[5] = (byte) 0x81; // Omega manually setted
		tByteBuffer[6] = (byte) 0x82; // Home
		tByteBuffer[7] = (byte) 0xB1; // +/-
		tByteBuffer[8] = (byte) 0xBB; // >>
		tByteBuffer[9] = (byte) 0xB5; // micro

		int[] tParameters = new int[6];

		// get actual canvas for drawing reference lines
		Canvas tCanvas = new Canvas(mBitmap);
		Paint tGraph1Paint = new Paint();
		tGraph1Paint.setColor(Color.BLACK);
		tGraph1Paint.setStyle(Paint.Style.STROKE);
		tGraph1Paint.setStrokeWidth(1);

		// direct draw reference line at start
		tCanvas.drawLine(aStartX, mScaleFactor * aStartY, aStartX + 10, mScaleFactor * aStartY, tGraph1Paint);
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
		tCanvas.drawPoint(mScaleFactor * tStartX + mScaleFactor * (10 + 1), mScaleFactor * aStartY + mScaleFactor * (10 + 1),
				tGraph1Paint); // 1
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
		byte[] tChartBuffer = new byte[mRequestedCanvasHeight / 2];
		for (int i = 0; i < tChartBuffer.length; i++) {
			tChartBuffer[i] = (byte) ((i + aStartY) % (mRequestedCanvasHeight / 2));
		}
		// DrawChart
		tParameters[0] = 0;
		tParameters[1] = (mRequestedCanvasHeight / 2);
		tParameters[2] = 0; // Color black
		tParameters[3] = 0; // No clearing of old chart
		interpretCommand(FUNCTION_DRAW_CHART, tParameters, 4, tChartBuffer, null, tChartBuffer.length);

		// Draw Z shaped chart
		tChartBuffer[0] = (byte) 3;
		tChartBuffer[1] = (byte) 2;
		tChartBuffer[2] = (byte) 1;
		tChartBuffer[3] = (byte) 0;
		tChartBuffer[4] = (byte) (mRequestedCanvasHeight / 2);
		tChartBuffer[5] = (byte) ((mRequestedCanvasHeight / 2) - 1);
		tChartBuffer[6] = (byte) ((mRequestedCanvasHeight / 2) - 2);
		tChartBuffer[7] = (byte) ((mRequestedCanvasHeight / 2) - 3);

		tParameters[0] = (aStartY % 30) + tChartBuffer.length;
		interpretCommand(FUNCTION_DRAW_CHART, tParameters, 4, tChartBuffer, null, 8);

		/*
		 * compare chart + lines
		 */
		tChartBuffer[0] = (byte) (mRequestedCanvasHeight / 2 + (aStartY % 30) + 0);
		tChartBuffer[1] = (byte) (mRequestedCanvasHeight / 2 + (aStartY % 30) + 2);
		tChartBuffer[2] = (byte) (mRequestedCanvasHeight / 2 + (aStartY % 30) + 4);
		tChartBuffer[3] = (byte) (mRequestedCanvasHeight / 2 + (aStartY % 30) + 8);
		tChartBuffer[4] = (byte) (mRequestedCanvasHeight / 2 + (aStartY % 30) + 16);
		tChartBuffer[5] = (byte) (mRequestedCanvasHeight / 2 + (aStartY % 30) + 10);
		tChartBuffer[6] = (byte) (mRequestedCanvasHeight / 2 + (aStartY % 30) + 16);
		tChartBuffer[7] = (byte) (mRequestedCanvasHeight / 2 + (aStartY % 30) + 10);
		tChartBuffer[8] = (byte) (mRequestedCanvasHeight / 2 + (aStartY % 30) + 6);
		tChartBuffer[9] = (byte) (mRequestedCanvasHeight / 2 + (aStartY % 30) + 2);
		tChartBuffer[10] = (byte) (mRequestedCanvasHeight / 2 + (aStartY % 30) + 0);

		tParameters[0] = 90;
		tParameters[1] = mRequestedCanvasHeight / 2 + (aStartY % 30) + 0;
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

	private void drawLogo(int aStartX, int aStartY, int aScaleDiv) {
		int[] tParameters = new int[6];
		int[] tPathParameters = new int[6];

		// blue rectangle
		tParameters[0] = 50 / aScaleDiv + aStartX;
		tParameters[1] = 330 / aScaleDiv + aStartY;
		tParameters[2] = 450 / aScaleDiv;
		tParameters[3] = 120 / aScaleDiv;
		tParameters[4] = 0x1F; // Color blue
		interpretCommand(FUNCTION_FILL_RECT_REL, tParameters, 5, null, null, 0);
		tParameters[4] = 0; // Color black
		tParameters[5] = 1; // Stroke width for Draw...
		interpretCommand(FUNCTION_DRAW_RECT_REL, tParameters, 5, null, null, 0);

		// red triangle
		tParameters[0] = 0xF800; // Color red
		tPathParameters[0] = 50 / aScaleDiv + aStartX;
		tPathParameters[1] = 100 / aScaleDiv + aStartY;
		tPathParameters[2] = 230 / aScaleDiv + aStartX;
		tPathParameters[3] = 440 / aScaleDiv + aStartY;
		tPathParameters[4] = 450 / aScaleDiv + aStartX;
		tPathParameters[5] = 330 / aScaleDiv + aStartY;
		interpretCommand(FUNCTION_FILL_PATH, tParameters, 1, null, tPathParameters, 6);
		tParameters[0] = 0x00; // Color black
		tParameters[1] = 1; // Stroke width for Draw...
		interpretCommand(FUNCTION_DRAW_PATH, tParameters, 2, null, tPathParameters, 6);

		// yellow circle
		tParameters[0] = 160 / aScaleDiv + aStartX;
		tParameters[1] = 210 / aScaleDiv + aStartY;
		tParameters[2] = 110 / aScaleDiv;
		tParameters[3] = 0XFFE0; // Color yellow

		interpretCommand(FUNCTION_FILL_CIRCLE, tParameters, 4, null, tPathParameters, 6);
		tParameters[3] = 0x00; // Color black
		tParameters[4] = 1; // Stroke width for Draw..
		interpretCommand(FUNCTION_DRAW_CIRCLE, tParameters, 2, null, tPathParameters, 6);
	}

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

		Paint tGraph1Paint = new Paint();
		tGraph1Paint.setColor(Color.BLACK);
		tGraph1Paint.setStyle(Paint.Style.STROKE);
		tGraph1Paint.setStrokeWidth(1);

		Paint tGraph1FillPaint = new Paint();
		tGraph1FillPaint.setARGB(0xFF, 0xFF, 0, 0xFF);
		tGraph1FillPaint.setStyle(Paint.Style.FILL);
		tGraph1FillPaint.setStrokeWidth(1);

		Paint tGraph2Paint = new Paint();
		tGraph2Paint.setColor(Color.BLACK);
		tGraph2Paint.setStyle(Paint.Style.STROKE);
		tGraph2Paint.setStrokeWidth(2);

		tCanvas.drawText("Display Width= " + mActualCanvasWidth + " of " + mActualViewWidth + " Height=" + mActualCanvasHeight
				+ " of " + mActualViewHeight, 75, tTextSize, tTextPaint);

		// mark corner
		// upper left
		tCanvas.drawRect(0, 0, 3, 3, tTextPaint); // results in 3*3 rect from 0
													// to 2 incl.
		tCanvas.drawText("0,0", 10, 10, tTextPaint);
		// upper right
		tCanvas.drawRect(TEST_CANVAS_WIDTH - 3, 0, TEST_CANVAS_WIDTH, 3, tGraph1Paint);
		// lower left
		tCanvas.drawRect(0, TEST_CANVAS_HEIGHT - 3, 3, TEST_CANVAS_HEIGHT, tGraph1Paint);
		// lower right
		tCanvas.drawRect(TEST_CANVAS_WIDTH - 3, TEST_CANVAS_HEIGHT - 3, TEST_CANVAS_WIDTH, TEST_CANVAS_HEIGHT, tTextPaint);
		if (TEST_CANVAS_WIDTH != mActualCanvasWidth) {
			tCanvas.drawText((mActualCanvasWidth - 1) + "," + (mActualCanvasHeight - 1), mActualCanvasWidth - 60,
					mActualCanvasHeight - 2, tTextPaint);
			tCanvas.drawRect(mActualCanvasWidth - 3, mActualCanvasHeight - 3, mActualCanvasWidth, mActualCanvasHeight, tTextPaint);
		}

		int startX;
		int startY;
		int tYPos = 25;
		String tString = "Cap=Butt | 5 times (stroke=1/stroke2 1*1-4*4 | 1*4 4*1) each one + 0.2 in Y";

		/*
		 * draw lines point and rects with stroke 1 and 2 and move by 0.2 in y direction
		 */
		for (int i = 0; i < 2; i++) {

			tCanvas.drawText(tString, 0, tYPos, tTextPaintInfo);
			tYPos += 5;
			startX = 4;
			startY = tYPos;
			for (int j = 0; j < 6; j++) {
				tCanvas.drawPoint(startX, startY, tGraph1Paint); // 1 pixel at 0
				startX += 3;
				tCanvas.drawPoint(startX, startY, tGraph2Paint);// 4 pixel at
																// 2/3
				tCanvas.drawPoint(startX, startY, tGraph1FillPaint); // reference
																		// point
				startX += 2;
				/*
				 * squares
				 */
				for (int k = 1; k < 5; k++) {
					tCanvas.drawRect(startX, startY, startX + k, startY + k, tGraph1Paint);
					tCanvas.drawPoint(startX, startY, tGraph1FillPaint);// reference
																		// point
					startX += k + 4;
					tCanvas.drawRect(startX, startY, startX + k, startY + k, tGraph2Paint);
					tCanvas.drawPoint(startX, startY, tGraph1FillPaint);// reference
																		// point
					startX += k + 2;
				}

				tCanvas.drawLine(startX, startY, startX + 4, startY, tGraph1Paint); // 4
				tCanvas.drawPoint(startX, startY, tGraph1FillPaint);// reference
																	// point
				startX += 7;
				tCanvas.drawLine(startX, startY, startX + 4, startY, tGraph2Paint); // 4x2
				tCanvas.drawPoint(startX, startY, tGraph1FillPaint);// reference
																	// point
				startX += 6;
				tCanvas.drawLine(startX, startY, startX, startY + 4, tGraph1Paint); // 4
				tCanvas.drawPoint(startX, startY, tGraph1FillPaint);// reference
																	// point
				startX += 4;
				tCanvas.drawLine(startX, startY, startX, startY + 4, tGraph2Paint); // 4x2
				tCanvas.drawPoint(startX, startY, tGraph1FillPaint);// reference
																	// point

				startX += 4;
				startY += 0.2;
			}
			tGraph1Paint.setStrokeCap(Cap.SQUARE);
			tGraph2Paint.setStrokeCap(Cap.SQUARE);
			tGraph1FillPaint.setStrokeCap(Cap.SQUARE);
			tString = "Cap=Square";
			tYPos += 20;
		}

		/*
		 * stars at different positions
		 */
		tGraph1Paint.setStrokeCap(Cap.BUTT);
		tGraph2Paint.setStrokeCap(Cap.BUTT);
		tYPos += 4;
		tCanvas.drawText("Stars | 4*stroke=1 Y=0,Y=0.5, Y=0.3,Y=0.7 | 2*stroke=2 | 2* corrected", 0, tYPos, tTextPaintInfo);

		tYPos += 20;
		float tX = 10.0f;
		drawStarForTests(tCanvas, tGraph1Paint, tGraph1FillPaint, tX, tYPos, 2, 5, 1, 3);

		// at 0.5 / 0.5
		tX = 25.5f;
		drawStarForTests(tCanvas, tGraph1Paint, tGraph1FillPaint, tX, (float) (tYPos + 0.5), 2, 5, 1, 3);

		// at 0.3 / 0.3
		tX = 40.3f;
		drawStarForTests(tCanvas, tGraph1Paint, tGraph1FillPaint, tX, (float) (tYPos + 0.3), 2, 5, 1, 3);

		// at 0.7 / 0.7
		tX = 55.7f;
		drawStarForTests(tCanvas, tGraph1Paint, tGraph1FillPaint, tX, (float) (tYPos + 0.7), 2, 5, 1, 3);

		tX = 90.0f;
		// without correction of origin and length
		drawStarForTests(tCanvas, tGraph2Paint, tGraph1FillPaint, tX, tYPos, 6, 6, 3, 2);

		tX += 30.0f;
		// manual length correction - 7 instead of 6 :-)
		drawStarForTests(tCanvas, tGraph2Paint, tGraph1FillPaint, tX, tYPos, 6, 7, 3, 3);

		tYPos += 10;
		/*
		 * corrected stars - for compatibility mode
		 */
		tX += 40.0f;
		// Zoom = 1
		drawStarCorrectedForTests(tCanvas, tGraph1Paint, tGraph1FillPaint, 1, tX, tYPos, 8, 12, 4, 4);

		tX += 40.0f;
		// Zoom = 2
		drawStarCorrectedForTests(tCanvas, tGraph1Paint, tGraph1FillPaint, 2, tX, tYPos, 8, 12, 4, 4);

		/*
		 * graph test
		 */
		tYPos -= 10;
		tX = 250;
		tCanvas.drawText("Graph: first BUTT then SQUARE", tX, tYPos, tTextPaintInfo);
		tYPos += 15;
		// draw baselines
		tCanvas.drawLine(tX, tYPos, tX + 40, tYPos, tGraph1Paint);
		tCanvas.drawLine(tX, tYPos - 10, tX + 40, tYPos - 10, tGraph1FillPaint);
		tCanvas.drawLine(tX, tYPos, tX + 2, tYPos - 5, tGraph2Paint);
		tX += 2;
		tCanvas.drawLine(tX, tYPos - 5, tX + 2, tYPos, tGraph2Paint);

		tX += 4;
		tCanvas.drawLine(tX, tYPos, tX + 2, tYPos - 5, tGraph2Paint);
		tX += 2;
		tCanvas.drawLine(tX, tYPos - 5, tX + 2, tYPos - 10, tGraph2Paint);
		tX += 2;
		tCanvas.drawLine(tX, tYPos - 10, tX + 2, tYPos - 5, tGraph2Paint);
		tX += 2;
		tCanvas.drawLine(tX, tYPos - 5, tX + 2, tYPos, tGraph2Paint);
		tX += 4;
		tCanvas.drawLine(tX, tYPos, tX + 2, tYPos - 10, tGraph2Paint);
		tX += 2;
		tCanvas.drawLine(tX, tYPos - 10, tX + 2, tYPos, tGraph2Paint);
		tX += 2;
		tCanvas.drawLine(tX, tYPos, tX + 2, tYPos - 10, tGraph2Paint);
		tX += 2;
		tCanvas.drawLine(tX, tYPos - 10, tX + 2, tYPos, tGraph2Paint);

		tGraph2Paint.setStrokeCap(Cap.SQUARE);
		tX += 4;
		tCanvas.drawLine(tX, tYPos, tX + 2, tYPos - 10, tGraph2Paint);
		tX += 2;
		tCanvas.drawLine(tX, tYPos - 10, tX + 2, tYPos, tGraph2Paint);
		tX += 2;
		tCanvas.drawLine(tX, tYPos, tX + 2, tYPos - 10, tGraph2Paint);
		tX += 2;
		tCanvas.drawLine(tX, tYPos - 10, tX + 2, tYPos, tGraph2Paint);

		tYPos += 20;
		tX = 250;
		tCanvas.drawText("Lines with StrokeWidth 1-5", tX, tYPos, tTextPaintInfo);
		tYPos += 10;
		// draw lines
		tCanvas.drawLine(tX, tYPos, tX + 10, tYPos, tGraph1Paint);
		tGraph1Paint.setStrokeWidth(2);
		tCanvas.drawLine(tX + 11, tYPos, tX + 20, tYPos, tGraph1Paint);
		tGraph1Paint.setStrokeWidth(3);
		tCanvas.drawLine(tX + 21, tYPos, tX + 30, tYPos, tGraph1Paint);
		tGraph1Paint.setStrokeWidth(4);
		tCanvas.drawLine(tX + 31, tYPos, tX + 40, tYPos, tGraph1Paint);
		tGraph1Paint.setStrokeWidth(5);
		tCanvas.drawLine(tX + 41, tYPos, tX + 50, tYPos, tGraph1Paint);
		return tYPos + 12;
	}

	/*
	 * Font size and background test
	 */
	@SuppressLint("DefaultLocale")
	public void drawFontTest() {
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

		Paint tGraph1Paint = new Paint();
		tGraph1Paint.setColor(Color.BLACK);
		tGraph1Paint.setStyle(Paint.Style.STROKE);
		tGraph1Paint.setStrokeWidth(1);

		Path tPath = new Path();
		RectF tRect = new RectF();

		tInfoText.setColor(Color.RED);
		tCanvas.drawText("Display Width= " + mActualCanvasWidth + " of " + mActualViewWidth + " Height=" + mActualCanvasHeight
				+ " of " + mActualViewHeight, 100, tTextSize, tInfoText);
		tCanvas.drawText("Font INFO:    SIZE|ASCENT|DESCENT|WIDTH", 100, 2 * tTextSize, tInfoText);
		tInfoText.setColor(Color.BLACK);

		float startX;
		float tYPos = 25;

		String tExampleString = "gti";
		startX = 0;
		String tString;
		/*
		 * Output TextSize-TextWidth for Size 5-75
		 */
		MyLog.i(LOG_TAG, "Font metrics:");
		int j = 5;
		for (int k = 0; k < 15; k++) {
			StringBuilder tFontsizes = new StringBuilder(100);
			StringBuilder tFontsizesMore = new StringBuilder(150);
			for (int i = 0; i < 5; i++) {
				tFontsizes.append(j);
				tFontsizesMore.append(j);
				tFontsizes.append('|');
				tFontsizesMore.append(';');

				tTextPaint.setTextSize(j);
				int tFontWidth = (int) (tTextPaint.measureText(tExampleString) / tExampleString.length());
				tString = String.format("%3.1f|", -tTextPaint.ascent());
				tFontsizes.append(tString);
				tString = String.format("%7.4f;", -tTextPaint.ascent());
				tFontsizesMore.append(tString);
				tString = String.format("%3.1f|", tTextPaint.descent());
				tFontsizes.append(tString);
				tString = String.format("%7.4f;", tTextPaint.descent());
				tFontsizesMore.append(tString);
				tFontsizes.append(tFontWidth);
				tFontsizesMore.append(tFontWidth);
				tTextPaint.getTextPath(tExampleString, 0, tExampleString.length(), 10, 10, tPath);
				tPath.computeBounds(tRect, false);
				tFontsizesMore.append(";" + (10 - tRect.top));
				tFontsizesMore.append(";" + (tRect.bottom - 10));
				tFontsizes.append(' ');
				tFontsizesMore.append("\r\n");
				j++;
			}
			tYPos += tTextSizeInfo;
			tCanvas.drawText(tFontsizes.toString(), 0, tYPos, tInfoText);
			MyLog.i(LOG_TAG, tFontsizesMore.toString());
		}

		tYPos += tTextSizeInfo;
		tYPos += tTextSizeInfo;
		tInfoText.setColor(Color.RED);
		tCanvas.drawText("draw text with background determined by ascent and decent", 0, tYPos, tInfoText);
		tYPos += tTextSizeInfo;

		/*
		 * draw text with background determined by ascent and decent.
		 */
		float tTextSizesArray[] = { 11.0f, 12.0f, 13.0f, 16.0f, 22.0f, 24.0f, 28.0f, 32.0f, 33.0f, 44.0f, 48.0f };
		float tEndX;
		tYPos += 60;
		startX = 0;
		tCanvas.drawLine(startX, tYPos, startX + 20, tYPos, tGraph1Paint); // Base
																			// line
		startX += 20;
		for (int i = 0; i < tTextSizesArray.length; i++) {
			tTextSize = tTextSizesArray[i];
			tTextPaint.setTextSize(tTextSize);
			tEndX = startX + (3 * ((tTextSize * 6) + 4) / 10);
			tCanvas.drawRect(startX, tYPos - (float) (tTextSize * 0.928), tEndX, tYPos + (float) (tTextSize * 0.235),
					tTextBackgroundPaint);
			tCanvas.drawText(tExampleString, startX, tYPos, tTextPaint);
			startX = tEndX + 3;
		}
		tCanvas.drawLine(startX + 3, tYPos, startX + 20, tYPos, tGraph1Paint); // Base
																				// line
		tYPos += tTextSizeInfo;
		tYPos += tTextSizeInfo;
		tCanvas.drawText("draw text with background determined by real ascent and decent from getTextPath()", 0, tYPos, tInfoText);
		tYPos += tTextSizeInfo;
		/*
		 * draw text with background determined by real ascent and decent - derived from getTextPath().
		 */

		tYPos += 60;
		startX = 0;
		tCanvas.drawLine(startX, tYPos, startX + 20, tYPos, tGraph1Paint); // Base
																			// line
		startX += 20;
		for (int i = 0; i < tTextSizesArray.length; i++) {
			tTextSize = tTextSizesArray[i];
			tTextPaint.setTextSize(tTextSize);
			tEndX = startX + (3 * ((tTextSize * 6) + 4) / 10);
			tCanvas.drawRect(startX, tYPos - (float) (tTextSize * 0.76), tEndX, tYPos + (float) (tTextSize * 0.24),
					tTextBackgroundPaint);
			tCanvas.drawText(tExampleString, startX, tYPos, tTextPaint);
			startX = tEndX + 3;
		}
		tCanvas.drawLine(startX + 3, tYPos, startX + 20, tYPos, tGraph1Paint); // Base
																				// line

	}

	private void drawStarForTests(Canvas tCanvas, Paint aPaint, Paint aFillPaint, float tX, float tY, int tOffsetCenter,
			int tLength, int tOffsetDiagonal, int tLengthDiagonal) {

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

	private void drawStarCorrectedForTests(Canvas tCanvas, Paint aPaint, Paint aFillPaint, int aZoom, float aX, float aY,
			int tOffsetCenter, int tLength, int tOffsetDiagonal, int tLengthDiagonal) {
		int tX = (int) aX;
		int tY = (int) aY;

		tLength--;
		tLengthDiagonal += tOffsetDiagonal - 1;

		int X = tX + tOffsetCenter;
		for (int i = 0; i < 2; i++) {
			drawCorrectedLineWithStartPixelForTests(X, tY, X + tLength, tY, aZoom, aPaint, tCanvas, aFillPaint);
			drawCorrectedLineWithStartPixelForTests(X, tY - tOffsetDiagonal, X + tLength, tY - tLengthDiagonal, aZoom, aPaint,
					tCanvas, aFillPaint);// < 45
			drawCorrectedLineWithStartPixelForTests(X, tY + tOffsetDiagonal, X + tLength, tY + tLengthDiagonal, aZoom, aPaint,
					tCanvas, aFillPaint); // < 45
			X = tX - tOffsetCenter;
			tLength = -tLength;
		}

		int Y = tY + tOffsetCenter;
		for (int i = 0; i < 2; i++) {
			drawCorrectedLineWithStartPixelForTests(tX, Y, tX, Y + tLength, aZoom, aPaint, tCanvas, aFillPaint);
			drawCorrectedLineWithStartPixelForTests(tX - tOffsetDiagonal, Y, tX - tLengthDiagonal, Y + tLength, aZoom, aPaint,
					tCanvas, aFillPaint);
			drawCorrectedLineWithStartPixelForTests(tX + tOffsetDiagonal, Y, tX + tLengthDiagonal, Y + tLength, aZoom, aPaint,
					tCanvas, aFillPaint);
			Y = tY - tOffsetCenter;
			tLength = -tLength;
		}

		X = tX + tOffsetCenter;
		tLengthDiagonal = tOffsetCenter + tLength;
		for (int i = 0; i < 2; i++) {
			drawCorrectedLineWithStartPixelForTests(X, tY - tOffsetCenter, X + tLength, tY - tLengthDiagonal, aZoom, aPaint,
					tCanvas, aFillPaint); // 45
			drawCorrectedLineWithStartPixelForTests(X, tY + tOffsetCenter, X + tLength, tY + tLengthDiagonal, aZoom, aPaint,
					tCanvas, aFillPaint); // 45

			X = tX - tOffsetCenter;
			tLength = -tLength;
		}

		tCanvas.drawPoint(tX, tY, aFillPaint);
	}

	private void drawCorrectedLineWithStartPixelForTests(int aStartX, int aStartY, int aStopX, int aStopY, int aScaleFactor,
			Paint aPaint, Canvas tCanvas, Paint aFillPaint) {
		drawLenghtCorrectedLine(aStartX, aStartY, aStopX, aStopY, aScaleFactor, aPaint, tCanvas);
		tCanvas.drawPoint(aStartX, aStartY, aFillPaint);
	}

}
