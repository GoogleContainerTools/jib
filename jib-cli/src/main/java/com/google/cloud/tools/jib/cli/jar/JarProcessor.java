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

public class JarProcessor {

  /**
   * Jar Type.
   *
   * <ul>
   *   <li>{@code REGULAR} a regular jar.
   *   <li>{@code SPRING_BOOT} a spring boot fat jar.
   * </ul>
   */
  public enum JarType {
    STANDARD,
    SPRING_BOOT;
  }

  /**
   * Determines whether the jar is a spring boot or regular jar, given a path to the jar.
   *
   * @param jarPath path to the jar.
   * @return the jar type.
   * @throws IOException if I/O error occurs when opening the file.
   */
  public static JarType determineJarType(Path jarPath) throws IOException {
    JarFile jarFile = new JarFile(jarPath.toFile());
    if (jarFile.getEntry("BOOT-INF") != null) {
      return JarType.SPRING_BOOT;
    }
    return JarType.STANDARD;
  }
}
