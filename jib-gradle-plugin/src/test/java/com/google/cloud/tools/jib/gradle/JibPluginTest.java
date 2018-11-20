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

import com.google.cloud.tools.jib.plugins.common.ProjectProperties;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.UnexpectedBuildFailure;
import org.junit.Assert;
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

  @Rule public final TemporaryFolder testProjectRoot = new TemporaryFolder();

  @Test
  public void testCheckGradleVersion_pass() {
    GradleRunner.create()
        .withProjectDir(testProjectRoot.getRoot())
        .withPluginClasspath()
        .withGradleVersion(JibPlugin.GRADLE_MIN_VERSION.getVersion())
        .build();
    // pass
  }

  @Test
  public void testCheckGradleVersion_fail() throws IOException {
    // Copy build file to temp dir
    Path buildFile = testProjectRoot.getRoot().toPath().resolve("build.gradle");
    InputStream buildFileContent =
        getClass().getClassLoader().getResourceAsStream("plugin-test/build.gradle");
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
    Task dependencyTask = rootProject.getTasks().create("myCustomTask", task -> {});
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
                    .map(Task.class::cast)
                    .map(Task::getPath)
                    .collect(Collectors.toSet())));
  }

  @Test
  public void testWebAppProject() {
    Project rootProject =
        ProjectBuilder.builder().withProjectDir(testProjectRoot.getRoot()).withName("root").build();
    rootProject.getPluginManager().apply("java");
    rootProject.getPluginManager().apply("war");
    rootProject.getPluginManager().apply("com.google.cloud.tools.jib");
    ((ProjectInternal) rootProject).evaluate();
    Assert.assertNotNull(rootProject.getTasks().getByPath(":" + JibPlugin.EXPLODED_WAR_TASK_NAME));
    ExplodedWarTask explodedWarTask =
        (ExplodedWarTask) rootProject.getTasks().getByPath(":" + JibPlugin.EXPLODED_WAR_TASK_NAME);
    Assert.assertEquals(
        rootProject.getBuildDir().toPath().resolve(ProjectProperties.EXPLODED_WAR_DIRECTORY_NAME),
        explodedWarTask.getExplodedWarDirectory().toPath());

    Assert.assertEquals(
        explodedWarTask,
        rootProject
            .getTasks()
            .getByPath(JibPlugin.BUILD_IMAGE_TASK_NAME)
            .getDependsOn()
            .iterator()
            .next());
    Assert.assertEquals(
        explodedWarTask,
        rootProject
            .getTasks()
            .getByPath(JibPlugin.BUILD_DOCKER_TASK_NAME)
            .getDependsOn()
            .iterator()
            .next());
    Assert.assertEquals(
        explodedWarTask,
        rootProject
            .getTasks()
            .getByPath(JibPlugin.BUILD_TAR_TASK_NAME)
            .getDependsOn()
            .iterator()
            .next());
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
      tasks.getByPath(":" + JibPlugin.EXPLODED_WAR_TASK_NAME);
      Assert.fail();
    } catch (UnknownTaskException ex) {
      Assert.assertNotNull(ex.getMessage());
    }
  }
}
