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
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Test;

/** Tests for {@link SpringBootPackagedModeProcessor}. */
public class SpringBootPackagedModeProcessorTest {

  private static final String SPRING_BOOT_JAR = "jar/spring-boot/springboot_sample.jar";

  @Test
  public void testCreateLayers() throws URISyntaxException {
    Path springBootJar = Paths.get(Resources.getResource(SPRING_BOOT_JAR).toURI());
    SpringBootPackagedModeProcessor springBootProcessor = new SpringBootPackagedModeProcessor();
    springBootProcessor.setJarPath(springBootJar);
    List<FileEntriesLayer> layers = springBootProcessor.createLayers();

    assertThat(layers.size()).isEqualTo(1);

    FileEntriesLayer jarLayer = layers.get(0);

    assertThat(jarLayer.getName()).isEqualTo("jar");
    assertThat(jarLayer.getEntries().size()).isEqualTo(1);
    assertThat(jarLayer.getEntries().get(0).getExtractionPath())
        .isEqualTo(AbsoluteUnixPath.get("/app/springboot_sample.jar"));
  }

  @Test
  public void testComputeEntrypoint() throws URISyntaxException {
    Path standardJar = Paths.get(Resources.getResource(SPRING_BOOT_JAR).toURI());
    SpringBootPackagedModeProcessor springBootProcessor = new SpringBootPackagedModeProcessor();
    springBootProcessor.setJarPath(standardJar);

    ImmutableList<String> actualEntrypoint = springBootProcessor.computeEntrypoint();

    assertThat(actualEntrypoint)
        .isEqualTo(ImmutableList.of("java", "-jar", "/app/springboot_sample.jar"));
  }
}
