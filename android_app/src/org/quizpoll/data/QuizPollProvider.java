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

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.provider.BaseColumns;

/**
 * Content Provider that exposes data in the database
 */
public class QuizPollProvider extends ContentProvider {

  private static final String DATABASE_NAME = "quizpoll.db";
  private static final int DATABASE_VERSION = 1;

  private static final String POLLS_TABLE_NAME = "polls";
  private static final String POLL_ITEM = "poll";

  public static final String AUTHORITY = "org.quizpoll";

  /**
   * Helper class that defines the columns that describe a polls list.
   */
  public static final class PollList implements BaseColumns {

    /**
     * Content URI for a poll list
     */
    public static final Uri CONTENT_URI = Uri.parse("content://"
        + QuizPollProvider.AUTHORITY + "/" + POLLS_TABLE_NAME);

    /**
     * Content URI for a single item
     */
    public static final Uri ITEM_URI = Uri.parse("content://"
        + QuizPollProvider.AUTHORITY + "/" + POLL_ITEM);

    /**
     * MIME type for a poll list
     */
    public static final String CONTENT_TYPE = "vnd.android.cursor.dir/org.quizpoll.poll_list";

    /**
     * MIME type for a poll item
     */
    public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/org.quizpoll.poll";

    /**
     * Name of the poll
     */
    public static final String TITLE = "title";

    /**
     * Spreadsheet ID
     */
    public static final String DOCUMENT_ID = "document_id";

    /**
     * Timestamp of last access, used for sorting
     */
    public static final String ACCESSSED = "accessed";

  }

  // Codes for types of content that can be addressed

  // All polls
  private static final int LOOKUP_POLLS = 0;
  // Poll by document id
  private static final int LOOKUP_POLL_BY_DOCUMENT_ID = 1;

  // UriMatcher to recognize the URIs passed to this provider
  private static final UriMatcher sUriMatcher;

  static {
    sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    // Catch content://.../polls
    sUriMatcher.addURI(AUTHORITY, POLLS_TABLE_NAME, LOOKUP_POLLS);

    // Catch content://.../poll/*
    sUriMatcher.addURI(AUTHORITY, POLL_ITEM + "/*", LOOKUP_POLL_BY_DOCUMENT_ID);
  }

  /**
   * Factory class that will (re)create the database if necessary.
   */
  private static class DatabaseHelper extends SQLiteOpenHelper {

    DatabaseHelper(Context content) {
      // Open the DB
      super(content, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

      // Create the poll lists table
      final String sql = "CREATE TABLE " + POLLS_TABLE_NAME + " (" +
                         PollList._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                         PollList.DOCUMENT_ID + " TEXT, " +
                         PollList.TITLE + " TEXT, " +
                         PollList.ACCESSSED + " BIGINT);";
      db.execSQL(sql);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

      // Drop tables
      db.execSQL("DROP TABLE IF EXISTS " + POLLS_TABLE_NAME);

      // Recreate database
      onCreate(db);
    }
  }

  // Instance of the DatabaseHelper providing access to the DB
  private DatabaseHelper mDbHelper;

  @Override
  public boolean onCreate() {
    mDbHelper = new DatabaseHelper(getContext());
    return true;
  }

  @Override
  public String getType(Uri uri) {
    // Match URI and return correct MIME type
    switch (sUriMatcher.match(uri)) {
      case LOOKUP_POLLS:
        return PollList.CONTENT_TYPE;
      case LOOKUP_POLL_BY_DOCUMENT_ID:
        return PollList.CONTENT_ITEM_TYPE;
    }
    throw new IllegalArgumentException("Invalid URI:" + uri);
  }

  @Override
  public Uri insert(Uri uri, ContentValues initialValues) {
    SQLiteDatabase db = mDbHelper.getWritableDatabase();
    try {
      switch (sUriMatcher.match(uri)) {
        case LOOKUP_POLLS:
          initialValues.put(PollList.ACCESSSED, System.currentTimeMillis());
          long taskId = db.insert(POLLS_TABLE_NAME, null, initialValues);
          return ContentUris.withAppendedId(PollList.CONTENT_URI, taskId);
      }
      throw new IllegalArgumentException("Invalid URI:" + uri);
    } finally {
      db.close();
    }
  }

  @Override
  public int delete(Uri uri, String where, String[] whereArgs) {
    throw new UnsupportedOperationException("Delete not supported");
  }

  @Override
  public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
      String sortOrder) {
    SQLiteDatabase db = mDbHelper.getReadableDatabase();
    switch (sUriMatcher.match(uri)) {
      case LOOKUP_POLLS:
        return db.query(POLLS_TABLE_NAME, projection, selection, selectionArgs, null, null,
            PollList.ACCESSSED + " DESC");
    }
    db.close(); // In matched case, cursor.close() is closing connection
                // automatically
    throw new IllegalArgumentException("Invalid URI:" + uri);
  }

  @Override
  public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
    SQLiteDatabase db = mDbHelper.getWritableDatabase();
    try {
      if (values == null) {
        values = new ContentValues();
      }
      switch (sUriMatcher.match(uri)) {
        case LOOKUP_POLL_BY_DOCUMENT_ID:
          values.put(PollList.ACCESSSED, System.currentTimeMillis());
          return db.update(POLLS_TABLE_NAME, values,
              PollList.DOCUMENT_ID + " = '" + uri.getLastPathSegment() + "'", selectionArgs);
      }
      throw new IllegalArgumentException("Invalid URI:" + uri + sUriMatcher.match(uri));
    } finally {
      db.close();
    }
  }
}
