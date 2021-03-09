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

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

public class GradleLayerConfigurationIntegrationTest {

  @ClassRule
  public static final TestProject multiTestProject = new TestProject("all-local-multi-service");

  @ClassRule public static final TestProject configTestProject = new TestProject("simple");

  @Test
  public void testGradleLayerConfiguration_configurationName() throws IOException {
    configTestProject.build("jibBuildTar", "-b=build-configuration.gradle");
    Path jibTar = configTestProject.getProjectRoot().resolve("build/jib-image.tar");
    List<List<String>> layers = getLayers(jibTar);

    // the expected order is:
    // no base image layers (scratch)
    // classes (0)
    // resources (1)
    // dependencies (2)
    // verify dependencies
    List<String> dependencies = layers.get(2);
    assertThat(dependencies).containsExactly("app/", "app/libs/", "app/libs/dependency2").inOrder();
  }

  @Test
  public void testGradleLayerConfiguration_configurationName_prioritizeSystemProperty()
      throws IOException {
    configTestProject.build(
        "jibBuildTar",
        "-Djib.configurationName=otherConfiguration",
        "-b=build-configuration.gradle");
    Path jibTar = configTestProject.getProjectRoot().resolve("build/jib-image.tar");
    List<List<String>> layers = getLayers(jibTar);

    // the expected order is:
    // no base image layers (scratch)
    // classes (0)
    // resources (1)
    // dependencies (2)
    // verify dependencies
    List<String> dependencies = layers.get(2);
    assertThat(dependencies).containsExactly("app/", "app/libs/", "app/libs/dependency3").inOrder();
  }

  @Test
  public void testGradleLayerConfiguration_multiModule() throws IOException {
    multiTestProject.build(":complex-service:jibBuildTar");

    Path jibTar = multiTestProject.getProjectRoot().resolve("complex-service/build/jib-image.tar");

    List<List<String>> layers = getLayers(jibTar);

    Assert.assertEquals(6, layers.size());

    // the expected order is:
    // no base image layers (scratch)
    // extra-files (0)
    // classes (1)
    // resources (2)
    // project dependencies (3)
    // snapshot dependencies (4)
    // dependencies (5)

    // verify dependencies
    List<String> dependencies = layers.get(5);
    List<String> expectedDependencies =
        ImmutableList.of("app/", "app/libs/", "app/libs/dependency-1.0.0.jar");
    Assert.assertEquals(expectedDependencies, dependencies);

    // verify snapshot dependencies
    List<String> snapshotDependencies = layers.get(4);
    List<String> expectedSnapshotDependencies =
        ImmutableList.of("app/", "app/libs/", "app/libs/dependencyX-1.0.0-SNAPSHOT.jar");
    Assert.assertEquals(expectedSnapshotDependencies, snapshotDependencies);

    // verify project dependencies
    List<String> projectDependencies = layers.get(3);
    List<String> expectedProjectDependencies =
        ImmutableList.of("app/", "app/libs/", "app/libs/lib.jar");
    Assert.assertEquals(expectedProjectDependencies, projectDependencies);

    // verify resources
    List<String> resources = layers.get(2);
    List<String> expectedResources =
        ImmutableList.of(
            "app/", "app/resources/", "app/resources/resource1.txt", "app/resources/resource2.txt");
    Assert.assertEquals(expectedResources, resources);

    // verify classes
    List<String> classes = layers.get(1);
    List<String> expectedClasses =
        ImmutableList.of(
            "app/",
            "app/classes/",
            "app/classes/com/",
            "app/classes/com/test/",
            "app/classes/com/test/HelloWorld.class");
    Assert.assertEquals(expectedClasses, classes);

    // verify extra files
    List<String> extraFiles = layers.get(0);
    List<String> expectedExtraFiles = ImmutableList.of("extra-file");
    Assert.assertEquals(expectedExtraFiles, extraFiles);
  }

  @Test
  public void testGradleLayerConfiguration_simpleModule() throws IOException {
    multiTestProject.build(":simple-service:jibBuildTar");

    Path jibTar = multiTestProject.getProjectRoot().resolve("simple-service/build/jib-image.tar");

    List<List<String>> layers = getLayers(jibTar);

    Assert.assertEquals(1, layers.size());

    // the expected order is:
    // no base image layers (scratch)
    // classes (0)

    // verify classes
    List<String> classes = layers.get(0);
    List<String> expectedClasses =
        ImmutableList.of(
            "app/",
            "app/classes/",
            "app/classes/com/",
            "app/classes/com/test/",
            "app/classes/com/test/HelloWorld.class");
    Assert.assertEquals(expectedClasses, classes);
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
