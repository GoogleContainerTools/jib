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

import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.ImageFormat;
import com.google.cloud.tools.jib.api.buildplan.LayerObject;
import com.google.cloud.tools.jib.api.buildplan.Platform;
import com.google.cloud.tools.jib.api.buildplan.Port;
import com.google.cloud.tools.jib.cli.Build;
import com.google.cloud.tools.jib.cli.CommonCliOptions;
import com.google.cloud.tools.jib.plugins.common.logging.ConsoleLogger;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Map;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class BuildFilesTest {

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();
  @Rule public final MockitoRule rule = MockitoJUnit.rule();

  @Mock private ConsoleLogger consoleLogger;
  @Mock private Build buildCli;
  @Mock private CommonCliOptions commonCliOptions;

  @Before
  public void setUp() {
    Mockito.when(buildCli.getTemplateParameters()).thenReturn(ImmutableMap.of());
  }

  @Test
  public void testToJibContainerBuilder_allProperties()
      throws URISyntaxException, IOException, InvalidImageReferenceException {
    Path buildfile =
        Paths.get(Resources.getResource("buildfiles/projects/allProperties/jib.yaml").toURI());
    Path projectRoot = buildfile.getParent();
    JibContainerBuilder jibContainerBuilder =
        BuildFiles.toJibContainerBuilder(
            projectRoot, buildfile, buildCli, commonCliOptions, consoleLogger);

    ContainerBuildPlan resolved = jibContainerBuilder.toContainerBuildPlan();
    Assert.assertEquals("ubuntu", resolved.getBaseImage());
    Assert.assertEquals(Instant.ofEpochMilli(2000), resolved.getCreationTime());
    Assert.assertEquals(ImageFormat.OCI, resolved.getFormat());
    Assert.assertEquals(
        ImmutableSet.of(new Platform("arm", "linux"), new Platform("amd64", "darwin")),
        resolved.getPlatforms());
    Assert.assertEquals(ImmutableMap.of("KEY1", "v1", "KEY2", "v2"), resolved.getEnvironment());
    Assert.assertEquals(
        ImmutableSet.of(AbsoluteUnixPath.get("/volume1"), AbsoluteUnixPath.get("/volume2")),
        resolved.getVolumes());
    Assert.assertEquals(ImmutableMap.of("label1", "l1", "label2", "l2"), resolved.getLabels());
    Assert.assertEquals(
        ImmutableSet.of(Port.udp(123), Port.tcp(456), Port.tcp(789)), resolved.getExposedPorts());
    Assert.assertEquals("customUser", resolved.getUser());
    Assert.assertEquals(AbsoluteUnixPath.get("/home"), resolved.getWorkingDirectory());
    Assert.assertEquals(ImmutableList.of("sh", "script.sh"), resolved.getEntrypoint());
    Assert.assertEquals(ImmutableList.of("--param", "param"), resolved.getCmd());

    Assert.assertEquals(1, resolved.getLayers().size());
    FileEntriesLayer resolvedLayer = (FileEntriesLayer) resolved.getLayers().get(0);
    Assert.assertEquals("scripts", resolvedLayer.getName());
    Assert.assertEquals(
        FileEntriesLayer.builder()
            .addEntry(
                projectRoot.resolve("project/script.sh"), AbsoluteUnixPath.get("/home/script.sh"))
            .build()
            .getEntries(),
        resolvedLayer.getEntries());
    Assert.assertEquals(LayerObject.Type.FILE_ENTRIES, resolvedLayer.getType());
  }

  @Test
  public void testToJibContainerBuilder_requiredProperties()
      throws URISyntaxException, IOException, InvalidImageReferenceException {
    Path buildfile =
        Paths.get(Resources.getResource("buildfiles/projects/allDefaults/jib.yaml").toURI());
    JibContainerBuilder jibContainerBuilder =
        BuildFiles.toJibContainerBuilder(
            buildfile.getParent(), buildfile, buildCli, commonCliOptions, consoleLogger);

    ContainerBuildPlan resolved = jibContainerBuilder.toContainerBuildPlan();
    Assert.assertEquals("scratch", resolved.getBaseImage());
    Assert.assertEquals(ImmutableSet.of(new Platform("amd64", "linux")), resolved.getPlatforms());
    Assert.assertEquals(Instant.EPOCH, resolved.getCreationTime());
    Assert.assertEquals(ImageFormat.Docker, resolved.getFormat());
    Assert.assertTrue(resolved.getEnvironment().isEmpty());
    Assert.assertTrue(resolved.getLabels().isEmpty());
    Assert.assertTrue(resolved.getVolumes().isEmpty());
    Assert.assertTrue(resolved.getExposedPorts().isEmpty());
    Assert.assertNull(resolved.getUser());
    Assert.assertNull(resolved.getWorkingDirectory());
    Assert.assertNull(resolved.getEntrypoint());
    Assert.assertTrue(resolved.getLayers().isEmpty());
  }

  @Test
  public void testToBuildFileSpec_withTemplating()
      throws URISyntaxException, InvalidImageReferenceException, IOException {
    Path buildfile =
        Paths.get(Resources.getResource("buildfiles/projects/templating/valid.yaml").toURI());

    Mockito.when(buildCli.getTemplateParameters())
        .thenReturn(
            ImmutableMap.of(
                "unused", "ignored", // keys that are defined but not used do not throw an error
                "key", "templateKey",
                "value", "templateValue",
                "repeated", "repeatedValue"));
    JibContainerBuilder jibContainerBuilder =
        BuildFiles.toJibContainerBuilder(
            buildfile.getParent(), buildfile, buildCli, commonCliOptions, consoleLogger);

    ContainerBuildPlan resolved = jibContainerBuilder.toContainerBuildPlan();
    Map<String, String> expectedLabels =
        ImmutableMap.<String, String>builder()
            .put("templateKey", "templateValue")
            .put("label1", "repeatedValue")
            .put("label2", "repeatedValue")
            .put("label3", "${escaped}")
            .put("label4", "free$")
            .put("unmatched", "${")
            .build();
    Assert.assertEquals(expectedLabels, resolved.getLabels());
  }

  @Test
  public void testToBuildFileSpec_failWithMissingTemplateVariable()
      throws URISyntaxException, InvalidImageReferenceException, IOException {
    Path buildfile =
        Paths.get(Resources.getResource("buildfiles/projects/templating/missingVar.yaml").toURI());

    try {
      BuildFiles.toJibContainerBuilder(
          buildfile.getParent(), buildfile, buildCli, commonCliOptions, consoleLogger);
      Assert.fail();
    } catch (IllegalArgumentException iae) {
      MatcherAssert.assertThat(
          iae.getMessage(), CoreMatchers.startsWith("Cannot resolve variable 'missingVar'"));
    }
  }

  @Test
  public void testToBuildFileSpec_templateMultiLineBehavior()
      throws URISyntaxException, InvalidImageReferenceException, IOException {
    Path buildfile =
        Paths.get(Resources.getResource("buildfiles/projects/templating/multiLine.yaml").toURI());

    String replaceThisMultiline = "replace" + System.lineSeparator() + "this";
    System.out.println(replaceThisMultiline);
    String replaceThisMultiline2 = "replace" + System.getProperty("line.separator") + "this";
    System.out.println(replaceThisMultiline2);

    Mockito.when(buildCli.getTemplateParameters())
        .thenReturn(
            ImmutableMap.of(
                "replace" + System.getProperty("line.separator") + "this", "creationTime: 1234"));
    JibContainerBuilder jibContainerBuilder =
        BuildFiles.toJibContainerBuilder(
            buildfile.getParent(), buildfile, buildCli, commonCliOptions, consoleLogger);
    ContainerBuildPlan resolved = jibContainerBuilder.toContainerBuildPlan();
    Assert.assertEquals(Instant.ofEpochMilli(1234), resolved.getCreationTime());
  }

  @Test
  public void testToBuildFileSpec_alternativeRootContext()
      throws URISyntaxException, InvalidImageReferenceException, IOException {
    Path buildfile =
        Paths.get(
            Resources.getResource("buildfiles/projects/allProperties/altYamls/alt-jib.yaml")
                .toURI());
    Path projectRoot = buildfile.getParent().getParent();
    JibContainerBuilder jibContainerBuilder =
        BuildFiles.toJibContainerBuilder(
            projectRoot, buildfile, buildCli, commonCliOptions, consoleLogger);

    ContainerBuildPlan resolved = jibContainerBuilder.toContainerBuildPlan();
    Assert.assertEquals(
        FileEntriesLayer.builder()
            .addEntry(
                projectRoot.resolve("project/script.sh"), AbsoluteUnixPath.get("/home/script.sh"))
            .build()
            .getEntries(),
        ((FileEntriesLayer) resolved.getLayers().get(0)).getEntries());
  }
}
