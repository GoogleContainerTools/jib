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
import java.util.Arrays;
import java.util.Collection;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Tests for {@link PlatformSpec}. */
public class PlatformSpecTest {

  private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

  @Test
  public void testPlatformSpec_full() throws JsonProcessingException {
    String data =
        "architecture: amd64\n"
            + "os: linux\n"
            + "os.version: 1.0.0\n"
            + "os.features:\n"
            + "  - headless\n"
            + "variant: amd64v10\n"
            + "features:\n"
            + "  - sse4\n"
            + "  - aes\n";

    PlatformSpec parsed = mapper.readValue(data, PlatformSpec.class);
    Assert.assertEquals("amd64", parsed.getArchitecture());
    Assert.assertEquals("linux", parsed.getOs());
    Assert.assertEquals("1.0.0", parsed.getOsVersion().get());
    Assert.assertEquals(ImmutableList.of("headless"), parsed.getOsFeatures());
    Assert.assertEquals("amd64v10", parsed.getVariant().get());
    Assert.assertEquals(ImmutableList.of("sse4", "aes"), parsed.getFeatures());
  }

  @Test
  public void testPlatformSpec_osRequired() {
    String data = "architecture: amd64\n";

    try {
      mapper.readValue(data, PlatformSpec.class);
      Assert.fail();
    } catch (JsonProcessingException jpe) {
      MatcherAssert.assertThat(
          jpe.getMessage(), CoreMatchers.startsWith("Missing required creator property 'os'"));
    }
  }

  @Test
  public void testPlatformSpec_osNotNull() {
    String data = "architecture: amd64\n" + "os: null";

    try {
      mapper.readValue(data, PlatformSpec.class);
      Assert.fail();
    } catch (JsonProcessingException jpe) {
      MatcherAssert.assertThat(
          jpe.getMessage(), CoreMatchers.containsString("Property 'os' cannot be null"));
    }
  }

  @Test
  public void testPlatformSpec_osNotEmpty() {
    String data = "architecture: amd64\n" + "os: ''";

    try {
      mapper.readValue(data, PlatformSpec.class);
      Assert.fail();
    } catch (JsonProcessingException jpe) {
      MatcherAssert.assertThat(
          jpe.getMessage(), CoreMatchers.containsString("Property 'os' cannot be an empty string"));
    }
  }

  @Test
  public void testPlatformSpec_architectureRequired() {
    String data = "os: linux\n";

    try {
      mapper.readValue(data, PlatformSpec.class);
      Assert.fail();
    } catch (JsonProcessingException jpe) {
      MatcherAssert.assertThat(
          jpe.getMessage(),
          CoreMatchers.startsWith("Missing required creator property 'architecture'"));
    }
  }

  @Test
  public void testPlatformSpec_architectureNotNull() {
    String data = "architecture: null\n" + "os: linux";

    try {
      mapper.readValue(data, PlatformSpec.class);
      Assert.fail();
    } catch (JsonProcessingException jpe) {
      MatcherAssert.assertThat(
          jpe.getMessage(), CoreMatchers.containsString("Property 'architecture' cannot be null"));
    }
  }

  @Test
  public void testPlatformSpec_architectureNotEmpty() {
    String data = "architecture: ''\n" + "os: linux";

    try {
      mapper.readValue(data, PlatformSpec.class);
      Assert.fail();
    } catch (JsonProcessingException jpe) {
      MatcherAssert.assertThat(
          jpe.getMessage(),
          CoreMatchers.containsString("Property 'architecture' cannot be an empty string"));
    }
  }

  @Test
  public void testPlatformSpec_nullCollections() throws JsonProcessingException {
    String data = "architecture: amd64\n" + "os: linux\n";

    PlatformSpec parsed = mapper.readValue(data, PlatformSpec.class);
    Assert.assertEquals(ImmutableList.of(), parsed.getOsFeatures());
    Assert.assertEquals(ImmutableList.of(), parsed.getFeatures());
  }

  @RunWith(Parameterized.class)
  public static class OptionalStringTests {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
      return Arrays.asList(new Object[][] {{"os.version"}, {"variant"}});
    }

    @Parameterized.Parameter public String fieldName;

    @Test
    public void testPlatformSpec_noEmptyValues() {
      String data = "architecture: ignored\n" + "os: ignored\n" + fieldName + ": ' '";

      try {
        mapper.readValue(data, PlatformSpec.class);
        Assert.fail();
      } catch (JsonProcessingException ex) {
        Assert.assertEquals(
            "Property '" + fieldName + "' cannot be an empty string", ex.getCause().getMessage());
      }
    }

    @Test
    public void testPlatformSpec_nullOkay() throws JsonProcessingException {
      String data = "architecture: ignored\n" + "os: ignored\n" + fieldName + ": null";

      mapper.readValue(data, PlatformSpec.class);
      // pass
    }
  }

  @RunWith(Parameterized.class)
  public static class OptionalStringCollectionTests {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
      return Arrays.asList(new Object[][] {{"os.features"}, {"features"}});
    }

    @Parameterized.Parameter public String fieldName;

    @Test
    public void testPlatformSpec_noNullEntries() {
      String data = "architecture: ignored\n" + "os: ignored\n" + fieldName + ": ['first', null]";

      try {
        mapper.readValue(data, PlatformSpec.class);
        Assert.fail();
      } catch (JsonProcessingException ex) {
        Assert.assertEquals(
            "Property '" + fieldName + "' cannot contain null entries", ex.getCause().getMessage());
      }
    }

    @Test
    public void testPlatformSpec_noEmptyEntries() {
      String data = "architecture: ignored\n" + "os: ignored\n" + fieldName + ": ['first', '  ']";

      try {
        mapper.readValue(data, PlatformSpec.class);
        Assert.fail();
      } catch (JsonProcessingException ex) {
        Assert.assertEquals(
            "Property '" + fieldName + "' cannot contain empty strings",
            ex.getCause().getMessage());
      }
    }

    @Test
    public void testPlatformSpec_emptyOkay() throws JsonProcessingException {
      String data = "architecture: ignored\n" + "os: ignored\n" + fieldName + ": []";

      mapper.readValue(data, PlatformSpec.class);
      // pass
    }

    @Test
    public void testPlatformSpec_nullOkay() throws JsonProcessingException {
      String data = "architecture: ignored\n" + "os: ignored\n" + fieldName + ": null";

      mapper.readValue(data, PlatformSpec.class);
      // pass
    }
  }
}
