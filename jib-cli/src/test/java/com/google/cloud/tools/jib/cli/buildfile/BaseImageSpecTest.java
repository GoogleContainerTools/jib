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
import com.google.common.collect.ImmutableList;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

/** Tests for {@link BaseImageSpec}. */
class BaseImageSpecTest {

  private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

  @Test
  void testBaseImageSpec_full() throws JsonProcessingException {
    String data =
        "image: gcr.io/example/jib\n"
            + "platforms:\n" // trivial platform spec
            + "  - architecture: amd64\n"
            + "    os: linux\n";

    BaseImageSpec baseImageSpec = mapper.readValue(data, BaseImageSpec.class);
    Assert.assertEquals("gcr.io/example/jib", baseImageSpec.getImage());
    Assert.assertEquals("amd64", baseImageSpec.getPlatforms().get(0).getArchitecture());
    Assert.assertEquals("linux", baseImageSpec.getPlatforms().get(0).getOs());
  }

  @Test
  void testBaseImageSpec_imageRequired() {
    String data =
        "platforms:\n" // trivial platform spec
            + "  - architecture: amd64\n"
            + "    os: linux\n";
    try {
      mapper.readValue(data, BaseImageSpec.class);
      Assert.fail();
    } catch (JsonProcessingException jpe) {
      MatcherAssert.assertThat(
          jpe.getMessage(), CoreMatchers.startsWith("Missing required creator property 'image'"));
    }
  }

  @Test
  void testBaseImageSpec_imageNotNull() {
    String data =
        "image: null\n"
            + "platforms:\n" // trivial platform spec
            + "  - architecture: amd64\n"
            + "    os: linux\n";
    try {
      mapper.readValue(data, BaseImageSpec.class);
      Assert.fail();
    } catch (JsonProcessingException jpe) {
      MatcherAssert.assertThat(
          jpe.getMessage(), CoreMatchers.containsString("Property 'image' cannot be null"));
    }
  }

  @Test
  void testBaseImageSpec_imageNotEmpty() {
    String data =
        "image: ''\n"
            + "platforms:\n" // trivial platform spec
            + "  - architecture: amd64\n"
            + "    os: linux\n";
    try {
      mapper.readValue(data, BaseImageSpec.class);
      Assert.fail();
    } catch (JsonProcessingException jpe) {
      MatcherAssert.assertThat(
          jpe.getMessage(),
          CoreMatchers.containsString("Property 'image' cannot be an empty string"));
    }
  }

  @Test
  void testBaseImageSpec_nullCollections() throws JsonProcessingException {
    String data = "image: gcr.io/example/jib\n";

    BaseImageSpec baseImageSpec = mapper.readValue(data, BaseImageSpec.class);
    Assert.assertEquals(ImmutableList.of(), baseImageSpec.getPlatforms());
  }

  @Test
  void testBaseImageSpec_platformsNoNullEntries() {
    String data = "image: gcr.io/example/jib\n" + "platforms: [null]\n";

    try {
      mapper.readValue(data, BaseImageSpec.class);
      Assert.fail();
    } catch (JsonProcessingException jpe) {
      MatcherAssert.assertThat(
          jpe.getMessage(),
          CoreMatchers.containsString("Property 'platforms' cannot contain null entries"));
    }
  }
}
