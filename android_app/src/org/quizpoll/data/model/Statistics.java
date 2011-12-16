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

import java.util.List;

/**
 * Data model for statistics related to question
 */
public class Statistics {
  private String questionText;
  private int successes;
  private int failures;
  private List<Integer> answerCounts;

  public int getSuccesses() {
    return successes;
  }

  public void setSuccesses(int successes) {
    this.successes = successes;
  }

  public int getFailures() {
    return failures;
  }

  public void setFailures(int failures) {
    this.failures = failures;
  }

  public List<Integer> getAnswerCounts() {
    return answerCounts;
  }

  public Statistics(int successes, int failures, List<Integer> answerCounts, String questionText) {
    this.successes = successes;
    this.failures = failures;
    this.answerCounts = answerCounts;
    this.questionText = questionText;
  }

  public String getQuestionText() {
    return questionText;
  }

  public void setQuestionText(String questionText) {
    this.questionText = questionText;
  }
}
