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

package com.google.cloud.tools.jib.builder;

import com.google.common.collect.ImmutableList;
import java.util.List;

/** Builds an image entrypoint for the Java application. */
public class EntrypointBuilder {

  /**
   * Builds the container entrypoint.
   *
   * <p>The entrypoint is {@code java [jvm flags] -cp [classpaths] [main class]}.
   *
   * @param dependenciesExtractionPath extraction path of dependencies
   * @param resourcesExtractionPath extraction path of resources
   * @param classesExtractionPath extraction path of classes
   * @param jvmFlags the JVM flags to start the container with
   * @param mainClass the name of the main class to run on startup
   * @return a list of the entrypoint tokens
   */
  public static ImmutableList<String> makeEntrypoint(
      String dependenciesExtractionPath,
      String resourcesExtractionPath,
      String classesExtractionPath,
      List<String> jvmFlags,
      String mainClass) {
    // TODO: Entrypoint should be built and added to BuildConfiguration rather than built here.
    ImmutableList<String> classPaths =
        ImmutableList.of(
            dependenciesExtractionPath + "*", resourcesExtractionPath, classesExtractionPath);

    String classPathsString = String.join(":", classPaths);

    ImmutableList.Builder<String> entrypointBuilder =
        ImmutableList.builderWithExpectedSize(4 + jvmFlags.size());
    entrypointBuilder.add("java");
    entrypointBuilder.addAll(jvmFlags);
    entrypointBuilder.add("-cp");
    entrypointBuilder.add(classPathsString);
    entrypointBuilder.add(mainClass);
    return entrypointBuilder.build();
  }

  private EntrypointBuilder() {}
}
