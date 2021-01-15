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

package com.google.cloud.tools.jib.api;

import com.google.cloud.tools.jib.api.MainClassFinder.Result;
import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link MainClassFinder}. */
@RunWith(MockitoJUnitRunner.class)
public class MainClassFinderTest {

  @Mock private Consumer<LogEvent> logEventConsumer;

  @Test
  public void testFindMainClass_simple() throws URISyntaxException, IOException {
    Path rootDirectory = Paths.get(Resources.getResource("core/class-finder-tests/simple").toURI());
    MainClassFinder.Result mainClassFinderResult =
        MainClassFinder.find(new DirectoryWalker(rootDirectory).walk(), logEventConsumer);
    Assert.assertSame(Result.Type.MAIN_CLASS_FOUND, mainClassFinderResult.getType());
    MatcherAssert.assertThat(
        mainClassFinderResult.getFoundMainClass(), CoreMatchers.containsString("HelloWorld"));
  }

  @Test
  public void testFindMainClass_subdirectories() throws URISyntaxException, IOException {
    Path rootDirectory =
        Paths.get(Resources.getResource("core/class-finder-tests/subdirectories").toURI());
    MainClassFinder.Result mainClassFinderResult =
        MainClassFinder.find(new DirectoryWalker(rootDirectory).walk(), logEventConsumer);
    Assert.assertSame(Result.Type.MAIN_CLASS_FOUND, mainClassFinderResult.getType());
    MatcherAssert.assertThat(
        mainClassFinderResult.getFoundMainClass(),
        CoreMatchers.containsString("multi.layered.HelloWorld"));
  }

  @Test
  public void testFindMainClass_noClass() throws URISyntaxException, IOException {
    Path rootDirectory =
        Paths.get(Resources.getResource("core/class-finder-tests/no-main").toURI());
    MainClassFinder.Result mainClassFinderResult =
        MainClassFinder.find(new DirectoryWalker(rootDirectory).walk(), logEventConsumer);
    Assert.assertEquals(Result.Type.MAIN_CLASS_NOT_FOUND, mainClassFinderResult.getType());
  }

  @Test
  public void testFindMainClass_multiple() throws URISyntaxException, IOException {
    Path rootDirectory =
        Paths.get(Resources.getResource("core/class-finder-tests/multiple").toURI());
    MainClassFinder.Result mainClassFinderResult =
        MainClassFinder.find(new DirectoryWalker(rootDirectory).walk(), logEventConsumer);
    Assert.assertEquals(Result.Type.MULTIPLE_MAIN_CLASSES, mainClassFinderResult.getType());
    Assert.assertEquals(2, mainClassFinderResult.getFoundMainClasses().size());
    Assert.assertTrue(
        mainClassFinderResult.getFoundMainClasses().contains("multi.layered.HelloMoon"));
    Assert.assertTrue(mainClassFinderResult.getFoundMainClasses().contains("HelloWorld"));
  }

  @Test
  public void testFindMainClass_extension() throws URISyntaxException, IOException {
    Path rootDirectory =
        Paths.get(Resources.getResource("core/class-finder-tests/extension").toURI());
    MainClassFinder.Result mainClassFinderResult =
        MainClassFinder.find(new DirectoryWalker(rootDirectory).walk(), logEventConsumer);
    Assert.assertSame(Result.Type.MAIN_CLASS_FOUND, mainClassFinderResult.getType());
    MatcherAssert.assertThat(
        mainClassFinderResult.getFoundMainClass(), CoreMatchers.containsString("main.MainClass"));
  }

  @Test
  public void testFindMainClass_importedMethods() throws URISyntaxException, IOException {
    Path rootDirectory =
        Paths.get(Resources.getResource("core/class-finder-tests/imported-methods").toURI());
    MainClassFinder.Result mainClassFinderResult =
        MainClassFinder.find(new DirectoryWalker(rootDirectory).walk(), logEventConsumer);
    Assert.assertSame(Result.Type.MAIN_CLASS_FOUND, mainClassFinderResult.getType());
    MatcherAssert.assertThat(
        mainClassFinderResult.getFoundMainClass(), CoreMatchers.containsString("main.MainClass"));
  }

  @Test
  public void testFindMainClass_externalClasses() throws URISyntaxException, IOException {
    Path rootDirectory =
        Paths.get(Resources.getResource("core/class-finder-tests/external-classes").toURI());
    MainClassFinder.Result mainClassFinderResult =
        MainClassFinder.find(new DirectoryWalker(rootDirectory).walk(), logEventConsumer);
    Assert.assertSame(Result.Type.MAIN_CLASS_FOUND, mainClassFinderResult.getType());
    MatcherAssert.assertThat(
        mainClassFinderResult.getFoundMainClass(), CoreMatchers.containsString("main.MainClass"));
  }

  @Test
  public void testFindMainClass_innerClasses() throws URISyntaxException, IOException {
    Path rootDirectory =
        Paths.get(Resources.getResource("core/class-finder-tests/inner-classes").toURI());
    MainClassFinder.Result mainClassFinderResult =
        MainClassFinder.find(new DirectoryWalker(rootDirectory).walk(), logEventConsumer);
    Assert.assertSame(Result.Type.MAIN_CLASS_FOUND, mainClassFinderResult.getType());
    MatcherAssert.assertThat(
        mainClassFinderResult.getFoundMainClass(),
        CoreMatchers.containsString("HelloWorld$InnerClass"));
  }

  @Test
  public void testMainClass_varargs() throws URISyntaxException, IOException {
    Path rootDirectory =
        Paths.get(Resources.getResource("core/class-finder-tests/varargs").toURI());
    MainClassFinder.Result mainClassFinderResult =
        MainClassFinder.find(new DirectoryWalker(rootDirectory).walk(), logEventConsumer);
    Assert.assertSame(Result.Type.MAIN_CLASS_FOUND, mainClassFinderResult.getType());
    MatcherAssert.assertThat(
        mainClassFinderResult.getFoundMainClass(), CoreMatchers.containsString("HelloWorld"));
  }

  @Test
  public void testMainClass_synthetic() throws URISyntaxException, IOException {
    Path rootDirectory =
        Paths.get(Resources.getResource("core/class-finder-tests/synthetic").toURI());
    MainClassFinder.Result mainClassFinderResult =
        MainClassFinder.find(new DirectoryWalker(rootDirectory).walk(), logEventConsumer);
    Assert.assertSame(
        MainClassFinder.Result.Type.MAIN_CLASS_FOUND, mainClassFinderResult.getType());
    MatcherAssert.assertThat(
        mainClassFinderResult.getFoundMainClass(), CoreMatchers.containsString("HelloWorldKt"));
  }
}
