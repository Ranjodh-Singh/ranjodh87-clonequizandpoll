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

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.quizpoll.R;
import org.quizpoll.data.model.Quiz;
import org.quizpoll.net.AppEngineHelper;
import org.quizpoll.net.HttpHelper;
import org.quizpoll.net.HttpListener;
import org.quizpoll.net.ImageDownloadHelper;
import org.quizpoll.util.ActivityHelper;

/**
 * Info screen for selected quiz. Starts the game.
 */
public class QuizInfoActivity extends GoogleAuthActivity {
  @SuppressWarnings("unused")
  private static final String TAG = "QuizInfoActivity";

  public static final String EXTRA_DOC_ID = "org.quizpoll.DocId";

  private Quiz quiz;
  private ActivityHelper helper;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_quiz_info);
    String docId;
    if (Intent.ACTION_VIEW.equals(getIntent().getAction())) {
      // This activity was called from URL directly
      docId = getIntent().getData().getLastPathSegment();
    } else {
      // Called from the list of spreadsheets
      docId = getIntent().getStringExtra(EXTRA_DOC_ID);
    }
    helper = new ActivityHelper(QuizInfoActivity.this);
    helper.setupActionBar(getString(R.string.quiz_game_detail));
    fetchQuiz(docId);
    // Button in action bar for sharing quizzes
    helper.addActionButtonCompat(R.drawable.ic_title_share, new View.OnClickListener() {

      @Override
      public void onClick(View v) {
        shareQuiz();
      }
    }, false);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.quiz_info_menu, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.share:
        shareQuiz();
        return true;
        // More menu items to come
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  /**
   * Starts the game
   */
  public void startClicked(View view) {
    Intent intent = new Intent(QuizInfoActivity.this,
        QuizPlayerActivity.class);
    intent.putExtra(QuizPlayerActivity.EXTRA_QUIZ, quiz);
    startActivity(intent);
    finish();
  }

  /**
   * Shows the leaderboard directly
   */
  public void leaderboardClicked(View view) {
    Intent intent = new Intent(QuizInfoActivity.this,
        LeaderboardActivity.class);
    intent.putExtra(LeaderboardActivity.EXTRA_DOCUMENT_ID, quiz.getDocumentId());
    intent.putExtra(LeaderboardActivity.EXTRA_WORKSHEET, quiz.getLeaderboardSheet());
    startActivity(intent);
  }

  /**
   * Fetch worksheets of selected spreadsheet
   */
  private void fetchQuiz(final String docId) {
    new AppEngineHelper(AppEngineHelper.QUIZ, docId, true, this,
        new HttpListener() {

          @Override
          public void onSuccess(Object responseData) {
            quiz = (Quiz) responseData;
            helper.changeTitle(quiz.getTitle());
            ((TextView) findViewById(R.id.quiz_description)).setText(quiz.getDescription());
            ((LinearLayout) findViewById(R.id.quiz_info)).setVisibility(View.VISIBLE);
            // Download image
            new ImageDownloadHelper(HttpHelper.SINGLE_MESSAGE_TYPE, quiz.getImage(), false,
                QuizInfoActivity.this,
                new HttpListener() {

                  @Override
                  public void onSuccess(Object responseData) {
                    Bitmap bitmap = (Bitmap) responseData;
                    ((ImageView) findViewById(R.id.quiz_image)).setImageBitmap(bitmap);
                  }
                });
          }
        });
  }

  /**
   * Shares quiz using ShareActivity
   */
  private void shareQuiz() {
    Intent intent = new Intent(this, ShareActivity.class);
    Uri url = Uri.parse(AppEngineHelper.BROKER_URL + "/quiz/").buildUpon()
        .appendPath(quiz.getDocumentId()).build();
    intent.putExtra(ShareActivity.EXTRA_URL, url.toString());
    intent.putExtra(ShareActivity.EXTRA_QUIZ_NAME, quiz.getTitle());
    startActivity(intent);
  }

}
