/**
 * 	SUMMARY
 * 	Blue Display is an Open Source Android remote Display for Arduino etc.
 * 	It receives basic draw requests from Arduino etc. over Bluetooth and renders it.
 * 	Send touch events over Bluetooth back to Arduino.
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
 *  This is the view which interprets the commands received by serial service.
 *  
 */
package de.joachimsmeyer.android.bluedisplay;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Toast;

public class RPCView extends View {

	public static final String LOG_TAG = "RPCView";

	private static final int DEFAULT_CANVAS_WIDTH = 320;
	private static final int DEFAULT_CANVAS_HEIGHT = 240;
	private static final int DEFAULT_SCALE_FACTOR = 2;
	private static final boolean DEFAULT_COMPATIBILITY_MODE = false;

	protected int mActualCanvasWidth;
	protected int mActualCanvasHeight;
	protected int mRequestedCanvasWidth;
	protected int mRequestedCanvasHeight;
	protected int mActualViewHeight; // Display Height - StatusBar - TitleBar
	protected int mActualViewWidth; // Display Width

	public static float[] mChartScreenBuffer = new float[DEFAULT_CANVAS_WIDTH * 4]; // 4 values for one line
	// public static float[] mChartLinesBuffer = new float[X_WIDTH*4];
	public static int mChartScreenBufferActualLength = 0;
	public static float mChartScreenBufferXStart = 0;

	public static Bitmap mBitmap;
	private Paint mBitmapPaint; // only used for onDraw() to draw bitmap
	private Paint mTextPaint;
	private Paint mTextBackgroundPaint;
	private Paint mGraphPaintStroke1;
	private Paint mGraphPaintStroke1Fill;
	private Paint mGraphPaintStrokeScaleFactor;
	private Paint mGraphPaintStrokeSettable;
	private Paint mResultingPaint;
	private Canvas mCanvas;
	private Path mPath = new Path();

	private BluetoothSerialService mSerialService;
	private BlueDisplay mBlueDisplayContext;

	private static char[] mChars = new char[1024];

	/*
	 * Scaling
	 */
	protected float mScaleFactor = DEFAULT_SCALE_FACTOR;
	protected float mMaxScaleFactor;
	float mTouchScaleFactor = DEFAULT_SCALE_FACTOR;
	Toast mLastScaleValueToast;
	/*
	 * Flags
	 */
	protected boolean mUseMaxSize = false;
	protected boolean mCompatibilityMode = true;
	protected boolean mTouchEnable = true;
	protected boolean mTouchMoveEnable = true;

	public boolean isCompatibilityMode() {
		return mCompatibilityMode;
	}

	public void setCompatibilityMode(boolean aCompatibilityMode) {
		this.mCompatibilityMode = aCompatibilityMode;
		mBlueDisplayContext.setCompatibilityModePreference(aCompatibilityMode);
	}

	public boolean isTouchEnable() {
		return mTouchEnable;
	}

	public void setTouchEnable(boolean aTouchEnable) {
		mTouchEnable = aTouchEnable;
		mBlueDisplayContext.setTouchModePreference(aTouchEnable);

	}

	public boolean isTouchMoveEnable() {
		return mTouchMoveEnable;
	}

	public void setTouchMoveEnable(boolean aTouchMoveEnable) {
		mTouchMoveEnable = aTouchMoveEnable;
		mBlueDisplayContext.setTouchMoveModePreference(aTouchMoveEnable);

	}

	private boolean mMultiTouchDetected = false;
	private ScaleGestureDetector mScaleDetector;

	// If used as background color for char or text, the background will not filled
	public static final int COLOR_NO_BACKGROUND = 0XFFFE;

	public static final int LAST_FUNCTION_TAG_DATAFIELD = 0x07;

	public static final int FUNCTION_TAG_SET_FLAGS = 0x08;
	private final int BD_FLAG_COMPATIBILITY_MODE_ENABLE = 0x01;
	private final int BD_FLAG_TOUCH_DISABLE = 0x02;
	private final int BD_FLAG_TOUCH_MOVE_DISABLE = 0x04;
	private final int BD_FLAG_USE_MAX_SIZE = 0x08;

	private final int FUNCTION_TAG_SET_FLAGS_AND_SIZE = 0x09;
	private final int FUNCTION_TAG_SET_CODEPAGE = 0x0A;
	private final int FUNCTION_TAG_SET_CHARACTER_CODE_MAPPING = 0x0B;

	private final int LAST_FUNCTION_TAG_CONFIGURATION = 0x0F;

	private final int FUNCTION_TAG_CLEAR_DISPLAY = 0x10;

	// 3 parameter
	private final int FUNCTION_TAG_DRAW_PIXEL = 0x14;

	// 5 parameter
	private final int FUNCTION_TAG_DRAW_LINE = 0x20;
	private final int FUNCTION_TAG_DRAW_RECT = 0x21;
	private final int FUNCTION_TAG_FILL_RECT = 0x22;
	private final int FUNCTION_TAG_DRAW_CIRCLE = 0x24;
	private final int FUNCTION_TAG_FILL_CIRCLE = 0x25;

	public static final int LAST_FUNCTION_TAG_WITHOUT_DATA = 0x5F;
	// Variable parameter length

	public static final int FUNCTION_TAG_DRAW_CHAR = 0x60;
	private final int FUNCTION_TAG_DRAW_STRING = 0x61;

	private final int FUNCTION_TAG_DRAW_PATH = 0x68;
	private final int FUNCTION_TAG_FILL_PATH = 0x69;
	private final int FUNCTION_TAG_DRAW_CHART = 0x6A;

	public void setSerialService(BluetoothSerialService aSerialService) {
		this.mSerialService = aSerialService;
	}

	protected void setMaxScaleFactor(int aWidthPixels, int aMaxViewHeight) {
		float tMaxHeightFactor = (float) aMaxViewHeight / mRequestedCanvasHeight;
		float tMaxWidthFactor = (float) aWidthPixels / mRequestedCanvasWidth;
		mMaxScaleFactor = Math.min(tMaxHeightFactor, tMaxWidthFactor);
		if (BlueDisplay.isDEBUG()) {
			Log.d(LOG_TAG, "MaxScaleFactor= " + mMaxScaleFactor);
		}
	}

	public RPCView(Context aContext) {
		super(aContext);
		Log.i(LOG_TAG, "+++ ON CREATE +++");

		mBlueDisplayContext = (BlueDisplay) aContext;

		mScaleDetector = new ScaleGestureDetector(aContext, new ScaleListener());

		mTextPaint = new Paint();
		mTextPaint.setTypeface(Typeface.MONOSPACE);
		mTextPaint.setStyle(Paint.Style.FILL);

		mTextBackgroundPaint = new Paint();
		mTextBackgroundPaint.setStrokeWidth(1);
		mTextBackgroundPaint.setStyle(Paint.Style.FILL);

		mGraphPaintStroke1 = new Paint();
		mGraphPaintStroke1.setStrokeWidth(1);
		mGraphPaintStroke1.setStyle(Paint.Style.STROKE);

		// no anti alias or +0.5 for lines with stroke 1 and 1 for lines with stroke 2
		// for fillRect
		mGraphPaintStroke1Fill = new Paint();
		mGraphPaintStroke1Fill.setStrokeWidth(1);
		mGraphPaintStroke1Fill.setStyle(Paint.Style.FILL);

		mGraphPaintStrokeScaleFactor = new Paint();
		mGraphPaintStrokeScaleFactor.setStyle(Paint.Style.STROKE);
		mGraphPaintStrokeScaleFactor.setStrokeCap(Cap.BUTT);

		mGraphPaintStrokeSettable = new Paint();
		mGraphPaintStrokeSettable.setStyle(Paint.Style.STROKE);

		/*
		 * create start Bitmap
		 */

		mRequestedCanvasWidth = DEFAULT_CANVAS_WIDTH;
		mRequestedCanvasHeight = DEFAULT_CANVAS_HEIGHT;
		mActualCanvasWidth = mRequestedCanvasWidth * DEFAULT_SCALE_FACTOR;
		mActualCanvasHeight = mRequestedCanvasHeight * DEFAULT_SCALE_FACTOR;
		mBitmap = Bitmap.createBitmap(mActualCanvasWidth, mActualCanvasHeight, Bitmap.Config.ARGB_8888);
		mBitmap.setHasAlpha(false);

		mBitmapPaint = new Paint();

		mCanvas = new Canvas(mBitmap);
		mCanvas.drawColor(Color.WHITE); // background

		/*
		 * initialize mapping array with actual codepage chars
		 */
		mCharMappingArray[0] = 0x00B5;
		short tUnicodeChar = 0x0080;
		for (int i = 0; i < mCharMappingArray.length; i++) {
			mCharMappingArray[i] = (char) tUnicodeChar;
			tUnicodeChar++;

		}

		setCompatibilityMode(DEFAULT_COMPATIBILITY_MODE);

	}

	@Override
	public void onSizeChanged(int aWidth, int aHeight, int aOldWidth, int aOldHeight) {
		// Us called on start and on changing orientation
		Log.i(LOG_TAG, "++ ON SizeChanged width=" + aWidth + " height=" + aHeight + " old width=" + aOldWidth + " old height="
				+ aOldHeight);

		mActualViewWidth = aWidth;
		mActualViewHeight = aHeight;
		setMaxScaleFactor(aWidth, aHeight);

		// resize canvas
		float tScaleFactor = mScaleFactor;
		if (mUseMaxSize) {
			tScaleFactor = 10;
		}
		setScaleFactor(tScaleFactor, true, true);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		if (BlueDisplay.isVERBOSE()) {
			Log.v(LOG_TAG, "+ ON Draw +");
		}

		if (mSerialService != null) {
			mSerialService.searchCommand(this);
		}
		canvas.drawBitmap(mBitmap, 0, 0, mBitmapPaint);
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(MotionEvent aEvent) {
		if (mTouchEnable) {
			// process event also by scale detector
			mScaleDetector.onTouchEvent(aEvent);

			if (aEvent.getActionIndex() > 0) {
				// multi touch started
				mMultiTouchDetected = true;
			}

			// get masked (not specific to a pointer) action
			int tMaskedAction = aEvent.getActionMasked();
			boolean tBasicTouch = false;
			switch (tMaskedAction) {
			case MotionEvent.ACTION_DOWN:
				tBasicTouch = true;
				mMultiTouchDetected = false;
				break;
			case MotionEvent.ACTION_UP:
				tBasicTouch = true;
				mMultiTouchDetected = false;
				break;
			default:
				break;
			}

			// send data if connected and and no multi-touch is present
			if (!mMultiTouchDetected && mSerialService != null
					&& mSerialService.getState() == BluetoothSerialService.STATE_CONNECTED) {
				if (mTouchMoveEnable || tBasicTouch)
					if (aEvent.getX() <= mActualCanvasWidth && aEvent.getY() <= mActualCanvasHeight) {
						mSerialService.write(tMaskedAction, aEvent.getX() / mScaleFactor, aEvent.getY() / mScaleFactor);
					}
			}
		}
		return true;
	}

	private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {

		@Override
		public boolean onScale(ScaleGestureDetector detector) {
			float tTempScaleFactor = detector.getScaleFactor();
			// reduce sensitivity by factor 3
			tTempScaleFactor -= 1.0;
			tTempScaleFactor /= 3;
			tTempScaleFactor += 1.0;

			mTouchScaleFactor *= tTempScaleFactor;
			// clip mmScaleFactor
			if (mTouchScaleFactor < 1) {
				mTouchScaleFactor = 1;
			} else if (mTouchScaleFactor > mMaxScaleFactor) {
				mTouchScaleFactor = mMaxScaleFactor;
			}

			if (BlueDisplay.isINFO()) {
				Log.i(LOG_TAG, "TouchScaleFactor=" + mTouchScaleFactor + " detector factor=" + tTempScaleFactor
						+ " MaxScaleFactor=" + mMaxScaleFactor);
			}

			// snap to 0.05 - 5%
			float tScaleFactor = mTouchScaleFactor;
			tScaleFactor *= 20;
			tScaleFactor = Math.round(tScaleFactor);
			tScaleFactor /= 20;

			// save and restore mTouchScaleFactor since setScaleFactor will overwrite it with tScaleFactor
			tTempScaleFactor = mTouchScaleFactor;
			boolean tRetvalue = !setScaleFactor(tScaleFactor, true, true);
			mTouchScaleFactor = tTempScaleFactor;
			return tRetvalue;
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

	private char[] mCharMappingArray = new char[128];

	private char[] myConvertChars(byte[] aData, char[] aChars, int aDataLength) {
		for (int i = 0; i < aDataLength; i++) {
			if (aData[i] > 0) {
				aChars[i] = (char) aData[i];
			} else {
				// mask highest bit
				int tHighChar = (aData[i] + 0x80);
				// get mapping
				char tChar = mCharMappingArray[tHighChar & 0x7F];
				if (tChar == 0x0000) {
					// no mapping found, use local codepage
					tChar = (char) (tHighChar + 0x80);
				}
				aChars[i] = tChar;
			}
		}
		return aChars;
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
			mScaleFactor = 1.0f;
		} else {
			/*
			 * Use maximum possible size if scale factor is too big or mUseMaxSize == true
			 */
			if (aScaleFactor > mMaxScaleFactor) {
				mScaleFactor = mMaxScaleFactor;
			} else {
				mScaleFactor = aScaleFactor;
			}
			if (!allowFloatFactor) {
				int tIntFactor = (int) mScaleFactor;
				mScaleFactor = tIntFactor;
			}
		}
		if (tOldFactor != mScaleFactor) {
			// this can be skipped for tests with aResizeCanvas = false
			mActualCanvasWidth = (int) (mRequestedCanvasWidth * mScaleFactor);
			mActualCanvasHeight = (int) (mRequestedCanvasHeight * mScaleFactor);
			Bitmap tOldBitmap = mBitmap;
			mBitmap = Bitmap.createScaledBitmap(mBitmap, mActualCanvasWidth, mActualCanvasHeight, false);
			mCanvas = new Canvas(mBitmap);
			tOldBitmap.recycle();

			mTouchScaleFactor = mScaleFactor;
		}

		mGraphPaintStrokeScaleFactor.setStrokeWidth(mScaleFactor);

		Log.i(LOG_TAG, "setScaleFactor(" + aScaleFactor + ") old factor=" + tOldFactor + " resulting factor=" + mScaleFactor);
		if (tOldFactor != mScaleFactor) {
			invalidate();
			// send new size to client
			if (mSerialService != null && aSendToClient) {
				mSerialService.write(BluetoothSerialService.EVENT_TAG_RESIZE_ACTION, mActualCanvasWidth, mActualCanvasHeight);
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
		return false;
	}

	public static int shortToLongColor(int aShortColor) {
		return ((aShortColor & 0x1F) << 3 | (aShortColor & 0x07E0) << 5 | (aShortColor & 0xF800) << 8) | 0xFF000000;
	}

	public boolean interpreteCommand(int aCommand, int[] aParameters, int aParamsLength, byte[] aDataBytes, int[] aDataInts,
			int aDataLength) {

		if (BlueDisplay.isVERBOSE()) {
			StringBuilder tParam = new StringBuilder("c=" + mCompatibilityMode + " cmd=" + aCommand);
			for (int i = 0; i < aParamsLength; i++) {
				tParam.append(" ").append(i).append("=").append(aParameters[i]);
			}
			Log.v(LOG_TAG, tParam.toString());
		}
		float tXStart = aParameters[0] * mScaleFactor;
		float tYStart = aParameters[1] * mScaleFactor;
		float tXEnd = 0;
		float tYEnd = 0;
		int tTextSize;
		int tColor;
		int tIndex;
		boolean tRetValue = false;
		String tFunctionName;
		String tAdditionalInfo = "";

		float tDecend;
		float tAscend;
		switch (aCommand) {
		case FUNCTION_TAG_SET_FLAGS:
			if (BlueDisplay.isINFO()) {
				Log.i(LOG_TAG, "Set flags=0x" + Integer.toHexString(aParameters[0]));
			}
			setFlags(aParameters[0]);
			break;

		case FUNCTION_TAG_SET_FLAGS_AND_SIZE:
			// set canvas size
			if (BlueDisplay.isINFO()) {
				Log.i(LOG_TAG, "Set flags=0x" + Integer.toHexString(aParameters[0]) + " and canvas size=" + aParameters[1] + "/"
						+ aParameters[2]);
			}
			setFlags(aParameters[0]);
			mRequestedCanvasWidth = aParameters[1];
			mRequestedCanvasHeight = aParameters[2];
			break;

		case FUNCTION_TAG_SET_CODEPAGE:
			if (BlueDisplay.isINFO()) {
				Log.i(LOG_TAG, "Set codepage=ISO_8859_" + aParameters[0]);
			}
			String tCharsetName = "ISO_8859_" + aParameters[0];
			Charset tCharset = Charset.forName(tCharsetName);
			byte[] tCodepage = new byte[mCharMappingArray.length];
			for (int i = 0; i < mCharMappingArray.length; i++) {
				tCodepage[i] = (byte) (0x0080 + i);
			}
			ByteBuffer tBytebuffer = ByteBuffer.wrap(tCodepage);
			CharBuffer tCharBuffer = tCharset.decode(tBytebuffer);
			mCharMappingArray = tCharBuffer.array();
			// char[] tCharArray = tCharBuffer.array();
			// for (int i = 0; i < mCharMappingArray.length; i++) {
			// mCharMappingArray[i] = tCharArray[i];
			// }
			break;
		case FUNCTION_TAG_SET_CHARACTER_CODE_MAPPING:
			tIndex = aParameters[0] - 0x80;
			if (BlueDisplay.isINFO()) {
				Log.i(LOG_TAG, "Set character mapping=" + mCharMappingArray[tIndex] + "->" + (char) aParameters[1] + " / "
						+ Integer.toHexString(aParameters[0]) + "->" + Integer.toHexString(aParameters[1]));
			}
			if (tIndex >= 0 && tIndex < mCharMappingArray.length) {
				mCharMappingArray[tIndex] = (char) aParameters[1];
			}
			break;

		case FUNCTION_TAG_CLEAR_DISPLAY:
			// clear screen
			if (BlueDisplay.isINFO()) {
				Log.i(LOG_TAG, "Clear screen color=" + Integer.toHexString(shortToLongColor(aParameters[0])));
			}
			mCanvas.drawColor(shortToLongColor(aParameters[0]));
			// reset screen buffer
			mChartScreenBufferActualLength = 0;
			break;

		case FUNCTION_TAG_DRAW_PIXEL:
			mGraphPaintStrokeScaleFactor.setColor(shortToLongColor(aParameters[2]));
			if (BlueDisplay.isINFO()) {
				Log.i(LOG_TAG,
						"drawPixel(" + tXStart + ", " + tYStart + ") color="
								+ Integer.toHexString(shortToLongColor(aParameters[2])));
			}
			mCanvas.drawPoint(tXStart, tYStart, mGraphPaintStrokeScaleFactor);
			break;

		case FUNCTION_TAG_DRAW_LINE:
			tXEnd = aParameters[2] * mScaleFactor;
			tYEnd = aParameters[3] * mScaleFactor;
			tColor = shortToLongColor(aParameters[4]);

			if (mCompatibilityMode) {
				mGraphPaintStroke1.setColor(tColor);
				drawLenghtCorrectedLine(tXStart, tYStart, tXEnd, tYEnd, (int) (mScaleFactor + 0.5), mGraphPaintStroke1, mCanvas);
			} else {
				if (aParamsLength > 5) {
					// Stroke parameter
					mGraphPaintStrokeSettable.setStrokeWidth(aParameters[5] * mScaleFactor);
					mResultingPaint = mGraphPaintStrokeSettable;
					if (BlueDisplay.isINFO()) {
						tAdditionalInfo = " strokeWidth=" + aParameters[5] * mScaleFactor;
					}
				} else {
					mResultingPaint = mGraphPaintStrokeScaleFactor;
				}

				mResultingPaint.setColor(tColor);
				if (tXStart == tXEnd && tYStart == tYEnd) {
					mCanvas.drawPoint(tXStart, tYStart, mResultingPaint);
				} else {
					mCanvas.drawLine(tXStart, tYStart, tXEnd, tYEnd, mResultingPaint);
				}
			}
			if (BlueDisplay.isINFO()) {
				Log.i(LOG_TAG,
						"drawLine(" + tXStart + ", " + tYStart + ", " + tXEnd + ", " + tYEnd + ") color="
								+ Integer.toHexString(tColor) + tAdditionalInfo);
			}
			break;

		case FUNCTION_TAG_DRAW_CHART:
			tColor = shortToLongColor(aParameters[2]);
			int tDeleteColor = shortToLongColor(aParameters[3]);

			if (BlueDisplay.isINFO()) {
				Log.i(LOG_TAG, "drawChart(" + tXStart + ", " + tYStart + ") color=" + Integer.toHexString(tColor)
						+ " ,deleteColor=" + Integer.toHexString(tDeleteColor) + " lenght=" + aDataLength);
			}

			if (aParameters[3] != 0) {
				/*
				 * delete old chart
				 */
				// set delete color
				mGraphPaintStrokeScaleFactor.setColor(tDeleteColor);
				mCanvas.drawLines(mChartScreenBuffer, 0, mChartScreenBufferActualLength * 4, mGraphPaintStrokeScaleFactor);
			}

			/*
			 * draw new chart
			 */

			mGraphPaintStrokeScaleFactor.setColor(tColor);

			float tYOffset = tYStart;
			tYStart += BluetoothSerialService.convertByteToFloat(aDataBytes[0]) * mScaleFactor;
			mChartScreenBuffer[0] = tXStart;
			mChartScreenBuffer[1] = tYStart;
			mChartScreenBufferActualLength = aDataLength;
			mChartScreenBufferXStart = tXStart;
			int j = 1;
			int i = 2;
			// for points for each line
			while (i < (aDataLength - 2) * 4) {
				tXStart += mScaleFactor;
				tYStart = (BluetoothSerialService.convertByteToFloat(aDataBytes[j++]) * mScaleFactor) + tYOffset;
				// end of first line ...
				mChartScreenBuffer[i++] = tXStart;
				mChartScreenBuffer[i++] = tYStart;
				// ... is start of next line
				mChartScreenBuffer[i++] = tXStart;
				mChartScreenBuffer[i++] = tYStart;
			}
			mChartScreenBuffer[i++] = tXStart + mScaleFactor;
			mChartScreenBuffer[i] = (BluetoothSerialService.convertByteToFloat(aDataBytes[j++]) * mScaleFactor) + tYOffset;

			mCanvas.drawLines(mChartScreenBuffer, 0, aDataLength * 4, mGraphPaintStrokeScaleFactor);

			tRetValue = true;
			break;

		case FUNCTION_TAG_DRAW_PATH:
		case FUNCTION_TAG_FILL_PATH:
			tColor = shortToLongColor(aParameters[0]);
			if (aCommand == FUNCTION_TAG_DRAW_PATH) {
				tFunctionName = "drawPath";
				mGraphPaintStrokeSettable.setColor(tColor);
				mGraphPaintStrokeSettable.setStrokeWidth(aParameters[1] * mScaleFactor);
				mResultingPaint = mGraphPaintStrokeSettable;
				if (BlueDisplay.isINFO()) {
					tAdditionalInfo = " strokeWidth=" + aParameters[1] * mScaleFactor;
				}
			} else {
				tFunctionName = "fillPath";
				mGraphPaintStroke1Fill.setColor(tColor);
				mResultingPaint = mGraphPaintStroke1Fill;
			}
			if (BlueDisplay.isINFO()) {
				Log.i(LOG_TAG, tFunctionName + "(" + tXStart + ", " + tYStart + ") color=" + Integer.toHexString(tColor)
						+ " lenght=" + aDataLength + tAdditionalInfo);
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

			mCanvas.drawPath(mPath, mResultingPaint);

			// Path only consists of lines
			mPath.rewind();

			tRetValue = true;
			break;

		case FUNCTION_TAG_DRAW_RECT: // 0x21
		case FUNCTION_TAG_FILL_RECT: // 0x22
			// get new values because of later calling drawLenghtCorrectedLine()
			tXEnd = aParameters[2] * mScaleFactor;
			tYEnd = aParameters[3] * mScaleFactor;
			tColor = shortToLongColor(aParameters[4]);

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
			if (aCommand == FUNCTION_TAG_DRAW_RECT) {
				tFunctionName = "drawRect";
				mGraphPaintStrokeSettable.setColor(tColor);
				mGraphPaintStrokeSettable.setStrokeWidth(aParameters[5] * mScaleFactor);
				mResultingPaint = mGraphPaintStrokeSettable;
				if (BlueDisplay.isINFO()) {
					tAdditionalInfo = " strokeWidth=" + aParameters[5] * mScaleFactor;
				}
			} else {
				tFunctionName = "fillRect";
				mGraphPaintStroke1Fill.setColor(tColor);
				mResultingPaint = mGraphPaintStroke1Fill;
			}

			if (BlueDisplay.isINFO()) {
				Log.i(LOG_TAG, tFunctionName + "(" + tXStart + ", " + tYStart + ", " + tXEnd + ", " + tYEnd + ") , color="
						+ Integer.toHexString(tColor) + tAdditionalInfo);
			}

			if (mCompatibilityMode) {
				if (aCommand == FUNCTION_TAG_DRAW_RECT) {
					mCanvas.drawRect(tXStart, tYStart, tXEnd + mScaleFactor, tYEnd + mScaleFactor, mGraphPaintStrokeSettable);
					if (BlueDisplay.isINFO()) {
						Log.i(LOG_TAG, "drawRect(" + tXStart + ", " + tYStart + ", " + tXEnd + ", " + tYEnd + ") , color="
								+ Integer.toHexString(tColor) + " strokeWidth=" + aParameters[1] * mScaleFactor);
					}
				} else {
					mCanvas.drawRect(tXStart, tYStart, tXEnd + mScaleFactor, tYEnd + mScaleFactor, mGraphPaintStroke1Fill);

				}
			} else {
				if (tXStart == tXEnd) {
					// vertical line
					mGraphPaintStrokeScaleFactor.setColor(tColor);
					if (BlueDisplay.isINFO()) {
						Log.i(LOG_TAG,
								"drawLine (" + tXStart + ", " + tYStart + ", " + tXEnd + ", " + tYEnd + ") color="
										+ Integer.toHexString(tColor));
					}
					mCanvas.drawLine(tXStart, tYStart, tXEnd, tYEnd, mGraphPaintStrokeScaleFactor);
				} else if (tYStart == tYEnd) {
					// horizontal line
					mGraphPaintStrokeScaleFactor.setColor(tColor);
					if (BlueDisplay.isINFO()) {
						Log.i(LOG_TAG,
								"drawLine (" + tXStart + ", " + tYStart + ", " + tXEnd + ", " + tYEnd + ") color="
										+ Integer.toHexString(tColor));
					}
					mCanvas.drawLine(tXStart, tYStart, tXEnd, tYEnd, mGraphPaintStrokeScaleFactor);
				} else {
					mCanvas.drawRect(tXStart, tYStart, tXEnd, tYEnd, mResultingPaint);
				}
			}
			break;

		case FUNCTION_TAG_DRAW_CIRCLE:
		case FUNCTION_TAG_FILL_CIRCLE:
			tColor = shortToLongColor(aParameters[3]);
			float tRadius = aParameters[2] * mScaleFactor;
			if (aCommand == FUNCTION_TAG_DRAW_CIRCLE) {
				tFunctionName = "drawCircle";
				mGraphPaintStrokeSettable.setColor(tColor);
				mGraphPaintStrokeSettable.setStrokeWidth(aParameters[5] * mScaleFactor);
				mResultingPaint = mGraphPaintStrokeSettable;
				if (BlueDisplay.isINFO()) {
					tAdditionalInfo = " strokeWidth=" + aParameters[5] * mScaleFactor;
				}
			} else {
				tFunctionName = "fillCircle";
				mGraphPaintStroke1Fill.setColor(tColor);
				mResultingPaint = mGraphPaintStroke1Fill;
			}

			if (BlueDisplay.isINFO()) {
				Log.i(LOG_TAG,
						tFunctionName + "(" + tXStart + ", " + tYStart + ", " + tRadius + ") ,color="
								+ Integer.toHexString(shortToLongColor(aParameters[3])) + tAdditionalInfo);
			}
			mCanvas.drawCircle(tXStart, tYStart, tRadius, mResultingPaint);
			break;

		case FUNCTION_TAG_DRAW_CHAR:
		case FUNCTION_TAG_DRAW_STRING:

			tTextSize = (int) (aParameters[2] * mScaleFactor);
			mTextPaint.setTextSize(tTextSize);
			int tTextWidth = ((tTextSize * 6) + 4) / 10;
			tAscend = (float) (tTextSize * 0.76);
			tDecend = (float) (tTextSize * 0.24);

			if (aCommand == FUNCTION_TAG_DRAW_CHAR) {
				tFunctionName = "drawChar";
			} else {
				tFunctionName = "drawString";
			}
			myConvertChars(aDataBytes, mChars, aDataLength);
			String tString = new String(mChars, 0, aDataLength);

			mTextPaint.setColor(shortToLongColor(aParameters[3]));

			if (BlueDisplay.isINFO()) {
				Log.i(LOG_TAG,
						tFunctionName + "(\"" + tString + "\", " + tXStart + ", " + tYStart + ", " + tTextSize + ", "
								+ aParameters[3] + ") color=" + Integer.toHexString(shortToLongColor(aParameters[3])) + " bg="
								+ Integer.toHexString(shortToLongColor(aParameters[4])));
			}

			tIndex = tString.indexOf('\n');
			if (tIndex > 0) {
				int tStartIndex = 0;
				while (tIndex > 0) {
					/*
					 * Multiline text
					 */
					if (mCompatibilityMode || aParameters[4] != COLOR_NO_BACKGROUND) {
						mTextBackgroundPaint.setColor(shortToLongColor(aParameters[4]));
						// draw background for whole rest of line
						mCanvas.drawRect(tXStart, tYStart - tAscend, mActualCanvasWidth, tYStart + tDecend, mTextBackgroundPaint);
					}
					// check for single newline
					if (tStartIndex != tIndex) {
						// draw string
						mCanvas.drawText(tString, tStartIndex, tIndex - 1, tXStart, tYStart, mTextPaint);
						tYStart += tTextSize + mScaleFactor;
					}
					// search for next newline
					tStartIndex = tIndex + 1;
					if (tIndex + 1 <= tString.length()) {
						tIndex = tString.indexOf('\n', tIndex + 1);
					} else {
						tIndex = 0;
					}
				}
			} else {
				if (mCompatibilityMode || aParameters[4] != COLOR_NO_BACKGROUND) {
					mTextBackgroundPaint.setColor(shortToLongColor(aParameters[4]));
					// draw background
					mCanvas.drawRect(tXStart, tYStart - tAscend, tXStart + (tTextWidth * aDataLength) + 1, tYStart + tDecend,
							mTextBackgroundPaint);
				}

				// draw char / string
				mCanvas.drawText(mChars, 0, aDataLength, tXStart, tYStart, mTextPaint);
			}
			break;

		default:
			Log.e(LOG_TAG, "unknown command " + aCommand + " received. paramsLength=" + aParamsLength + " dataLenght="
					+ aDataLength);
			break;
		}
		// long tEnd = System.nanoTime();
		// Log.i(LOG_TAG, "Interpret=" + (tEnd - tStart));
		return tRetValue;
	}

	private void setFlags(int aFlags) {
		if ((aFlags & BD_FLAG_COMPATIBILITY_MODE_ENABLE) != 0) {
			setCompatibilityMode(true);
		} else {
			setCompatibilityMode(false);
		}
		if ((aFlags & BD_FLAG_TOUCH_DISABLE) != 0) {
			setTouchEnable(false);
		} else {
			setTouchEnable(true);
		}
		if ((aFlags & BD_FLAG_TOUCH_MOVE_DISABLE) != 0) {
			setTouchMoveEnable(false);
		} else {
			setTouchMoveEnable(true);
		}
		if ((aFlags & BD_FLAG_USE_MAX_SIZE) != 0) {
			mUseMaxSize = true;
			// resize canvas
			setScaleFactor(10, true, false);
		} else {
			mUseMaxSize = false;
		}
		if (BlueDisplay.isINFO()) {
			Log.i(LOG_TAG, "compatibility Mode=" + mCompatibilityMode + ", TouchEnable=" + mTouchEnable + ", TouchMoveEnable"
					+ mTouchMoveEnable + ", UseMaxSize=" + mUseMaxSize);
		}

	}

	/******************************************************************************************
	 * TEST METHODS
	 ******************************************************************************************/

	public void showGraphTestpage() {
		// adjust canvas size to scale factor 2
		mRequestedCanvasWidth = DEFAULT_CANVAS_WIDTH;
		mRequestedCanvasHeight = DEFAULT_CANVAS_HEIGHT;
		mCanvas.drawColor(Color.WHITE); // clear screen

		int tY = (int) (drawGraphTestPattern() / mScaleFactor) + 10;

		setCompatibilityMode(false);
		DrawLogo(200, 120, 5);
		testInterpreteCommand(5, tY);
		setCompatibilityMode(true);
		tY += 15;
		testInterpreteCommand(5, tY);
		setCompatibilityMode(false);

		invalidate();
	}

	public void showFontTestpage() {
		// adjust canvas size to scale factor 2
		mRequestedCanvasWidth = DEFAULT_CANVAS_WIDTH;
		mRequestedCanvasHeight = DEFAULT_CANVAS_HEIGHT;
		// setScaleFactor(2, false, true);
		mCanvas.drawColor(Color.WHITE); // clear screen
		drawFontTest();
		invalidate();
	}

	/*
	 * Works with mRequestedCanvasHeight / mRequestedCanvasWidth
	 */
	void testInterpreteCommand(int aStartX, int aStartY) {

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
		interpreteCommand(FUNCTION_TAG_DRAW_LINE, tParameters, 6, tByteBuffer, null, 0);
		tParameters[0] = tParameters[2] = tStartX;
		tParameters[1] = aStartY + 1;
		tParameters[3] = aStartY + 10;
		tParameters[4] = 0x7E0; // Color green
		interpreteCommand(FUNCTION_TAG_DRAW_LINE, tParameters, 6, tByteBuffer, null, 0);
		tParameters[0] = tParameters[2] = tStartX + 10;
		interpreteCommand(FUNCTION_TAG_DRAW_LINE, tParameters, 6, tByteBuffer, null, 0);
		tParameters[1] = tParameters[3] = aStartY + 10;
		tParameters[0] = tStartX + 1;
		tParameters[2] = tStartX + 9;
		tParameters[4] = 0xF800; // Color red
		// red bottom line as rect
		interpreteCommand(FUNCTION_TAG_FILL_RECT, tParameters, 6, tByteBuffer, null, 0);

		// draw 2 border points outside box
		tCanvas.drawPoint(mScaleFactor * tStartX - 1, mScaleFactor * aStartY - 1, tGraph1Paint); // 1 pixel
		tCanvas.drawPoint(mScaleFactor * tStartX + mScaleFactor * (10 + 1), mScaleFactor * aStartY + mScaleFactor * (10 + 1),
				tGraph1Paint); // 1 pixel

		// draw bigger rect outside
		tParameters[0] = tStartX - 2;
		tParameters[2] = tStartX + 12;
		tParameters[1] = aStartY - 2;
		tParameters[3] = aStartY + 12;
		tParameters[4] = 0xF800; // Color red
		interpreteCommand(FUNCTION_TAG_DRAW_RECT, tParameters, 6, tByteBuffer, null, 0);

		// fill with red square
		tParameters[0] = tStartX + 2;
		tParameters[2] = tStartX + 8;
		tParameters[1] = aStartY + 2;
		tParameters[3] = aStartY + 8;
		tParameters[4] = 0xF800; // Color red
		interpreteCommand(FUNCTION_TAG_FILL_RECT, tParameters, 6, tByteBuffer, null, 0);

		// fill with black circle
		tParameters[0] = tStartX + 5;
		tParameters[1] = aStartY + 5;
		tParameters[2] = 3; // radius
		tParameters[3] = 0;
		interpreteCommand(FUNCTION_TAG_DRAW_CIRCLE, tParameters, 6, tByteBuffer, null, 0);

		// and draw center point
		tCanvas.drawPoint(mScaleFactor * tStartX + mScaleFactor * 5, mScaleFactor * aStartY + mScaleFactor * 5, tGraph1Paint); // 1

		// draw char + string
		tParameters[0] = tStartX + 15;
		tParameters[1] = aStartY;
		tParameters[2] = 11; // Size
		tParameters[3] = 0; // Color black
		tParameters[4] = 0x1F; // Background color blue
		interpreteCommand(FUNCTION_TAG_DRAW_CHAR, tParameters, 6, tByteBuffer, null, 1);

		// ISO_8859_15
		tParameters[0] = 15;
		interpreteCommand(FUNCTION_TAG_SET_CODEPAGE, tParameters, 1, tByteBuffer, null, 0);

		tParameters[0] = 0x81;
		tParameters[1] = 0x03A9; // Omega in UTF16
		interpreteCommand(FUNCTION_TAG_SET_CHARACTER_CODE_MAPPING, tParameters, 2, tByteBuffer, null, 0);

		tParameters[0] = 0x82;
		tParameters[1] = 0x2302; // House in UTF16
		interpreteCommand(FUNCTION_TAG_SET_CHARACTER_CODE_MAPPING, tParameters, 2, tByteBuffer, null, 0);

		tParameters[0] = tStartX + 30;
		tParameters[1] = aStartY;
		interpreteCommand(FUNCTION_TAG_DRAW_STRING, tParameters, 6, tByteBuffer, null, tByteBuffer.length);

		// generate chart data
		byte[] tChartBuffer = new byte[mRequestedCanvasHeight / 2];
		for (int i = 0; i < tChartBuffer.length; i++) {
			tChartBuffer[i] = (byte) ((i + aStartY) % (mRequestedCanvasHeight / 2));
		}
		// DrawChart
		tParameters[0] = 0;
		tParameters[1] = (mRequestedCanvasHeight / 2);
		tParameters[2] = 0; // Color black
		tParameters[3] = 0; // No background
		interpreteCommand(FUNCTION_TAG_DRAW_CHART, tParameters, 6, tChartBuffer, null, tChartBuffer.length);

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
		interpreteCommand(FUNCTION_TAG_DRAW_CHART, tParameters, 6, tChartBuffer, null, 8);

		tParameters[0] = BD_FLAG_COMPATIBILITY_MODE_ENABLE;
		interpreteCommand(FUNCTION_TAG_SET_FLAGS, tParameters, 1, null, null, 0);
	}

	private void DrawLogo(int aStartX, int aStartY, int aScaleDiv) {
		int[] tParameters = new int[6];
		int[] tPathParameters = new int[6];

		// blue rectangle
		tParameters[0] = 50 / aScaleDiv + aStartX;
		tParameters[1] = 330 / aScaleDiv + aStartY;
		tParameters[2] = (50 + 450) / aScaleDiv + aStartX;
		tParameters[3] = (330 + 120) / aScaleDiv + aStartY;
		tParameters[4] = 0x1F; // Color blue
		interpreteCommand(FUNCTION_TAG_FILL_RECT, tParameters, 5, null, null, 0);
		tParameters[4] = 0; // Color black
		tParameters[5] = 1; // Stroke width for Draw...
		interpreteCommand(FUNCTION_TAG_DRAW_RECT, tParameters, 5, null, null, 0);

		// red triangle
		tParameters[0] = 0xF800; // Color red
		tPathParameters[0] = 50 / aScaleDiv + aStartX;
		tPathParameters[1] = 100 / aScaleDiv + aStartY;
		tPathParameters[2] = 230 / aScaleDiv + aStartX;
		tPathParameters[3] = 440 / aScaleDiv + aStartY;
		tPathParameters[4] = 450 / aScaleDiv + aStartX;
		tPathParameters[5] = 330 / aScaleDiv + aStartY;
		interpreteCommand(FUNCTION_TAG_FILL_PATH, tParameters, 1, null, tPathParameters, 6);
		tParameters[0] = 0x00; // Color black
		tParameters[1] = 1; // Stroke width for Draw...
		interpreteCommand(FUNCTION_TAG_DRAW_PATH, tParameters, 2, null, tPathParameters, 6);

		// yellow circle
		tParameters[0] = 160 / aScaleDiv + aStartX;
		tParameters[1] = 210 / aScaleDiv + aStartY;
		tParameters[2] = 110 / aScaleDiv;
		tParameters[3] = 0XFFE0; // Color yellow

		interpreteCommand(FUNCTION_TAG_FILL_CIRCLE, tParameters, 4, null, tPathParameters, 6);
		tParameters[3] = 0x00; // Color black
		tParameters[4] = 1; // Stroke width for Draw..
		interpreteCommand(FUNCTION_TAG_DRAW_CIRCLE, tParameters, 2, null, tPathParameters, 6);

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
		tCanvas.drawRect(0, 0, 3, 3, tTextPaint); // results in 3*3 rect from 0 to 2 incl.
		tCanvas.drawText("0,0", 10, 10, tTextPaint);
		// upper right
		tCanvas.drawRect(DEFAULT_CANVAS_WIDTH - 3, 0, DEFAULT_CANVAS_WIDTH, 3, tGraph1Paint);
		// lower left
		tCanvas.drawRect(0, DEFAULT_CANVAS_HEIGHT - 3, 3, DEFAULT_CANVAS_HEIGHT, tGraph1Paint);
		// lower right
		tCanvas.drawRect(DEFAULT_CANVAS_WIDTH - 3, DEFAULT_CANVAS_HEIGHT - 3, DEFAULT_CANVAS_WIDTH, DEFAULT_CANVAS_HEIGHT,
				tTextPaint);
		if (DEFAULT_CANVAS_WIDTH != mActualCanvasWidth) {
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
				tCanvas.drawPoint(startX, startY, tGraph2Paint);// 4 pixel at 2/3
				tCanvas.drawPoint(startX, startY, tGraph1FillPaint); // reference point
				startX += 2;
				/*
				 * squares
				 */
				for (int k = 1; k < 5; k++) {
					tCanvas.drawRect(startX, startY, startX + k, startY + k, tGraph1Paint);
					tCanvas.drawPoint(startX, startY, tGraph1FillPaint);// reference point
					startX += k + 4;
					tCanvas.drawRect(startX, startY, startX + k, startY + k, tGraph2Paint);
					tCanvas.drawPoint(startX, startY, tGraph1FillPaint);// reference point
					startX += k + 2;
				}

				tCanvas.drawLine(startX, startY, startX + 4, startY, tGraph1Paint); // 4
				tCanvas.drawPoint(startX, startY, tGraph1FillPaint);// reference point
				startX += 7;
				tCanvas.drawLine(startX, startY, startX + 4, startY, tGraph2Paint); // 4x2
				tCanvas.drawPoint(startX, startY, tGraph1FillPaint);// reference point
				startX += 6;
				tCanvas.drawLine(startX, startY, startX, startY + 4, tGraph1Paint); // 4
				tCanvas.drawPoint(startX, startY, tGraph1FillPaint);// reference point
				startX += 4;
				tCanvas.drawLine(startX, startY, startX, startY + 4, tGraph2Paint); // 4x2
				tCanvas.drawPoint(startX, startY, tGraph1FillPaint);// reference point

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
		tYPos += 20;
		tString = "Stars | 4*stroke=1 Y=0,Y=0.5, Y=0.3,Y=0.7 | 2*stroke=2 | 2* corrected";
		tCanvas.drawText(tString, 0, tYPos, tTextPaintInfo);

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
		tYPos += 20;
		tX = 0;
		// draw baselines
		tCanvas.drawLine(tX, tYPos, tX + 40, tYPos, tGraph1Paint);
		tGraph1Paint.setStrokeWidth(2);
		tCanvas.drawLine(tX + 41, tYPos, tX + 50, tYPos, tGraph1Paint);
		tGraph1Paint.setStrokeWidth(3);
		tCanvas.drawLine(tX + 51, tYPos, tX + 60, tYPos, tGraph1Paint);
		tGraph1Paint.setStrokeWidth(4);
		tCanvas.drawLine(tX + 61, tYPos, tX + 70, tYPos, tGraph1Paint);
		tGraph1Paint.setStrokeWidth(5);
		tCanvas.drawLine(tX + 71, tYPos, tX + 80, tYPos, tGraph1Paint);

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

		return tYPos + 12;
	}

	/*
	 * Font size and background test
	 */
	@SuppressLint("DefaultLocale")
	public void drawFontTest() {
		float tTextSize = 11;
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

		Path tPath = new Path();
		RectF tRect = new RectF();

		tCanvas.drawText("Display Width= " + mActualCanvasWidth + " of " + mActualViewWidth + " Height=" + mActualCanvasHeight
				+ " of " + mActualViewHeight, 100, tTextSize, tTextPaint);
		tCanvas.drawText("Font INFO:    SIZE|ASCENT|DESCENT|WIDTH", 100, 2 * tTextSize, tTextPaint);

		float startX;
		float tYPos = 25;

		String tExampleString = "gti";
		startX = 0;
		String tString;
		/*
		 * Output TextSize-TextWidth for Size 5-75
		 */
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
			tYPos += 11;
			tCanvas.drawText(tFontsizes.toString(), 0, tYPos, tTextPaintInfo);
			Log.i(LOG_TAG, tFontsizesMore.toString());
		}

		/*
		 * draw text with background determined by ascent and decent.
		 */
		float tTextSizesArray[] = { 11.0f, 12.0f, 13.0f, 16.0f, 22.0f, 24.0f, 28.0f, 32.0f, 33.0f, 44.0f, 48.0f };
		float tEndX;
		tYPos += 60;
		startX = 0;
		tCanvas.drawLine(startX, tYPos, startX + 10, tYPos, tGraph1Paint); // Base line
		startX += 10;
		for (int i = 0; i < tTextSizesArray.length; i++) {
			tTextSize = tTextSizesArray[i];
			tTextPaint.setTextSize(tTextSize);
			tEndX = startX + (3 * ((tTextSize * 6) + 4) / 10);
			tCanvas.drawRect(startX, tYPos - (float) (tTextSize * 0.928), tEndX, tYPos + (float) (tTextSize * 0.235),
					tTextBackgroundPaint);
			tCanvas.drawText(tExampleString, startX, tYPos, tTextPaint);
			startX = tEndX + 3;
		}
		tCanvas.drawLine(startX + 3, tYPos, startX + 10, tYPos, tGraph1Paint); // Base line

		/*
		 * draw text with background determined by real ascent and decent - derived from getTextPath().
		 */

		tYPos += 60;
		startX = 0;
		tCanvas.drawLine(startX, tYPos, startX + 10, tYPos, tGraph1Paint); // Base line
		startX += 10;
		for (int i = 0; i < tTextSizesArray.length; i++) {
			tTextSize = tTextSizesArray[i];
			tTextPaint.setTextSize(tTextSize);
			tEndX = startX + (3 * ((tTextSize * 6) + 4) / 10);
			tCanvas.drawRect(startX, tYPos - (float) (tTextSize * 0.76), tEndX, tYPos + (float) (tTextSize * 0.24),
					tTextBackgroundPaint);
			tCanvas.drawText(tExampleString, startX, tYPos, tTextPaint);
			startX = tEndX + 3;
		}
		tCanvas.drawLine(startX + 3, tYPos, startX + 10, tYPos, tGraph1Paint); // Base line

	}

	private void drawStarForTests(Canvas tCanvas, Paint aPaint, Paint aFillPaint, float tX, float tY, int tOffsetCenter,
			int tLength, int tOffsetDiagonal, int tLengthDiagonal) {

		tLength--;
		tLengthDiagonal += tOffsetDiagonal - 1;

		float X = tX + tOffsetCenter;
		tCanvas.drawLine(X, tY, X + tLength, tY, aPaint);
		tCanvas.drawPoint(X, tY, aFillPaint);
		tCanvas.drawLine(X, tY - tOffsetDiagonal, X + tLength, tY - tLengthDiagonal, aPaint);// < 45 degree
		tCanvas.drawPoint(X, tY - tOffsetDiagonal, aFillPaint);
		tCanvas.drawLine(X, tY + tOffsetDiagonal, X + tLength, tY + tLengthDiagonal, aPaint); // < 45 degree
		tCanvas.drawPoint(X, tY + tOffsetDiagonal, aFillPaint);
		tCanvas.drawLine(X, tY + tOffsetCenter, X + tLength, tY + tOffsetCenter + tLength, aPaint); // 45 degree +
		tCanvas.drawPoint(X, tY + tOffsetCenter, aFillPaint);

		float Y = tY + tOffsetCenter;
		tCanvas.drawLine(tX, Y, tX, Y + tLength, aPaint);
		tCanvas.drawPoint(tX, Y, aFillPaint);
		tCanvas.drawLine(tX - tOffsetDiagonal, Y, tX - tLengthDiagonal, Y + tLength, aPaint);
		tCanvas.drawPoint(tX - tOffsetDiagonal, Y, aFillPaint);
		tCanvas.drawLine(tX + tOffsetDiagonal, Y, tX + tLengthDiagonal, Y + tLength, aPaint);
		tCanvas.drawPoint(tX + tOffsetDiagonal, Y, aFillPaint);
		tCanvas.drawLine(tX - tOffsetCenter, Y, tX - tOffsetCenter - tLength, Y + tLength, aPaint); // 45 degree +
		tCanvas.drawPoint(tX - tOffsetCenter, Y, aFillPaint);

		X = tX - tOffsetCenter;
		tCanvas.drawLine(X, tY, X - tLength, tY, aPaint);
		tCanvas.drawPoint(X, tY, aFillPaint);
		tCanvas.drawLine(X, tY - tOffsetDiagonal, X - tLength, tY - tLengthDiagonal, aPaint);
		tCanvas.drawPoint(X, tY - tOffsetDiagonal, aFillPaint);
		tCanvas.drawLine(X, tY + tOffsetDiagonal, X - tLength, tY + tLengthDiagonal, aPaint);
		tCanvas.drawPoint(X, tY + tOffsetDiagonal, aFillPaint);
		tCanvas.drawLine(X, tY - tOffsetCenter, X - tLength, tY - tOffsetCenter - tLength, aPaint); // 45 degree +
		tCanvas.drawPoint(X, tY - tOffsetCenter, aFillPaint);

		Y = tY - tOffsetCenter;
		tCanvas.drawLine(tX, Y, tX, Y - tLength, aPaint);
		tCanvas.drawPoint(tX, Y, aFillPaint);
		tCanvas.drawLine(tX - tOffsetDiagonal, Y, tX - tLengthDiagonal, Y - tLength, aPaint);
		tCanvas.drawPoint(tX - tOffsetDiagonal, Y, aFillPaint);
		tCanvas.drawLine(tX + tOffsetDiagonal, Y, tX + tLengthDiagonal, Y - tLength, aPaint);
		tCanvas.drawPoint(tX + tOffsetDiagonal, Y, aFillPaint);
		tCanvas.drawLine(tX + tOffsetCenter, Y, tX + tOffsetCenter + tLength, Y - tLength, aPaint); // 45 degree +
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
