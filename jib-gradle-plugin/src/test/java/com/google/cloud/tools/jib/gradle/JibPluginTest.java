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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.tasks.Jar;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.UnexpectedBuildFailure;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
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
  @Rule public final TestProject testProject = new TestProject("lazy-evaluation");

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
  public void testWebAppProject() {
    Project project = createProject("java", "war", "com.google.cloud.tools.jib");

    TaskContainer tasks = project.getTasks();
    Task warTask = tasks.getByPath(":war");
    Assert.assertNotNull(warTask);

    for (String taskName : KNOWN_JIB_TASKS) {
      Set<Task> taskDependencies =
          tasks
              .getByPath(taskName)
              .getDependsOn()
              .stream()
              .filter(TaskProvider.class::isInstance)
              .map(it -> ((TaskProvider<?>) it).get())
              .collect(Collectors.toSet());

      Assert.assertTrue(taskDependencies.contains(warTask));
    }
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testWebAppProject_bootWar() {
    Project project =
        createProject("java", "war", "org.springframework.boot", "com.google.cloud.tools.jib");

    TaskContainer tasks = project.getTasks();
    Task warTask = tasks.getByPath(":war");
    Task bootWarTask = tasks.getByPath(":bootWar");
    Assert.assertNotNull(warTask);
    Assert.assertNotNull(bootWarTask);

    for (String taskName : KNOWN_JIB_TASKS) {
      Set<Task> taskDependencies =
          tasks
              .getByPath(taskName)
              .getDependsOn()
              .stream()
              .filter(TaskProvider.class::isInstance)
              .map(it -> ((TaskProvider<?>) it).get())
              .collect(Collectors.toSet());

      Assert.assertTrue(taskDependencies.containsAll(Arrays.asList(warTask, bootWarTask)));
    }
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testWebAppProject_bootWarDisabled() {
    Project project =
        createProject("java", "war", "org.springframework.boot", "com.google.cloud.tools.jib");
    TaskContainer tasks = project.getTasks();
    // should depend on bootWar even if disabled
    tasks.named("bootWar").configure(task -> task.setEnabled(false));

    Task warTask = tasks.getByPath(":war");
    Task bootWarTask = tasks.getByPath(":bootWar");
    Assert.assertNotNull(warTask);
    Assert.assertNotNull(bootWarTask);

    for (String taskName : KNOWN_JIB_TASKS) {
      Set<Task> taskDependencies =
          tasks
              .getByPath(taskName)
              .getDependsOn()
              .stream()
              .filter(TaskProvider.class::isInstance)
              .map(it -> ((TaskProvider<?>) it).get())
              .collect(Collectors.toSet());

      Assert.assertTrue(taskDependencies.containsAll(Arrays.asList(warTask, bootWarTask)));
    }
  }

  @Test
  public void testSpringBootJarProject_nonPackagedMode() {
    Project project =
        createProject("java", "org.springframework.boot", "com.google.cloud.tools.jib");

    Jar jar = (Jar) project.getTasks().getByPath(":jar");
    Assert.assertFalse(jar.getEnabled());
    Assert.assertEquals("", jar.getArchiveClassifier().get());
  }

  @Test
  public void testSpringBootJarProject_packagedMode() {
    Project project =
        createProject("java", "org.springframework.boot", "com.google.cloud.tools.jib");
    JibExtension jibExtension = (JibExtension) project.getExtensions().getByName("jib");
    jibExtension.setContainerizingMode("packaged");

    Jar jar = (Jar) project.getTasks().getByPath(":jar");
    Assert.assertTrue(jar.getEnabled());
    Assert.assertEquals("original", jar.getArchiveClassifier().get());
  }

  @Test
  public void testSpringBootJarProject_packagedMode_jarClassifierSet() {
    Project project =
        createProject("java", "org.springframework.boot", "com.google.cloud.tools.jib");
    JibExtension jibExtension = (JibExtension) project.getExtensions().getByName("jib");
    jibExtension.setContainerizingMode("packaged");
    TaskProvider<Task> jarTask = project.getTasks().named("jar");
    jarTask.configure(task -> ((Jar) task).getArchiveClassifier().set("jar-classifier"));

    Jar jar = (Jar) project.getTasks().getByPath(":jar");
    Assert.assertTrue(jar.getEnabled());
    Assert.assertEquals("jar-classifier", jar.getArchiveClassifier().get());
  }

  @Test
  public void testSpringBootJarProject_packagedMode_bootJarClassifierSet() {
    Project project =
        createProject("java", "org.springframework.boot", "com.google.cloud.tools.jib");
    JibExtension jibExtension = (JibExtension) project.getExtensions().getByName("jib");
    jibExtension.setContainerizingMode("packaged");
    TaskProvider<Task> bootJarTask = project.getTasks().named("bootJar");
    bootJarTask.configure(task -> ((Jar) task).getArchiveClassifier().set("boot-classifier"));

    Jar jar = (Jar) project.getTasks().getByPath(":jar");
    Assert.assertTrue(jar.getEnabled());
    Assert.assertEquals("", jar.getArchiveClassifier().get());
  }

  @Test
  public void testSpringBootJarProject_packagedMode_jarEnabled() {
    Project project =
        createProject("java", "org.springframework.boot", "com.google.cloud.tools.jib");
    JibExtension jibExtension = (JibExtension) project.getExtensions().getByName("jib");
    jibExtension.setContainerizingMode("packaged");
    project.getTasks().named("jar").configure(task -> task.setEnabled(true));

    TaskContainer tasks = project.getTasks();
    try {
      tasks.getByPath(":jar");
      Assert.fail();
    } catch (GradleException ex) {
      MatcherAssert.assertThat(
          ex.getCause().getMessage(),
          CoreMatchers.startsWith(
              "Both 'bootJar' and 'jar' tasks are enabled, but they write their jar file into the "
                  + "same location at "));
      MatcherAssert.assertThat(
          ex.getCause().getMessage(),
          CoreMatchers.endsWith(
              "root.jar. Did you forget to set 'archiveClassifier' on either task?"));
    }
  }

  @Test
  public void testSpringBootJarProject_packagedMode_jarEnabledAndClassifierSet() {
    Project project =
        createProject("java", "org.springframework.boot", "com.google.cloud.tools.jib");
    JibExtension jibExtension = (JibExtension) project.getExtensions().getByName("jib");
    jibExtension.setContainerizingMode("packaged");
    TaskProvider<Task> jarTask = project.getTasks().named("jar");
    jarTask.configure(task -> task.setEnabled(true));
    jarTask.configure(task -> ((Jar) task).getArchiveClassifier().set("jar-classifier"));

    Jar jar = (Jar) project.getTasks().getByPath(":jar");
    Assert.assertTrue(jar.getEnabled());
    Assert.assertEquals("jar-classifier", jar.getArchiveClassifier().get());
  }

  @Test
  public void testSpringBootJarProject_packagedMode_jarEnabledAndBootJarClassifierSet() {
    Project project =
        createProject("java", "org.springframework.boot", "com.google.cloud.tools.jib");
    JibExtension jibExtension = (JibExtension) project.getExtensions().getByName("jib");
    jibExtension.setContainerizingMode("packaged");
    TaskProvider<Task> bootJarTask = project.getTasks().named("bootJar");
    bootJarTask.configure(task -> ((Jar) task).getArchiveClassifier().set("boot-classifier"));

    Jar jarTask = (Jar) project.getTasks().getByPath(":jar");
    Assert.assertTrue(jarTask.getEnabled());
    Assert.assertEquals("", jarTask.getArchiveClassifier().get());
  }

  @Test
  public void testSpringBootJarProject_packagedMode_jarEnabledAndBootJarDisabled() {
    Project project =
        createProject("java", "org.springframework.boot", "com.google.cloud.tools.jib");
    JibExtension jibExtension = (JibExtension) project.getExtensions().getByName("jib");
    jibExtension.setContainerizingMode("packaged");
    project.getTasks().named("jar").configure(task -> task.setEnabled(true));
    project.getTasks().named("bootJar").configure(task -> task.setEnabled(false));

    Jar jar = (Jar) project.getTasks().getByPath(":jar");
    Assert.assertTrue(jar.getEnabled());
    Assert.assertFalse(project.getTasks().getByPath(":bootJar").getEnabled());
    Assert.assertEquals("", jar.getArchiveClassifier().get());
  }

  @Test
  public void
      testSpringBootJarProject_packagedMode_jarEnabledAndBootJarDisabledAndJarClassifierSet() {
    Project project =
        createProject("java", "org.springframework.boot", "com.google.cloud.tools.jib");
    JibExtension jibExtension = (JibExtension) project.getExtensions().getByName("jib");
    jibExtension.setContainerizingMode("packaged");
    TaskProvider<Task> jarTask = project.getTasks().named("jar");
    jarTask.configure(task -> task.setEnabled(true));
    jarTask.configure(task -> ((Jar) task).getArchiveClassifier().set("jar-classifier"));
    project.getTasks().named("bootJar").configure(task -> task.setEnabled(false));

    Jar jar = (Jar) project.getTasks().getByPath(":jar");
    Assert.assertTrue(jar.getEnabled());
    Assert.assertFalse(project.getTasks().getByPath(":bootJar").getEnabled());
    Assert.assertEquals("jar-classifier", jar.getArchiveClassifier().get());
  }

  @Test
  public void testNonWebAppProject() {
    Project project = createProject("java", "com.google.cloud.tools.jib");

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

    TaskContainer tasks = project.getTasks();
    KNOWN_JIB_TASKS.forEach(
        taskName -> Assert.assertEquals(taskName, "Jib", tasks.getByPath(taskName).getGroup()));
  }

  @Test
  public void testLazyEvalForImageAndTags() {
    // TODO: Pass in `-Djib.console=plain` as argument for build and remove filtering for cyan
    // coloring regex once [#2764](https://github.com/GoogleContainerTools/jib/issues/2764) is
    // submitted.
    try {
      testProject.build(JibPlugin.BUILD_IMAGE_TASK_NAME);
      Assert.fail("Expect this to fail");
    } catch (UnexpectedBuildFailure ex) {
      String output = ex.getBuildResult().getOutput().trim();
      String cleanOutput = output.replace("\u001B[36m", "").replace("\u001B[0m", "");

      MatcherAssert.assertThat(
          cleanOutput,
          CoreMatchers.containsString(
              "Containerizing application to updated-image, updated-image:updated-tag, updated-image:tag2"));
    }
  }

  private Project createProject(String... plugins) {
    Project project =
        ProjectBuilder.builder().withProjectDir(testProjectRoot.getRoot()).withName("root").build();
    Arrays.asList(plugins).forEach(project.getPluginManager()::apply);
    return project;
  }
}
