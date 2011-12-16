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


/**
 * Data model for poll response. It is sent to server after response of student.
 */
public class PollResponse {

  private final String documentId;
  private final String sheetId;
  private final boolean anonymous;
  private final int questionNumber;
  private final String answers;
  private final boolean success;

  public PollResponse(String documentId, String sheetId, boolean anonymous, int questionNumber,
      String answers, boolean success) {
    super();
    this.documentId = documentId;
    this.sheetId = sheetId;
    this.anonymous = anonymous;
    this.questionNumber = questionNumber;
    this.answers = answers;
    this.success = success;
  }

  public String getDocumentId() {
    return documentId;
  }

  public String getSheetId() {
    return sheetId;
  }

  public boolean isAnonymous() {
    return anonymous;
  }

  public int getQuestionNumber() {
    return questionNumber;
  }

  public String getAnswers() {
    return answers;
  }

  public boolean isSuccess() {
    return success;
  }

}
