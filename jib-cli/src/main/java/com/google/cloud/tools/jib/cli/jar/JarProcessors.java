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

import java.io.IOException;
import java.nio.file.Path;
import java.util.jar.JarFile;

/** Class to create a {@link JarProcessor} instance depending on jar type and processsing mode. */
public class JarProcessors {
  private static String SPRING_BOOT = "spring-boot";
  private static String STANDARD = "standard";

  /**
   * Creates a {@link JarProcessor} instance based on jar type and processing mode.
   *
   * @param jarPath path to the jar
   * @param temporaryDirectory path to temporary directory
   * @param mode processing mode
   * @return JarProcessor
   * @throws IOException if I/O error occurs when opening the jar file
   */
  public static JarProcessor from(Path jarPath, Path temporaryDirectory, ProcessingMode mode)
      throws IOException {
    String jarType = determineJarType(jarPath);
    if (jarType.equals(SPRING_BOOT) && mode.equals(ProcessingMode.packaged)) {
      SpringBootPackagedProcessor processor = new SpringBootPackagedProcessor();
      processor.setJarPath(jarPath);
      return processor;
    } else if (jarType.equals(SPRING_BOOT) && mode.equals(ProcessingMode.exploded)) {
      SpringBootExplodedProcessor processor = new SpringBootExplodedProcessor();
      processor.setJarPath(jarPath);
      processor.setTempDirectoryPath(temporaryDirectory);
      return processor;
    } else if (jarType.equals(STANDARD) && mode.equals(ProcessingMode.packaged)) {
      StandardPackagedProcessor processor = new StandardPackagedProcessor();
      processor.setJarPath(jarPath);
      return processor;
    } else {
      StandardExplodedProcessor processor = new StandardExplodedProcessor();
      processor.setJarPath(jarPath);
      processor.setTempDirectoryPath(temporaryDirectory);
      return processor;
    }
  }

  /**
   * Determines whether the jar is a spring boot or standard jar.
   *
   * @param jarPath path to the jar
   * @return the jar type
   * @throws IOException if I/O error occurs when opening the file
   */
  private static String determineJarType(Path jarPath) throws IOException {
    try (JarFile jarFile = new JarFile(jarPath.toFile())) {
      if (jarFile.getEntry("BOOT-INF") != null) {
        return SPRING_BOOT;
      }
      return STANDARD;
    }
  }
}
