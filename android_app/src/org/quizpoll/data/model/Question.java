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
 * Data model for question in the quiz
 */
public class Question implements Serializable {
  private final String questionText;
  private final List<Answer> answers;
  private final int number;
  private boolean success;
  private boolean anonymous; // Just for polling

  /**
   * Multiple choice is select 1 of X, Multiple select is Y of X
   */
  public enum QuestionType {
    SINGLE_CHOICE, MULTIPLE_CHOICE
  }

  public String getQuestionText() {
    return questionText;
  }

  public List<Answer> getAnswers() {
    // Can be modified, because I perform randomizing answers later
    return answers;
  }

  /**
   * Determines question type based on number of correct answers
   */
  public QuestionType getType() {
    int correctAnswers = 0;
    for (Answer answer : answers) {
      if (answer.isCorrect()) {
        correctAnswers++;
      }
    }
    if (correctAnswers == 1) {
      return QuestionType.SINGLE_CHOICE;
    } else {
      return QuestionType.MULTIPLE_CHOICE;
    }
  }

  public Question(String questionText, List<Answer> answers, int number) {
    this.questionText = questionText;
    this.answers = answers;
    this.number = number;
    this.anonymous = false;
  }

  public int getNumber() {
    return number;
  }

  public boolean isSuccess() {
    return success;
  }

  public void setSuccess(boolean success) {
    this.success = success;
  }

  public boolean isAnonymous() {
    return anonymous;
  }

  public void setAnonymous(boolean anonymous) {
    this.anonymous = anonymous;
  }

}
