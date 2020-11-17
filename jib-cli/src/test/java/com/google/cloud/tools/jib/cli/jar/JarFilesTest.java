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

package com.google.cloud.tools.jib.cli.jar;

import static com.google.common.truth.Truth.assertThat;

import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.ImageFormat;
import com.google.cloud.tools.jib.api.buildplan.Platform;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class JarFilesTest {
  private static final String SIMPLE_STANDARD_JAR = "jar/standard/basicStandardJar.jar";

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testToJibContainerBuilder_basicInfo()
      throws IOException, URISyntaxException, InvalidImageReferenceException {
    Path standardJar = Paths.get(Resources.getResource(SIMPLE_STANDARD_JAR).toURI());
    Path destDir = temporaryFolder.getRoot().toPath();
    JibContainerBuilder containerBuilder = JarFiles.toJibContainerBuilder(standardJar, destDir);
    ContainerBuildPlan buildPlan = containerBuilder.toContainerBuildPlan();

    assertThat(buildPlan.getBaseImage()).isEqualTo("gcr.io/distroless/java");
    assertThat(buildPlan.getPlatforms()).isEqualTo(ImmutableSet.of(new Platform("amd64", "linux")));
    assertThat(buildPlan.getCreationTime()).isEqualTo(Instant.EPOCH);
    assertThat(buildPlan.getFormat()).isEqualTo(ImageFormat.Docker);
    assertThat(buildPlan.getEnvironment()).isEmpty();
    assertThat(buildPlan.getLabels()).isEmpty();
    assertThat(buildPlan.getVolumes()).isEmpty();
    assertThat(buildPlan.getExposedPorts()).isEmpty();
    assertThat(buildPlan.getUser()).isNull();
    assertThat(buildPlan.getWorkingDirectory()).isNull();
    assertThat(buildPlan.getEntrypoint())
        .isEqualTo(
            ImmutableList.of("java", "-cp", "/app/explodedJar:/app/dependencies/*", "HelloWorld"));
    assertThat(((FileEntriesLayer) buildPlan.getLayers().get(0)).getEntries())
        .isEqualTo(
            FileEntriesLayer.builder()
                .addEntry(
                    standardJar.getParent().resolve("dependency1"),
                    AbsoluteUnixPath.get("/app/dependencies/dependency1"))
                .build()
                .getEntries());
    assertThat(((FileEntriesLayer) buildPlan.getLayers().get(1)).getEntries())
        .containsExactlyElementsIn(
            FileEntriesLayer.builder()
                .addEntry(
                    destDir.resolve("META-INF/"),
                    AbsoluteUnixPath.get("/app/explodedJar/META-INF/"))
                .addEntry(
                    destDir.resolve("META-INF/MANIFEST.MF"),
                    AbsoluteUnixPath.get("/app/explodedJar/META-INF/MANIFEST.MF"))
                .addEntry(
                    destDir.resolve("resource1.txt"),
                    AbsoluteUnixPath.get("/app/explodedJar/resource1.txt"))
                .addEntry(
                    destDir.resolve("directory1/"),
                    AbsoluteUnixPath.get("/app/explodedJar/directory1/"))
                .build()
                .getEntries());
    assertThat(((FileEntriesLayer) buildPlan.getLayers().get(2)).getEntries())
        .containsExactlyElementsIn(
            FileEntriesLayer.builder()
                .addEntry(
                    destDir.resolve("class1.class"),
                    AbsoluteUnixPath.get("/app/explodedJar/class1.class"))
                .addEntry(
                    destDir.resolve("directory1/"),
                    AbsoluteUnixPath.get("/app/explodedJar/directory1/"))
                .addEntry(
                    destDir.resolve("directory1/class2.class"),
                    AbsoluteUnixPath.get("/app/explodedJar/directory1/class2.class"))
                .addEntry(
                    destDir.resolve("META-INF/"),
                    AbsoluteUnixPath.get("/app/explodedJar/META-INF/"))
                .build()
                .getEntries());
  }
}
