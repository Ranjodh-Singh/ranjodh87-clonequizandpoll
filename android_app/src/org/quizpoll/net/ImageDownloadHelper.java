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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;

import java.io.IOException;

/**
 * Handles creating and parsing request for image somewhere on the web.
 */
public class ImageDownloadHelper extends HttpHelper {

  public ImageDownloadHelper(int messageType, Object data, boolean showDialog, Activity activity,
      HttpListener listener) {
    super(messageType, data, showDialog, activity, listener);
    start();
  }

  @Override
  public int getDialogMessage() {
    // No dialog message
    return UNKNOWN_DIALOG_MESSAGE;
  }

  @Override
  public HttpUriRequest createRequest() {
    Uri url = Uri.parse((String) requestData);
    return new HttpGet(url.toString());
  }

  @Override
  public void parseResponse(HttpResponse response) {
    Bitmap bitmap;
    try {
      bitmap = BitmapFactory.decodeStream(response.getEntity()
          .getContent());
      success(bitmap);
    } catch (IllegalStateException e) {
      // Ignore
    } catch (IOException e) {
      // Ignore
    }
  }

}
