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

/** Static initializers for {@link BlobStream}. */
public class BlobStreams {

  public static BlobStream empty() {
    return new EmptyBlobStream();
  }

  public static BlobStream from(InputStream inputStream) {
    return new InputStreamBlobStream(inputStream);
  }

  public static BlobStream from(File file) {
    return new FileBlobStream(file);
  }

  public static BlobStream from(String content, boolean hashing) {
    return hashing ? new HashingStringBlobStream(content) : new StringBlobStream(content);
  }

  public static BlobStream from(BlobStreamWriter writer) {
    return new HashingWriterBlobStream(writer);
  }
}
