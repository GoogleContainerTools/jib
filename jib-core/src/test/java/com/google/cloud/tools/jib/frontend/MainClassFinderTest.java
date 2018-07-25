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
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Test for MainClassFinder. */
@RunWith(MockitoJUnitRunner.class)
public class MainClassFinderTest {

  @Mock private BuildLogger mockBuildLogger;
  @Mock private ProjectProperties mockProjectProperties;
  @Mock private HelpfulSuggestions mockHelpfulSuggestions;

  private final ImmutableList<Path> fakeClassesPath = ImmutableList.of(Paths.get("a/b/c"));

  @Before
  public void setup() {
    Mockito.when(mockProjectProperties.getLogger()).thenReturn(mockBuildLogger);
    Mockito.when(mockProjectProperties.getPluginName()).thenReturn("plugin");
    Mockito.when(mockProjectProperties.getMainClassHelpfulSuggestions(ArgumentMatchers.any()))
        .thenReturn(mockHelpfulSuggestions);
    Mockito.when(mockProjectProperties.getJarPluginName()).thenReturn("jar-plugin");
  }

  @Test
  public void testFindMainClass_simple() throws URISyntaxException, IOException {
    Path rootDirectory = Paths.get(Resources.getResource("class-finder-tests/simple").toURI());
    List<String> mainClasses = MainClassFinder.findMainClasses(rootDirectory, mockBuildLogger);
    Assert.assertEquals(1, mainClasses.size());
    Assert.assertTrue(mainClasses.contains("HelloWorld"));
  }

  @Test
  public void testFindMainClass_subdirectories() throws URISyntaxException, IOException {
    Path rootDirectory =
        Paths.get(Resources.getResource("class-finder-tests/subdirectories").toURI());
    List<String> mainClasses = MainClassFinder.findMainClasses(rootDirectory, mockBuildLogger);
    Assert.assertEquals(1, mainClasses.size());
    Assert.assertTrue(mainClasses.contains("multi.layered.HelloWorld"));
  }

  @Test
  public void testFindMainClass_noClass() throws URISyntaxException, IOException {
    Path rootDirectory = Paths.get(Resources.getResource("class-finder-tests/no-main").toURI());
    List<String> mainClasses = MainClassFinder.findMainClasses(rootDirectory, mockBuildLogger);
    Assert.assertTrue(mainClasses.isEmpty());
  }

  @Test
  public void testFindMainClass_multiple() throws URISyntaxException, IOException {
    Path rootDirectory = Paths.get(Resources.getResource("class-finder-tests/multiple").toURI());
    List<String> mainClasses = MainClassFinder.findMainClasses(rootDirectory, mockBuildLogger);
    Assert.assertEquals(2, mainClasses.size());
    Assert.assertTrue(mainClasses.contains("multi.layered.HelloMoon"));
    Assert.assertTrue(mainClasses.contains("HelloWorld"));
  }

  @Test
  public void testFindMainClass_extension() throws URISyntaxException, IOException {
    Path rootDirectory = Paths.get(Resources.getResource("class-finder-tests/extension").toURI());
    List<String> mainClasses = MainClassFinder.findMainClasses(rootDirectory, mockBuildLogger);
    Assert.assertEquals(1, mainClasses.size());
    Assert.assertTrue(mainClasses.contains("main.MainClass"));
  }

  @Test
  public void testFindMainClass_importedMethods() throws URISyntaxException, IOException {
    Path rootDirectory =
        Paths.get(Resources.getResource("class-finder-tests/imported-methods").toURI());
    List<String> mainClasses = MainClassFinder.findMainClasses(rootDirectory, mockBuildLogger);
    Assert.assertEquals(1, mainClasses.size());
    Assert.assertTrue(mainClasses.contains("main.MainClass"));
  }

  @Test
  public void testFindMainClass_externalClasses() throws URISyntaxException, IOException {
    Path rootDirectory =
        Paths.get(Resources.getResource("class-finder-tests/external-classes").toURI());
    List<String> mainClasses = MainClassFinder.findMainClasses(rootDirectory, mockBuildLogger);
    Assert.assertEquals(1, mainClasses.size());
    Assert.assertTrue(mainClasses.contains("main.MainClass"));
  }

  @Test
  public void testFindMainClass_innerClasses() throws URISyntaxException, IOException {
    Path rootDirectory =
        Paths.get(Resources.getResource("class-finder-tests/inner-classes").toURI());
    List<String> mainClasses = MainClassFinder.findMainClasses(rootDirectory, mockBuildLogger);
    Assert.assertEquals(1, mainClasses.size());
    Assert.assertTrue(mainClasses.contains("HelloWorld$InnerClass"));
  }

  @Test
  public void testResolveMainClass() throws MainClassInferenceException {
    Mockito.when(mockProjectProperties.getMainClassFromJar()).thenReturn("some.main.class");
    Assert.assertEquals(
        "some.main.class", MainClassFinder.resolveMainClass(null, mockProjectProperties));
    Assert.assertEquals(
        "configured", MainClassFinder.resolveMainClass("configured", mockProjectProperties));
  }

  @Test
  public void testResolveMainClass_notValid() throws MainClassInferenceException {
    Mockito.when(mockProjectProperties.getMainClassFromJar()).thenReturn("${start-class}");
    Mockito.when(mockProjectProperties.getClassesLayerEntry())
        .thenReturn(new LayerEntry(fakeClassesPath, "ignored"));
    Assert.assertEquals(
        "${start-class}", MainClassFinder.resolveMainClass(null, mockProjectProperties));
    Mockito.verify(mockBuildLogger).warn("'mainClass' is not a valid Java class : ${start-class}");
  }

  @Test
  public void testResolveMainClass_multipleInferredWithBackup()
      throws MainClassInferenceException, URISyntaxException {
    Mockito.when(mockProjectProperties.getMainClassFromJar()).thenReturn("${start-class}");
    Mockito.when(mockProjectProperties.getClassesLayerEntry())
        .thenReturn(
            new LayerEntry(
                ImmutableList.of(
                    Paths.get(Resources.getResource("class-finder-tests/multiple/multi").toURI()),
                    Paths.get(
                        Resources.getResource("class-finder-tests/multiple/HelloWorld.class")
                            .toURI()),
                    Paths.get(
                        Resources.getResource("class-finder-tests/multiple/NotMain.class")
                            .toURI())),
                "ignored"));
    Assert.assertEquals(
        "${start-class}", MainClassFinder.resolveMainClass(null, mockProjectProperties));
    Mockito.verify(mockBuildLogger).warn("'mainClass' is not a valid Java class : ${start-class}");
  }

  @Test
  public void testResolveMainClass_multipleInferredWithoutBackup() throws URISyntaxException {
    Mockito.when(mockProjectProperties.getMainClassFromJar()).thenReturn(null);
    Mockito.when(mockProjectProperties.getClassesLayerEntry())
        .thenReturn(
            new LayerEntry(
                ImmutableList.of(
                    Paths.get(Resources.getResource("class-finder-tests/multiple/multi").toURI()),
                    Paths.get(
                        Resources.getResource("class-finder-tests/multiple/HelloWorld.class")
                            .toURI()),
                    Paths.get(
                        Resources.getResource("class-finder-tests/multiple/NotMain.class")
                            .toURI())),
                "ignored"));
    try {
      MainClassFinder.resolveMainClass(null, mockProjectProperties);
      Assert.fail();
    } catch (MainClassInferenceException ex) {
      Mockito.verify(mockProjectProperties)
          .getMainClassHelpfulSuggestions(
              "Multiple valid main classes were found: HelloWorld, multi.layered.HelloMoon");
    }
  }

  @Test
  public void testResolveMainClass_noneInferredWithBackup() throws MainClassInferenceException {
    Mockito.when(mockProjectProperties.getMainClassFromJar()).thenReturn("${start-class}");
    Mockito.when(mockProjectProperties.getClassesLayerEntry())
        .thenReturn(new LayerEntry(ImmutableList.of(), "ignored"));
    Assert.assertEquals(
        "${start-class}", MainClassFinder.resolveMainClass(null, mockProjectProperties));
    Mockito.verify(mockBuildLogger).warn("'mainClass' is not a valid Java class : ${start-class}");
  }

  @Test
  public void testResolveMainClass_noneInferredWithoutBackup() {
    Mockito.when(mockProjectProperties.getClassesLayerEntry())
        .thenReturn(new LayerEntry(ImmutableList.of(), "ignored"));
    try {
      MainClassFinder.resolveMainClass(null, mockProjectProperties);
      Assert.fail();
    } catch (MainClassInferenceException ex) {
      Mockito.verify(mockProjectProperties)
          .getMainClassHelpfulSuggestions("Main class was not found");
    }
  }
}
