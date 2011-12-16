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
 * Data model for quizzes from Google Docs List API. It's used both for
 * collections and spreadsheets (quizzes)
 */
public class DocsEntry implements Serializable, Comparable<DocsEntry> {
  /**
   * Type of doc entry
   */
  public static final int COLLECTION = 0;
  public static final int QUIZ = 1;

  private int type;
  private String title;
  private String id;

  public DocsEntry(int type, String title, String id) {
    this.type = type;
    this.title = title;
    this.id = id;
  }

  public int getType() {
    return type;
  }

  public String getTitle() {
    return title;
  }

  public String getId() {
    return id;
  }

  @Override
  public int compareTo(DocsEntry another) {
    // Collections first, then sort alphabetically
    if (this.getType() == another.getType()) {
      return this.getTitle().compareTo(another.getTitle());
    } else if (this.getType() == QUIZ && another.getType() == COLLECTION) {
      return 1;
    } else {
      return -1;
    }
  }
}
