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

package com.google.cloud.tools.crepecake.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.OutputStream;

// TODO: Add JsonFactory for HTTP response parsing.
/**
 * Helper class for serializing and deserializing JSON.
 *
 * <p>The interface uses Jackson as the JSON parser. Some useful annotations to include on classes
 * used as templates for JSON are:
 *
 * <p>{@code @JsonInclude(JsonInclude.Include.NON_NULL)}
 *
 * <ul>
 *   <li> Does not serialize fields that are {@code null}.
 * </ul>
 *
 * {@code @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)}
 *
 * <ul>
 *   <li> Fields that are private are also accessible for serialization/deserialization.
 * </ul>
 *
 * @see <a href="https://github.com/FasterXML/jackson">https://github.com/FasterXML/jackson</a>
 */
public class JsonHelper {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Serializes a JSON object into a JSON string.
   *
   * @param outputStream the {@link OutputStream} to write to
   * @param source the JSON object to serialize
   */
  public static void writeJson(OutputStream outputStream, JsonTemplate source) throws IOException {
    objectMapper.writeValue(outputStream, source);
  }
}
