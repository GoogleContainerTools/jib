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
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.cloud.tools.jib.cli.CacheDirectories;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link JarProcessors}. */
@RunWith(MockitoJUnitRunner.class)
public class JarProcessorsTest {

  private static final String SPRING_BOOT = "jar/spring-boot/springboot_sample.jar";
  private static final String STANDARD = "jar/standard/emptyStandardJar.jar";
  private static final String JAVA_14_JAR = "jar/java14WithModuleInfo.jar";

  @Mock private CacheDirectories mockCacheDirectories;

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testFrom_standardExploded() throws IOException, URISyntaxException {
    Path jarPath = Paths.get(Resources.getResource(STANDARD).toURI());
    Path explodedJarRoot = temporaryFolder.getRoot().toPath();
    when(mockCacheDirectories.getExplodedJarDirectory()).thenReturn(explodedJarRoot);

    JarProcessor processor =
        JarProcessors.from(jarPath, mockCacheDirectories, ProcessingMode.exploded);

    verify(mockCacheDirectories).getExplodedJarDirectory();
    assertThat(processor).isInstanceOf(StandardExplodedProcessor.class);
  }

  @Test
  public void testFrom_standardPackaged() throws IOException, URISyntaxException {
    Path jarPath = Paths.get(Resources.getResource(STANDARD).toURI());
    JarProcessor processor =
        JarProcessors.from(jarPath, mockCacheDirectories, ProcessingMode.packaged);

    verifyNoInteractions(mockCacheDirectories);
    assertThat(processor).isInstanceOf(StandardPackagedProcessor.class);
  }

  @Test
  public void testFrom_springBootPackaged() throws IOException, URISyntaxException {
    Path jarPath = Paths.get(Resources.getResource(SPRING_BOOT).toURI());
    JarProcessor processor =
        JarProcessors.from(jarPath, mockCacheDirectories, ProcessingMode.packaged);

    verifyNoInteractions(mockCacheDirectories);
    assertThat(processor).isInstanceOf(SpringBootPackagedProcessor.class);
  }

  @Test
  public void testFrom_springBootExploded() throws IOException, URISyntaxException {
    Path jarPath = Paths.get(Resources.getResource(SPRING_BOOT).toURI());
    Path explodedJarRoot = temporaryFolder.getRoot().toPath();
    when(mockCacheDirectories.getExplodedJarDirectory()).thenReturn(explodedJarRoot);

    JarProcessor processor =
        JarProcessors.from(jarPath, mockCacheDirectories, ProcessingMode.exploded);

    verify(mockCacheDirectories).getExplodedJarDirectory();
    assertThat(processor).isInstanceOf(SpringBootExplodedProcessor.class);
  }

  @Test
  public void testFrom_incompatibleBaseImage() throws URISyntaxException {
    Path jarPath = Paths.get(Resources.getResource(JAVA_14_JAR).toURI());
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> JarProcessors.from(jarPath, mockCacheDirectories, ProcessingMode.exploded));
    assertThat(exception)
        .hasMessageThat()
        .startsWith("The input JAR (" + jarPath + ") is compiled with Java 14");
  }

  @Test
  public void testGetMajorJavaVersion_versionNotFound() throws URISyntaxException, IOException {
    Path jarPath = Paths.get(Resources.getResource(STANDARD).toURI());
    Integer version = JarProcessors.getJavaMajorVersion(jarPath);
    assertThat(version).isEqualTo(0);
  }
}
