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

import com.google.cloud.tools.jib.builder.BuildLogger;
import com.google.cloud.tools.jib.builder.SourceFilesConfiguration;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Test for MainClassFinder. */
@RunWith(MockitoJUnitRunner.class)
public class MainClassFinderTest {

  @Mock private BuildLogger mockBuildLogger;
  @Mock private SourceFilesConfiguration mockSourceFilesConfiguration;
  @Mock private ProjectProperties projectProperties;

  private final List<Path> fakeClassesPath = Collections.singletonList(Paths.get("a/b/c"));

  @Before
  public void setup() {
    Mockito.when(projectProperties.getLogger()).thenReturn(mockBuildLogger);
    Mockito.when(projectProperties.getPluginName()).thenReturn("plugin");
    Mockito.when(projectProperties.getSourceFilesConfiguration())
        .thenReturn(mockSourceFilesConfiguration);
  }

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

  @Test
  public void testResolveMainClass() {
    Mockito.when(projectProperties.getMainClassFromJar()).thenReturn("some.main.class");
    Assert.assertEquals(
        "some.main.class", MainClassFinder.resolveMainClass(null, projectProperties));
    Assert.assertEquals(
        "configured", MainClassFinder.resolveMainClass("configured", projectProperties));
  }

  @Test
  public void testResolveMainClass_notValid() {
    Mockito.when(projectProperties.getMainClassFromJar()).thenReturn("${start-class}");
    Mockito.when(mockSourceFilesConfiguration.getClassesFiles()).thenReturn(fakeClassesPath);
    Assert.assertEquals(
        "${start-class}", MainClassFinder.resolveMainClass(null, projectProperties));
    Mockito.verify(mockBuildLogger).warn("'mainClass' is not a valid Java class : ${start-class}");
  }

  @Test
  public void testResolveMainClass_multipleInferredWithBackup() throws Throwable {
    Mockito.when(projectProperties.getMainClassFromJar()).thenReturn("${start-class}");
    Mockito.when(mockSourceFilesConfiguration.getClassesFiles())
        .thenReturn(
            Collections.singletonList(
                Paths.get(Resources.getResource("class-finder-tests/multiple").toURI())));

    Assert.assertEquals(
        "${start-class}", MainClassFinder.resolveMainClass(null, projectProperties));
    Mockito.verify(mockBuildLogger).warn("'mainClass' is not a valid Java class : ${start-class}");
  }

  @Test
  public void testResolveMainClass_multipleInferredWithoutBackup() throws URISyntaxException {
    Mockito.when(projectProperties.getMainClassFromJar()).thenReturn(null);
    Mockito.when(mockSourceFilesConfiguration.getClassesFiles())
        .thenReturn(
            Collections.singletonList(
                Paths.get(Resources.getResource("class-finder-tests/multiple").toURI())));

    try {
      MainClassFinder.resolveMainClass(null, projectProperties);
      Assert.fail();
    } catch (IllegalStateException ex) {
      Mockito.verify(projectProperties)
          .getMainClassErrorMessage(
              "Multiple valid main classes were found: HelloWorld, multi.layered.HelloMoon");
    }
  }

  @Test
  public void testResolveMainClass_noneInferredWithBackup() {
    Mockito.when(projectProperties.getMainClassFromJar()).thenReturn("${start-class}");
    Mockito.when(mockSourceFilesConfiguration.getClassesFiles()).thenReturn(fakeClassesPath);

    Assert.assertEquals(
        "${start-class}", MainClassFinder.resolveMainClass(null, projectProperties));
    Mockito.verify(mockBuildLogger).warn("'mainClass' is not a valid Java class : ${start-class}");
  }

  @Test
  public void testResolveMainClass_noneInferredWithoutBackup() {
    Mockito.when(projectProperties.getMainClassFromJar()).thenReturn(null);
    Mockito.when(mockSourceFilesConfiguration.getClassesFiles()).thenReturn(fakeClassesPath);

    try {
      MainClassFinder.resolveMainClass(null, projectProperties);
      Assert.fail();
    } catch (IllegalStateException ex) {
      Mockito.verify(projectProperties).getMainClassErrorMessage("Main class was not found");
    }
  }
}
