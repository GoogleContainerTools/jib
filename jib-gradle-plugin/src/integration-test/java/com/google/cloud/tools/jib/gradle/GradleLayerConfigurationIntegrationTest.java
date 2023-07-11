/*
 * Copyright 2019 Google LLC.
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

package com.google.cloud.tools.jib.gradle;

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.ClassRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GradleLayerConfigurationIntegrationTest {

  @TempDir Path tempDir;

  @TempDir Path tempDirSimple;

  @ClassRule
  public final TestProject multiTestProject = new TestProject("all-local-multi-service", tempDir);

  @ClassRule public final TestProject configTestProject = new TestProject("simple", tempDirSimple);

  @Test
  void testGradleLayerConfiguration_configurationName() throws IOException {
    configTestProject.build("jibBuildTar", "-b=build-configuration.gradle");
    Path jibTar = configTestProject.getProjectRoot().resolve("build/jib-image.tar");
    List<List<String>> layers = getLayers(jibTar);

    // the expected order is:
    // no base image layers (scratch)
    // jvm arg files (0)
    // classes (1)
    // resources (2)
    // dependencies (3)
    // verify dependencies
    List<String> dependencies = layers.get(3);
    assertThat(dependencies).containsExactly("app/", "app/libs/", "app/libs/dependency2").inOrder();
  }

  @Test
  void testGradleLayerConfiguration_configurationName_prioritizeSystemProperty()
      throws IOException {
    configTestProject.build(
        "jibBuildTar",
        "--stacktrace",
        "-Djib.configurationName=otherConfiguration",
        "-b=build-configuration.gradle");
    Path jibTar = configTestProject.getProjectRoot().resolve("build/jib-image.tar");
    List<List<String>> layers = getLayers(jibTar);

    // the expected order is:
    // no base image layers (scratch)
    // jvm arg files (0)
    // classes (1)
    // resources (2)
    // dependencies (3)
    // verify dependencies
    List<String> dependencies = layers.get(3);
    assertThat(dependencies).containsExactly("app/", "app/libs/", "app/libs/dependency3").inOrder();
  }

  @Test
  void testGradleLayerConfiguration_multiModule() throws IOException {
    multiTestProject.build(":complex-service:jibBuildTar");

    Path jibTar = multiTestProject.getProjectRoot().resolve("complex-service/build/jib-image.tar");

    List<List<String>> layers = getLayers(jibTar);

    assertThat(layers).hasSize(7);

    // the expected order is:
    // no base image layers (scratch)
    // extra-files (0)
    // jvm arg files (1)
    // classes (2)
    // resources (3)
    // project dependencies (4)
    // snapshot dependencies (5)
    // dependencies (6)

    // verify dependencies
    assertThat(layers.get(6))
        .containsExactly("app/", "app/libs/", "app/libs/dependency-1.0.0.jar")
        .inOrder();

    // verify snapshot dependencies
    assertThat(layers.get(5))
        .containsExactly("app/", "app/libs/", "app/libs/dependencyX-1.0.0-SNAPSHOT.jar")
        .inOrder();

    // verify project dependencies
    assertThat(layers.get(4)).containsExactly("app/", "app/libs/", "app/libs/lib.jar").inOrder();

    // verify resources
    assertThat(layers.get(3))
        .containsExactly(
            "app/", "app/resources/", "app/resources/resource1.txt", "app/resources/resource2.txt")
        .inOrder();

    // verify classes
    assertThat(layers.get(2))
        .containsExactly(
            "app/",
            "app/classes/",
            "app/classes/com/",
            "app/classes/com/test/",
            "app/classes/com/test/HelloWorld.class");

    // verify jvm arg files
    assertThat(layers.get(1))
        .containsExactly("app/", "app/jib-classpath-file", "app/jib-main-class-file")
        .inOrder();

    // verify extra files
    assertThat(layers.get(0)).containsExactly("extra-file");
  }

  @Test
  void testGradleLayerConfiguration_simpleModule() throws IOException {
    multiTestProject.build(":simple-service:jibBuildTar");

    Path jibTar = multiTestProject.getProjectRoot().resolve("simple-service/build/jib-image.tar");

    List<List<String>> layers = getLayers(jibTar);

    assertThat(layers).hasSize(2);

    // the expected order is:
    // no base image layers (scratch)
    // jvm arg files (0)
    // classes (1)

    // verify classes
    assertThat(layers.get(1))
        .containsExactly(
            "app/",
            "app/classes/",
            "app/classes/com/",
            "app/classes/com/test/",
            "app/classes/com/test/HelloWorld.class")
        .inOrder();

    // verify jvm arg files
    assertThat(layers.get(0))
        .containsExactly("app/", "app/jib-classpath-file", "app/jib-main-class-file")
        .inOrder();
  }

  // returns all files in layers (*.tar.gz) in a image tar
  private List<List<String>> getLayers(Path tar) throws IOException {
    List<List<String>> layers = new ArrayList<>();

    try (TarArchiveInputStream image = new TarArchiveInputStream(Files.newInputStream(tar))) {
      TarArchiveEntry entry;
      while ((entry = image.getNextTarEntry()) != null) {
        if (entry.getName().endsWith(".tar.gz")) {
          @SuppressWarnings("resource") // must not close sub-streams
          TarArchiveInputStream layer = new TarArchiveInputStream(new GZIPInputStream(image));
          TarArchiveEntry layerEntry;
          List<String> layerFiles = new ArrayList<>();
          while ((layerEntry = layer.getNextTarEntry()) != null) {
            layerFiles.add(layerEntry.getName());
          }
          layers.add(0, layerFiles);
        }
      }
    }
    return layers;
  }
}
