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

import com.google.cloud.tools.jib.cli.CacheDirectories;
import com.google.cloud.tools.jib.cli.Jar;
import com.google.common.annotations.VisibleForTesting;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
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
   * @param cacheDirectories the location of the relevant caches
   * @param jarOptions jar cli options
   * @return JarProcessor
   * @throws IOException if I/O error occurs when opening the jar file
   */
  public static JarProcessor from(Path jarPath, CacheDirectories cacheDirectories, Jar jarOptions)
      throws IOException {
    Integer jarJavaVersion = determineJavaMajorVersion(jarPath);
    if (jarJavaVersion > 11 && !jarOptions.getFrom().isPresent()) {
      throw new IllegalStateException(
          "The input JAR ("
              + jarPath
              + ") is compiled with Java "
              + jarJavaVersion
              + ", but the default base image only supports versions up to Java 11. Specify a custom base image with --from.");
    }

    String jarType = determineJarType(jarPath);
    ProcessingMode mode = jarOptions.getMode();
    if (jarType.equals(SPRING_BOOT) && mode.equals(ProcessingMode.packaged)) {
      return new SpringBootPackagedProcessor(jarPath, jarJavaVersion);
    } else if (jarType.equals(SPRING_BOOT) && mode.equals(ProcessingMode.exploded)) {
      return new SpringBootExplodedProcessor(
          jarPath, cacheDirectories.getExplodedJarDirectory(), jarJavaVersion);
    } else if (jarType.equals(STANDARD) && mode.equals(ProcessingMode.packaged)) {
      return new StandardPackagedProcessor(jarPath, jarJavaVersion);
    } else {
      return new StandardExplodedProcessor(
          jarPath, cacheDirectories.getExplodedJarDirectory(), jarJavaVersion);
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
  @VisibleForTesting
  static Integer determineJavaMajorVersion(Path jarPath) throws IOException {
    try (JarFile jarFile = new JarFile(jarPath.toFile())) {
      Enumeration<JarEntry> jarEntries = jarFile.entries();
      while (jarEntries.hasMoreElements()) {
        String jarEntry = jarEntries.nextElement().toString();
        if (jarEntry.endsWith(".class") && !jarEntry.endsWith("module-info.class")) {
          try (URLClassLoader loader = new URLClassLoader(new URL[] {jarPath.toUri().toURL()});
              DataInputStream classFile =
                  new DataInputStream(loader.getResourceAsStream(jarEntry))) {

            // Check magic number
            if (classFile == null || classFile.readInt() != 0xCAFEBABE) {
              throw new IllegalArgumentException(
                  "The class file (" + jarEntry + ") is of an invalid format.");
            }

            // Skip over minor version
            classFile.skipBytes(2);

            int majorVersion = classFile.readUnsignedShort();
            int javaVersion = (majorVersion - 45) + 1;
            return javaVersion;
          } catch (EOFException ex) {
            throw new IllegalArgumentException(
                "Reached end of class file ("
                    + jarEntry
                    + ") before being able to read the java major version. Make sure that the file is of the correct format.");
          }
        }
      }
      return VERSION_NOT_FOUND;
    }
  }
}
