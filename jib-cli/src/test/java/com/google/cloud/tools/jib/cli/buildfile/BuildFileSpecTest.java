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
import com.google.cloud.tools.jib.api.Ports;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.ImageFormat;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Tests for {@link BuildFileSpec}. */
public class BuildFileSpecTest {

  private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

  @Test
  public void testBuildFileSpec_full() throws JsonProcessingException {
    String data =
        "apiVersion: v1alpha1\n"
            + "kind: BuildFile\n"
            + "from:\n" // trivial base image spec
            + "  image: gcr.io/example/jib\n"
            + "creationTime: 1\n"
            + "format: OCI\n"
            + "environment:\n"
            + "  env_key: env_value\n"
            + "labels:\n"
            + "  label_key: label_value\n"
            + "volumes:\n"
            + "   - /my/volume\n"
            + "exposedPorts:\n"
            + "  - 8080\n"
            + "user: username\n"
            + "workingDirectory: /workspace\n"
            + "entrypoint:\n"
            + "  - java\n"
            + "  - -jar\n"
            + "cmd:\n"
            + "  - myjar.jar\n"
            + "layers:\n" // trivial layers
            + "  entries:\n"
            + "    - name: some layer\n"
            + "      archive: /something.tgz\n";

    BuildFileSpec parsed = mapper.readValue(data, BuildFileSpec.class);
    Assert.assertEquals("v1alpha1", parsed.getApiVersion());
    Assert.assertEquals("BuildFile", parsed.getKind());
    Assert.assertEquals("gcr.io/example/jib", parsed.getFrom().get().getImage());
    Assert.assertEquals(Instant.ofEpochMilli(1), parsed.getCreationTime().get());
    Assert.assertEquals(ImageFormat.OCI, parsed.getFormat().get());
    Assert.assertEquals(ImmutableMap.of("env_key", "env_value"), parsed.getEnvironment());
    Assert.assertEquals(ImmutableMap.of("label_key", "label_value"), parsed.getLabels());
    Assert.assertEquals(ImmutableSet.of(AbsoluteUnixPath.get("/my/volume")), parsed.getVolumes());
    Assert.assertEquals(Ports.parse(ImmutableList.of("8080")), parsed.getExposedPorts());
    Assert.assertEquals("username", parsed.getUser().get());
    Assert.assertEquals(AbsoluteUnixPath.get("/workspace"), parsed.getWorkingDirectory().get());
    Assert.assertEquals(ImmutableList.of("java", "-jar"), parsed.getEntrypoint().get());
    Assert.assertEquals(ImmutableList.of("myjar.jar"), parsed.getCmd().get());
    Assert.assertEquals(
        "some layer", ((ArchiveLayerSpec) parsed.getLayers().get().getEntries().get(0)).getName());
    Assert.assertEquals(
        Paths.get("/something.tgz"),
        ((ArchiveLayerSpec) parsed.getLayers().get().getEntries().get(0)).getArchive());
  }

  @Test
  public void testBuildFileSpec_apiVersionRequired() {
    String data = "kind: BuildFile\n";

    try {
      mapper.readValue(data, BuildFileSpec.class);
      Assert.fail();
    } catch (JsonProcessingException jpe) {
      MatcherAssert.assertThat(
          jpe.getMessage(),
          CoreMatchers.startsWith("Missing required creator property 'apiVersion'"));
    }
  }

  @Test
  public void testBuildFileSpec_apiVersionNotNull() {
    String data = "apiVersion: null\n" + "kind: BuildFile\n";

    try {
      mapper.readValue(data, BuildFileSpec.class);
      Assert.fail();
    } catch (JsonProcessingException jpe) {
      MatcherAssert.assertThat(
          jpe.getMessage(), CoreMatchers.containsString("Property 'apiVersion' cannot be null"));
    }
  }

  @Test
  public void testBuildFileSpec_apiVersionNotEmpty() {
    String data = "apiVersion: ''\n" + "kind: BuildFile\n";

    try {
      mapper.readValue(data, BuildFileSpec.class);
      Assert.fail();
    } catch (JsonProcessingException jpe) {
      MatcherAssert.assertThat(
          jpe.getMessage(),
          CoreMatchers.containsString("Property 'apiVersion' cannot be an empty string"));
    }
  }

  @Test
  public void testBuildFileSpec_kindRequired() {
    String data = "apiVersion: v1alpha1\n";

    try {
      mapper.readValue(data, BuildFileSpec.class);
      Assert.fail();
    } catch (JsonProcessingException jpe) {
      MatcherAssert.assertThat(
          jpe.getMessage(), CoreMatchers.startsWith("Missing required creator property 'kind'"));
    }
  }

  @Test
  public void testBuildFileSpec_kindMustBeBuildFile() {
    String data = "apiVersion: v1alpha1\n" + "kind: NotBuildFile\n";

    try {
      mapper.readValue(data, BuildFileSpec.class);
      Assert.fail();
    } catch (JsonProcessingException jpe) {
      MatcherAssert.assertThat(
          jpe.getMessage(),
          CoreMatchers.containsString("Property 'kind' must be 'BuildFile' but is 'NotBuildFile'"));
    }
  }

  @Test
  public void testBuildFileSpec_kindNotNull() {
    String data = "apiVersion: v1alpha1\n" + "kind: null\n";

    try {
      mapper.readValue(data, BuildFileSpec.class);
      Assert.fail();
    } catch (JsonProcessingException jpe) {
      MatcherAssert.assertThat(
          jpe.getMessage(), CoreMatchers.containsString("Property 'kind' cannot be null"));
    }
  }

  @Test
  public void testBuildFileSpec_nullCollections() throws JsonProcessingException {
    String data = "apiVersion: v1alpha1\n" + "kind: BuildFile\n";

    BuildFileSpec parsed = mapper.readValue(data, BuildFileSpec.class);
    Assert.assertEquals(ImmutableMap.of(), parsed.getEnvironment());
    Assert.assertEquals(ImmutableMap.of(), parsed.getLabels());
    Assert.assertEquals(ImmutableSet.of(), parsed.getVolumes());
    Assert.assertEquals(ImmutableSet.of(), parsed.getExposedPorts());
    // entrypoint and cmd CAN be not present
    Assert.assertFalse(parsed.getEntrypoint().isPresent());
    Assert.assertFalse(parsed.getCmd().isPresent());
  }

  @RunWith(Parameterized.class)
  public static class OptionalStringCollectionTests {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
      return Arrays.asList(new Object[][] {{"volumes"}, {"exposedPorts"}, {"entrypoint"}, {"cmd"}});
    }

    @Parameterized.Parameter public String fieldName;

    @Test
    public void testBuildFileSpec_noNullEntries() {
      String data =
          "apiVersion: v1alpha1\n" + "kind: BuildFile\n" + fieldName + ": ['first', null]";

      try {
        mapper.readValue(data, BuildFileSpec.class);
        Assert.fail();
      } catch (JsonProcessingException ex) {
        Assert.assertEquals(
            "Property '" + fieldName + "' cannot contain null entries", ex.getCause().getMessage());
      }
    }

    @Test
    public void testBuildFileSpec_noEmptyEntries() {
      String data = "apiVersion: v1alpha1\n" + "kind: BuildFile\n" + fieldName + ": ['first', ' ']";

      try {
        mapper.readValue(data, BuildFileSpec.class);
        Assert.fail();
      } catch (JsonProcessingException ex) {
        Assert.assertEquals(
            "Property '" + fieldName + "' cannot contain empty strings",
            ex.getCause().getMessage());
      }
    }

    @Test
    public void testBuildFileSpec_emptyOkay() throws JsonProcessingException {
      String data = "apiVersion: v1alpha1\n" + "kind: BuildFile\n" + fieldName + ": []";

      mapper.readValue(data, BuildFileSpec.class);
      // pass
    }

    @Test
    public void testBuildFileSpec_nullOkay() throws JsonProcessingException {
      String data = "apiVersion: v1alpha1\n" + "kind: BuildFile\n" + fieldName + ": null";

      mapper.readValue(data, BuildFileSpec.class);
      // pass
    }
  }

  @RunWith(Parameterized.class)
  public static class OptionalStringTests {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
      return Arrays.asList(
          new Object[][] {{"creationTime"}, {"format"}, {"user"}, {"workingDirectory"}});
    }

    @Parameterized.Parameter public String fieldName;

    @Test
    public void testBuildFileSpec_noEmptyValues() {
      String data = "apiVersion: v1alpha1\n" + "kind: BuildFile\n" + fieldName + ": ' '";

      try {
        mapper.readValue(data, BuildFileSpec.class);
        Assert.fail();
      } catch (JsonProcessingException ex) {
        Assert.assertEquals(
            "Property '" + fieldName + "' cannot be an empty string", ex.getCause().getMessage());
      }
    }

    @Test
    public void testBuildFileSpec_nullOkay() throws JsonProcessingException {
      String data = "apiVersion: v1alpha1\n" + "kind: BuildFile\n" + fieldName + ": null";

      mapper.readValue(data, BuildFileSpec.class);
      // pass
    }
  }

  @RunWith(Parameterized.class)
  public static class OptionalStringMapTests {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
      return Arrays.asList(new Object[][] {{"environment"}, {"labels"}});
    }

    @Parameterized.Parameter public String fieldName;

    @Test
    public void testBuildFileSpec_noNullValues() {
      String data =
          "apiVersion: v1alpha1\n" + "kind: BuildFile\n" + fieldName + ":\n" + "  key: null";

      try {
        mapper.readValue(data, BuildFileSpec.class);
        Assert.fail();
      } catch (JsonProcessingException ex) {
        Assert.assertEquals(
            "Property '" + fieldName + "' cannot contain null values", ex.getCause().getMessage());
      }
    }

    /**
     * A quirk of our parser is that "null" keys are parsed as strings and not null, this test just
     * formalizes that behavior.
     */
    @Test
    public void testBuildFileSpec_yamlNullKeysPass() throws JsonProcessingException {
      String data =
          "apiVersion: v1alpha1\n" + "kind: BuildFile\n" + fieldName + ":\n" + "  null: value";

      mapper.readValue(data, BuildFileSpec.class);
      // pass
    }

    @Test
    public void testBuildFileSpec_noEmptyValues() {
      String data =
          "apiVersion: v1alpha1\n" + "kind: BuildFile\n" + fieldName + ":\n" + "  key: ' '";

      try {
        mapper.readValue(data, BuildFileSpec.class);
        Assert.fail();
      } catch (JsonProcessingException ex) {
        Assert.assertEquals(
            "Property '" + fieldName + "' cannot contain empty string values",
            ex.getCause().getMessage());
      }
    }

    @Test
    public void testBuildFileSpec_noEmptyKeys() {
      String data =
          "apiVersion: v1alpha1\n" + "kind: BuildFile\n" + fieldName + ":\n" + "  ' ': value";

      try {
        mapper.readValue(data, BuildFileSpec.class);
        Assert.fail();
      } catch (JsonProcessingException ex) {
        Assert.assertEquals(
            "Property '" + fieldName + "' cannot contain empty string keys",
            ex.getCause().getMessage());
      }
    }

    @Test
    public void testBuildFileSpec_emptyOkay() throws JsonProcessingException {
      String data = "apiVersion: v1alpha1\n" + "kind: BuildFile\n" + fieldName + ": {}";

      mapper.readValue(data, BuildFileSpec.class);
      // pass
    }

    @Test
    public void testBuildFileSpec_nullOkay() throws JsonProcessingException {
      String data = "apiVersion: v1alpha1\n" + "kind: BuildFile\n" + fieldName + ": null";

      mapper.readValue(data, BuildFileSpec.class);
      // pass
    }
  }
}
