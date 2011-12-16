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

/**
 * Use this listener in activity to make HTTP requests.
 */
public abstract class HttpListener {
  /**
   * Handle received data in UI
   */
  public abstract void onSuccess(Object responseData);

  /**
   * Default errors are handled in HttpHelper and children, so overriding this
   * is optional
   */
  public void onFailure(int errorCode) {
    // Do nothing
  }
}
