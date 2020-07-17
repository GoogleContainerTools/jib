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
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Test;

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
    Assert.assertEquals(ImmutableMap.of("env_key", "env_value"), parsed.getEnvironment().get());
    Assert.assertEquals(ImmutableMap.of("label_key", "label_value"), parsed.getLabels().get());
    Assert.assertEquals(
        ImmutableSet.of(AbsoluteUnixPath.get("/my/volume")), parsed.getVolumes().get());
    Assert.assertEquals(Ports.parse(ImmutableList.of("8080")), parsed.getExposedPorts().get());
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
          CoreMatchers.containsString("Field 'kind' must be BuildFile but is NotBuildFile"));
    }
  }
}
