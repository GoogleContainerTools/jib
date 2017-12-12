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

import java.io.OutputStream;

/**
 * An empty {@link Blob}. This is used, for example, to send an HTTP request with an empty body
 * without having to pass {@code null} for the body {@link Blob}.
 */
class EmptyBlob implements Blob {

  @Override
  public BlobDescriptor writeTo(OutputStream outputStream) {
    return new BlobDescriptor(0);
  }
}
