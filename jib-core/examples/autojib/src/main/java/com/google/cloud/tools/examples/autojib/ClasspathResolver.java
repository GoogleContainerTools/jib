/*
 * Copyright 2019 Google LLC.
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

package com.google.cloud.tools.examples.autojib;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Resolves files on the current classpath. */
class ClasspathResolver {

  /**
   * Gets the classpath files.
   *
   * @return the list of classpath files
   * @throws IOException if an I/O exception occurs
   */
  static ImmutableList<Path> getClasspathFiles() throws IOException {

    String javaClasspath = System.getProperty("java.class.path");
    if (javaClasspath == null) {
      throw new IllegalStateException("Cannot find classpath");
    }
    List<String> classpath = Lists.reverse(Lists.newArrayList(Splitter.on(":").split(javaClasspath)));

    ImmutableList.Builder<Path> classpathFiles = ImmutableList.builder();
    // Reverses the classpathElements due to classpath precedence.
    for (String classpathElement : classpath) {
      classpathFiles.addAll(getClasspathFiles(classpathElement));
    }
    return classpathFiles.build();
  }

  /**
   * Gets the files for a classpath element. If the classpath element is a file, resolves that file;
   * if the classpath element is a directory, lists the files in that directory.
   *
   * @param classpathElement the classpath element
   * @return the list of files for that classpath element
   * @throws IOException if an I/O exception occurs
   */
  private static List<Path> getClasspathFiles(String classpathElement) throws IOException {
    Path classpathFile = Paths.get(classpathElement);
    if (Files.notExists(classpathFile)) {
      return Collections.emptyList();
    }
    if (Files.isDirectory(classpathFile)) {
      // If classpathElement is a directory, adds all the files in that directory.
      try (Stream<Path> classpathDirectoryFiles = Files.list(classpathFile)) {
        return classpathDirectoryFiles.collect(Collectors.toList());
      }
    }
    return Collections.singletonList(classpathFile);
  }

  private ClasspathResolver() {}
}
