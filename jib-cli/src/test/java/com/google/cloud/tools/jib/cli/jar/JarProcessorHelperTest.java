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
import org.gradle.internal.impldep.org.junit.Test;

/** Tests for {@link JarProcessorHelper}. */
public class JarProcessorHelperTest {

  private static final String SPRING_BOOT_JAR = "jar/spring-boot/springboot_sample.jar";
  private static final String STANDARD_JAR_EMPTY = "jar/standard/emptyStandardJar.jar";

  @Test
  public void testDetermineJarType_springBoot() throws IOException, URISyntaxException {
    Path springBootJar = Paths.get(Resources.getResource(SPRING_BOOT_JAR).toURI());
    JarProcessorHelper.JarType jarType = JarProcessorHelper.determineJarType(springBootJar);
    assertThat(jarType).isEqualTo(JarProcessorHelper.JarType.SPRING_BOOT);
  }

  @Test
  public void testDetermineJarType_standard() throws IOException, URISyntaxException {
    Path standardJar = Paths.get(Resources.getResource(STANDARD_JAR_EMPTY).toURI());
    JarProcessorHelper.JarType jarType = JarProcessorHelper.determineJarType(standardJar);
    assertThat(jarType).isEqualTo(JarProcessorHelper.JarType.STANDARD);
  }
}
