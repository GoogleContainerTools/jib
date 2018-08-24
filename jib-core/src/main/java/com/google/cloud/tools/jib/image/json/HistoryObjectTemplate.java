/*
 * Copyright 2018 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.tools.jib.image.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.cloud.tools.jib.json.JsonTemplate;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HistoryObjectTemplate implements JsonTemplate {

  /** The name of the author specified when committing the image. */
  private String author;

  /** The timestamp at which the image was created. */
  private String created;

  /** The command used while building the image. */
  private String created_by;

  /** Whether or not the layer is empty */
  private boolean empty_layer;

  public HistoryObjectTemplate() {
    this("Jib", "1970-01-01T00:00:00Z", "jib");
  }

  public HistoryObjectTemplate(String author, String created, String createdBy) {
    this.author = author;
    this.created = created;
    this.created_by = createdBy;
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof HistoryObjectTemplate) {
      HistoryObjectTemplate otherHistory = (HistoryObjectTemplate) other;
      return otherHistory.author.equals(author)
          && otherHistory.created.equals(created)
          && otherHistory.created_by.equals(created_by)
          && otherHistory.empty_layer == empty_layer;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(author, created, created_by, empty_layer);
  }
}
