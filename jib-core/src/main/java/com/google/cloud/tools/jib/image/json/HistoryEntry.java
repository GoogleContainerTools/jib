/*
 * Copyright 2018 Google LLC.
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
import java.time.Instant;
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

  public static class Builder {

    @Nullable private Instant creationTimestamp;
    @Nullable private String author;
    @Nullable private String createdBy;
    @Nullable private String comment;
    @Nullable private Boolean emptyLayer;

    public Builder setCreationTimestamp(Instant creationTimestamp) {
      this.creationTimestamp = creationTimestamp;
      return this;
    }

    public Builder setAuthor(String author) {
      this.author = author;
      return this;
    }

    public Builder setCreatedBy(String createdBy) {
      this.createdBy = createdBy;
      return this;
    }

    public Builder setComment(String comment) {
      this.comment = comment;
      return this;
    }

    public Builder setEmptyLayer(Boolean emptyLayer) {
      this.emptyLayer = emptyLayer;
      return this;
    }

    public HistoryEntry build() {
      return new HistoryEntry(
          creationTimestamp == null ? null : creationTimestamp.toString(),
          author,
          createdBy,
          comment,
          emptyLayer);
    }

    private Builder() {}
  }

  /**
   * Creates a builder for a {@link HistoryEntry}.
   *
   * @return the builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /** The ISO-8601 formatted timestamp at which the image was created. */
  @JsonProperty("created")
  @Nullable
  private String creationTimestamp;

  /** The name of the author specified when committing the image. */
  @JsonProperty("author")
  @Nullable
  private String author;

  /** The command used to build the layer. */
  @JsonProperty("created_by")
  @Nullable
  private String createdBy;

  /** A custom message set when creating the layer. */
  @JsonProperty("comment")
  @Nullable
  private String comment;

  /**
   * Whether or not the entry corresponds to a layer in the container ({@code @Nullable Boolean} to
   * make field optional).
   */
  @JsonProperty("empty_layer")
  @Nullable
  private Boolean emptyLayer;

  public HistoryEntry() {}

  private HistoryEntry(
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
  public boolean hasCorrespondingLayer() {
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

  @Override
  public String toString() {
    return createdBy == null ? "" : createdBy;
  }
}
