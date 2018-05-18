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
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/** Test for MainClassFinder. */
public class MainClassFinderTest {

  @Test
  public void testFindMainClass_simple() throws URISyntaxException, IOException {
    Path rootDirectory = Paths.get(Resources.getResource("class-finder-tests/simple").toURI());
    List<String> mainClasses = MainClassFinder.findMainClasses(rootDirectory);
    Assert.assertEquals(1, mainClasses.size());
    Assert.assertTrue(mainClasses.contains("HelloWorld"));
  }

  @Test
  public void testFindMainClass_subdirectories() throws URISyntaxException, IOException {
    Path rootDirectory =
        Paths.get(Resources.getResource("class-finder-tests/subdirectories").toURI());
    List<String> mainClasses = MainClassFinder.findMainClasses(rootDirectory);
    Assert.assertEquals(1, mainClasses.size());
    Assert.assertTrue(mainClasses.contains("multi.layered.HelloWorld"));
  }

  @Test
  public void testFindMainClass_noClass() throws URISyntaxException, IOException {
    Path rootDirectory = Paths.get(Resources.getResource("class-finder-tests/no-main").toURI());
    List<String> mainClasses = MainClassFinder.findMainClasses(rootDirectory);
    Assert.assertTrue(mainClasses.isEmpty());
  }

  @Test
  public void testFindMainClass_multiple() throws URISyntaxException, IOException {
    Path rootDirectory = Paths.get(Resources.getResource("class-finder-tests/multiple").toURI());
    List<String> mainClasses = MainClassFinder.findMainClasses(rootDirectory);
    Assert.assertEquals(2, mainClasses.size());
    Assert.assertTrue(mainClasses.contains("multi.layered.HelloMoon"));
    Assert.assertTrue(mainClasses.contains("HelloWorld"));
  }
}
