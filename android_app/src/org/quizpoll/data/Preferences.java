/*
 Copyright 2011 Google Inc. All Rights Reserved.

 Licensed under the Apache License, Version 2.0 (the "License');
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS-IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
*/

package org.quizpoll.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * Helper class for accessing key-value settings.
 */
public class Preferences {

  /**
   * Types of preferences
   */
  public static enum PrefType {
    AUTH_TOKEN_AE, AUTH_TOKEN_DOCS, USER_EMAIL, COOKIE_APPENGINE
  }

  public static void add(PrefType name, int value, Context context) {
    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
    SharedPreferences.Editor editor = settings.edit();
    editor.putInt(name.toString(), value);
    editor.commit();
  }

  public static void add(PrefType name, float value, Context context) {
    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
    SharedPreferences.Editor editor = settings.edit();
    editor.putFloat(name.toString(), value);
    editor.commit();
  }

  public static void add(PrefType name, String value, Context context) {
    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
    SharedPreferences.Editor editor = settings.edit();
    editor.putString(name.toString(), value);
    editor.commit();
  }

  public static void add(PrefType name, boolean value, Context context) {
    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
    SharedPreferences.Editor editor = settings.edit();
    editor.putBoolean(name.toString(), value);
    editor.commit();
  }

  public static int getInt(PrefType name, Context context) {
    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
    return settings.getInt(name.toString(), 0);
  }

  public static double getDouble(PrefType name, Context context) {
    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
    return settings.getFloat(name.toString(), 0f);
  }

  public static String getString(PrefType name, Context context) {
    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
    return settings.getString(name.toString(), null);
  }

  public static boolean getBoolean(PrefType name, Context context) {
    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
    return settings.getBoolean(name.toString(), false);
  }

  public static boolean hasString(PrefType name, Context context) {
    return (getString(name, context) != null);
  }
}
