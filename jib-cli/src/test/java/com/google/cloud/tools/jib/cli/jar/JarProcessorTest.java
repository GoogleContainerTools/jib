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

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FileEntry;
import com.google.cloud.tools.jib.cli.jar.JarProcessor.JarType;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class JarProcessorTest {

  private static final String SPRING_BOOT_JAR = "jar/springboot/springboot_sample.jar";
  private static final String STANDARD_JAR = "jar/standard/standardJar.jar";
  private static final String STANDARD_JAR_WITH_CLASS_PATH_MANIFEST =
      "jar/standard/standardJarWithClassPath.jar";
  private static final String STANDARD_JAR_WITHOUT_CLASS_PATH_MANIFEST =
      "jar/standard/standardJarWithoutClassPath.jar";
  private static final String STANDARD_JAR_WITH_ONLY_CLASSES =
      "jar/standard/standardJarWithOnlyClasses.jar";
  private static final String STANDARD_JAR_EMPTY = "jar/standard/emptyStandardJar.jar";

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testDetermineJarType_springBoot() throws IOException, URISyntaxException {
    Path springBootJar = Paths.get(Resources.getResource(SPRING_BOOT_JAR).toURI());
    JarType jarType = JarProcessor.determineJarType(springBootJar);
    assertThat(jarType).isEqualTo(JarType.SPRING_BOOT);
  }

  @Test
  public void testDetermineJarType_standard() throws IOException, URISyntaxException {
    Path standardJar = Paths.get(Resources.getResource(STANDARD_JAR).toURI());
    JarType jarType = JarProcessor.determineJarType(standardJar);
    assertThat(jarType).isEqualTo(JarType.STANDARD);
  }

  @Test
  public void testExplodeMode_standard_emptyJar() throws IOException, URISyntaxException {
    Path standardJar = Paths.get(Resources.getResource(STANDARD_JAR_EMPTY).toURI());
    Path destDir = temporaryFolder.newFolder().toPath();
    List<FileEntriesLayer> layers = JarProcessor.explodeStandardJar(standardJar, destDir);

    FileEntriesLayer resourcesLayer = layers.get(0);
    FileEntriesLayer classesLayer = layers.get(1);

    assertThat(layers.size()).isEqualTo(2);

    // Validate resources layer
    List<AbsoluteUnixPath> actualResourcesPath =
        resourcesLayer
            .getEntries()
            .stream()
            .map(FileEntry::getExtractionPath)
            .collect(Collectors.toList());
    assertThat(actualResourcesPath)
        .containsExactly(
            AbsoluteUnixPath.get("/app/explodedJar/META-INF"),
            AbsoluteUnixPath.get("/app/explodedJar/META-INF/MANIFEST.MF"));

    // Validate classes layer
    List<AbsoluteUnixPath> actualClassesPath =
        classesLayer
            .getEntries()
            .stream()
            .map(FileEntry::getExtractionPath)
            .collect(Collectors.toList());
    assertThat(actualClassesPath)
        .containsExactly(AbsoluteUnixPath.get("/app/explodedJar/META-INF"));
  }

  @Test
  public void testExplodeMode_standard_withClassPathInManifest()
      throws IOException, URISyntaxException {
    Path standardJar =
        Paths.get(Resources.getResource(STANDARD_JAR_WITH_CLASS_PATH_MANIFEST).toURI());
    Path destDir = temporaryFolder.newFolder().toPath();
    List<FileEntriesLayer> layers = JarProcessor.explodeStandardJar(standardJar, destDir);

    assertThat(layers.size()).isEqualTo(3);

    FileEntriesLayer dependenciesLayer = layers.get(0);
    FileEntriesLayer resourcesLayer = layers.get(1);
    FileEntriesLayer classesLayer = layers.get(2);

    // Validate dependencies layer.
    assertThat(dependenciesLayer.getName()).isEqualTo("dependencies");
    assertThat(
            dependenciesLayer
                .getEntries()
                .stream()
                .map(FileEntry::getExtractionPath)
                .collect(Collectors.toList()))
        .isEqualTo(
            ImmutableList.of(
                AbsoluteUnixPath.get("/app/dependencies/dependency1"),
                AbsoluteUnixPath.get("/app/dependencies/dependency2"),
                AbsoluteUnixPath.get("/app/dependencies/directory/dependency3")));

    // Validate resources layer.
    // TODO: Validate order of file paths once
    // https://github.com/GoogleContainerTools/jib/issues/2821 is fixed.
    assertThat(resourcesLayer.getName()).isEqualTo("resources");
    List<AbsoluteUnixPath> actualResourcesPaths =
        resourcesLayer
            .getEntries()
            .stream()
            .map(FileEntry::getExtractionPath)
            .collect(Collectors.toList());
    assertThat(actualResourcesPaths)
        .containsExactly(
            AbsoluteUnixPath.get("/app/explodedJar/META-INF/"),
            AbsoluteUnixPath.get("/app/explodedJar/META-INF/MANIFEST.MF"),
            AbsoluteUnixPath.get("/app/explodedJar/directory1"),
            AbsoluteUnixPath.get("/app/explodedJar/directory1/resource1.txt"),
            AbsoluteUnixPath.get("/app/explodedJar/directory2"),
            AbsoluteUnixPath.get("/app/explodedJar/directory2/directory3"),
            AbsoluteUnixPath.get("/app/explodedJar/directory2/directory3/resource2.sql"),
            AbsoluteUnixPath.get("/app/explodedJar/directory4"),
            AbsoluteUnixPath.get("/app/explodedJar/directory4/resource3.txt"),
            AbsoluteUnixPath.get("/app/explodedJar/resource4.sql"));

    // Validate classes layer.
    // TODO: Validate order of file paths once
    // https://github.com/GoogleContainerTools/jib/issues/2821 is fixed.
    assertThat(classesLayer.getName()).isEqualTo("classes");
    List<AbsoluteUnixPath> actualClassesPaths =
        classesLayer
            .getEntries()
            .stream()
            .map(FileEntry::getExtractionPath)
            .collect(Collectors.toList());
    assertThat(actualClassesPaths)
        .containsExactly(
            AbsoluteUnixPath.get("/app/explodedJar/META-INF"),
            AbsoluteUnixPath.get("/app/explodedJar/class5.class"),
            AbsoluteUnixPath.get("/app/explodedJar/directory1"),
            AbsoluteUnixPath.get("/app/explodedJar/directory1/class1.class"),
            AbsoluteUnixPath.get("/app/explodedJar/directory1/class2.class"),
            AbsoluteUnixPath.get("/app/explodedJar/directory2"),
            AbsoluteUnixPath.get("/app/explodedJar/directory2/class4.class"),
            AbsoluteUnixPath.get("/app/explodedJar/directory2/directory3"),
            AbsoluteUnixPath.get("/app/explodedJar/directory2/directory3/class3.class"),
            AbsoluteUnixPath.get("/app/explodedJar/directory4"));
  }

  @Test
  public void testExplodeMode_standard_withoutClassPathInManifest()
      throws IOException, URISyntaxException {
    Path standardJar =
        Paths.get(Resources.getResource(STANDARD_JAR_WITHOUT_CLASS_PATH_MANIFEST).toURI());
    Path destDir = temporaryFolder.newFolder().toPath();
    List<FileEntriesLayer> layers = JarProcessor.explodeStandardJar(standardJar, destDir);

    // Validate that only two layers created.
    assertThat(layers.size()).isEqualTo(2);

    FileEntriesLayer resourcesLayer = layers.get(0);
    FileEntriesLayer classesLayer = layers.get(1);

    // Validate resources layer.
    // TODO: Validate order of file paths once
    // https://github.com/GoogleContainerTools/jib/issues/2821 is fixed.
    assertThat(resourcesLayer.getName()).isEqualTo("resources");
    List<AbsoluteUnixPath> actualResourcesPaths =
        resourcesLayer
            .getEntries()
            .stream()
            .map(FileEntry::getExtractionPath)
            .collect(Collectors.toList());
    assertThat(actualResourcesPaths)
        .containsExactly(
            AbsoluteUnixPath.get("/app/explodedJar/META-INF/"),
            AbsoluteUnixPath.get("/app/explodedJar/META-INF/MANIFEST.MF"),
            AbsoluteUnixPath.get("/app/explodedJar/directory1"),
            AbsoluteUnixPath.get("/app/explodedJar/directory1/resource1.txt"),
            AbsoluteUnixPath.get("/app/explodedJar/directory2"),
            AbsoluteUnixPath.get("/app/explodedJar/directory2/directory3"),
            AbsoluteUnixPath.get("/app/explodedJar/directory2/directory3/resource2.sql"),
            AbsoluteUnixPath.get("/app/explodedJar/directory4"),
            AbsoluteUnixPath.get("/app/explodedJar/directory4/resource3.txt"),
            AbsoluteUnixPath.get("/app/explodedJar/resource4.sql"));

    // Validate classes layer.
    // TODO: Validate order of file paths once
    // https://github.com/GoogleContainerTools/jib/issues/2821 is fixed.
    assertThat(classesLayer.getName()).isEqualTo("classes");
    List<AbsoluteUnixPath> actualClassesPaths =
        classesLayer
            .getEntries()
            .stream()
            .map(FileEntry::getExtractionPath)
            .collect(Collectors.toList());
    assertThat(actualClassesPaths)
        .containsExactly(
            AbsoluteUnixPath.get("/app/explodedJar/META-INF"),
            AbsoluteUnixPath.get("/app/explodedJar/class5.class"),
            AbsoluteUnixPath.get("/app/explodedJar/directory1"),
            AbsoluteUnixPath.get("/app/explodedJar/directory1/class1.class"),
            AbsoluteUnixPath.get("/app/explodedJar/directory1/class2.class"),
            AbsoluteUnixPath.get("/app/explodedJar/directory2"),
            AbsoluteUnixPath.get("/app/explodedJar/directory2/class4.class"),
            AbsoluteUnixPath.get("/app/explodedJar/directory2/directory3"),
            AbsoluteUnixPath.get("/app/explodedJar/directory2/directory3/class3.class"),
            AbsoluteUnixPath.get("/app/explodedJar/directory4"));
  }

  @Test
  public void testExplodeMode_standard_withoutClassPathInManifest_containsOnlyClasses()
      throws IOException, URISyntaxException {
    Path standardJar = Paths.get(Resources.getResource(STANDARD_JAR_WITH_ONLY_CLASSES).toURI());
    Path destDir = temporaryFolder.newFolder().toPath();
    List<FileEntriesLayer> layers = JarProcessor.explodeStandardJar(standardJar, destDir);

    // Validate that only two layers created.
    assertThat(layers.size()).isEqualTo(2);

    FileEntriesLayer resourcesLayer = layers.get(0);
    FileEntriesLayer classesLayer = layers.get(1);

    // Validate resources layer
    // TODO: Validate order of file paths once
    // https://github.com/GoogleContainerTools/jib/issues/2821 is fixed.
    List<AbsoluteUnixPath> actualResourcesPath =
        resourcesLayer
            .getEntries()
            .stream()
            .map(FileEntry::getExtractionPath)
            .collect(Collectors.toList());
    assertThat(actualResourcesPath)
        .containsExactly(
            AbsoluteUnixPath.get("/app/explodedJar/META-INF"),
            AbsoluteUnixPath.get("/app/explodedJar/META-INF/MANIFEST.MF"));

    // Validate classes layer
    // TODO: Validate order of file paths once
    // https://github.com/GoogleContainerTools/jib/issues/2821 is fixed.
    List<AbsoluteUnixPath> actualClassesPath =
        classesLayer
            .getEntries()
            .stream()
            .map(FileEntry::getExtractionPath)
            .collect(Collectors.toList());
    assertThat(actualClassesPath)
        .containsExactly(
            AbsoluteUnixPath.get("/app/explodedJar/META-INF"),
            AbsoluteUnixPath.get("/app/explodedJar/class1.class"),
            AbsoluteUnixPath.get("/app/explodedJar/class2.class"));
  }
}
