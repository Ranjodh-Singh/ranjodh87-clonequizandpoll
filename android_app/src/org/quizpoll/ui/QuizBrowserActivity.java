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
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.quizpoll.R;
import org.quizpoll.data.model.DocsEntry;
import org.quizpoll.net.AppEngineHelper;
import org.quizpoll.net.DocsHelper;
import org.quizpoll.net.HttpListener;
import org.quizpoll.util.ActivityHelper;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Browser for available quizzes. Uses Google Document List API - collections
 * and spreadsheets.
 */
public class QuizBrowserActivity extends GoogleAuthActivity implements OnItemClickListener {
  @SuppressWarnings("unused")
  private static final String TAG = "QuizBrowserActivity";

  public static final String EXTRA_COLLECTION_ID = "org.quizpoll.CollectionId";
  public static final String EXTRA_TITLE = "org.quizpoll.Title";
  static final String INFO_URL = "http://quiz-n-poll.appspot.com";
  static final int DIALOG_CREATE_GAME = 0;

  // ID of shared collection in Docs containing all quizzes
  public static final String QUIZZES_SHARED_COLLECTION =
      "0B6rxb_ov7Sd5OWVkNmEyNTAtNWM1Ni00Yzg2LWE0NmEtYjc3YzIyOTcxNjU4";

  private ArrayList<DocsEntry> docsEntries;
  private boolean privateQuizGames = false;

  @SuppressWarnings("unchecked")
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_quiz_browser);
    String title = getIntent().getStringExtra(EXTRA_TITLE);
    ActivityHelper helper = new ActivityHelper(this);
    helper.setupActionBar(title);
    String collectionId = getIntent().getStringExtra(EXTRA_COLLECTION_ID);
    if (collectionId == null) {
      privateQuizGames = true;
    }
    if (privateQuizGames) {
      fetchPrivateDocList();
    } else {
      fetchCollectionDocList(collectionId);
      helper.addActionButtonCompat(R.drawable.ic_title_private, new View.OnClickListener() {

        @Override
        public void onClick(View v) {
          showPrivateQuizzes();
        }
      }, false);
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    if (!privateQuizGames) {
      MenuInflater inflater = getMenuInflater();
      inflater.inflate(R.menu.quiz_browser_menu, menu);
    }
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.private_quiz:
        showPrivateQuizzes();
        return true;
      case R.id.create_game:
        showDialog(DIALOG_CREATE_GAME);
        return true;
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  @Override
  public void onItemClick(final AdapterView<?> adapter, final View view, final int position,
      final long id) {
    final DocsEntry item = docsEntries.get(position);
    switch (item.getType()) {
      case DocsEntry.COLLECTION:
        Intent intent = new Intent(QuizBrowserActivity.this, QuizBrowserActivity.class);
        intent.putExtra(QuizBrowserActivity.EXTRA_COLLECTION_ID, item.getId());
        intent.putExtra(QuizBrowserActivity.EXTRA_TITLE, item.getTitle());
        startActivity(intent);
        break;
      case DocsEntry.QUIZ:
        Intent intent2 = new Intent(this, QuizInfoActivity.class);
        intent2.putExtra(QuizInfoActivity.EXTRA_DOC_ID, item.getId());
        startActivity(intent2);
        break;
    }
  }

  @Override
  protected Dialog onCreateDialog(int id) {
    if (id == DIALOG_CREATE_GAME) {
      AlertDialog.Builder alert = new AlertDialog.Builder(this);
      alert.setTitle(R.string.create_own_game);
      alert.setMessage(R.string.create_description);
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
    return null;
  }

  /**
   * Fetch private quizzes
   */
  private void fetchPrivateDocList() {
    new DocsHelper(DocsHelper.MY_DOCUMENTS, null, true, QuizBrowserActivity.this,
        new HttpListener() {

          @SuppressWarnings("unchecked")
          @Override
          public void onSuccess(Object responseData) {
            docsEntries = (ArrayList<DocsEntry>) responseData;
            ListView list = (ListView) findViewById(R.id.quiz_list);
            list.setAdapter(new DocsListAdapter(QuizBrowserActivity.this, R.layout.list_item_icon,
                docsEntries));
            list.setOnItemClickListener(QuizBrowserActivity.this);
          }
        });
  }

  /**
   * Fetch documents from some collection
   */
  private void fetchCollectionDocList(final String collectionId) {
    new AppEngineHelper(AppEngineHelper.COLLECTION_DOCUMENTS, collectionId, true,
        QuizBrowserActivity.this, new HttpListener() {

          @SuppressWarnings("unchecked")
          @Override
          public void onSuccess(Object responseData) {
            docsEntries = (ArrayList<DocsEntry>) responseData;
            Collections.sort(docsEntries);
            ListView list = (ListView) findViewById(R.id.quiz_list);
            list.setAdapter(new DocsListAdapter(QuizBrowserActivity.this, R.layout.list_item_icon,
                docsEntries));
            list.setOnItemClickListener(QuizBrowserActivity.this);
          }
        });
  }

  /**
   * Shows user's spreadsheets
   */
  private void showPrivateQuizzes() {
    Intent intent = new Intent(this, QuizBrowserActivity.class);
    intent.putExtra(QuizBrowserActivity.EXTRA_TITLE, getString(R.string.my_own_quiz_games));
    startActivity(intent);
  }

  /**
   * List adapter for document list, showing name of the spreadsheet/folder and
   * icon
   */
  private class DocsListAdapter extends ArrayAdapter<DocsEntry> {

    private ArrayList<DocsEntry> items;

    public DocsListAdapter(Context context, int textViewResourceId, ArrayList<DocsEntry> items) {
      super(context, textViewResourceId, items);
      this.items = items;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      View view = convertView;
      if (view == null) {
        LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        view = vi.inflate(R.layout.list_item_icon, parent, false);
      }
      DocsEntry item = items.get(position);
      TextView text = (TextView) view.findViewById(R.id.title);
      text.setText(item.getTitle());
      ImageView icon = (ImageView) view.findViewById(R.id.icon);
      switch (item.getType()) {
        case DocsEntry.COLLECTION:
          icon.setImageResource(R.drawable.folder);
          break;
        case DocsEntry.QUIZ:
          icon.setImageResource(R.drawable.quiz);
          break;
      }

      return view;
    }
  }

}
