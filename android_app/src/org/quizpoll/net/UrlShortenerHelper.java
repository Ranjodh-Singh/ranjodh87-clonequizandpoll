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

import android.app.Activity;
import android.net.Uri;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.quizpoll.R;
import org.quizpoll.data.model.ShortenedUrl;

import java.io.UnsupportedEncodingException;

/**
 * Creates and parses requests for URL Shortener service.
 */
public class UrlShortenerHelper extends HttpHelper {

  // API key for URL Shortener service
  private static final String URL_SHORTENER_API_KEY = "AIzaSyBWORrW1tLCNfe4_44eWqP_bxfugEHMasg";

  // URL to Google URL Shortener API
  private static final String URL_SHORTENER_URL = "https://www.googleapis.com/urlshortener/v1/url";

  public UrlShortenerHelper(int messageType, Object data, boolean showDialog, Activity activity,
      HttpListener listener) {
    super(messageType, data, showDialog, activity, listener);
    start();
  }

  @Override
  public int getDialogMessage() {
    return R.string.loading;
  }

  @Override
  public HttpUriRequest createRequest() {
    try {
      Uri url = Uri.parse(URL_SHORTENER_URL).buildUpon()
          .appendQueryParameter("key", URL_SHORTENER_API_KEY).build();
      HttpUriRequest request = new HttpPost(url.toString());
      String requestString = "{\"longUrl\": \"" + (String) requestData + "\"}";
      ((HttpPost) request).setEntity(new StringEntity(requestString));
      request.setHeader("Content-Type", "application/json");
      return request;
    } catch (UnsupportedEncodingException e) {
      return null;
    }
  }

  @Override
  public void parseResponse(HttpResponse response) {
    String content = readContent(response);
    ShortenedUrl shortenerResponse = getGson().fromJson(content, ShortenedUrl.class);
    success(shortenerResponse.getId());
  }

}
