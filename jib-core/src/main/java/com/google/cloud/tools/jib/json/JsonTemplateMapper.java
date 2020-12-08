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

package com.google.cloud.tools.jib.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

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
 *   <li>Does not serialize fields that are {@code null}.
 * </ul>
 *
 * {@code @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)}
 *
 * <ul>
 *   <li>Fields that are private are also accessible for serialization/deserialization.
 * </ul>
 *
 * @see <a href="https://github.com/FasterXML/jackson">https://github.com/FasterXML/jackson</a>
 */
public class JsonTemplateMapper {

  private static final ObjectMapper objectMapper = createObjectMapper();

  private static ObjectMapper createObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    return mapper;
  }

  /**
   * Deserializes a JSON file via a JSON object template.
   *
   * @param <T> child type of {@link JsonTemplate}
   * @param jsonFile a file containing a JSON string
   * @param templateClass the template to deserialize the string to
   * @return the template filled with the values parsed from {@code jsonFile}
   * @throws IOException if an error occurred during reading the file or parsing the JSON
   */
  public static <T extends JsonTemplate> T readJsonFromFile(Path jsonFile, Class<T> templateClass)
      throws IOException {
    try (InputStream fileIn = Files.newInputStream(jsonFile)) {
      return objectMapper.readValue(fileIn, templateClass);
    }
  }

  /**
   * Deserializes a JSON file via a JSON object template with a shared lock on the file.
   *
   * @param <T> child type of {@link JsonTemplate}
   * @param jsonFile a file containing a JSON string
   * @param templateClass the template to deserialize the string to
   * @return the template filled with the values parsed from {@code jsonFile}
   * @throws IOException if an error occurred during reading the file or parsing the JSON
   */
  public static <T extends JsonTemplate> T readJsonFromFileWithLock(
      Path jsonFile, Class<T> templateClass) throws IOException {
    // channel is closed by inputStream.close()
    FileChannel channel = FileChannel.open(jsonFile, StandardOpenOption.READ);
    channel.lock(0, Long.MAX_VALUE, true); // shared lock, released by channel close
    try (InputStream inputStream = Channels.newInputStream(channel)) {
      return objectMapper.readValue(inputStream, templateClass);
    }
  }

  /**
   * Deserializes a JSON object from a JSON input stream.
   *
   * @param <T> child type of {@link JsonTemplate}
   * @param jsonStream input stream
   * @param templateClass the template to deserialize the string to
   * @return the template filled with the values parsed from {@code jsonString}
   * @throws IOException if an error occurred during parsing the JSON
   */
  public static <T extends JsonTemplate> T readJson(InputStream jsonStream, Class<T> templateClass)
      throws IOException {
    return objectMapper.readValue(jsonStream, templateClass);
  }

  /**
   * Deserializes a JSON object from a JSON string.
   *
   * @param <T> child type of {@link JsonTemplate}
   * @param jsonString a JSON string
   * @param templateClass the template to deserialize the string to
   * @return the template filled with the values parsed from {@code jsonString}
   * @throws IOException if an error occurred during parsing the JSON
   */
  public static <T extends JsonTemplate> T readJson(String jsonString, Class<T> templateClass)
      throws IOException {
    return objectMapper.readValue(jsonString, templateClass);
  }

  /**
   * Deserializes a JSON object from a JSON byte array.
   *
   * @param <T> child type of {@link JsonTemplate}
   * @param jsonBytes a JSON byte array
   * @param templateClass the template to deserialize the string to
   * @return the template filled with the values parsed from {@code jsonBytes}
   * @throws IOException if an error occurred during parsing the JSON
   */
  public static <T extends JsonTemplate> T readJson(byte[] jsonBytes, Class<T> templateClass)
      throws IOException {
    return objectMapper.readValue(jsonBytes, templateClass);
  }

  /**
   * Deserializes a JSON object list from a JSON string.
   *
   * @param <T> child type of {@link JsonTemplate}
   * @param jsonString a JSON string
   * @param templateClass the template to deserialize the string to
   * @return the template filled with the values parsed from {@code jsonString}
   * @throws IOException if an error occurred during parsing the JSON
   */
  public static <T extends JsonTemplate> List<T> readListOfJson(
      String jsonString, Class<T> templateClass) throws IOException {
    CollectionType listType =
        objectMapper.getTypeFactory().constructCollectionType(List.class, templateClass);
    return objectMapper.readValue(jsonString, listType);
  }

  public static String toUtf8String(JsonTemplate template) throws IOException {
    return toUtf8String((Object) template);
  }

  public static String toUtf8String(List<? extends JsonTemplate> templates) throws IOException {
    return toUtf8String((Object) templates);
  }

  public static byte[] toByteArray(JsonTemplate template) throws IOException {
    return toByteArray((Object) template);
  }

  public static byte[] toByteArray(List<? extends JsonTemplate> templates) throws IOException {
    return toByteArray((Object) templates);
  }

  public static void writeTo(JsonTemplate template, OutputStream out) throws IOException {
    writeTo((Object) template, out);
  }

  public static void writeTo(List<? extends JsonTemplate> templates, OutputStream out)
      throws IOException {
    writeTo((Object) templates, out);
  }

  private static String toUtf8String(Object template) throws IOException {
    return new String(toByteArray(template), StandardCharsets.UTF_8);
  }

  private static byte[] toByteArray(Object template) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    writeTo(template, out);
    return out.toByteArray();
  }

  private static void writeTo(Object template, OutputStream out) throws IOException {
    objectMapper.writeValue(out, template);
  }

  private JsonTemplateMapper() {}
}
