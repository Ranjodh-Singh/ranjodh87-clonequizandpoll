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

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.util.SparseBooleanArray;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.quizpoll.R;
import org.quizpoll.data.model.Answer;
import org.quizpoll.data.model.Question;
import org.quizpoll.data.model.Question.QuestionType;
import org.quizpoll.data.model.Quiz;
import org.quizpoll.net.AppEngineHelper;
import org.quizpoll.net.HttpListener;
import org.quizpoll.util.ActivityHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Screen where actual game is played.
 */
public class QuizPlayerActivity extends GoogleAuthActivity {
  @SuppressWarnings("unused")
  private static final String TAG = "QuizPlayerActivity";

  public static final String EXTRA_QUIZ = "org.quizpoll.Quiz";
  // Constants defining rules of the game
  public static final int SECONDS_PER_QUESTION = 120;
  public static final int MAX_QUESTIONS = 10;
  public static final int COST_OF_WRONG_ANSWER = 100;

  private Quiz quiz;
  private ActivityHelper activityHelper;
  private int currentQuestion = -1; // Next question after start will be 0
  private int remainingTime;
  private int score;
  private Timer timer;
  private Handler handler;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_quiz_player);
    quiz = (Quiz) getIntent().getSerializableExtra(EXTRA_QUIZ);
    activityHelper = new ActivityHelper(this);
    activityHelper.setupActionBar(""); // Empty title, it is changed in
                                       // showQuestion
    setVolumeControlStream(AudioManager.STREAM_MUSIC);
    randomizeQuestions();
    showNextQuestion();
    handler = new Handler();
  }

  @Override
  protected void onPause() {
    super.onPause();
    if (timer != null) {
      timer.cancel();
    }
  }

  /**
   * Submit answer button was clicked
   */
  public void submitClicked(View view) {
    // Stop timer
    if (timer != null) {
      timer.cancel();
    }
    // Was the answer correct?
    boolean correct = isAnswerCorrect();
    // Show special effects (sound, vibration, color)
    specialEffects(correct);
    // Update score
    updateScore(correct);
    // Disable Submit button
    ((Button) findViewById(R.id.submit)).setEnabled(false);
    // Wait some time and show next question
    handler.postDelayed(new Runnable() {

      @Override
      public void run() {
        // Enable Submit button
        ((Button) findViewById(R.id.submit)).setEnabled(true);
        if (currentQuestion + 1 < quiz.getQuestions().size()) {
          showNextQuestion();
        } else {
          submitScoreAndStatistics();
        }
      }
    }, 500);

  }

  /**
   * Determine if answer is correct and mark this for later statistics
   */
  private boolean isAnswerCorrect() {
    SparseBooleanArray checkedPositions = ((ListView) findViewById(R.id.answers))
        .getCheckedItemPositions();
    boolean correct = true;
    Question question = quiz.getQuestions().get(currentQuestion);
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
   * Shows question with answers in the UI
   */
  private void showNextQuestion() {
    currentQuestion++;
    activityHelper.changeTitle(getString(R.string.question_number, (currentQuestion + 1), quiz
        .getQuestions().size()));
    Question question = quiz.getQuestions().get(currentQuestion);
    QuestionType questionType = question.getType();
    String questionText = question.getQuestionText();
    if (questionType == QuestionType.MULTIPLE_CHOICE) {
      findViewById(R.id.choose_all_correct).setVisibility(View.VISIBLE);
    } else {
      findViewById(R.id.choose_all_correct).setVisibility(View.GONE);
    }
    ((TextView) findViewById(R.id.question_text)).setText(questionText);
    // Show answers in UI
    List<String> answerTexts = new ArrayList<String>();
    for (Answer answer : question.getAnswers()) {
      answerTexts.add(answer.getAnswerText());
    }
    ListView answerList = (ListView) findViewById(R.id.answers);
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
    startTimer();
  }

  /**
   * Starts the countdown for each question
   */
  private void startTimer() {
    remainingTime = SECONDS_PER_QUESTION;
    timer = new Timer();
    final TextView timerTextView = (TextView) findViewById(R.id.timer);
    final Runnable tick = new Runnable() {

      @Override
      public void run() {
        timerTextView.setText(remainingTime + " s");
        if (remainingTime <= 0) {
          submitClicked(null); // Time is out, click the button
        }
      }
    };

    timer.schedule(new TimerTask() {

      @Override
      public void run() {
        remainingTime--;
        runOnUiThread(tick);
      }

    }, 0, 1000);
  }

  /**
   * Update score based on correctness
   */
  private void updateScore(boolean correct) {
    if (correct) {
      score += remainingTime;
    } else {
      score -= COST_OF_WRONG_ANSWER;
    }
    ((TextView) findViewById(R.id.score)).setText(String.valueOf(score));
  }

  /**
   * Blinks with color, plays sound and vibration based on correctness of answer
   */
  private void specialEffects(boolean correct) {
    // Blink bottom bar with color
    final RelativeLayout bottomBar = (RelativeLayout) findViewById(R.id.bottom_bar);
    if (correct) {
      bottomBar.setBackgroundColor(getResources().getColor(R.color.positive));
    } else {
      bottomBar.setBackgroundColor(getResources().getColor(R.color.negative));
    }
    handler.postDelayed(new Runnable() {

      @Override
      public void run() {
        bottomBar.setBackgroundColor(getResources().getColor(R.color.lightgray));
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
   * Selects subset of questions, randomizes questions and answers
   */
  private void randomizeQuestions() {
    //TODO(vavrad): remove this after randomizing is live on prod server
    List<Question> questions = quiz.getQuestions();
    Collections.shuffle(questions);
    // Filter questions
    if (questions.size() > MAX_QUESTIONS) {
      questions = questions.subList(0, MAX_QUESTIONS);
      quiz.setQuestions(questions);
    }
    // Randomize answers within questions
    for (Question question : questions) {
      Collections.shuffle(question.getAnswers());
    }
  }

  /**
   * Submits score into spreadsheet
   */
  private void submitScoreAndStatistics() {
    // Disable Submit button
    ((Button) findViewById(R.id.submit)).setEnabled(false);
    // Save score
    quiz.setScore(score);

    new AppEngineHelper(AppEngineHelper.QUIZ_SUBMIT, quiz, true, this,
        new HttpListener() {

          @Override
          public void onSuccess(Object responseData) {
            showLeaderboard();
          }
        });
  }

  /**
   * Shows native leaderboard or calls Arena
   */
  private void showLeaderboard() {
    Intent intent = new Intent(this,
        LeaderboardActivity.class);
    intent.putExtra(LeaderboardActivity.EXTRA_WORKSHEET, quiz.getLeaderboardSheet());
    intent.putExtra(LeaderboardActivity.EXTRA_DOCUMENT_ID, quiz.getDocumentId());
    intent.putExtra(LeaderboardActivity.EXTRA_SCORE, score);
    startActivity(intent);
  }
}
