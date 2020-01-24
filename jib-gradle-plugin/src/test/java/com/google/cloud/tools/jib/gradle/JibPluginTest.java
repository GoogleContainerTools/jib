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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.tasks.Jar;
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
    Assert.assertEquals(
        JibPlugin.SKAFFOLD_CHECK_REQUIRED_VERSION_TASK_NAME, "_skaffoldFailIfJibOutOfDate");
  }

  @Test
  public void testCheckJibVersionInvoked() {
    Project project = createProject();
    System.setProperty(JibPlugin.REQUIRED_VERSION_PROPERTY_NAME, "10000.0"); // not here yet
    try {
      project.getPluginManager().apply("com.google.cloud.tools.jib");
      Assert.fail("should have failed");
    } catch (GradleException ex) {
      // Gradle tests aren't run from a jar and so don't have an identifiable plugin version
      Assert.assertEquals(
          "Failed to apply plugin [id 'com.google.cloud.tools.jib']", ex.getMessage());
      Assert.assertEquals("Could not determine Jib plugin version", ex.getCause().getMessage());
    }
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testProjectDependencyAssembleTasksAreRun() {
    // root project is our jib packaged service
    Project rootProject = createProject("java");

    // our service DOES depend on this, but since it's a regular 'java' project it should not
    // trigger
    // an assemble
    Project subProject =
        ProjectBuilder.builder()
            .withParent(rootProject)
            .withProjectDir(testProjectRoot.getRoot())
            .withName("sub")
            .build();
    subProject.getPluginManager().apply("java");

    // our service DOES depend on this, and since it's a 'java-library' it should trigger an
    // assemble
    Project subProjectLibrary =
        ProjectBuilder.builder()
            .withParent(rootProject)
            .withProjectDir(testProjectRoot.getRoot())
            .withName("sub-lib")
            .build();
    subProjectLibrary.getPluginManager().apply("java-library");

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
        .addAll(
            ImmutableSet.of(
                rootProject
                    .getDependencies()
                    .project(ImmutableMap.of("path", subProject.getPath())),
                rootProject
                    .getDependencies()
                    .project(ImmutableMap.of("path", subProjectLibrary.getPath()))));

    // programmatic check
    Assert.assertEquals(
        ImmutableList.of(":sub", ":sub-lib"),
        JibPlugin.getProjectDependencies(rootProject)
            .stream()
            .map(Project::getPath)
            .collect(Collectors.toList()));

    // check by applying the jib plugin and inspect the task dependencies
    rootProject.getPluginManager().apply("com.google.cloud.tools.jib");

    TaskContainer tasks = rootProject.getTasks();
    // add a custom task that our jib tasks depend on to ensure we do not overwrite this dependsOn
    TaskProvider<Task> dependencyTask = rootProject.getTasks().register("myCustomTask", task -> {});
    KNOWN_JIB_TASKS.forEach(taskName -> tasks.getByPath(taskName).dependsOn(dependencyTask));

    ((ProjectInternal) rootProject).evaluate();

    KNOWN_JIB_TASKS.forEach(
        taskName ->
            Assert.assertEquals(
                ImmutableSet.of(":sub-lib:assemble", ":classes", ":myCustomTask"),
                tasks
                    .getByPath(taskName)
                    .getDependsOn()
                    .stream()
                    .map(
                        object ->
                            object instanceof List ? object : Collections.singletonList(object))
                    .map(List.class::cast)
                    .flatMap(List::stream)
                    .map(object -> ((TaskProvider<Task>) object).get().getPath())
                    .collect(Collectors.toSet())));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testWebAppProject() {
    Project project = createProject("java", "war", "com.google.cloud.tools.jib");

    ((ProjectInternal) project).evaluate();
    TaskContainer tasks = project.getTasks();
    Task warTask = tasks.getByPath(":war");
    Assert.assertNotNull(warTask);

    for (String taskName : KNOWN_JIB_TASKS) {
      List<TaskProvider<?>> taskProviders =
          (List<TaskProvider<?>>) tasks.getByPath(taskName).getDependsOn().iterator().next();
      Assert.assertEquals(1, taskProviders.size());
      Assert.assertEquals(warTask, taskProviders.get(0).get());
    }
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testWebAppProject_bootWar() {
    Project project =
        createProject("java", "war", "org.springframework.boot", "com.google.cloud.tools.jib");
    ((ProjectInternal) project).evaluate();

    TaskContainer tasks = project.getTasks();
    Task warTask = tasks.getByPath(":war");
    Task bootWarTask = tasks.getByPath(":bootWar");
    Assert.assertNotNull(warTask);
    Assert.assertNotNull(bootWarTask);

    for (String taskName : KNOWN_JIB_TASKS) {
      List<TaskProvider<?>> taskProviders =
          (List<TaskProvider<?>>) tasks.getByPath(taskName).getDependsOn().iterator().next();
      Assert.assertEquals(
          ImmutableSet.of(warTask, bootWarTask),
          taskProviders.stream().map(TaskProvider::get).collect(Collectors.toSet()));
    }
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testWebAppProject_bootWarDisabled() {
    Project project =
        createProject("java", "war", "org.springframework.boot", "com.google.cloud.tools.jib");
    ((ProjectInternal) project).evaluate();

    TaskContainer tasks = project.getTasks();
    Task warTask = tasks.getByPath(":war");
    Task bootWarTask = tasks.getByPath(":bootWar");
    Assert.assertNotNull(warTask);
    Assert.assertNotNull(bootWarTask);
    bootWarTask.setEnabled(false); // should depend on bootWar even if disabled

    for (String taskName : KNOWN_JIB_TASKS) {
      List<TaskProvider<?>> taskProviders =
          (List<TaskProvider<?>>) tasks.getByPath(taskName).getDependsOn().iterator().next();
      Assert.assertEquals(
          ImmutableSet.of(warTask, bootWarTask),
          taskProviders.stream().map(TaskProvider::get).collect(Collectors.toSet()));
    }
  }

  @Test
  public void testSpringBootJarProject_nonPackagedMode() {
    Project project =
        createProject("java", "org.springframework.boot", "com.google.cloud.tools.jib");

    Jar jarTask = (Jar) project.getTasks().getByPath(":jar");
    Assert.assertFalse(jarTask.getEnabled());
    Assert.assertEquals("", jarTask.getArchiveClassifier().get());
  }

  @Test
  public void testSpringBootJarProject_packagedMode() {
    Project project =
        createProject("java", "org.springframework.boot", "com.google.cloud.tools.jib");
    JibExtension jibExtension = (JibExtension) project.getExtensions().getByName("jib");
    jibExtension.setContainerizingMode("packaged");

    Jar jarTask = (Jar) project.getTasks().getByPath(":jar");
    Assert.assertTrue(jarTask.getEnabled());
    Assert.assertEquals("original", jarTask.getArchiveClassifier().get());
  }

  @Test
  public void testNonWebAppProject() {
    Project project = createProject("java", "com.google.cloud.tools.jib");
    ((ProjectInternal) project).evaluate();

    TaskContainer tasks = project.getTasks();
    try {
      tasks.getByPath(":war");
      Assert.fail();
    } catch (UnknownTaskException ex) {
      Assert.assertNotNull(ex.getMessage());
    }
  }

  @Test
  public void testJibTaskGroupIsSet() {
    Project project = createProject("java", "com.google.cloud.tools.jib");
    ((ProjectInternal) project).evaluate();

    TaskContainer tasks = project.getTasks();
    KNOWN_JIB_TASKS.forEach(
        taskName -> Assert.assertEquals(taskName, "Jib", tasks.getByPath(taskName).getGroup()));
  }

  private Project createProject(String... plugins) {
    Project project =
        ProjectBuilder.builder().withProjectDir(testProjectRoot.getRoot()).withName("root").build();
    Arrays.asList(plugins).forEach(project.getPluginManager()::apply);
    return project;
  }
}
