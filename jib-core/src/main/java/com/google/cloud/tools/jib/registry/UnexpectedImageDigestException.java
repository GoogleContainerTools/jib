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

package com.google.cloud.tools.jib.registry;

import com.google.cloud.tools.jib.image.DescriptorDigest;
import java.util.List;
import java.util.StringJoiner;

/**
 * When an image is pushed, the registry responds with a digest of the pushed image. This exception
 * indicates that the received digest is not the expected digest of the image.
 */
public class UnexpectedImageDigestException extends RegistryException {

  private static String makeMessage(DescriptorDigest expectedDigest, List<String> receivedDigests) {
    StringJoiner message =
        new StringJoiner(", ", "Expected image digest " + expectedDigest + ", but received: ", "");
    for (String receivedDigest : receivedDigests) {
      message.add(receivedDigest);
    }
    return message.toString();
  }

  UnexpectedImageDigestException(DescriptorDigest expectedDigest, List<String> receivedDigests) {
    super(makeMessage(expectedDigest, receivedDigests));
  }
}
