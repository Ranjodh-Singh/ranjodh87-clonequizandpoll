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
 * Data model for answer for the question
 */
public class Answer implements Serializable, Comparable<Answer> {
  private final String answerText;
  private final boolean correct;
  private final int number;
  private boolean answered;

  public Answer(String answerText, boolean correct, int number) {
    this.answerText = answerText;
    this.correct = correct;
    this.number = number;
  }

  public String getAnswerText() {
    return answerText;
  }

  public boolean isCorrect() {
    return correct;
  }

  public int getNumber() {
    return number;
  }

  public boolean isAnswered() {
    return answered;
  }

  public void setAnswered(boolean answered) {
    this.answered = answered;
  }

  @Override
  public int compareTo(Answer another) {
    return this.number - another.number;
  }
}
