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

package org.quizpoll.net;

import com.google.gson.reflect.TypeToken;

import android.net.Uri;
import android.net.Uri.Builder;
import android.text.TextUtils;
import android.widget.Toast;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.quizpoll.data.Preferences;
import org.quizpoll.data.Preferences.PrefType;
import org.quizpoll.data.model.Answer;
import org.quizpoll.data.model.DocsEntry;
import org.quizpoll.data.model.LeaderboardEntry;
import org.quizpoll.data.model.Poll;
import org.quizpoll.data.model.PollResponse;
import org.quizpoll.data.model.Question;
import org.quizpoll.data.model.Quiz;
import org.quizpoll.ui.GoogleAuthActivity;
import org.quizpoll.R;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Creating and parsing of requests for the AppEngine backend.
 */
public class AppEngineHelper extends HttpHelper {

  // URL to broker JSON API
  public static final String BROKER_URL = "http://quiz-n-poll.appspot.com";

  // Message types
  public static final int LOGIN = -1;
  public static final int COLLECTION_DOCUMENTS = 0;
  public static final int QUIZ = 1;
  public static final int QUIZ_LEADERBOARD = 2;
  public static final int QUIZ_SUBMIT = 3;
  public static final int POLL = 4;
  public static final int POLL_STATUS = 5;
  public static final int POLL_SUBMIT = 6;

  protected GoogleAuthActivity authActivity;

  public AppEngineHelper(int messageType, Object data, boolean showDialog,
      GoogleAuthActivity authActivity, HttpListener listener) {
    super(messageType, data, showDialog, authActivity, listener);
    this.authActivity = authActivity;
    if (messageType == LOGIN) {
      start();
    } else {
      authActivity.authenticatedRequest(GoogleAuthActivity.AUTHSERVER_APPENGINE, this);
    }
  }

  @Override
  public int getDialogMessage() {
    switch (messageType) {
      case LOGIN:
        return R.string.verifying_google_account;
      case COLLECTION_DOCUMENTS:
        return R.string.fetching_quiz_games;
      case QUIZ:
        return R.string.loading_quiz;
      case QUIZ_LEADERBOARD:
        return R.string.fetching_leaderboard;
      case QUIZ_SUBMIT:
        return R.string.submitting_score;
      case POLL:
        return R.string.loading_polling;
      case POLL_SUBMIT:
        return R.string.submitting_answer;
    }
    return UNKNOWN_DIALOG_MESSAGE;
  }

  @Override
  public HttpUriRequest createRequest() {
    Builder url = Uri.parse(BROKER_URL).buildUpon().appendPath("qp_api");
    String postData = null;
    switch (messageType) {
      case LOGIN:
        return createLoginRequest(BROKER_URL);
      case COLLECTION_DOCUMENTS:
        url.appendPath("documents").appendPath((String) requestData);
        break;
      case QUIZ:
        url.appendPath("quiz").appendPath((String) requestData);
        break;
      case QUIZ_LEADERBOARD:
        @SuppressWarnings("unchecked")
        List<String> args = (List<String>) requestData;
        url.appendPath("quiz").appendPath("leaderboard").appendPath(args.get(0))
            .appendPath(args.get(1));
        break;
      case QUIZ_SUBMIT:
        url.appendPath("quiz").appendPath("submit");
        postData = getGson().toJson(requestData);
        break;
      case POLL:
        url.appendPath("poll").appendPath((String) requestData);
        break;
      case POLL_STATUS:
        @SuppressWarnings("unchecked")
        List<String> pargs = (List<String>) requestData;
        url.appendPath("poll").appendPath("status").appendPath(pargs.get(0))
            .appendPath(pargs.get(1));
        break;
      case POLL_SUBMIT:
        Poll poll = (Poll) requestData;
        url.appendPath("poll").appendPath("submit");
        postData = createPollSubmitRequest(poll);
        break;
    }
    if (postData == null) {
      return addCookie(new HttpGet(url.build().toString()));
    } else {
      HttpPost post = new HttpPost(url.build().toString());
      try {
        post.setEntity(new StringEntity(postData, "utf8"));
      } catch (UnsupportedEncodingException e) {
        return null;
      }
      return addCookie(post);
    }
  }

  @Override
  public void parseResponse(HttpResponse response) {
    if (messageType == LOGIN) {
      parseLoginResponse(response);
    } else {
      if (response.getStatusLine().getStatusCode() == HttpStatus.SC_MOVED_TEMPORARILY) {
        // Cookie expired
        error(HttpStatus.SC_UNAUTHORIZED);
        authActivity.authenticatedRequest(GoogleAuthActivity.AUTHSERVER_APPENGINE, this, true);
      } else {
        switch (messageType) {
          case COLLECTION_DOCUMENTS:
            handleDocuments(response);
            break;
          case QUIZ:
            handleQuiz(response);
            break;
          case QUIZ_LEADERBOARD:
            handleLeaderboard(response);
            break;
          case QUIZ_SUBMIT:
            success(null); // Ignore response
            break;
          case POLL:
            handlePoll(response);
            break;
          case POLL_STATUS:
            handlePollStatus(response);
            break;
          case POLL_SUBMIT:
            success(null); // Ignore response
            break;
        }
      }
    }
  }

  @Override
  protected void error(int statusCode) {
    if (statusCode == HttpStatus.SC_FORBIDDEN) {
      Toast.makeText(activity, R.string.wrong_permissions_error, Toast.LENGTH_SHORT).show();
    } else if (statusCode == HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE) {
      Toast.makeText(activity, R.string.format_error, Toast.LENGTH_SHORT).show();
    }
    super.error(statusCode);
  }

  /**
   * Makes login request to AppEngine server
   */
  protected HttpUriRequest createLoginRequest(String baseUrl) {
    Uri url =
        Uri.parse(baseUrl).buildUpon().appendEncodedPath("_ah/login")
            .appendQueryParameter("auth", (String) requestData).build();
    return new HttpGet(url.toString());
  }

  /**
   * Saves cookies from AppEngine login
   */
  protected void parseLoginResponse(HttpResponse response) {
    Header[] cookies = response.getHeaders("Set-Cookie");
    String acsid = null;
    if (cookies.length > 0) {
      acsid = cookies[0].getValue().split("; ")[0];
      success(acsid);
    } else {
      error(HttpStatus.SC_UNAUTHORIZED);
    }
  }

  /**
   * Adds AppEngine cookies to the request
   */
  private HttpUriRequest addCookie(HttpUriRequest request) {
    String cookie = Preferences.getString(PrefType.COOKIE_APPENGINE, activity);
    if (cookie != null) {
      request.setHeader("Cookie", cookie);
    }
    return request;
  }

  /**
   * Create polling submit request
   */
  private String createPollSubmitRequest(Poll poll) {
    Question question = poll.getQuestions().get(poll.getCurrentQuestion());
    List<String> answeredAnswers = new ArrayList<String>();
    for (Answer answer : question.getAnswers()) {
      if (answer.isAnswered()) {
        answeredAnswers.add(String.valueOf(answer.getNumber() + 1));
      }
    }
    String answers = TextUtils.join(",", answeredAnswers);
    PollResponse response =
        new PollResponse(poll.getDocumentId(), poll.getResponsesSheet(), question.isAnonymous(),
            poll.getCurrentQuestion() + 1, answers, question.isSuccess());
    return getGson().toJson(response);
  }

  /**
   * Parses document list inside collection from broker
   */
  private void handleDocuments(HttpResponse response) {
    String content = readContent(response);
    Type collectionType = new TypeToken<List<DocsEntry>>() {}.getType();
    List<DocsEntry> entries = getGson().fromJson(content, collectionType);
    success(entries);
  }

  /**
   * Parses the quiz from broker
   */
  private void handleQuiz(HttpResponse response) {
    String content = readContent(response);
    Quiz quiz = getGson().fromJson(content, Quiz.class);
    success(quiz);
  }

  /**
   * Parses statistics from broker
   */
  private void handleLeaderboard(HttpResponse response) {
    String content = readContent(response);
    Type collectionType = new TypeToken<List<LeaderboardEntry>>() {}.getType();
    List<LeaderboardEntry> entries = getGson().fromJson(content, collectionType);
    success(entries);
  }

  /**
   * Parses the polling from broker
   */
  private void handlePoll(HttpResponse response) {
    String content = readContent(response);
    Poll polling = getGson().fromJson(content, Poll.class);
    success(polling);
  }

  /**
   * Parses the polling status from broker
   */
  private void handlePollStatus(HttpResponse response) {
    String content = readContent(response);
    Integer questionNumber = getGson().fromJson(content, Integer.class);
    if (questionNumber != Poll.CLOSED && questionNumber != Poll.WAITING_FOR_INSTRUCTOR) {
      questionNumber--;
    }
    success(questionNumber);
  }

}
