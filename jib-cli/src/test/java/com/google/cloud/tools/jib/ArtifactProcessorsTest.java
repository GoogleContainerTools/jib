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

package com.google.cloud.tools.jib;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.cloud.tools.jib.cli.ArtifactProcessor;
import com.google.cloud.tools.jib.cli.ArtifactProcessors;
import com.google.cloud.tools.jib.cli.CacheDirectories;
import com.google.cloud.tools.jib.cli.CommonArtifactCommandOptions;
import com.google.cloud.tools.jib.cli.Jar;
import com.google.cloud.tools.jib.cli.SharedArtifactCliOptions;
import com.google.cloud.tools.jib.cli.jar.ProcessingMode;
import com.google.cloud.tools.jib.cli.jar.SpringBootExplodedProcessor;
import com.google.cloud.tools.jib.cli.jar.SpringBootPackagedProcessor;
import com.google.cloud.tools.jib.cli.jar.StandardExplodedProcessor;
import com.google.cloud.tools.jib.cli.jar.StandardPackagedProcessor;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link ArtifactProcessors}. */
@RunWith(MockitoJUnitRunner.class)
public class ArtifactProcessorsTest {

  private static final String SPRING_BOOT = "jar/spring-boot/springboot_sample.jar";
  private static final String STANDARD = "jar/standard/emptyStandardJar.jar";
  private static final String STANDARD_WITH_INVALID_CLASS = "jar/standard/jarWithInvalidClass.jar";
  private static final String STANDARD_WITH_EMPTY_CLASS_FILE =
      "jar/standard/standardJarWithOnlyClasses.jar";
  private static final String JAVA_14_JAR = "jar/java14WithModuleInfo.jar";

  @Mock private CacheDirectories mockCacheDirectories;
  @Mock private Jar mockJarCommand;
  @Mock private CommonArtifactCommandOptions mockCommonArtifactCommandOptions;

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testFrom_standardExploded() throws IOException, URISyntaxException {
    Path jarPath = Paths.get(Resources.getResource(STANDARD).toURI());
    Path explodedJarRoot = temporaryFolder.getRoot().toPath();
    when(mockCacheDirectories.getExplodedJarDirectory()).thenReturn(explodedJarRoot);
    when(mockJarCommand.getMode()).thenReturn(ProcessingMode.exploded);

    ArtifactProcessor processor =
        ArtifactProcessors.fromJar(
            jarPath, mockCacheDirectories, mockJarCommand, mockCommonArtifactCommandOptions);

    verify(mockCacheDirectories).getExplodedJarDirectory();
    assertThat(processor).isInstanceOf(StandardExplodedProcessor.class);
  }

  @Test
  public void testFrom_standardPackaged() throws IOException, URISyntaxException {
    Path jarPath = Paths.get(Resources.getResource(STANDARD).toURI());
    when(mockJarCommand.getMode()).thenReturn(ProcessingMode.packaged);

    ArtifactProcessor processor =
        ArtifactProcessors.fromJar(
            jarPath, mockCacheDirectories, mockJarCommand, mockCommonArtifactCommandOptions);

    verifyNoInteractions(mockCacheDirectories);
    assertThat(processor).isInstanceOf(StandardPackagedProcessor.class);
  }

  @Test
  public void testFrom_springBootPackaged() throws IOException, URISyntaxException {
    Path jarPath = Paths.get(Resources.getResource(SPRING_BOOT).toURI());
    when(mockJarCommand.getMode()).thenReturn(ProcessingMode.packaged);

    ArtifactProcessor processor =
        ArtifactProcessors.fromJar(
            jarPath, mockCacheDirectories, mockJarCommand, mockCommonArtifactCommandOptions);

    verifyNoInteractions(mockCacheDirectories);
    assertThat(processor).isInstanceOf(SpringBootPackagedProcessor.class);
  }

  @Test
  public void testFrom_springBootExploded() throws IOException, URISyntaxException {
    Path jarPath = Paths.get(Resources.getResource(SPRING_BOOT).toURI());
    Path explodedJarRoot = temporaryFolder.getRoot().toPath();
    when(mockCacheDirectories.getExplodedJarDirectory()).thenReturn(explodedJarRoot);
    when(mockJarCommand.getMode()).thenReturn(ProcessingMode.exploded);

    ArtifactProcessor processor =
        ArtifactProcessors.fromJar(
            jarPath, mockCacheDirectories, mockJarCommand, mockCommonArtifactCommandOptions);

    verify(mockCacheDirectories).getExplodedJarDirectory();
    assertThat(processor).isInstanceOf(SpringBootExplodedProcessor.class);
  }

  @Test
  public void testFrom_incompatibleDefaultBaseImage() throws URISyntaxException {
    Path jarPath = Paths.get(Resources.getResource(JAVA_14_JAR).toURI());

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                ArtifactProcessors.fromJar(
                    jarPath,
                    mockCacheDirectories,
                    mockJarCommand,
                    mockCommonArtifactCommandOptions));
    assertThat(exception)
        .hasMessageThat()
        .startsWith("The input JAR (" + jarPath + ") is compiled with Java 14");
  }

  @Test
  public void testFrom_incompatibleDefaultBaseImage_baseImageSpecified()
      throws URISyntaxException, IOException {
    Path jarPath = Paths.get(Resources.getResource(JAVA_14_JAR).toURI());
    when(mockJarCommand.getMode()).thenReturn(ProcessingMode.exploded);
    when(mockCommonArtifactCommandOptions.getFrom()).thenReturn(Optional.of("base-image"));

    ArtifactProcessor processor =
        ArtifactProcessors.fromJar(
            jarPath, mockCacheDirectories, mockJarCommand, mockCommonArtifactCommandOptions);

    verify(mockCacheDirectories).getExplodedJarDirectory();
    assertThat(processor).isInstanceOf(StandardExplodedProcessor.class);
  }

  @Test
  public void testDetermineJavaMajorVersion_versionNotFound()
      throws URISyntaxException, IOException {
    Path jarPath = Paths.get(Resources.getResource(STANDARD).toURI());
    Integer version = ArtifactProcessors.determineJavaMajorVersion(jarPath);
    assertThat(version).isEqualTo(0);
  }

  @Test
  public void testDetermineJavaMajorVersion_invalidClassFile() throws URISyntaxException {
    Path jarPath = Paths.get(Resources.getResource(STANDARD_WITH_INVALID_CLASS).toURI());
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> ArtifactProcessors.determineJavaMajorVersion(jarPath));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("The class file (class1.class) is of an invalid format.");
  }

  @Test
  public void testDetermineJavaMajorVersion_emptyClassFile() throws URISyntaxException {
    Path jarPath = Paths.get(Resources.getResource(STANDARD_WITH_EMPTY_CLASS_FILE).toURI());
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> ArtifactProcessors.determineJavaMajorVersion(jarPath));
    assertThat(exception).hasMessageThat().startsWith("Reached end of class file (class1.class)");
  }
}
