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

package org.quizpoll.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Toast;

import org.quizpoll.R;
import org.quizpoll.data.QuizPollProvider.PollList;
import org.quizpoll.net.AppEngineHelper;
import org.quizpoll.util.ActivityHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * List of recently used polls. Allows to scan QR code right from the app.
 */
public class RecentPollsActivity extends GoogleAuthActivity {
  @SuppressWarnings("unused")
  private static final String TAG = "RecentPollsActivity";
  static final int DIALOG_CREATE_POLL = 0;
  static final String INFO_URL = "http://quiz-n-poll.appspot.com";
  static final int SCAN_QR = 1;
  // Google Goggles use same API as Zxing's QR Code scanner, it works for both
  static final String GOGGLES_INTENT = "com.google.zxing.client.android.SCAN";
  static final String GOGGLES_RESULT = "SCAN_RESULT";

  @SuppressWarnings("unchecked")
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_recent_polls);
    ActivityHelper helper = new ActivityHelper(this);
    helper.setupActionBar(getString(R.string.recent_polls));
    helper.addActionButtonCompat(R.drawable.ic_title_scan, new View.OnClickListener() {

      @Override
      public void onClick(View v) {
        scanQrCode();
      }
    }, false);
  }

  @Override
  protected void onStart() {
    super.onStart();
    new RecentPollsTask().execute();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.recent_polls_menu, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.scan_poll_qr:
        scanQrCode();
        return true;
      case R.id.create_game:
        showDialog(DIALOG_CREATE_POLL);
        return true;
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  @Override
  protected Dialog onCreateDialog(int id) {
    if (id != DIALOG_CREATE_POLL) {
      throw new IllegalArgumentException("Invalid dialog");
    }
    AlertDialog.Builder alert = new AlertDialog.Builder(this);
    alert.setTitle(R.string.create_own_poll);
    alert.setMessage(R.string.create_poll_description);
    alert.setPositiveButton(R.string.read_more, new OnClickListener() {

      @Override
      public void onClick(DialogInterface dialog, int which) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(INFO_URL));
        startActivity(intent);
      }
    });
    alert.setNegativeButton(R.string.cancel, new OnClickListener() {

      @Override
      public void onClick(DialogInterface dialog, int which) {
        dialog.dismiss();
      }
    });
    return alert.create();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == SCAN_QR && resultCode == RESULT_OK) {
      if (resultCode == RESULT_OK) {
        String url = data.getStringExtra(GOGGLES_RESULT);
        if (url != null && url.startsWith(AppEngineHelper.BROKER_URL + "/poll")) {
          Intent intent = new Intent(this, PollActivity.class);
          intent.setData(Uri.parse(url));
          startActivity(intent);
        } else {
          Toast.makeText(this, R.string.unsupported_qr_code, Toast.LENGTH_SHORT).show();
        }
      } else {
        Toast.makeText(this, R.string.qr_scan_failed, Toast.LENGTH_SHORT).show();
      }
    }
  }

  private void scanQrCode() {
    try {
      Intent intent = new Intent(GOGGLES_INTENT);
      startActivityForResult(intent, SCAN_QR);
    } catch (ActivityNotFoundException e) {
      Toast.makeText(this, R.string.goggles_needed, Toast.LENGTH_LONG).show();
      // Launches Market listing of Google Goggles
      Intent intent =
          new Intent(Intent.ACTION_VIEW,
              Uri.parse("market://details?id=com.google.android.apps.unveil"));
      try {
        startActivity(intent);
      } catch (ActivityNotFoundException e2) {
        // Ignore
      }
    }
  }

  private class RecentPollsTask extends AsyncTask<Void, Void, List<RecentPollsEntry>> {

    @Override
    protected List<RecentPollsEntry> doInBackground(Void... arg0) {
      Cursor cursor =
          getContentResolver().query(PollList.CONTENT_URI,
              new String[] {PollList._ID, PollList.TITLE, PollList.DOCUMENT_ID}, null, null, null);
      try {
        List<RecentPollsEntry> entries = new ArrayList<RecentPollsEntry>();
        while (cursor.moveToNext()) {
          entries.add(new RecentPollsEntry(cursor.getString(1), cursor.getString(2)));
        }
        return entries;
      } finally {
        cursor.close();
      }
    }

    @Override
    protected void onPostExecute(final List<RecentPollsEntry> entries) {
      if (entries.size() == 0) {
        findViewById(R.id.no_recent_polls).setVisibility(View.VISIBLE);
        findViewById(R.id.poll_list).setVisibility(View.GONE);
      } else {
        ListView list = ((ListView) findViewById(R.id.poll_list));
        list.setVisibility(View.VISIBLE);
        findViewById(R.id.no_recent_polls).setVisibility(View.GONE);
        ListAdapter adapter =
            new ArrayAdapter<RecentPollsEntry>(RecentPollsActivity.this,
                android.R.layout.simple_list_item_1, entries);
        list.setAdapter(adapter);
        list.setOnItemClickListener(new OnItemClickListener() {

          @Override
          public void onItemClick(AdapterView<?> adapter, View view, int position, long id) {
            Intent intent = new Intent(RecentPollsActivity.this, PollActivity.class);
            String url = AppEngineHelper.BROKER_URL + "/poll/" + entries.get(position).documentId;
            intent.setData(Uri.parse(url));
            startActivity(intent);
          }
        });
      }
    }

  }

  class RecentPollsEntry {
    private final String title;
    private final String documentId;

    public RecentPollsEntry(String title, String documentId) {
      this.title = title;
      this.documentId = documentId;
    }

    public String getTitle() {
      return title;
    }

    public String getDocumentId() {
      return documentId;
    }

    @Override
    public String toString() {
      return title;
    }
  }

}
