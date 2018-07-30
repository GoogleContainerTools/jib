package com.google.cloud.tools.jib.frontend;

import com.google.cloud.tools.jib.builder.BuildLogger;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import java.io.IOException;
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

  @Mock private BuildLogger mockBuildLogger;

  @Test
  public void testFindMainClass_simple() throws URISyntaxException, IOException {
    Path rootDirectory = Paths.get(Resources.getResource("class-finder-tests/simple").toURI());
    MainClassFinder.Result mainClassFinderResult =
        new MainClassFinder(ImmutableList.of(rootDirectory.resolve("child")), mockBuildLogger)
            .find();
    Assert.assertTrue(mainClassFinderResult.isSuccess());
    Assert.assertThat(
        mainClassFinderResult.getFoundMainClass(), CoreMatchers.containsString("HelloWorld"));
  }

  @Test
  public void testFindMainClass_subdirectories() throws URISyntaxException, IOException {
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
  public void testFindMainClass_noClass() throws URISyntaxException, IOException {
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
  public void testFindMainClass_multiple() throws URISyntaxException, IOException {
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
  public void testFindMainClass_extension() throws URISyntaxException, IOException {
    Path rootDirectory = Paths.get(Resources.getResource("class-finder-tests/extension").toURI());
    MainClassFinder.Result mainClassFinderResult =
        new MainClassFinder(ImmutableList.of(rootDirectory.resolve("child")), mockBuildLogger)
            .find();
    Assert.assertTrue(mainClassFinderResult.isSuccess());
    Assert.assertThat(
        mainClassFinderResult.getFoundMainClass(), CoreMatchers.containsString("main.MainClass"));
  }

  @Test
  public void testFindMainClass_importedMethods() throws URISyntaxException, IOException {
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
  public void testFindMainClass_externalClasses() throws URISyntaxException, IOException {
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
  public void testFindMainClass_innerClasses() throws URISyntaxException, IOException {
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
