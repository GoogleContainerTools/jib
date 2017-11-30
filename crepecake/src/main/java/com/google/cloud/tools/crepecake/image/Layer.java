/*
 * Copyright 2017 Google Inc.
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

package com.google.cloud.tools.crepecake.image;

import com.google.cloud.tools.crepecake.blob.BlobStream;
import javax.annotation.Nullable;

/**
 * Represents a layer in an image.
 *
 * <p>A layer consists of:
 *
 * <ul>
 *   <li>Content BLOB
 *   <li>
 *       <ul>
 *         <li>The compressed archive (tarball gzip) of the partial filesystem changeset.
 *       </ul>
 *
 *   <li>Content Digest
 *   <li>
 *       <ul>
 *         <li>The SHA-256 hash of the content BLOB.
 *       </ul>
 *
 *   <li>Content Size
 *   <li>
 *       <ul>
 *         <li>The size (in bytes) of the content BLOB.
 *       </ul>
 *
 *   <li>Diff ID
 *   <li>
 *       <ul>
 *         <li>The SHA-256 hash of the uncompressed archive (tarball) of the partial filesystem
 *             changeset.
 *       </ul>
 *
 * </ul>
 */
public class Layer {

  @Nullable private final BlobStream content;

  private final Digest digest;
  private final int size;

  /** The digest of the uncompressed layer content. */
  private final Digest diffId;

  /**
   * Instantiate a layer without the content BLOB. This is to work with layer references that don't
   * require the actual layer itself.
   */
  public Layer(Digest digest, int size, Digest diffId) {
    this(digest, size, diffId, null);
  }

  /**
   * Instantiate a layer with the content BLOB. This is for representing a full layer where use of
   * its content BLOB is expected.
   */
  public Layer(Digest digest, int size, Digest diffId, BlobStream content) {
    this.digest = digest;
    this.size = size;
    this.diffId = diffId;
    this.content = content;
  }

  public boolean hasContent() {
    return content != null;
  }

  public Digest getDigest() {
    return digest;
  }

  public int getSize() {
    return size;
  }

  public BlobStream getContent() {
    return content;
  }
}
