/*
 * Copyright 2017 Google LLC.
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

package com.google.cloud.tools.jib.blob;

import com.google.cloud.tools.jib.hash.WritableContents;
import com.google.cloud.tools.jib.json.JsonTemplate;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** Static methods for {@link Blob}. */
public class Blobs {

  public static Blob from(InputStream inputStream) {
    return new InputStreamBlob(inputStream);
  }

  public static Blob from(Path file) {
    return new FileBlob(file);
  }

  public static Blob from(JsonTemplate template) {
    return new JsonBlob(template);
  }

  /**
   * Creates a {@link StringBlob} with UTF-8 encoding.
   *
   * @param content the string to create the blob from
   * @return the {@link StringBlob}
   */
  public static Blob from(String content) {
    return new StringBlob(content);
  }

  public static Blob from(WritableContents writable) {
    return from(writable, false);
  }

  public static Blob from(WritableContents writable, boolean retryable) {
    return new WritableContentsBlob(writable, retryable);
  }

  /**
   * Writes the BLOB to a string with UTF-8 decoding.
   *
   * @param blob the BLOB to write
   * @return the BLOB contents as a string
   * @throws IOException if writing out the BLOB contents fails
   */
  public static String writeToString(Blob blob) throws IOException {
    return new String(writeToByteArray(blob), StandardCharsets.UTF_8);
  }

  /**
   * Writes the BLOB to a byte array.
   *
   * @param blob the BLOB to write
   * @return the BLOB contents as a byte array
   * @throws IOException if writing out the BLOB contents fails
   */
  public static byte[] writeToByteArray(Blob blob) throws IOException {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    blob.writeTo(byteArrayOutputStream);
    return byteArrayOutputStream.toByteArray();
  }

  private Blobs() {}
}
