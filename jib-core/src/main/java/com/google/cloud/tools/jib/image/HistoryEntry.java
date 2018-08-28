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

package com.google.cloud.tools.jib.image;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.cloud.tools.jib.json.JsonTemplate;
import java.util.Objects;
import javax.annotation.Nullable;

/** Represents an item in the container configuration's {@code history} list. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class HistoryEntry implements JsonTemplate {

  /** The timestamp at which the image was created. */
  private String created;

  /** The name of the author specified when committing the image. */
  @Nullable private String author;

  /** The command used while building the image. */
  @JsonProperty("created_by")
  @Nullable
  private String createdBy;

  /** Whether or not the layer is empty ({@code @Nullable Boolean} to make field optional). */
  @JsonProperty("empty_layer")
  @Nullable
  private Boolean emptyLayer;

  public HistoryEntry() {
    this("1970-01-01T00:00:00Z", null, null, null);
  }

  public HistoryEntry(
      String created,
      @Nullable String author,
      @Nullable String createdBy,
      @Nullable Boolean emptyLayer) {
    this.author = author;
    this.created = created;
    this.createdBy = createdBy;
    this.emptyLayer = emptyLayer;
  }

  /**
   * Returns whether or not the history object corresponds to an empty layer.
   *
   * @return {@code true} if the history object corresponds to an empty layer
   */
  @JsonIgnore
  public boolean isEmptyLayer() {
    return emptyLayer == null ? false : emptyLayer;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other instanceof HistoryEntry) {
      HistoryEntry otherHistory = (HistoryEntry) other;
      return otherHistory.created.equals(created)
          && Objects.equals(otherHistory.author, author)
          && Objects.equals(otherHistory.createdBy, createdBy)
          && Objects.equals(otherHistory.emptyLayer, emptyLayer);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(author, created, createdBy, emptyLayer);
  }
}
