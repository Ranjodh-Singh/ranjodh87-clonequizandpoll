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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.util.SparseBooleanArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.quizpoll.R;
import org.quizpoll.data.QuizPollProvider.PollList;
import org.quizpoll.data.model.Answer;
import org.quizpoll.data.model.Poll;
import org.quizpoll.data.model.Question;
import org.quizpoll.data.model.Question.QuestionType;
import org.quizpoll.net.AppEngineHelper;
import org.quizpoll.net.HttpListener;
import org.quizpoll.util.ActivityHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Screen for polling. It's launched only from link, so it works like private
 * quiz. Polling is basically a type of quiz which is driven by the instructor
 * (using interface written in Apps Script).
 */
public class PollActivity extends GoogleAuthActivity {
  @SuppressWarnings("unused")
  private static final String TAG = "PollActivity";
  // How often to check if there is new question
  private static final int CHECKING_INTERVAL = 3000;
  private static final int NOTIFICATION_ID = 1;
  private ActivityHelper activityHelper;
  private static Poll poll;
  private Timer timer;
  private Handler handler = new Handler();
  // UI elements for faster access
  LinearLayout questionLayout;
  LinearLayout waitingLayout;
  ProgressBar waitingProgress;
  TextView statusTextView;
  TextView questionTextView;
  TextView anonymousTextView;
  TextView chooseAllCorrect;
  ListView answerList;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_polling);
    activityHelper = new ActivityHelper(this);
    activityHelper.setupActionBar(getString(R.string.loading_polling), false);
    setVolumeControlStream(AudioManager.STREAM_MUSIC);
    if (getIntent().getData() != null) {
      // Called from URL
      poll = null;
      String docId = getIntent().getData().getLastPathSegment();
      fetchPoll(docId);
    } else {
      // Called from notification
      if (savedInstanceState != null) {
        // if activity was killed in the meantime
        poll = (Poll) savedInstanceState.getSerializable("poll");
      }
      if (poll != null) {
        activityHelper.changeTitle(poll.getTitle());
      }
    }
    // Button in action bar for sharing quizzes
    activityHelper.addActionButtonCompat(R.drawable.ic_title_share, new View.OnClickListener() {

      @Override
      public void onClick(View v) {
        sharePoll();
      }
    }, false);
    // Load UI elements for better performance
    questionLayout = (LinearLayout) findViewById(R.id.question);
    waitingLayout = (LinearLayout) findViewById(R.id.waiting);
    waitingProgress = (ProgressBar) findViewById(R.id.polling_progress);
    statusTextView = (TextView) findViewById(R.id.polling_status);
    questionTextView = (TextView) findViewById(R.id.question_text);
    anonymousTextView = (TextView) findViewById(R.id.anonymous);
    answerList = (ListView) findViewById(R.id.answers);
    chooseAllCorrect = (TextView) findViewById(R.id.choose_all_correct);
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    outState.putSerializable("poll", poll);
    super.onSaveInstanceState(outState);
  }

  @Override
  protected void onStart() {
    super.onStart();
    if (poll != null) {
      poll.setCurrentQuestion(Poll.UNKNOWN);
      startTimer();
    }
  }

  @Override
  protected void onStop() {
    if (timer != null) {
      timer.cancel();
    }
    super.onStop();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.polling_menu, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.exit:
        hideNotification();
        finish();
        return true;
      case R.id.share:
        sharePoll();
        return true;
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  /**
   * Submit answer button was clicked
   */
  public void submitClicked(View view) {
    // Was the answer correct?
    boolean correct = isAnswerCorrect();
    // Show special effects (sound, vibration, color)
    specialEffects(correct);
    // Submit response
    new AppEngineHelper(AppEngineHelper.POLL_SUBMIT, poll, true, this, new HttpListener() {

      @Override
      public void onSuccess(Object responseData) {
        waitForInstructor();
      }
    });
  }

  /**
   * Exit button clicked
   */
  public void exitClicked(View view) {
    hideNotification();
    finish();
  }

  /**
   * Shows notification to access running polling later
   */
  private void showNotification() {
    NotificationManager notificationManager =
        (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    Notification notification =
        new Notification(R.drawable.ic_stat_polling, null, System
            .currentTimeMillis());
    notification.flags |= Notification.FLAG_ONGOING_EVENT;
    Intent notificationIntent = new Intent(this, PollActivity.class);
    PendingIntent contentIntent =
        PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    notification.setLatestEventInfo(this, getString(R.string.running_polling), poll.getTitle(),
        contentIntent);
    notificationManager.notify(NOTIFICATION_ID, notification);
  }

  /**
   * Disables notification
   */
  private void hideNotification() {
    NotificationManager notificationManager =
        (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    notificationManager.cancel(NOTIFICATION_ID);
  }

  /**
   * Determine if answer is correct and mark this for later statistics
   */
  private boolean isAnswerCorrect() {
    SparseBooleanArray checkedPositions = answerList.getCheckedItemPositions();
    boolean correct = true;
    Question question = poll.getQuestions().get(poll.getCurrentQuestion());
    for (int i = 0; i < question.getAnswers().size(); i++) {
      Answer answer = question.getAnswers().get(i);
      boolean answered = checkedPositions.get(i);
      if (answer.isCorrect() != answered) {
        correct = false;
      }
      answer.setAnswered(answered);
    }
    question.setSuccess(correct);
    return correct;
  }

  /**
   * During polling, app periodically checks the spreadsheet for current
   * question and it shows it.
   */
  private void startTimer() {
    timer = new Timer();
    final Runnable tick = new Runnable() {

      @Override
      public void run() {
        List<String> arguments = new ArrayList<String>();
        arguments.add(poll.getDocumentId());
        arguments.add(poll.getInternalDataSheet());
        new AppEngineHelper(AppEngineHelper.POLL_STATUS, arguments, false, PollActivity.this,
            new HttpListener() {

              @Override
              public void onSuccess(Object responseData) {
                int questionNumber = (Integer) responseData;
                if (questionNumber != poll.getCurrentQuestion()) {
                  poll.setCurrentQuestion(questionNumber);
                  if (questionNumber == Poll.WAITING_FOR_INSTRUCTOR) {
                    waitForInstructor();
                  } else if (questionNumber == Poll.CLOSED) {
                    closePolling();
                  } else {
                    showQuestion();
                  }
                }
              }
            });
      }
    };

    timer.schedule(new TimerTask() {

      @Override
      public void run() {
        runOnUiThread(tick);
      }

    }, 0, CHECKING_INTERVAL);
  }

  /**
   * This screen is shown when instructor don't want students to answer anything
   */
  private void waitForInstructor() {
    questionLayout.setVisibility(View.GONE);
    waitingLayout.setVisibility(View.VISIBLE);
    waitingProgress.setVisibility(View.VISIBLE);
    statusTextView.setText(R.string.waiting_for_instructor);
  }

  /**
   * Is showed when instructor goes to results - polling is closed.
   */
  private void closePolling() {
    questionLayout.setVisibility(View.GONE);
    waitingLayout.setVisibility(View.VISIBLE);
    waitingProgress.setVisibility(View.GONE);
    statusTextView.setText(R.string.polling_is_closed);
    hideNotification();
    if (timer != null) {
      timer.cancel();
    }
    timer = null;
  }

  /**
   * Shows question with answers in the UI
   */
  private void showQuestion() {
    questionLayout.setVisibility(View.VISIBLE);
    waitingLayout.setVisibility(View.GONE);
    Question question = poll.getQuestions().get(poll.getCurrentQuestion());
    QuestionType questionType = question.getType();
    String questionText = question.getQuestionText();
    if (questionType == QuestionType.MULTIPLE_CHOICE) {
      chooseAllCorrect.setVisibility(View.VISIBLE);
    } else {
      chooseAllCorrect.setVisibility(View.GONE);
    }
    questionTextView.setText(questionText);
    // Show answers in UI
    List<String> answerTexts = new ArrayList<String>();
    for (Answer answer : question.getAnswers()) {
      answerTexts.add(answer.getAnswerText());
    }
    // Multiple choice questions are radio buttons, Multiple select questions
    // are checkboxes
    if (question.getType() == QuestionType.SINGLE_CHOICE) {
      answerList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
      answerList.setAdapter(new ArrayAdapter<String>(this,
          R.layout.list_item_single_choice, answerTexts));
    } else {
      answerList.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
      answerList.setAdapter(new ArrayAdapter<String>(this,
          R.layout.list_item_multiple_choice, answerTexts));
    }
    // anonymous question
    if (question.isAnonymous()) {
      anonymousTextView.setVisibility(View.VISIBLE);
    } else {
      anonymousTextView.setVisibility(View.GONE);
    }
  }

  /**
   * Blinks with color, plays sound and vibration based on correctness of answer
   */
  private void specialEffects(boolean correct) {
    // Blink bottom bar with color
    if (correct) {
      questionLayout.setBackgroundColor(getResources().getColor(R.color.positive));
    } else {
      questionLayout.setBackgroundColor(getResources().getColor(R.color.negative));
    }
    handler.postDelayed(new Runnable() {

      @Override
      public void run() {
        questionLayout.setBackgroundColor(getResources().getColor(android.R.color.white));
      }
    }, 500);
    // vibrate
    if (!correct) {
      Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
      v.vibrate(300);
    }
    // play sound
    if (correct) {
      MediaPlayer.create(this, R.raw.positive).start();
    } else {
      MediaPlayer.create(this, R.raw.negative).start();
    }
  }

  /**
   * Fetch worksheets of selected spreadsheet
   */
  private void fetchPoll(final String docId) {
    new AppEngineHelper(AppEngineHelper.POLL, docId, true, this,
        new HttpListener() {

          @Override
          public void onSuccess(Object responseData) {
            poll = (Poll) responseData;
            poll.setCurrentQuestion(Poll.UNKNOWN);
            activityHelper.changeTitle(poll.getTitle());
            showNotification();
            startTimer();
            savePollAccess();
          }
        });
  }

  /**
   * Saves poll into recent polls for easy access later
   */
  private void savePollAccess() {
    Cursor cursor = getContentResolver().query(PollList.CONTENT_URI, new String[] {
        PollList.DOCUMENT_ID
    }, null, null, null);
    try {
      boolean found = false;
      while (cursor.moveToNext()) {
        if (cursor.getString(0).equals(poll.getDocumentId())) {
          found = true;
          break;
        }
      }
      if (found) {
        ContentValues values = new ContentValues();
        values.put(PollList.TITLE, poll.getTitle());
        getContentResolver().update(Uri.parse(PollList.ITEM_URI + "/" + poll.getDocumentId()),
            values, null, null);
      } else {
        ContentValues values = new ContentValues();
        values.put(PollList.DOCUMENT_ID, poll.getDocumentId());
        values.put(PollList.TITLE, poll.getTitle());
        getContentResolver().insert(PollList.CONTENT_URI, values);
      }
    } finally {
      cursor.close();
    }
  }

  /**
   * Shares poll using ShareActivity
   */
  private void sharePoll() {
    Intent intent = new Intent(this, ShareActivity.class);
    Uri url = Uri.parse(AppEngineHelper.BROKER_URL + "/poll/").buildUpon()
        .appendPath(poll.getDocumentId()).build();
    intent.putExtra(ShareActivity.EXTRA_URL, url.toString());
    intent.putExtra(ShareActivity.EXTRA_POLL_NAME, poll.getTitle());
    startActivity(intent);
  }
}
