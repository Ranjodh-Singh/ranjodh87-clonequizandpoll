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

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import org.quizpoll.R;
import org.quizpoll.util.ActivityHelper;

/**
 * Screen for user feedback and help.
 */
public class AboutActivity extends Activity {

  public static final String INFO_URL = "http://quiz-n-poll.appspot.com";
  public static final String FEEDBACK_EMAIL = "quiz-and-poll@googlegroups.com";
  public static final String MARKET_LISTING = "market://details?id=org.quizpoll";

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_about);
    new ActivityHelper(this).setupActionBar(getString(R.string.about));
  }

  /**
   * Sends e-mail to developer with some predefined text.
   */
  public void mailClicked(View view) {
    Intent i = new Intent(Intent.ACTION_SEND);
    i.setType("text/plain");
    i.putExtra(Intent.EXTRA_EMAIL, new String[] {FEEDBACK_EMAIL});
    i.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.feedback_subject));
    String device =
        Build.MANUFACTURER + " " + Build.MODEL + " (Android " + Build.VERSION.RELEASE + ")";
    i.putExtra(Intent.EXTRA_TEXT, getString(R.string.feedback_text, device));
    startActivity(i);
  }

  /**
   * Opens website of the project.
   */
  public void webClicked(View view) {
    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(INFO_URL));
    startActivity(intent);
  }

  /**
   * Opens Android Market listing
   */
  public void rate(View view) {
    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(MARKET_LISTING));
    startActivity(intent);
  }
}
