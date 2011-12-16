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

import org.quizpoll.util.Utils;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Data model for polling session. Contains questions and metadata.
 */
public class Poll implements Serializable {

  // Special values for currentQuestion
  public static final int WAITING_FOR_INSTRUCTOR = -1;
  public static final int CLOSED = -2;
  public static final int UNKNOWN = -3;

  private final String title;
  private final String internalDataSheet;
  private final String responsesSheet;
  private final String documentId;
  private final List<Question> questions;
  private int currentQuestion;

  public Poll(String title, List<Question> questions, String internalDataWorksheet,
      String responsesWorksheet, String spreadsheetId) {
    this.internalDataSheet = internalDataWorksheet;
    this.responsesSheet = responsesWorksheet;
    this.title = title;
    this.questions = questions;
    this.documentId = spreadsheetId;
    this.currentQuestion = UNKNOWN;
  }

  public String getTitle() {
    return Utils.formatPollingName(title);
  }

  public List<Question> getQuestions() {
    return Collections.unmodifiableList(questions);
  }

  public String getDocumentId() {
    return documentId;
  }

  public String getInternalDataSheet() {
    return internalDataSheet;
  }

  public String getResponsesSheet() {
    return responsesSheet;
  }

  public int getCurrentQuestion() {
    return currentQuestion;
  }

  public void setCurrentQuestion(int currentQuestion) {
    this.currentQuestion = currentQuestion;
  }
}
