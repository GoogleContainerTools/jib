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

import com.google.cloud.tools.jib.cli.jar.JarProcessor.JarType;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;

public class JarProcessorTest {

  private static final String SPRING_BOOT_RESOURCE_DIR = "jar/springboot/springBootJar.jar";
  private static final String REGULAR_RESOURCE_DIR = "jar/regular/regularJar.jar";

  @Test
  public void testDetermineJarType_springBoot() throws IOException, URISyntaxException {
    Path springBootJar = Paths.get(Resources.getResource(SPRING_BOOT_RESOURCE_DIR).toURI());
    JarType jarType = JarProcessor.determineJarType(springBootJar);
    assertThat(jarType).isEqualTo(JarType.SPRINGBOOT);
  }

  @Test
  public void testDetermineJarType_regular() throws IOException, URISyntaxException {
    Path regularJar = Paths.get(Resources.getResource(REGULAR_RESOURCE_DIR).toURI());
    JarType jarType = JarProcessor.determineJarType(regularJar);
    assertThat(jarType).isEqualTo(JarType.REGULAR);
  }
}
