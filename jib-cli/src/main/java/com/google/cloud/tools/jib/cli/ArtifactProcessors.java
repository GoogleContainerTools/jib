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

package com.google.cloud.tools.jib.cli;

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.cli.jar.ProcessingMode;
import com.google.cloud.tools.jib.cli.jar.SpringBootExplodedProcessor;
import com.google.cloud.tools.jib.cli.jar.SpringBootPackagedProcessor;
import com.google.cloud.tools.jib.cli.jar.StandardExplodedProcessor;
import com.google.cloud.tools.jib.cli.jar.StandardPackagedProcessor;
import com.google.cloud.tools.jib.cli.war.StandardWarExplodedProcessor;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Class to create a {@link com.google.cloud.tools.jib.cli.ArtifactProcessor} instance depending on
 * jar type and processing mode.
 */
public class ArtifactProcessors {
  private static String SPRING_BOOT = "spring-boot";
  private static String STANDARD = "standard";
  private static Integer VERSION_NOT_FOUND = 0;
  private static final String DEFAULT_JETTY_APP_ROOT = "/var/lib/jetty/webapps/ROOT";

  /**
   * Creates a {@link com.google.cloud.tools.jib.cli.ArtifactProcessor} instance based on jar type
   * and processing mode.
   *
   * @param jarPath path to the jar
   * @param cacheDirectories the location of the relevant caches
   * @param jarOptions jar cli options
   * @param commonArtifactCommandOptions common cli options shared between jar and war command
   * @return ArtifactProcessor
   * @throws IOException if I/O error occurs when opening the jar file
   */
  public static ArtifactProcessor fromJar(
      Path jarPath,
      CacheDirectories cacheDirectories,
      Jar jarOptions,
      CommonArtifactCommandOptions commonArtifactCommandOptions)
      throws IOException {
    Integer jarJavaVersion = determineJavaMajorVersion(jarPath);
    if (jarJavaVersion > 11 && !commonArtifactCommandOptions.getFrom().isPresent()) {
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
   * Creates a {@link com.google.cloud.tools.jib.cli.ArtifactProcessor} instance based on jar type
   * and processing mode.
   *
   * @param warPath path to the jar
   * @param cacheDirectories the location of the relevant caches
   * @param warOptions jar cli options
   * @param sharedArtifactCliOptions shared artifact cli options
   * @return ArtifactProcessor
   * @throws IOException if I/O error occurs when opening the jar file
   */
  public static ArtifactProcessor fromWar(
      Path warPath,
      CacheDirectories cacheDirectories,
      War warOptions,
      SharedArtifactCliOptions sharedArtifactCliOptions)
      throws IOException {
    Integer warJavaVersion = determineJavaMajorVersion(warPath);
    Optional<Path> appRoot = warOptions.getAppRoot();
    Optional<String> baseImage = sharedArtifactCliOptions.getFrom();
    if (baseImage.isPresent() && !baseImage.get().startsWith("jetty")) {
      if (!warOptions.getAppRoot().isPresent()) {
        throw new IllegalStateException("provide app root");
      }
    }
    AbsoluteUnixPath appRootPath =
        appRoot.isPresent()
            ? AbsoluteUnixPath.fromPath(appRoot.get())
            : AbsoluteUnixPath.get(DEFAULT_JETTY_APP_ROOT);
    return new StandardWarExplodedProcessor(
        warPath, cacheDirectories.getExplodedJarDirectory(), warJavaVersion, appRootPath);
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
  public static Integer determineJavaMajorVersion(Path jarPath) throws IOException {
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
