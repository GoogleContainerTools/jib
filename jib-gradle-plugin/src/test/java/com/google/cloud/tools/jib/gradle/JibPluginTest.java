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

package com.google.cloud.tools.jib.gradle;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.stream.Collectors;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.WarPlugin;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.UnexpectedBuildFailure;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for {@link JibPlugin}. */
public class JibPluginTest {

  private static final ImmutableList<String> KNOWN_JIB_TASKS =
      ImmutableList.of(
          JibPlugin.BUILD_IMAGE_TASK_NAME,
          JibPlugin.BUILD_DOCKER_TASK_NAME,
          JibPlugin.BUILD_TAR_TASK_NAME);

  private static boolean isJava8Runtime() {
    return System.getProperty("java.version").startsWith("1.8.");
  }

  @Rule public final TemporaryFolder testProjectRoot = new TemporaryFolder();

  @After
  public void tearDown() {
    System.clearProperty(JibPlugin.REQUIRED_VERSION_PROPERTY_NAME);
  }

  @Test
  public void testCheckGradleVersion_pass() throws IOException {
    Assume.assumeTrue(isJava8Runtime());

    // Copy build file to temp dir
    Path buildFile = testProjectRoot.getRoot().toPath().resolve("build.gradle");
    InputStream buildFileContent =
        getClass().getClassLoader().getResourceAsStream("gradle/plugin-test/build.gradle");
    Files.copy(buildFileContent, buildFile);

    GradleRunner.create()
        .withProjectDir(testProjectRoot.getRoot())
        .withPluginClasspath()
        .withGradleVersion(JibPlugin.GRADLE_MIN_VERSION.getVersion())
        .build();
    // pass
  }

  @Test
  public void testCheckGradleVersion_fail() throws IOException {
    Assume.assumeTrue(isJava8Runtime());

    // Copy build file to temp dir
    Path buildFile = testProjectRoot.getRoot().toPath().resolve("build.gradle");
    InputStream buildFileContent =
        getClass().getClassLoader().getResourceAsStream("gradle/plugin-test/build.gradle");
    Files.copy(buildFileContent, buildFile);

    GradleRunner gradleRunner =
        GradleRunner.create()
            .withProjectDir(testProjectRoot.getRoot())
            .withPluginClasspath()
            .withGradleVersion("4.3");
    try {
      gradleRunner.build();
      Assert.fail();
    } catch (UnexpectedBuildFailure ex) {
      Assert.assertTrue(
          ex.getMessage()
              .contains(
                  "Detected Gradle 4.3, but jib requires "
                      + JibPlugin.GRADLE_MIN_VERSION
                      + " or higher. You can upgrade by running 'gradle wrapper --gradle-version="
                      + JibPlugin.GRADLE_MIN_VERSION.getVersion()
                      + "'."));
    }
  }

  @Test
  public void testCheckJibVersionNames() {
    // These identifiers will be baked into Skaffold and should not be changed
    Assert.assertEquals(JibPlugin.REQUIRED_VERSION_PROPERTY_NAME, "jib.requiredVersion");
    Assert.assertEquals(JibPlugin.CHECK_REQUIRED_VERSION_TASK_NAME, "_skaffoldFailIfJibOutOfDate");
  }

  @Test
  public void testCheckJibVersionInvoked() {
    Project rootProject =
        ProjectBuilder.builder().withProjectDir(testProjectRoot.getRoot()).withName("root").build();
    System.setProperty(JibPlugin.REQUIRED_VERSION_PROPERTY_NAME, "10000.0"); // not here yet
    try {
      rootProject.getPluginManager().apply("com.google.cloud.tools.jib");
      Assert.fail("should have failed");
    } catch (GradleException ex) {
      // Gradle tests aren't run from a jar and so don't have an identifiable plugin version
      Assert.assertEquals(
          "Failed to apply plugin [id 'com.google.cloud.tools.jib']", ex.getMessage());
      Assert.assertEquals("Could not determine Jib plugin version", ex.getCause().getMessage());
    }
  }

  @Test
  public void testProjectDependencyAssembleTasksAreRun() {
    // root project is our jib packaged service
    Project rootProject =
        ProjectBuilder.builder().withProjectDir(testProjectRoot.getRoot()).withName("root").build();
    rootProject.getPluginManager().apply("java");

    // our service DOES depend on this, and jib should trigger an assemble from this project
    Project subProject =
        ProjectBuilder.builder()
            .withParent(rootProject)
            .withProjectDir(testProjectRoot.getRoot())
            .withName("sub")
            .build();
    subProject.getPluginManager().apply("java");

    // our service doesn't depend on this, and jib should NOT trigger an assemble from this project
    Project unrelatedSubProject =
        ProjectBuilder.builder()
            .withParent(rootProject)
            .withProjectDir(testProjectRoot.getRoot())
            .withName("unrelated")
            .build();
    unrelatedSubProject.getPluginManager().apply("java");

    // equivalent of "compile project(':sub')" on the root(jib) project
    rootProject
        .getConfigurations()
        .getByName("compile")
        .getDependencies()
        .add(rootProject.getDependencies().project(ImmutableMap.of("path", subProject.getPath())));

    // programmatic check
    Assert.assertEquals(
        Collections.singletonList(":sub"),
        JibPlugin.getProjectDependencies(rootProject)
            .stream()
            .map(Project::getPath)
            .collect(Collectors.toList()));

    // check by applying the jib plugin and inspect the task dependencies
    rootProject.getPluginManager().apply("com.google.cloud.tools.jib");

    // add a custom task that our jib tasks depend on to ensure we do not overwrite this dependsOn
    TaskProvider<Task> dependencyTask = rootProject.getTasks().register("myCustomTask", task -> {});
    KNOWN_JIB_TASKS.forEach(
        taskName -> rootProject.getTasks().getByPath(taskName).dependsOn(dependencyTask));

    ((ProjectInternal) rootProject).evaluate();

    KNOWN_JIB_TASKS.forEach(
        taskName ->
            Assert.assertEquals(
                ImmutableSet.of(":sub:assemble", ":classes", ":myCustomTask"),
                rootProject
                    .getTasks()
                    .getByPath(taskName)
                    .getDependsOn()
                    .stream()
                    .map(TaskProvider.class::cast)
                    .map(TaskProvider::get)
                    .map(Task.class::cast)
                    .map(Task::getPath)
                    .collect(Collectors.toSet())));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testWebAppProject() {
    Project rootProject =
        ProjectBuilder.builder().withProjectDir(testProjectRoot.getRoot()).withName("root").build();
    rootProject.getPluginManager().apply("java");
    rootProject.getPluginManager().apply("war");
    rootProject.getPluginManager().apply("com.google.cloud.tools.jib");

    ((ProjectInternal) rootProject).evaluate();
    TaskContainer tasks = rootProject.getTasks();
    Task explodedWarTask = tasks.getByPath(":" + WarPlugin.WAR_TASK_NAME);
    Assert.assertNotNull(explodedWarTask);
    Assert.assertEquals(
        explodedWarTask,
        ((TaskProvider<Task>)
                tasks.getByPath(JibPlugin.BUILD_IMAGE_TASK_NAME).getDependsOn().iterator().next())
            .get());
    Assert.assertEquals(
        explodedWarTask,
        ((TaskProvider<Task>)
                tasks.getByPath(JibPlugin.BUILD_DOCKER_TASK_NAME).getDependsOn().iterator().next())
            .get());
    Assert.assertEquals(
        explodedWarTask,
        ((TaskProvider<Task>)
                tasks.getByPath(JibPlugin.BUILD_TAR_TASK_NAME).getDependsOn().iterator().next())
            .get());
  }

  @Test
  public void testNonWebAppProject() {
    Project rootProject =
        ProjectBuilder.builder().withProjectDir(testProjectRoot.getRoot()).withName("root").build();
    rootProject.getPluginManager().apply("java");
    rootProject.getPluginManager().apply("com.google.cloud.tools.jib");
    ((ProjectInternal) rootProject).evaluate();
    TaskContainer tasks = rootProject.getTasks();
    try {
      tasks.getByPath(":" + WarPlugin.WAR_TASK_NAME);
      Assert.fail();
    } catch (UnknownTaskException ex) {
      Assert.assertNotNull(ex.getMessage());
    }
  }

  @Test
  public void testJibTaskGroupIsSet() {
    Project rootProject =
        ProjectBuilder.builder().withProjectDir(testProjectRoot.getRoot()).withName("root").build();
    rootProject.getPluginManager().apply("java");
    rootProject.getPluginManager().apply("com.google.cloud.tools.jib");
    ((ProjectInternal) rootProject).evaluate();
    TaskContainer tasks = rootProject.getTasks();

    KNOWN_JIB_TASKS.forEach(
        taskName -> Assert.assertEquals(taskName, "Jib", tasks.getByPath(taskName).getGroup()));
  }
}
