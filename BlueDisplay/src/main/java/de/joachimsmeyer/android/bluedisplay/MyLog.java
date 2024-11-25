/*
 *     SUMMARY
 *     Blue Display is an Open Source Android remote Display for Arduino etc.
 *     It receives basic draw requests from Arduino etc. over Bluetooth and renders it.
 *     It also implements basic GUI elements as buttons and sliders.
 *     It sends touch or GUI callback events over Bluetooth back to Arduino.
 *
 *  Copyright (C) 2015-2020  Armin Joachimsmeyer
 *  armin.joachimsmeyer@gmail.com
 *
 *     This file is part of BlueDisplay.
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
 *
 * This class implements a simple wrapper for the android Log class.
 * It stores the last 300 entries in a string array for local output.
 */

package de.joachimsmeyer.android.bluedisplay;

import android.util.Log;

public class MyLog {

    static final int SIZE_OF_LOG_HISTORY = 1500;
    static String[] Logmessages = new String[SIZE_OF_LOG_HISTORY];
    static int mNextIndex = 0;
    static int mLength = 0; // used to determine if wrap around has happened
    // to prevent "The content of the adapter has changed but ListView did not receive a notification"
    static boolean mStopLoggingSinceLogIsDisplayed = false;

    // Debugging
    static final boolean isDevelopmentTesting = false; // set manually to true
    // static final boolean isDevelopmentTesting = true; // set manually to true
    public static final String LOGLEVEL_KEY = "loglevel";
    static int mLoglevel = Log.INFO; // 6=ERROR 5=WARN, 4=INFO, 3=DEBUG, 2=VERBOSE

    public static boolean isDEVELOPMENT_TESTING() {
        return (isDevelopmentTesting);
    }

    public static void setLoglevel(int aLoglevel) {
        MyLog.mLoglevel = aLoglevel;
    }

    public static boolean isINFO() {
        return (mLoglevel <= Log.INFO);
    }

    public static boolean isDEBUG() {
        return (mLoglevel <= Log.DEBUG);
    }

    public static boolean isVERBOSE() {
        return (mLoglevel <= Log.VERBOSE);
    }

    public static void clear() {
        mNextIndex = 0;
        mLength = 0;
    }

    public static String get(int aPosition) {
        if (mLength < SIZE_OF_LOG_HISTORY) {
            if (aPosition > mLength) {
                return null;
            }
            return Logmessages[aPosition];
        } else {
            int tIndex = (mNextIndex + aPosition) % SIZE_OF_LOG_HISTORY;
            return Logmessages[tIndex];
        }
    }

    public static int getCount() {
        return mLength;
    }

    private static void InsertLog(String aLevel, String tag, String msg) {
        if (!mStopLoggingSinceLogIsDisplayed) {
            Logmessages[mNextIndex++] = aLevel + " " + tag + " " + msg;
            if (mNextIndex >= SIZE_OF_LOG_HISTORY) {
                mNextIndex = 0;
            }
            if (mLength < SIZE_OF_LOG_HISTORY) {
                mLength++;
            }

        }
    }

    public static void v(String tag, String msg) {
        InsertLog("V", tag, msg);
        Log.v(tag, msg);
    }

    public static void d(String tag, String msg) {
        InsertLog("D", tag, msg);
        Log.d(tag, msg);
    }

    public static void i(String tag, String msg) {
        InsertLog("I", tag, msg);
        Log.i(tag, msg);
    }

    public static void w(String tag, String msg) {
        InsertLog("W", tag, msg);
        Log.w(tag, msg);
    }

    public static void e(String tag, String msg) {
        InsertLog("E", tag, msg);
        Log.e(tag, msg);
    }

}