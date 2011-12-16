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

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
import android.net.http.AndroidHttpClient;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpUriRequest;
import org.quizpoll.R;
import org.quizpoll.ui.AboutActivity;
import org.quizpoll.util.Utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Handles HTTP connections, connection progress and downloading data from the
 * network. Also reacts to common HTTP-related errors. Create subclass of this
 * helper for every server you are communicating with.
 */
public abstract class HttpHelper {

  protected static final String TAG = "HttpHelper";

  private static final String USER_AGENT = "Android/org.quizpoll/";

  /**
   * Should be returned when network connection error occurs.
   */
  public static final int ERROR_CONNECTION = 0;
  /**
   * Should be used when subclass has just one message type
   */
  public static final int SINGLE_MESSAGE_TYPE = -1;
  /**
   * Should be returned when no dialog message is defined;
   */
  public static final int UNKNOWN_DIALOG_MESSAGE = -1;

  protected final int messageType;
  protected final Object requestData;
  private final HttpListener listener;
  protected final Activity activity;
  private ProgressDialog progressDialog;
  private final boolean showDialog;
  private AndroidHttpClient httpClient;

  public HttpHelper(int messageType, Object data, boolean showDialog, Activity context,
      HttpListener listener) {
    this.messageType = messageType;
    this.requestData = data;
    this.listener = listener;
    this.activity = context;
    this.showDialog = showDialog;
  }

  /**
   * Starts the HTTP request-response
   */
  public void start() {
    if (showDialog) {
      progressDialog = new ProgressDialog(activity);
      progressDialog.setOwnerActivity(activity);
      int message = getDialogMessage();
      progressDialog.setMessage(activity.getString(message));
      progressDialog.show();
    }
    new NetworkTask().execute();
  }

  /**
   * Subclass should provide string resource for loading dialog
   */
  public abstract int getDialogMessage();

  /**
   * Subclass should implement creation of requests
   */
  public abstract HttpUriRequest createRequest();

  /**
   * Subclass should implement parsing of valid response
   */
  public abstract void parseResponse(HttpResponse response);

  /**
   * Makes HTTP request with JSON data.
   */
  private HttpResponse doRequest(HttpUriRequest request) {
    try {
      httpClient =
          AndroidHttpClient.newInstance(USER_AGENT + Utils.getVersion(activity) + "/"
              + Utils.getVersionCode(activity));
      Log.i(TAG, "Request: " + request.getURI());
      HttpResponse response = httpClient.execute(request);
      Log.i(TAG, "Response: " + response.getStatusLine().toString());
      return response;
    } catch (IOException e) {
      return null;
    }
  }

  /**
   * Handles received data from network.
   */
  private void handleResponse(HttpResponse response) {
    int statusCode = response.getStatusLine().getStatusCode();
    if (statusCode <= HttpStatus.SC_MOVED_TEMPORARILY) {
      parseResponse(response);
    } else {
      error(statusCode);
    }
  }

  /**
   * Reads content of HTTP response, is used in subclasses
   */
  protected String readContent(HttpResponse response) {
    try {
      BufferedReader br =
          new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = br.readLine()) != null) {
        sb.append(line);
      }
      String content = sb.toString();
      if (content != null) {
        Log.i(TAG, content);
      }

      br.close();
      return content;
    } catch (IOException e) {
      error(ERROR_CONNECTION);
      return null;
    }
  }

  /**
   * Creates new GSON parser instance, is used in subclasses
   */
  protected Gson getGson() {
    return new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create();
  }

  /**
   * HTTP request successful, send parsed object back to caller.
   */
  protected void success(Object responseData) {
    if (showDialog) {
      progressDialog.hide();
      progressDialog.dismiss();
    }
    listener.onSuccess(responseData);
    httpClient.close();
  }

  /**
   * Default handing of errors
   */
  protected void error(int statusCode) {
    // General errors
    switch (statusCode) {
      case ERROR_CONNECTION:
        Toast.makeText(activity, R.string.connection_error, Toast.LENGTH_SHORT).show();
        break;
      case HttpStatus.SC_INTERNAL_SERVER_ERROR:
        Toast.makeText(activity, R.string.server_error, Toast.LENGTH_SHORT).show();
        break;
      case HttpStatus.SC_NOT_FOUND:
        Toast.makeText(activity, R.string.not_found_error, Toast.LENGTH_SHORT).show();
        break;
      case 426:
        // Upgrade Required
        showUpgradeRequiredDialog();
        break;
    }
    if (showDialog) {
      progressDialog.hide();
      progressDialog.dismiss();
    }
    listener.onFailure(statusCode);
    httpClient.close();
  }

  /**
   * Shows dialog for forced update
   */
  private void showUpgradeRequiredDialog() {
    AlertDialog.Builder alert = new AlertDialog.Builder(activity);
    alert.setTitle(R.string.update_required);
    alert.setMessage(R.string.update_required_description);
    alert.setPositiveButton(R.string.go_to_market, new OnClickListener() {

      @Override
      public void onClick(DialogInterface dialog, int which) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(AboutActivity.MARKET_LISTING));
        activity.startActivity(intent);
      }
    });
    Dialog dialog = alert.create();
    dialog.setOwnerActivity(activity);
    dialog.show();
  }

  /**
   * Make network request on background
   */
  private class NetworkTask extends AsyncTask<Void, Void, HttpResponse> {

    @Override
    protected HttpResponse doInBackground(Void... parameters) {
      return doRequest(createRequest());
    }

    @Override
    protected void onPostExecute(HttpResponse response) {
      if (response == null) {
        error(ERROR_CONNECTION);
      } else {
        handleResponse(response);
      }
    }
  }
}
