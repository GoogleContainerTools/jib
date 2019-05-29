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

import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.event.JibEvent;
import com.google.cloud.tools.jib.event.events.LogEvent;
import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;
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

  private static final Path FAKE_CLASSES_PATH = Paths.get("a/b/c");

  @Mock private Consumer<JibEvent> mockJibEventConsumer;
  @Mock private ProjectProperties mockProjectProperties;

  @Before
  public void setup() {
    Mockito.when(mockProjectProperties.getEventHandlers())
        .thenReturn(EventHandlers.builder().add(mockJibEventConsumer).build());
    Mockito.when(mockProjectProperties.getPluginName()).thenReturn("plugin");
    Mockito.when(mockProjectProperties.getJarPluginName()).thenReturn("jar-plugin");
  }

  @Test
  public void testResolveMainClass() throws MainClassInferenceException, IOException {
    Mockito.when(mockProjectProperties.getMainClassFromJar()).thenReturn("some.main.class");
    Assert.assertEquals(
        "some.main.class", MainClassResolver.resolveMainClass(null, mockProjectProperties));
    Assert.assertEquals(
        "configured", MainClassResolver.resolveMainClass("configured", mockProjectProperties));
  }

  @Test
  public void testResolveMainClass_notValid() throws MainClassInferenceException, IOException {
    Mockito.when(mockProjectProperties.getMainClassFromJar()).thenReturn("${start-class}");
    Mockito.when(mockProjectProperties.getClassFiles())
        .thenReturn(ImmutableList.of(FAKE_CLASSES_PATH));
    Assert.assertEquals(
        "${start-class}", MainClassResolver.resolveMainClass(null, mockProjectProperties));
    Mockito.verify(mockJibEventConsumer)
        .accept(LogEvent.warn("'mainClass' is not a valid Java class : ${start-class}"));
  }

  @Test
  public void testResolveMainClass_multipleInferredWithBackup()
      throws MainClassInferenceException, URISyntaxException, IOException {
    Mockito.when(mockProjectProperties.getMainClassFromJar()).thenReturn("${start-class}");
    Mockito.when(mockProjectProperties.getClassFiles())
        .thenReturn(
            new DirectoryWalker(
                    Paths.get(Resources.getResource("core/class-finder-tests/multiple").toURI()))
                .walk()
                .asList());
    Assert.assertEquals(
        "${start-class}", MainClassResolver.resolveMainClass(null, mockProjectProperties));
    Mockito.verify(mockJibEventConsumer)
        .accept(LogEvent.warn("'mainClass' is not a valid Java class : ${start-class}"));
  }

  @Test
  public void testResolveMainClass_multipleInferredWithoutBackup()
      throws URISyntaxException, IOException {
    Mockito.when(mockProjectProperties.getMainClassFromJar()).thenReturn(null);
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
      Assert.assertThat(
          ex.getMessage(),
          CoreMatchers.containsString(
              "Multiple valid main classes were found: HelloWorld, multi.layered.HelloMoon"));
    }
  }

  @Test
  public void testResolveMainClass_noneInferredWithBackup()
      throws MainClassInferenceException, IOException {
    Mockito.when(mockProjectProperties.getMainClassFromJar()).thenReturn("${start-class}");
    Mockito.when(mockProjectProperties.getClassFiles())
        .thenReturn(ImmutableList.of(Paths.get("ignored")));
    Assert.assertEquals(
        "${start-class}", MainClassResolver.resolveMainClass(null, mockProjectProperties));
    Mockito.verify(mockJibEventConsumer)
        .accept(LogEvent.warn("'mainClass' is not a valid Java class : ${start-class}"));
  }

  @Test
  public void testResolveMainClass_noneInferredWithoutBackup() throws IOException {
    Mockito.when(mockProjectProperties.getClassFiles())
        .thenReturn(ImmutableList.of(Paths.get("ignored")));
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
