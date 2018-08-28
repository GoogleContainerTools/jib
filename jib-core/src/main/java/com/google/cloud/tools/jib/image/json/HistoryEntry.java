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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.cloud.tools.jib.json.JsonTemplate;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Represents an item in the container configuration's {@code history} list.
 *
 * @see <a href=https://github.com/opencontainers/image-spec/blob/master/config.md#properties>OCI
 *     image spec ({@code history} field)</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class HistoryEntry implements JsonTemplate {

  /** The RFC 3339 formatted timestamp at which the image was created. */
  @JsonProperty("created")
  @Nullable
  private String creationTimestamp;

  /** The name of the author specified when committing the image. */
  @JsonProperty("author")
  @Nullable
  private String author;

  /** The command used to build the image. */
  @JsonProperty("created_by")
  @Nullable
  private String createdBy;

  /** A custom message set when creating the layer. */
  @JsonProperty("comment")
  @Nullable
  private String comment;

  /**
   * Whether or not the entry corresponds to an empty layer ({@code @Nullable Boolean} to make field
   * optional).
   */
  @JsonProperty("empty_layer")
  @Nullable
  private Boolean emptyLayer;

  public HistoryEntry() {}

  public HistoryEntry(
      @Nullable String creationTimestamp,
      @Nullable String author,
      @Nullable String createdBy,
      @Nullable String comment,
      @Nullable Boolean emptyLayer) {
    this.author = author;
    this.creationTimestamp = creationTimestamp;
    this.createdBy = createdBy;
    this.comment = comment;
    this.emptyLayer = emptyLayer;
  }

  /**
   * Returns whether or not the history object corresponds to a layer in the container.
   *
   * @return {@code true} if the history object corresponds to a layer in the container
   */
  @JsonIgnore
  public boolean hasLayer() {
    return emptyLayer == null ? false : emptyLayer;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other instanceof HistoryEntry) {
      HistoryEntry otherHistory = (HistoryEntry) other;
      return Objects.equals(otherHistory.creationTimestamp, creationTimestamp)
          && Objects.equals(otherHistory.author, author)
          && Objects.equals(otherHistory.createdBy, createdBy)
          && Objects.equals(otherHistory.comment, comment)
          && Objects.equals(otherHistory.emptyLayer, emptyLayer);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(author, creationTimestamp, createdBy, comment, emptyLayer);
  }
}
