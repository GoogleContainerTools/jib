/*
 * Copyright 2020 Google LLC.
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

package com.google.cloud.tools.jib.cli.buildfile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import java.nio.file.Paths;
import java.time.Instant;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

/** Tests for {@link FileLayerSpec}. */
class FileLayerSpecTest {

  private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

  @Test
  void testFileLayerSpec_full() throws JsonProcessingException {
    String data =
        "name: layer name\n"
            + "files:\n" // trivial copy spec
            + "  - src: source\n"
            + "    dest: /dest\n"
            + "properties:\n" // trivial file properties spec
            + "  timestamp: 1\n";

    FileLayerSpec parsed = mapper.readValue(data, FileLayerSpec.class);
    Assert.assertEquals("layer name", parsed.getName());
    Assert.assertEquals(Paths.get("source"), parsed.getFiles().get(0).getSrc());
    Assert.assertEquals(AbsoluteUnixPath.get("/dest"), parsed.getFiles().get(0).getDest());
    Assert.assertEquals(Instant.ofEpochMilli(1), parsed.getProperties().get().getTimestamp().get());
  }

  @Test
  void testFileLayerSpec_nameRequired() {
    String data = "files:\n" + "  - src: source\n" + "    dest: /dest\n";

    try {
      mapper.readValue(data, FileLayerSpec.class);
      Assert.fail();
    } catch (JsonProcessingException jpe) {
      MatcherAssert.assertThat(
          jpe.getMessage(), CoreMatchers.startsWith("Missing required creator property 'name'"));
    }
  }

  @Test
  void testFileLayerSpec_nameNotNull() {
    String data = "name: null\n" + "files:\n" + "  - src: source\n" + "    dest: /dest\n";

    try {
      mapper.readValue(data, FileLayerSpec.class);
      Assert.fail();
    } catch (JsonProcessingException jpe) {
      MatcherAssert.assertThat(
          jpe.getMessage(), CoreMatchers.containsString("Property 'name' cannot be null"));
    }
  }

  @Test
  void testFileLayerSpec_nameNotEmpty() {
    String data = "name: ''\n" + "files:\n" + "  - src: source\n" + "    dest: /dest\n";

    try {
      mapper.readValue(data, FileLayerSpec.class);
      Assert.fail();
    } catch (JsonProcessingException jpe) {
      MatcherAssert.assertThat(
          jpe.getMessage(),
          CoreMatchers.containsString("Property 'name' cannot be an empty string"));
    }
  }

  @Test
  void testFileLayerSpec_filesRequired() {
    String data = "name: layer name";

    try {
      mapper.readValue(data, FileLayerSpec.class);
      Assert.fail();
    } catch (JsonProcessingException jpe) {
      MatcherAssert.assertThat(
          jpe.getMessage(), CoreMatchers.startsWith("Missing required creator property 'files'"));
    }
  }

  @Test
  void testFileLayerSpec_filesNotNull() {
    String data = "name: layer name\n" + "files: null";

    try {
      mapper.readValue(data, FileLayerSpec.class);
      Assert.fail();
    } catch (JsonProcessingException jpe) {
      MatcherAssert.assertThat(
          jpe.getMessage(), CoreMatchers.containsString("Property 'files' cannot be null"));
    }
  }

  @Test
  void testFileLayerSpec_filesNotEmpty() {
    String data = "name: layer name\n" + "files: []\n";

    try {
      mapper.readValue(data, FileLayerSpec.class);
      Assert.fail();
    } catch (JsonProcessingException jpe) {
      MatcherAssert.assertThat(
          jpe.getMessage(),
          CoreMatchers.containsString("Property 'files' cannot be an empty collection"));
    }
  }
}
