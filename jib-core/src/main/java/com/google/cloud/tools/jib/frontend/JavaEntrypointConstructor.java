/*
 * Copyright 2018 Google LLC.
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

import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.filesystem.RelativeUnixPath;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Constructs an image entrypoint for the Java application. */
public class JavaEntrypointConstructor {

  public static final RelativeUnixPath DEFAULT_RELATIVE_RESOURCES_PATH_ON_IMAGE =
      RelativeUnixPath.get("resources");
  public static final RelativeUnixPath DEFAULT_RELATIVE_CLASSES_PATH_ON_IMAGE =
      RelativeUnixPath.get("classes");
  public static final RelativeUnixPath DEFAULT_RELATIVE_DEPENDENCIES_PATH_ON_IMAGE =
      RelativeUnixPath.get("libs");

  public static List<String> makeDefaultEntrypoint(
      AbsoluteUnixPath appRoot, List<String> jvmFlags, String mainClass) {
    return makeEntrypoint(
        Arrays.asList(
            appRoot.resolve(DEFAULT_RELATIVE_RESOURCES_PATH_ON_IMAGE).toString(),
            appRoot.resolve(DEFAULT_RELATIVE_CLASSES_PATH_ON_IMAGE).toString(),
            appRoot.resolve(DEFAULT_RELATIVE_DEPENDENCIES_PATH_ON_IMAGE).resolve("*").toString()),
        jvmFlags,
        mainClass);
  }

  /**
   * Constructs the container entrypoint for the gcr.io/distroless/jetty base image.
   *
   * @return ["java", "-jar", "/jetty/start.jar"]
   * @see <a href="https://github.com/GoogleContainerTools/distroless/blob/master/java/jetty/BUILD">
   *     https://github.com/GoogleContainerTools/distroless/blob/master/java/jetty/BUILD</a>
   */
  // TODO: inherit CMD and ENTRYPOINT from the base image and remove this.
  public static List<String> makeDistrolessJettyEntrypoint() {
    return Arrays.asList("java", "-jar", "/jetty/start.jar");
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

    List<String> entrypoint = new ArrayList<>(4 + jvmFlags.size());
    entrypoint.add("java");
    entrypoint.addAll(jvmFlags);
    entrypoint.add("-cp");
    entrypoint.add(classpathString);
    entrypoint.add(mainClass);
    return entrypoint;
  }

  private JavaEntrypointConstructor() {}
}
