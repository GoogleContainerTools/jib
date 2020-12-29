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

import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;

/** Tests for {@link JarProcessors}. */
public class JarProcessorsTest {

  private static final String SPRING_BOOT = "jar/spring-boot/springboot_sample.jar";
  private static final String STANDARD = "jar/standard/emptyStandardJar.jar";

  @Test
  public void testFrom_standardExploded() throws IOException, URISyntaxException {
    Path jarPath = Paths.get(Resources.getResource(STANDARD).toURI());
    JarProcessor processor =
        JarProcessors.from(jarPath, Paths.get("path/to/tempDirectory"), ProcessingMode.exploded);

    assertThat(processor).isInstanceOf(StandardExplodedProcessor.class);
    assertThat(((StandardExplodedProcessor) processor).getJarPath().getFileName().toString())
        .isEqualTo("emptyStandardJar.jar");
    assertThat(((StandardExplodedProcessor) processor).getTempDirectoryPath().toString())
        .isEqualTo(Paths.get("path/to/tempDirectory").toString());
  }

  @Test
  public void testFrom_standardPackaged() throws IOException, URISyntaxException {
    Path jarPath = Paths.get(Resources.getResource(STANDARD).toURI());
    JarProcessor processor =
        JarProcessors.from(jarPath, Paths.get("ignored"), ProcessingMode.packaged);

    assertThat(processor).isInstanceOf(StandardPackagedProcessor.class);
    assertThat(((StandardPackagedProcessor) processor).getJarPath().getFileName().toString())
        .isEqualTo("emptyStandardJar.jar");
  }

  @Test
  public void testFrom_springBootPackaged() throws IOException, URISyntaxException {
    Path jarPath = Paths.get(Resources.getResource(SPRING_BOOT).toURI());
    JarProcessor processor =
        JarProcessors.from(jarPath, Paths.get("ignored"), ProcessingMode.packaged);

    assertThat(processor).isInstanceOf(SpringBootPackagedProcessor.class);
    assertThat(((SpringBootPackagedProcessor) processor).getJarPath().getFileName().toString())
        .isEqualTo("springboot_sample.jar");
  }

  @Test
  public void testFrom_springBootExploded() throws IOException, URISyntaxException {
    Path jarPath = Paths.get(Resources.getResource(SPRING_BOOT).toURI());
    JarProcessor processor =
        JarProcessors.from(jarPath, Paths.get("path/to/tempDirectory"), ProcessingMode.exploded);

    assertThat(processor).isInstanceOf(SpringBootExplodedProcessor.class);
    assertThat(((SpringBootExplodedProcessor) processor).getJarPath().getFileName().toString())
        .isEqualTo("springboot_sample.jar");
    assertThat(((SpringBootExplodedProcessor) processor).getTempDirectoryPath().toString())
        .isEqualTo(Paths.get("path/to/tempDirectory").toString());
  }
}
