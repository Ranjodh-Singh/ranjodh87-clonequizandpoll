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
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import org.quizpoll.R;
import org.quizpoll.util.ActivityHelper;

/**
 * Home screen of the app. Uses DashBoardLayout for main app icons.
 */
public class HomeActivity extends Activity {
  @SuppressWarnings("unused")
  private static final String TAG = "HomeActivity";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_home);
    ActivityHelper helper = new ActivityHelper(this);
    helper.setupActionBar(null); // No title, app name instead
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.home_menu, menu);
    return true;
  }

  /**
   * When menu item selected.
   */
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.about:
        startActivity(new Intent(this, AboutActivity.class));
        return true;
        // More menu items to come
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  /**
   * When button Quiz Games clicked
   */
  public void gamesClicked(View view) {
    Intent intent = new Intent(HomeActivity.this, QuizBrowserActivity.class);
    intent.putExtra(QuizBrowserActivity.EXTRA_COLLECTION_ID,
        QuizBrowserActivity.QUIZZES_SHARED_COLLECTION);
    intent.putExtra(QuizBrowserActivity.EXTRA_TITLE, getString(R.string.public_quizzes));
    startActivity(intent);
  }

  /**
   * When button Polling clicked
   */
  public void pollingClicked(View view) {
    Intent intent = new Intent(HomeActivity.this, RecentPollsActivity.class);
    startActivity(intent);
  }
}
