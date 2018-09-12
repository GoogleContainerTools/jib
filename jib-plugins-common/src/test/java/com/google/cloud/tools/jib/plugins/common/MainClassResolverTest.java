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

package com.google.cloud.tools.jib.plugins.common;

import com.google.cloud.tools.jib.JibLogger;
import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Test for {@link MainClassResolver}. */
@RunWith(MockitoJUnitRunner.class)
public class MainClassResolverTest {

  @Mock private JibLogger mockBuildLogger;
  @Mock private ProjectProperties mockProjectProperties;
  @Mock private JavaLayerConfigurations mockJavaLayerConfigurations;

  private final Path FAKE_CLASSES_PATH = Paths.get("a/b/c");

  @Before
  public void setup() {
    Mockito.when(mockProjectProperties.getLogger()).thenReturn(mockBuildLogger);
    Mockito.when(mockProjectProperties.getPluginName()).thenReturn("plugin");
    Mockito.when(mockProjectProperties.getJarPluginName()).thenReturn("jar-plugin");
    Mockito.when(mockProjectProperties.getJavaLayerConfigurations())
        .thenReturn(mockJavaLayerConfigurations);
  }

  @Test
  public void testResolveMainClass() throws MainClassInferenceException {
    Mockito.when(mockProjectProperties.getMainClassFromJar()).thenReturn("some.main.class");
    Assert.assertEquals(
        "some.main.class", MainClassResolver.resolveMainClass(null, mockProjectProperties));
    Assert.assertEquals(
        "configured", MainClassResolver.resolveMainClass("configured", mockProjectProperties));
  }

  @Test
  public void testResolveMainClass_notValid() throws MainClassInferenceException {
    Mockito.when(mockProjectProperties.getMainClassFromJar()).thenReturn("${start-class}");
    Mockito.when(mockProjectProperties.getJavaLayerConfigurations().getClassLayerEntries())
        .thenReturn(ImmutableList.of(new LayerEntry(FAKE_CLASSES_PATH, Paths.get("ignored"))));
    Assert.assertEquals(
        "${start-class}", MainClassResolver.resolveMainClass(null, mockProjectProperties));
    Mockito.verify(mockBuildLogger).warn("'mainClass' is not a valid Java class : ${start-class}");
  }

  @Test
  public void testResolveMainClass_multipleInferredWithBackup()
      throws MainClassInferenceException, URISyntaxException, IOException {
    Mockito.when(mockProjectProperties.getMainClassFromJar()).thenReturn("${start-class}");
    Mockito.when(mockProjectProperties.getJavaLayerConfigurations().getClassLayerEntries())
        .thenReturn(
            new DirectoryWalker(
                    Paths.get(Resources.getResource("class-finder-tests/multiple").toURI()))
                .walk()
                .stream()
                .map(path -> new LayerEntry(path, Paths.get("ignored")))
                .collect(ImmutableList.toImmutableList()));
    Assert.assertEquals(
        "${start-class}", MainClassResolver.resolveMainClass(null, mockProjectProperties));
    Mockito.verify(mockBuildLogger).warn("'mainClass' is not a valid Java class : ${start-class}");
  }

  @Test
  public void testResolveMainClass_multipleInferredWithoutBackup()
      throws URISyntaxException, IOException {
    Mockito.when(mockProjectProperties.getMainClassFromJar()).thenReturn(null);
    Mockito.when(mockProjectProperties.getJavaLayerConfigurations().getClassLayerEntries())
        .thenReturn(
            new DirectoryWalker(
                    Paths.get(Resources.getResource("class-finder-tests/multiple").toURI()))
                .walk()
                .stream()
                .map(path -> new LayerEntry(path, Paths.get("ignored")))
                .collect(ImmutableList.toImmutableList()));
    try {
      MainClassResolver.resolveMainClass(null, mockProjectProperties);
      Assert.fail();

    } catch (MainClassInferenceException ex) {
      Assert.assertThat(
          ex.getMessage(),
          CoreMatchers.containsString(
              "Multiple valid main classes were found: HelloWorld, multi.layered.HelloMoon"));
    }
  }

  @Test
  public void testResolveMainClass_noneInferredWithBackup() throws MainClassInferenceException {
    Mockito.when(mockProjectProperties.getMainClassFromJar()).thenReturn("${start-class}");
    Mockito.when(mockProjectProperties.getJavaLayerConfigurations().getClassLayerEntries())
        .thenReturn(ImmutableList.of(new LayerEntry(Paths.get("ignored"), Paths.get("ignored"))));
    Assert.assertEquals(
        "${start-class}", MainClassResolver.resolveMainClass(null, mockProjectProperties));
    Mockito.verify(mockBuildLogger).warn("'mainClass' is not a valid Java class : ${start-class}");
  }

  @Test
  public void testResolveMainClass_noneInferredWithoutBackup() {
    Mockito.when(mockJavaLayerConfigurations.getClassLayerEntries())
        .thenReturn(ImmutableList.of(new LayerEntry(Paths.get("ignored"), Paths.get("ignored"))));
    try {
      MainClassResolver.resolveMainClass(null, mockProjectProperties);
      Assert.fail();

    } catch (MainClassInferenceException ex) {
      Assert.assertThat(ex.getMessage(), CoreMatchers.containsString("Main class was not found"));
    }
  }

  @Test
  public void testValidJavaClassRegex() {
    Assert.assertTrue(MainClassResolver.isValidJavaClass("my.Class"));
    Assert.assertTrue(MainClassResolver.isValidJavaClass("my.java_Class$valid"));
    Assert.assertTrue(MainClassResolver.isValidJavaClass("multiple.package.items"));
    Assert.assertTrue(MainClassResolver.isValidJavaClass("is123.valid"));
    Assert.assertFalse(MainClassResolver.isValidJavaClass("${start-class}"));
    Assert.assertFalse(MainClassResolver.isValidJavaClass("123not.Valid"));
    Assert.assertFalse(MainClassResolver.isValidJavaClass("{class}"));
    Assert.assertFalse(MainClassResolver.isValidJavaClass("not valid"));
  }
}
