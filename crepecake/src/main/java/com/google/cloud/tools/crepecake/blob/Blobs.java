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

package com.google.cloud.tools.crepecake.blob;

import java.io.File;
import java.io.InputStream;

/** Static initializers for {@link Blob}. */
public abstract class Blobs {

  public static Blob empty() {
    return new EmptyBlob();
  }

  public static Blob from(InputStream inputStream) {
    return new InputStreamBlob(inputStream);
  }

  public static Blob from(File file) {
    return new FileBlob(file);
  }

  public static Blob from(String content, boolean hashing) {
    return hashing ? new HashingStringBlob(content) : new StringBlob(content);
  }

  public static Blob from(BlobWriter writer) {
    return new HashingWriterBlob(writer);
  }

  private Blobs() {}
}
