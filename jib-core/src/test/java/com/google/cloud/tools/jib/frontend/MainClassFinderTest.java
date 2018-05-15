/*
 * Copyright 2018 Google LLC. All Rights Reserved.
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

import com.google.cloud.tools.jib.frontend.MainClassFinder.MultipleClassesFoundException;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Assert;
import org.junit.Test;

/** Test for MainClassFinder. */
public class MainClassFinderTest {

  @Test
  public void testFindMainClass_simple()
      throws URISyntaxException, MultipleClassesFoundException, IOException {
    Path rootDirectory = Paths.get(Resources.getResource("application/classes").toURI());
    try (Stream<Path> classStream = Files.walk(rootDirectory)) {
      List<Path> classFiles = classStream.collect(Collectors.toList());

      String mainClass = MainClassFinder.findMainClass(classFiles, rootDirectory.toString());

      Assert.assertEquals("HelloWorld", mainClass);
    }
  }

  @Test
  public void testFindMainClass_none() throws URISyntaxException, MultipleClassesFoundException {
    Path rootDirectory = Paths.get(Resources.getResource("application/classes").toURI());
    List<Path> classFiles = new ArrayList<>();

    String mainClass = MainClassFinder.findMainClass(classFiles, rootDirectory.toString());

    Assert.assertEquals(null, mainClass);
  }

  @Test
  public void testFindMainClass_multiple() throws URISyntaxException, IOException {
    Path rootDirectory = Paths.get(Resources.getResource("application/classes").toURI());
    try (Stream<Path> classStream = Files.walk(rootDirectory)) {
      List<Path> classFiles = classStream.collect(Collectors.toList());
      classFiles.add(
          Paths.get(Resources.getResource("application/classes/HelloWorld.class").toURI()));
      try {
        MainClassFinder.findMainClass(classFiles, rootDirectory.toString());
        Assert.fail();
      } catch (MultipleClassesFoundException ex) {
        Assert.assertEquals(
            "Multiple classes found while trying to infer main class", ex.getMessage());
      }
    }
  }
}
