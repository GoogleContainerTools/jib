/*
 * Copyright 2018 Google LLC. All rights reserved.
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

package com.google.cloud.tools.jib.frontend;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Constructs an image entrypoint for the Java application. */
public class JavaEntrypointConstructor {

  public static final String DEFAULT_DEPENDENCIES_PATH_ON_IMAGE = "/app/libs/";
  public static final String DEFAULT_RESOURCES_PATH_ON_IMAGE = "/app/resources/";
  public static final String DEFAULT_CLASSES_PATH_ON_IMAGE = "/app/classes/";

  public static List<String> makeDefaultEntrypoint(List<String> jvmFlags, String mainClass) {
    return makeEntrypoint(
        Arrays.asList(
            DEFAULT_DEPENDENCIES_PATH_ON_IMAGE + "*",
            DEFAULT_RESOURCES_PATH_ON_IMAGE,
            DEFAULT_CLASSES_PATH_ON_IMAGE),
        jvmFlags,
        mainClass);
  }

  /**
   * Constructs the container entrypoint.
   *
   * <p>The entrypoint is {@code java [jvm flags] -cp [classpaths] [main class]}.
   *
   * @param classpathElements paths to add to the classpath (will be separated by {@code :}
   * @param jvmFlags the JVM flags to start the container with
   * @param mainClass the name of the main class to run on startup
   * @return a list of the entrypoint tokens
   */
  public static List<String> makeEntrypoint(
      List<String> classpathElements, List<String> jvmFlags, String mainClass) {
    String classpathString = String.join(":", classpathElements);

    List<String> entrypointBuilder = new ArrayList<>(4 + jvmFlags.size());
    entrypointBuilder.add("java");
    entrypointBuilder.addAll(jvmFlags);
    entrypointBuilder.add("-cp");
    entrypointBuilder.add(classpathString);
    entrypointBuilder.add(mainClass);
    return entrypointBuilder;
  }

  private JavaEntrypointConstructor() {}
}
