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
import java.util.List;

/**
 * Data model for individual quiz. Contains questions and metadata.
 */
public class Quiz implements Serializable {
  private final String title;
  private final String description;
  private final String image;
  private final String leaderboardSheet;
  private final String statisticsSheet;
  private final String documentId;
  private int score;
  private List<Question> questions;

  public Quiz(String title, String description, String image, List<Question> questions,
      String leaderboardWorksheet,
      String documentId,
      String statisticsSheet) {
    this.title = title;
    this.description = description;
    this.questions = questions;
    this.image = image;
    this.leaderboardSheet = leaderboardWorksheet;
    this.documentId = documentId;
    this.statisticsSheet = statisticsSheet;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public String getImage() {
    return image;
  }

  public List<Question> getQuestions() {
    // Can be modified, because I perform filtering and randomizing of questions
    // later
    return questions;
  }

  public void setQuestions(List<Question> questions) {
    this.questions = questions;
  }

  public int getScore() {
    return score;
  }

  public void setScore(int score) {
    this.score = score;
  }

  public String getLeaderboardSheet() {
    return leaderboardSheet;
  }

  public String getDocumentId() {
    return documentId;
  }

  public String getStatisticsSheet() {
    return statisticsSheet;
  }

}
