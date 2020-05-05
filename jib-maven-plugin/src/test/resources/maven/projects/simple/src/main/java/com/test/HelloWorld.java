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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(
                HelloWorld.class.getResourceAsStream("/world"), StandardCharsets.UTF_8))) {
      String world = reader.readLine();
      System.out.println(greeting + ", " + world + ". " + (args.length > 0 ? args[0] : ""));
      Path worldFilePath = Paths.get("/app/resources/world");
      if (worldFilePath.toFile().exists()) {
        System.out.println(Files.getLastModifiedTime(worldFilePath).toString());
      }

      // Prints the contents of the extra files.
      if (Files.exists(Paths.get("/foo"))) {
        System.out.println(
            PosixFilePermissions.toString(Files.getPosixFilePermissions(Paths.get("/foo"))));
        System.out.println(
            PosixFilePermissions.toString(Files.getPosixFilePermissions(Paths.get("/bar/cat"))));
        System.out.println(
            new String(Files.readAllBytes(Paths.get("/foo")), StandardCharsets.UTF_8));
        System.out.println(
            new String(Files.readAllBytes(Paths.get("/bar/cat")), StandardCharsets.UTF_8));
        System.out.println(Files.getLastModifiedTime(Paths.get("/foo")).toString());
        System.out.println(Files.getLastModifiedTime(Paths.get("/bar/cat")).toString());
      }
      // Prints the contents of the files in the second extra directory.
      if (Files.exists(Paths.get("/custom/target/baz"))) {
        System.out.println(
            new String(
                Files.readAllBytes(Paths.get("/custom/target/baz")), StandardCharsets.UTF_8));
        System.out.println(Files.getLastModifiedTime(Paths.get("/custom/target/baz")).toString());
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

      Package pack = HelloWorld.class.getPackage();
      if (pack.getImplementationTitle() != null) {
        System.out.println("Implementation-Title: " + pack.getImplementationTitle());
        System.out.println("Implementation-Version: " + pack.getImplementationVersion());
      }
    }
  }
}
