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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import java.io.IOException;

/** Serializes a {@link DescriptorDigest} into JSON element. */
public class DescriptorDigestSerializer extends JsonSerializer<DescriptorDigest> {

  @Override
  public void serialize(
      DescriptorDigest value, JsonGenerator jsonGenerator, SerializerProvider ignored)
      throws IOException {
    jsonGenerator.writeString(value.toString());
  }
}
