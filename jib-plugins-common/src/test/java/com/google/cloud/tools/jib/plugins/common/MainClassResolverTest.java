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

import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
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

  @Mock private ProjectProperties mockProjectProperties;

  @Before
  public void setup() {
    Mockito.when(mockProjectProperties.getPluginName()).thenReturn("jib-plugin");
    Mockito.when(mockProjectProperties.getJarPluginName()).thenReturn("jar-plugin");
  }

  @Test
  public void testResolveMainClass_validMainClassConfigured()
      throws MainClassInferenceException, IOException {
    Assert.assertEquals(
        "configured.main.class",
        MainClassResolver.resolveMainClass("configured.main.class", mockProjectProperties));
    Mockito.verify(mockProjectProperties, Mockito.never()).log(Mockito.any());
  }

  @Test
  public void testResolveMainClass_invalidMainClassConfigured() throws IOException {
    try {
      MainClassResolver.resolveMainClass("In Val id", mockProjectProperties);
      Assert.fail();

    } catch (MainClassInferenceException ex) {
      MatcherAssert.assertThat(
          ex.getMessage(),
          CoreMatchers.containsString(
              "'mainClass' configured in jib-plugin is not a valid Java class: In Val id"));

      Mockito.verify(mockProjectProperties, Mockito.never()).log(Mockito.any());
    }
  }

  @Test
  public void testResolveMainClass_validMainClassFromJarPlugin()
      throws MainClassInferenceException, IOException {
    Mockito.when(mockProjectProperties.getMainClassFromJarPlugin())
        .thenReturn("main.class.from.jar");
    Assert.assertEquals(
        "main.class.from.jar", MainClassResolver.resolveMainClass(null, mockProjectProperties));

    String info =
        "Searching for main class... Add a 'mainClass' configuration to 'jib-plugin' to "
            + "improve build speed.";
    Mockito.verify(mockProjectProperties).log(LogEvent.info(info));
  }

  @Test
  public void testResolveMainClass_multipleInferredWithInvalidMainClassFromJarPlugin()
      throws URISyntaxException, IOException {
    Mockito.when(mockProjectProperties.getMainClassFromJarPlugin()).thenReturn("${start-class}");
    Mockito.when(mockProjectProperties.getClassFiles())
        .thenReturn(
            new DirectoryWalker(
                    Paths.get(Resources.getResource("core/class-finder-tests/multiple").toURI()))
                .walk()
                .asList());

    try {
      MainClassResolver.resolveMainClass(null, mockProjectProperties);
      Assert.fail();

    } catch (MainClassInferenceException ex) {
      MatcherAssert.assertThat(
          ex.getMessage(),
          CoreMatchers.containsString(
              "Multiple valid main classes were found: HelloWorld, multi.layered.HelloMoon"));

      String info1 =
          "Searching for main class... Add a 'mainClass' configuration to 'jib-plugin' to "
              + "improve build speed.";
      String info2 =
          "Could not find a valid main class from jar-plugin; looking into all class files to "
              + "infer main class.";
      String warn =
          "'mainClass' configured in jar-plugin is not a valid Java class: ${start-class}";
      Mockito.verify(mockProjectProperties).log(LogEvent.info(info1));
      Mockito.verify(mockProjectProperties).log(LogEvent.info(info2));
      Mockito.verify(mockProjectProperties).log(LogEvent.warn(warn));
    }
  }

  @Test
  public void testResolveMainClass_multipleInferredWithoutMainClassFromJarPlugin()
      throws URISyntaxException, IOException {
    Mockito.when(mockProjectProperties.getClassFiles())
        .thenReturn(
            new DirectoryWalker(
                    Paths.get(Resources.getResource("core/class-finder-tests/multiple").toURI()))
                .walk()
                .asList());
    try {
      MainClassResolver.resolveMainClass(null, mockProjectProperties);
      Assert.fail();

    } catch (MainClassInferenceException ex) {
      MatcherAssert.assertThat(
          ex.getMessage(),
          CoreMatchers.containsString(
              "Multiple valid main classes were found: HelloWorld, multi.layered.HelloMoon"));

      String info1 =
          "Searching for main class... Add a 'mainClass' configuration to 'jib-plugin' to "
              + "improve build speed.";
      String info2 =
          "Could not find a valid main class from jar-plugin; looking into all class files to "
              + "infer main class.";
      Mockito.verify(mockProjectProperties).log(LogEvent.info(info1));
      Mockito.verify(mockProjectProperties).log(LogEvent.info(info2));
    }
  }

  @Test
  public void testResolveMainClass_noneInferredWithInvalidMainClassFromJarPlugin()
      throws IOException {
    Mockito.when(mockProjectProperties.getMainClassFromJarPlugin()).thenReturn("${start-class}");
    Mockito.when(mockProjectProperties.getClassFiles())
        .thenReturn(ImmutableList.of(Paths.get("ignored")));
    try {
      MainClassResolver.resolveMainClass(null, mockProjectProperties);
      Assert.fail();

    } catch (MainClassInferenceException ex) {
      MatcherAssert.assertThat(
          ex.getMessage(), CoreMatchers.containsString("Main class was not found"));

      String info1 =
          "Searching for main class... Add a 'mainClass' configuration to 'jib-plugin' to "
              + "improve build speed.";
      String info2 =
          "Could not find a valid main class from jar-plugin; looking into all class files to "
              + "infer main class.";
      String warn =
          "'mainClass' configured in jar-plugin is not a valid Java class: ${start-class}";
      Mockito.verify(mockProjectProperties).log(LogEvent.info(info1));
      Mockito.verify(mockProjectProperties).log(LogEvent.info(info2));
      Mockito.verify(mockProjectProperties).log(LogEvent.warn(warn));
    }
  }

  @Test
  public void testResolveMainClass_noneInferredWithoutMainClassFromJar() throws IOException {
    Mockito.when(mockProjectProperties.getClassFiles())
        .thenReturn(ImmutableList.of(Paths.get("ignored")));
    try {
      MainClassResolver.resolveMainClass(null, mockProjectProperties);
      Assert.fail();

    } catch (MainClassInferenceException ex) {
      MatcherAssert.assertThat(
          ex.getMessage(), CoreMatchers.containsString("Main class was not found"));

      String info1 =
          "Searching for main class... Add a 'mainClass' configuration to 'jib-plugin' to "
              + "improve build speed.";
      String info2 =
          "Could not find a valid main class from jar-plugin; looking into all class files to "
              + "infer main class.";
      Mockito.verify(mockProjectProperties).log(LogEvent.info(info1));
      Mockito.verify(mockProjectProperties).log(LogEvent.info(info2));
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
