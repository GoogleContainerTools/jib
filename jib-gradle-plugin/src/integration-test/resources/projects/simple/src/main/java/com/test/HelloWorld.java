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

package com.test;

import dependency.Greeting;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;

/** Example class that uses a dependency and a resource file. */
public class HelloWorld {

  public static void main(String[] args) throws IOException, URISyntaxException {
    // 'Greeting' comes from the dependency artfiact.
    String greeting = Greeting.getGreeting();

    // Gets the contents of the resource file 'world'.
    ClassLoader classLoader = HelloWorld.class.getClassLoader();
    Path worldFile = Paths.get(classLoader.getResource("world").toURI());
    String world = new String(Files.readAllBytes(worldFile), StandardCharsets.UTF_8);

    System.out.println(greeting + ", " + world + ". " + (args.length > 0 ? args[0] : ""));

    // Prints the contents of the extra files.
    if (Files.exists(Paths.get("/foo"))) {
      if (System.getenv("usePermissions") != null
          && System.getenv("usePermissions").equals("true")) {
        System.out.println(
            PosixFilePermissions.toString(Files.getPosixFilePermissions(Paths.get("/foo"))));
        System.out.println(
            PosixFilePermissions.toString(Files.getPosixFilePermissions(Paths.get("/bar/cat"))));
      }
      System.out.println(new String(Files.readAllBytes(Paths.get("/foo")), StandardCharsets.UTF_8));
      System.out.println(
          new String(Files.readAllBytes(Paths.get("/bar/cat")), StandardCharsets.UTF_8));
    }

    // Prints jvm flags
    for (String jvmFlag : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
      System.out.println(jvmFlag);
    }

    if (System.getenv("env1") != null) {
      System.out.println(System.getenv("env1"));
    }
    if (System.getenv("env2") != null) {
      System.out.println(System.getenv("env2"));
    }
  }
}
