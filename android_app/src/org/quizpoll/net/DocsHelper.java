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

import com.google.android.common.gdata2.AndroidXmlParserFactory;
import com.google.wireless.gdata2.data.Entry;
import com.google.wireless.gdata2.parser.ParseException;
import com.google.wireless.gdata2.parser.xml.XmlGDataParser;

import android.net.Uri;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.quizpoll.R;
import org.quizpoll.data.Preferences;
import org.quizpoll.data.Preferences.PrefType;
import org.quizpoll.data.model.DocsEntry;
import org.quizpoll.ui.GoogleAuthActivity;
import org.quizpoll.util.Utils;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates and parses requests for Google Document List API.
 */
public class DocsHelper extends HttpHelper {

  // URL to Google Document List API - private quizzes
  private static final String PRIVATE_DOCLIST_URL =
      "https://docs.google.com/feeds/default/private/full?title=%5BQ%5D";

  // Message types
  public static final int MY_DOCUMENTS = 0;

  private GoogleAuthActivity authActivity;

  public DocsHelper(int messageType, Object data, boolean showDialog,
      GoogleAuthActivity authActivity,
      HttpListener listener) {
    super(messageType, data, showDialog, authActivity, listener);
    this.authActivity = authActivity;
    authActivity.authenticatedRequest(GoogleAuthActivity.AUTHSERVER_DOCS, this);
  }

  @Override
  public int getDialogMessage() {
    switch (messageType) {
      case MY_DOCUMENTS:
        return R.string.fetching_quiz_games;
    }
    return UNKNOWN_DIALOG_MESSAGE;
  }

  @Override
  public HttpUriRequest createRequest() {
    Uri url;
    switch (messageType) {
      case MY_DOCUMENTS:
        // Own documents
        url = Uri.parse(PRIVATE_DOCLIST_URL);
        return addHeaders(new HttpGet(url.toString()), PrefType.AUTH_TOKEN_DOCS);
    }
    return null;
  }

  @Override
  public void parseResponse(HttpResponse response) {
    try {
      switch (messageType) {
        case MY_DOCUMENTS:
          handleDocumentList(response);
          break;
      }
    } catch (IOException e) {
      error(ERROR_CONNECTION);
    } catch (ParseException e) {
      error(ERROR_CONNECTION);
    } catch (IllegalStateException e) {
      error(ERROR_CONNECTION);
    } catch (XmlPullParserException e) {
      error(ERROR_CONNECTION);
    }
  }

  @Override
  protected void error(int statusCode) {
    super.error(statusCode);
    if (statusCode == HttpStatus.SC_UNAUTHORIZED) {
      // Authtoken expired, login again
      authActivity.authenticatedRequest(GoogleAuthActivity.AUTHSERVER_DOCS, this, true);
    }
  }

  /**
   * Adds GData API headers to the request
   */
  private HttpUriRequest addHeaders(HttpUriRequest request, PrefType type) {
    request.addHeader("GData-Version", "3.0");
    request.addHeader(
        "Authorization",
        "GoogleLogin auth="
            + Preferences.getString(type, authActivity));
    request.addHeader("Content-Type", "application/atom+xml; charset=UTF-8");
    return request;
  }

  /**
   * Starts parsing XML-based data
   */
  private XmlGDataParser getXmlParser(HttpResponse response) throws ParseException,
      IllegalStateException, IOException, XmlPullParserException {
    return new XmlGDataParser(response.getEntity().getContent(),
        new AndroidXmlParserFactory().createParser());
  }

  /**
   * Parses documents in some folder and filter folders and spreadsheets
   */
  private void handleDocumentList(HttpResponse response) throws ParseException,
      IllegalStateException, IOException, XmlPullParserException {
    final XmlGDataParser parser = getXmlParser(response);
    parser.parseFeedEnvelope();
    List<DocsEntry> docsEntries = new ArrayList<DocsEntry>();
    Entry entry = null;
    while (parser.hasMoreData()) {
      entry = parser.readNextEntry(entry);
      int type;
      if (entry.getCategory().endsWith("folder")) {
        type = DocsEntry.COLLECTION;
      } else if (entry.getCategory().endsWith("spreadsheet")) {
        type = DocsEntry.QUIZ;
      } else {
        // Skip other types than folders and spreadsheets
        continue;
      }
      String[] parts = Uri.parse(entry.getId()).getLastPathSegment().split(":");
      if (parts.length < 2) {
        throw new ParseException();
      }
      String id = parts[1];
      String title = entry.getTitle();
      if (!title.contains("[Q]")) {
        continue; // Skip spreadsheets which do not have [Q] in title
      }
      docsEntries.add(new DocsEntry(type, Utils.formatQuizName(entry.getTitle()), id));
    }
    success(docsEntries);
  }

}
