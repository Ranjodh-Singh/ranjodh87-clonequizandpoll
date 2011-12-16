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

package org.quizpoll.data.model;

import java.io.Serializable;

/**
 * Data model for leaderboard entry from spreadsheet.
 */
public class LeaderboardEntry implements Serializable, Comparable<LeaderboardEntry> {
  private int score;
  private final String ldap;

  public LeaderboardEntry(int score, String ldap) {
    this.score = score;
    this.ldap = ldap;
  }

  public int getScore() {
    return score;
  }

  public void setScore(int score) {
    this.score = score;
  }

  public String getLdap() {
    return ldap;
  }

  @Override
  public int compareTo(LeaderboardEntry another) {
    return another.score - this.score;
  }

}
