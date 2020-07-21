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

package com.google.cloud.tools.jib.api.buildplan;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link ContainerBuildPlanTest}. */
public class ContainerBuildPlanTest {

  @Test
  public void testDefaults() {
    ContainerBuildPlan plan = ContainerBuildPlan.builder().build();

    Assert.assertEquals("scratch", plan.getBaseImage());
    Assert.assertEquals(ImmutableSet.of(new Platform("amd64", "linux")), plan.getPlatforms());
    Assert.assertEquals(ImageFormat.Docker, plan.getFormat());
    Assert.assertEquals(Instant.EPOCH, plan.getCreationTime());
    Assert.assertEquals(Collections.emptyMap(), plan.getEnvironment());
    Assert.assertEquals(Collections.emptySet(), plan.getVolumes());
    Assert.assertEquals(Collections.emptyMap(), plan.getLabels());
    Assert.assertEquals(Collections.emptySet(), plan.getExposedPorts());
    Assert.assertNull(plan.getUser());
    Assert.assertNull(plan.getWorkingDirectory());
    Assert.assertNull(plan.getEntrypoint());
    Assert.assertNull(plan.getCmd());
    Assert.assertEquals(Collections.emptyList(), plan.getLayers());
  }

  @Test
  public void testBuilder() {
    ContainerBuildPlan plan = createSamplePlan();

    Assert.assertEquals("base/image", plan.getBaseImage());
    Assert.assertEquals(
        ImmutableSet.of(new Platform("testOs", "testArchitecture")), plan.getPlatforms());
    Assert.assertEquals(ImageFormat.OCI, plan.getFormat());
    Assert.assertEquals(Instant.ofEpochMilli(30), plan.getCreationTime());
    Assert.assertEquals(ImmutableMap.of("env", "var"), plan.getEnvironment());
    Assert.assertEquals(
        ImmutableSet.of(AbsoluteUnixPath.get("/mnt/foo"), AbsoluteUnixPath.get("/bar")),
        plan.getVolumes());
    Assert.assertEquals(ImmutableMap.of("com.example.label", "cool"), plan.getLabels());
    Assert.assertEquals(ImmutableSet.of(Port.tcp(443)), plan.getExposedPorts());
    Assert.assertEquals(":", plan.getUser());
    Assert.assertEquals(AbsoluteUnixPath.get("/workspace"), plan.getWorkingDirectory());
    Assert.assertEquals(Arrays.asList("foo", "entrypoint"), plan.getEntrypoint());
    Assert.assertEquals(Arrays.asList("bar", "cmd"), plan.getCmd());

    Assert.assertEquals(1, plan.getLayers().size());
    MatcherAssert.assertThat(
        plan.getLayers().get(0), CoreMatchers.instanceOf(FileEntriesLayer.class));
    Assert.assertEquals(
        Arrays.asList(
            new FileEntry(
                Paths.get("/src/file/foo"),
                AbsoluteUnixPath.get("/path/in/container"),
                FilePermissions.fromOctalString("644"),
                Instant.ofEpochSecond(1))),
        ((FileEntriesLayer) plan.getLayers().get(0)).getEntries());
  }

  @Test
  public void testToBuilder() {
    ContainerBuildPlan plan = createSamplePlan().toBuilder().build();

    Assert.assertEquals("base/image", plan.getBaseImage());
    Assert.assertEquals(
        ImmutableSet.of(new Platform("testOs", "testArchitecture")), plan.getPlatforms());
    Assert.assertEquals(ImageFormat.OCI, plan.getFormat());
    Assert.assertEquals(Instant.ofEpochMilli(30), plan.getCreationTime());
    Assert.assertEquals(ImmutableMap.of("env", "var"), plan.getEnvironment());
    Assert.assertEquals(
        ImmutableSet.of(AbsoluteUnixPath.get("/mnt/foo"), AbsoluteUnixPath.get("/bar")),
        plan.getVolumes());
    Assert.assertEquals(ImmutableMap.of("com.example.label", "cool"), plan.getLabels());
    Assert.assertEquals(ImmutableSet.of(Port.tcp(443)), plan.getExposedPorts());
    Assert.assertEquals(":", plan.getUser());
    Assert.assertEquals(AbsoluteUnixPath.get("/workspace"), plan.getWorkingDirectory());
    Assert.assertEquals(Arrays.asList("foo", "entrypoint"), plan.getEntrypoint());
    Assert.assertEquals(Arrays.asList("bar", "cmd"), plan.getCmd());

    Assert.assertEquals(1, plan.getLayers().size());
    MatcherAssert.assertThat(
        plan.getLayers().get(0), CoreMatchers.instanceOf(FileEntriesLayer.class));
    Assert.assertEquals(
        Arrays.asList(
            new FileEntry(
                Paths.get("/src/file/foo"),
                AbsoluteUnixPath.get("/path/in/container"),
                FilePermissions.fromOctalString("644"),
                Instant.ofEpochSecond(1))),
        ((FileEntriesLayer) plan.getLayers().get(0)).getEntries());
  }

  @Test
  public void testAddPlatform_duplicatePlatforms() {
    ContainerBuildPlan plan =
        ContainerBuildPlan.builder()
            .addPlatform("testOS", "testArchitecture")
            .addPlatform("testOS", "testArchitecture")
            .build();
    Assert.assertEquals(
        ImmutableSet.of(new Platform("amd64", "linux"), new Platform("testOS", "testArchitecture")),
        plan.getPlatforms());
  }

  @Test
  public void testSetPlatforms_emptyPlatformsSet() {
    try {
      ContainerBuildPlan.builder().setPlatforms(Collections.emptySet());
      Assert.fail();
    } catch (IllegalArgumentException ex) {
      Assert.assertEquals("platforms set cannot be empty", ex.getMessage());
    }
  }

  private ContainerBuildPlan createSamplePlan() {
    FileEntriesLayer layer =
        FileEntriesLayer.builder()
            .addEntry(Paths.get("/src/file/foo"), AbsoluteUnixPath.get("/path/in/container"))
            .build();

    return ContainerBuildPlan.builder()
        .setBaseImage("base/image")
        .setPlatforms(ImmutableSet.of(new Platform("testOs", "testArchitecture")))
        .setFormat(ImageFormat.OCI)
        .setCreationTime(Instant.ofEpochMilli(30))
        .setEnvironment(ImmutableMap.of("env", "var"))
        .setVolumes(ImmutableSet.of(AbsoluteUnixPath.get("/mnt/foo"), AbsoluteUnixPath.get("/bar")))
        .setLabels(ImmutableMap.of("com.example.label", "cool"))
        .setExposedPorts(ImmutableSet.of(Port.tcp(443)))
        .setLayers(Arrays.asList(layer))
        .setUser(":")
        .setWorkingDirectory(AbsoluteUnixPath.get("/workspace"))
        .setEntrypoint(Arrays.asList("foo", "entrypoint"))
        .setCmd(Arrays.asList("bar", "cmd"))
        .build();
  }
}
