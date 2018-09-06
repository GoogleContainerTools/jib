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

package com.google.cloud.tools.jib.frontend;

import com.google.cloud.tools.jib.JibLogger;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link MainClassFinder}. */
@RunWith(MockitoJUnitRunner.class)
public class MainClassFinderTest {

  @Mock private JibLogger mockBuildLogger;

  @Test
  public void testFindMainClass_simple() throws URISyntaxException {
    Path rootDirectory = Paths.get(Resources.getResource("class-finder-tests/simple").toURI());
    MainClassFinder.Result mainClassFinderResult =
        new MainClassFinder(ImmutableList.of(rootDirectory.resolve("child")), mockBuildLogger)
            .find();
    Assert.assertTrue(mainClassFinderResult.isSuccess());
    Assert.assertThat(
        mainClassFinderResult.getFoundMainClass(), CoreMatchers.containsString("HelloWorld"));
  }

  @Test
  public void testFindMainClass_subdirectories() throws URISyntaxException {
    Path rootDirectory =
        Paths.get(Resources.getResource("class-finder-tests/subdirectories").toURI());
    MainClassFinder.Result mainClassFinderResult =
        new MainClassFinder(ImmutableList.of(rootDirectory.resolve("child")), mockBuildLogger)
            .find();
    Assert.assertTrue(mainClassFinderResult.isSuccess());
    Assert.assertThat(
        mainClassFinderResult.getFoundMainClass(),
        CoreMatchers.containsString("multi.layered.HelloWorld"));
  }

  @Test
  public void testFindMainClass_noClass() throws URISyntaxException {
    Path rootDirectory = Paths.get(Resources.getResource("class-finder-tests/no-main").toURI());
    MainClassFinder.Result mainClassFinderResult =
        new MainClassFinder(ImmutableList.of(rootDirectory.resolve("child")), mockBuildLogger)
            .find();
    Assert.assertFalse(mainClassFinderResult.isSuccess());
    Assert.assertEquals(
        MainClassFinder.Result.ErrorType.MAIN_CLASS_NOT_FOUND,
        mainClassFinderResult.getErrorType());
  }

  @Test
  public void testFindMainClass_multiple() throws URISyntaxException {
    Path rootDirectory = Paths.get(Resources.getResource("class-finder-tests/multiple").toURI());
    MainClassFinder.Result mainClassFinderResult =
        new MainClassFinder(ImmutableList.of(rootDirectory.resolve("child")), mockBuildLogger)
            .find();
    Assert.assertFalse(mainClassFinderResult.isSuccess());
    Assert.assertEquals(
        MainClassFinder.Result.ErrorType.MULTIPLE_MAIN_CLASSES,
        mainClassFinderResult.getErrorType());
    Assert.assertEquals(2, mainClassFinderResult.getFoundMainClasses().size());
    Assert.assertTrue(
        mainClassFinderResult.getFoundMainClasses().contains("multi.layered.HelloMoon"));
    Assert.assertTrue(mainClassFinderResult.getFoundMainClasses().contains("HelloWorld"));
  }

  @Test
  public void testFindMainClass_extension() throws URISyntaxException {
    Path rootDirectory = Paths.get(Resources.getResource("class-finder-tests/extension").toURI());
    MainClassFinder.Result mainClassFinderResult =
        new MainClassFinder(ImmutableList.of(rootDirectory.resolve("child")), mockBuildLogger)
            .find();
    Assert.assertTrue(mainClassFinderResult.isSuccess());
    Assert.assertThat(
        mainClassFinderResult.getFoundMainClass(), CoreMatchers.containsString("main.MainClass"));
  }

  @Test
  public void testFindMainClass_importedMethods() throws URISyntaxException {
    Path rootDirectory =
        Paths.get(Resources.getResource("class-finder-tests/imported-methods").toURI());
    MainClassFinder.Result mainClassFinderResult =
        new MainClassFinder(ImmutableList.of(rootDirectory.resolve("child")), mockBuildLogger)
            .find();
    Assert.assertTrue(mainClassFinderResult.isSuccess());
    Assert.assertThat(
        mainClassFinderResult.getFoundMainClass(), CoreMatchers.containsString("main.MainClass"));
  }

  @Test
  public void testFindMainClass_externalClasses() throws URISyntaxException {
    Path rootDirectory =
        Paths.get(Resources.getResource("class-finder-tests/external-classes").toURI());
    MainClassFinder.Result mainClassFinderResult =
        new MainClassFinder(ImmutableList.of(rootDirectory.resolve("child")), mockBuildLogger)
            .find();
    Assert.assertTrue(mainClassFinderResult.isSuccess());
    Assert.assertThat(
        mainClassFinderResult.getFoundMainClass(), CoreMatchers.containsString("main.MainClass"));
  }

  @Test
  public void testFindMainClass_innerClasses() throws URISyntaxException {
    Path rootDirectory =
        Paths.get(Resources.getResource("class-finder-tests/inner-classes").toURI());
    MainClassFinder.Result mainClassFinderResult =
        new MainClassFinder(ImmutableList.of(rootDirectory.resolve("child")), mockBuildLogger)
            .find();
    Assert.assertTrue(mainClassFinderResult.isSuccess());
    Assert.assertThat(
        mainClassFinderResult.getFoundMainClass(),
        CoreMatchers.containsString("HelloWorld$InnerClass"));
  }
}
