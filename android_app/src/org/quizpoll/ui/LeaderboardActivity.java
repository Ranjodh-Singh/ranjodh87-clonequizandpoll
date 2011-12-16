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
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import org.quizpoll.R;
import org.quizpoll.data.model.LeaderboardEntry;
import org.quizpoll.net.AppEngineHelper;
import org.quizpoll.net.HttpListener;
import org.quizpoll.util.ActivityHelper;
import org.quizpoll.util.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Screen where leaderboard is shown. It is native leaderboard with data from
 * spreadsheet.
 */
public class LeaderboardActivity extends GoogleAuthActivity {
  @SuppressWarnings("unused")
  private static final String TAG = "LeaderboardActivity";

  public static final String EXTRA_DOCUMENT_ID = "org.quizpoll.DocumentId";
  public static final String EXTRA_SCORE = "org.quizpoll.Score";
  public static final String EXTRA_WORKSHEET = "org.quizpoll.Worksheet";

  private String documentId;
  private String worksheetId;
  private int score;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_leaderboard);
    ActivityHelper helper = new ActivityHelper(this);
    helper.setupActionBar(getString(R.string.leaderboard));
    helper.addActionButtonCompat(R.drawable.ic_title_refresh, new OnClickListener() {

      @Override
      public void onClick(View v) {
        fetchLeaderboard();
      }
    }, false);
    documentId = getIntent().getStringExtra(EXTRA_DOCUMENT_ID);
    worksheetId = getIntent().getStringExtra(EXTRA_WORKSHEET);
    score = getIntent().getIntExtra(EXTRA_SCORE, Integer.MIN_VALUE);
    fetchLeaderboard();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.leaderboard_menu, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.refresh:
        fetchLeaderboard();
        return true;
        // More menu items to come
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  /**
   * Try again button was clicked
   */
  public void tryAgainClicked(View view) {
    Intent intent = new Intent(this, QuizInfoActivity.class);
    intent.putExtra(QuizInfoActivity.EXTRA_DOC_ID, documentId);
    startActivity(intent);
    finish();
  }

  /**
   * Loads leaderboard data into UI
   */
  private void load(List<LeaderboardEntry> entries) {
    Collections.sort(entries);
    ListView list = (ListView) findViewById(R.id.leaderboard_list);
    list.setAdapter(new LeaderboardAdapter(this, R.layout.list_item_leaderboard, entries));
    // Select player
    boolean firstInLeaderboard = false;
    String ldap = Utils.getLdap(this);
    int position = -1;
    int lastScore = Integer.MIN_VALUE;
    for (int i = 0; i < entries.size(); i++) {
      LeaderboardEntry entry = entries.get(i);
      if (ldap.equals(entry.getLdap())) {
        position = i;
        lastScore = entry.getScore();
      }
    }
    if (position != -1) {
      list.setSelection(position);
      if (position == 0) {
        firstInLeaderboard = true;
      }
    }
    // React to score change after game
    if (score == Integer.MIN_VALUE) {
      // Leaderboard directly from QuizInfo
      findViewById(R.id.bottom_bar).setVisibility(View.GONE);
    } else {
      // Leaderboard after game
      TextView status = (TextView) findViewById(R.id.status_message);
      if (firstInLeaderboard) {
        status.setText(R.string.no_1);
      } else {
        if (score >= lastScore) {
          status.setText(R.string.new_highscore);
        } else {
          status.setText(getString(R.string.less_than_highscore, score));
        }
      }
    }
  }

  /**
   * Reloads leaderboard
   */
  private void fetchLeaderboard() {
    List<String> arguments = new ArrayList<String>();
    arguments.add(documentId);
    arguments.add(worksheetId);
    new AppEngineHelper(AppEngineHelper.QUIZ_LEADERBOARD, arguments, true, this,
        new HttpListener() {

          @Override
          public void onSuccess(Object responseData) {
            @SuppressWarnings("unchecked")
            List<LeaderboardEntry> entries = (List<LeaderboardEntry>) responseData;
            load(entries);
          }
        });
  }

  /**
   * Adapter to fill leaderboard data into the list
   */
  private class LeaderboardAdapter extends ArrayAdapter<LeaderboardEntry> {

    private List<LeaderboardEntry> entries;
    private String player;
    private Context context;

    public LeaderboardAdapter(Context context, int textViewResourceId,
        List<LeaderboardEntry> entries) {
      super(context, textViewResourceId, entries);
      this.entries = entries;
      player = Utils.getLdap(context);
      this.context = context;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      if (convertView == null) {
        LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        convertView = vi.inflate(R.layout.list_item_leaderboard, parent, false);
      }
      LeaderboardEntry entry = entries.get(position);
      TextView number = (TextView) convertView.findViewById(R.id.number);
      number.setText((position + 1) + ".");
      TextView ldap = (TextView) convertView.findViewById(R.id.ldap);
      ldap.setText(entry.getLdap());
      TextView score = (TextView) convertView.findViewById(R.id.score);
      score.setText(String.valueOf(entry.getScore()));
      // Coloring of this player
      if (entry.getLdap().equals(player)) {
        convertView.setBackgroundColor(context.getResources().getColor(R.color.pressed_start));
        ldap.setTextColor(Color.BLACK);
      } else {
        convertView.setBackgroundColor(Color.WHITE);
        ldap.setTextColor(context.getResources().getColor(R.color.orange));
      }
      return convertView;
    }

  }

}
