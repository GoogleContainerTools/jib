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

package com.google.cloud.tools.jib.image.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import java.io.IOException;
import java.security.DigestException;

/** Deserializes a JSON element into a {@link DescriptorDigest} object. */
public class DescriptorDigestDeserializer extends JsonDeserializer<DescriptorDigest> {

  @Override
  public DescriptorDigest deserialize(JsonParser jsonParser, DeserializationContext ignored)
      throws IOException {
    try {
      return DescriptorDigest.fromDigest(jsonParser.getValueAsString());
    } catch (DigestException ex) {
      throw new IOException(ex);
    }
  }
}
