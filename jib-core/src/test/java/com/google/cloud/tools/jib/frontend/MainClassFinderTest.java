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

import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;

/** Test for MainClassFinder. */
public class MainClassFinderTest {

  @Test
  public void testFindMainClass_simple()
      throws URISyntaxException, MultipleClassesFoundException, IOException {
    Path rootDirectory = Paths.get(Resources.getResource("class-finder-tests/simple").toURI());
    String mainClass = MainClassFinder.findMainClass(rootDirectory.toString());
    Assert.assertEquals("HelloWorld", mainClass);
  }

  @Test
  public void testFindMainClass_subdirectories()
      throws URISyntaxException, MultipleClassesFoundException, IOException {
    Path rootDirectory =
        Paths.get(Resources.getResource("class-finder-tests/subdirectories").toURI());
    String mainClass = MainClassFinder.findMainClass(rootDirectory.toString());
    Assert.assertEquals("multi.layered.HelloWorld", mainClass);
  }

  @Test
  public void testFindMainClass_noClass()
      throws URISyntaxException, MultipleClassesFoundException, IOException {
    Path rootDirectory = Paths.get(Resources.getResource("class-finder-tests/no-main").toURI());
    String mainClass = MainClassFinder.findMainClass(rootDirectory.toString());
    Assert.assertEquals(null, mainClass);
  }

  @Test
  public void testFindMainClass_multiple() throws URISyntaxException, IOException {
    Path rootDirectory = Paths.get(Resources.getResource("class-finder-tests/multiple").toURI());
    try {
      MainClassFinder.findMainClass(rootDirectory.toString());
      Assert.fail();
    } catch (MultipleClassesFoundException ex) {
      Assert.assertTrue(
          ex.getMessage().contains("Multiple classes found while trying to infer main class: "));
      Assert.assertTrue(ex.getMessage().contains("multi.layered.HelloMoon"));
      Assert.assertTrue(ex.getMessage().contains("HelloWorld"));
    }
  }
}
