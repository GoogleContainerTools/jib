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

import com.google.cloud.tools.jib.filesystem.TempDirectoryProvider;
import com.google.cloud.tools.jib.plugins.common.IncompatibleBaseImageJavaVersionException;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Class to create a {@link JarProcessor} instance depending on jar type and processing mode. */
public class JarProcessors {
  private static String SPRING_BOOT = "spring-boot";
  private static String STANDARD = "standard";
  private static Integer VERSION_NOT_FOUND = 0;

  /**
   * Creates a {@link JarProcessor} instance based on jar type and processing mode.
   *
   * @param jarPath path to the jar
   * @param temporaryDirectoryProvider temporary directory provider
   * @param mode processing mode
   * @return JarProcessor
   * @throws IOException if I/O error occurs when opening the jar file
   * @throws IncompatibleBaseImageJavaVersionException if the base image java version cannot support
   *     this build
   */
  public static JarProcessor from(
      Path jarPath, TempDirectoryProvider temporaryDirectoryProvider, ProcessingMode mode)
      throws IOException, IncompatibleBaseImageJavaVersionException {
    Integer jarVersion = getVersion(jarPath);
    if (jarVersion > 11) {
      throw new IncompatibleBaseImageJavaVersionException(11, jarVersion);
    }

    String jarType = determineJarType(jarPath);
    if (jarType.equals(SPRING_BOOT) && mode.equals(ProcessingMode.packaged)) {
      return new SpringBootPackagedProcessor(jarPath);
    } else if (jarType.equals(SPRING_BOOT) && mode.equals(ProcessingMode.exploded)) {
      return new SpringBootExplodedProcessor(jarPath, temporaryDirectoryProvider.newDirectory());
    } else if (jarType.equals(STANDARD) && mode.equals(ProcessingMode.packaged)) {
      return new StandardPackagedProcessor(jarPath);
    } else {
      return new StandardExplodedProcessor(jarPath, temporaryDirectoryProvider.newDirectory());
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

  /**
   * Determines the java version of JAR. Derives the version from the first .class file it finds in
   * the JAR.
   *
   * @param jarPath path to the jar
   * @return java version
   * @throws IOException if I/O exception thrown when opening the jar file
   */
  private static Integer getVersion(Path jarPath) throws IOException {
    try (JarFile jarFile = new JarFile(jarPath.toFile())) {
      Enumeration<JarEntry> jarEntries = jarFile.entries();
      while (jarEntries.hasMoreElements()) {
        String jarEntry = jarEntries.nextElement().toString();
        if (jarEntry.endsWith(".class")) {
          URLClassLoader loader = new URLClassLoader(new URL[] {jarPath.toUri().toURL()});
          try (InputStream classFileInfo = loader.getResourceAsStream(jarEntry)) {
            DataInputStream data = new DataInputStream(classFileInfo);

            // Check magic number
            if (data.readInt() != 0xCAFEBABE) {
              throw new IOException("Invalid class file format.");
            }

            // Skip over minor version
            data.skipBytes(2);

            int majorVersion = data.readUnsignedShort();
            int javaVersion = (majorVersion - 45) + 1;
            return javaVersion;
          }
        }
      }
      return VERSION_NOT_FOUND;
    }
  }
}
