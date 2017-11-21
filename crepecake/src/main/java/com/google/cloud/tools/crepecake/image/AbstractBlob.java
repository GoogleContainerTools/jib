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

import java.util.Optional;

/** Skeletal implementation for {@link Blob} interface. */
abstract class AbstractBlob implements Blob {

  private final Optional<BlobStream> content;
  private final Digest digest;
  private final int size;

  protected AbstractBlob(Digest digest, int size) {
    this.digest = digest;
    this.size = size;
    this.content = Optional.empty();
  }

  protected AbstractBlob(Digest digest, int size, BlobStream content) {
    this.digest = digest;
    this.size = size;
    this.content = Optional.of(content);
  }

  public boolean hasContent() {
    return content.isPresent();
  }

  public String getHash() {
    return digest.getHash();
  }

  public Digest getDigest() {
    return digest;
  }

  public int getSize() {
    return size;
  }

  public BlobStream getContent() {
    return content.get();
  }
}
