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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Correspondence;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.tasks.Jar;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.UnexpectedBuildFailure;
import org.junit.After;
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

  private static final Correspondence<Object, Task> PROVIDES_TASK_OF =
      Correspondence.from(
          (object, task) ->
              object instanceof TaskProvider && ((TaskProvider<?>) object).get().equals(task),
          "provides task of");

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

    BuildResult result =
        GradleRunner.create()
            .withProjectDir(testProjectRoot.getRoot())
            .withPluginClasspath()
            .withGradleVersion(JibPlugin.GRADLE_MIN_VERSION.getVersion())
            .build();
    assertThat(result).isNotNull();
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
            .withGradleVersion("8.4");

    Exception exception = assertThrows(UnexpectedBuildFailure.class, () -> gradleRunner.build());
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Detected Gradle 4.3, but jib requires "
                + JibPlugin.GRADLE_MIN_VERSION
                + " or higher. You can upgrade by running 'gradle wrapper --gradle-version="
                + JibPlugin.GRADLE_MIN_VERSION.getVersion()
                + "'.");
  }

  @Test
  public void testCheckJibVersionNames() {
    // These identifiers will be baked into Skaffold and should not be changed
    assertThat(JibPlugin.REQUIRED_VERSION_PROPERTY_NAME).isEqualTo("jib.requiredVersion");
    assertThat(JibPlugin.SKAFFOLD_CHECK_REQUIRED_VERSION_TASK_NAME)
        .isEqualTo("_skaffoldFailIfJibOutOfDate");
  }

  @Test
  public void testCheckJibVersionInvoked() {
    Project project = createProject();
    System.setProperty(JibPlugin.REQUIRED_VERSION_PROPERTY_NAME, "10000.0"); // not here yet

    Exception exception =
        assertThrows(
            GradleException.class,
            () -> project.getPluginManager().apply("com.google.cloud.tools.jib"));
    // Gradle tests aren't run from a jar and so don't have an identifiable plugin version
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("Failed to apply plugin 'com.google.cloud.tools.jib'.");
    assertThat(exception.getCause())
        .hasMessageThat()
        .isEqualTo("Could not determine Jib plugin version");
  }

  @Test
  public void testWebAppProject() {
    Project project = createProject("java", "war", "com.google.cloud.tools.jib");

    TaskContainer tasks = project.getTasks();
    Task warTask = tasks.getByPath(":war");
    assertThat(warTask).isNotNull();

    for (String taskName : KNOWN_JIB_TASKS) {
      Set<Object> taskDependencies = tasks.getByPath(taskName).getDependsOn();
      assertThat(taskDependencies).comparingElementsUsing(PROVIDES_TASK_OF).contains(warTask);
    }
  }

  @Test
  public void testWebAppProject_bootWar() {
    Project project =
        createProject("java", "war", "org.springframework.boot", "com.google.cloud.tools.jib");

    TaskContainer tasks = project.getTasks();
    Task warTask = tasks.getByPath(":war");
    Task bootWarTask = tasks.getByPath(":bootWar");
    assertThat(warTask).isNotNull();
    assertThat(bootWarTask).isNotNull();

    for (String taskName : KNOWN_JIB_TASKS) {
      Set<Object> taskDependencies = tasks.getByPath(taskName).getDependsOn();
      assertThat(taskDependencies)
          .comparingElementsUsing(PROVIDES_TASK_OF)
          .containsAtLeast(warTask, bootWarTask);
    }
  }

  @Test
  public void testWebAppProject_bootWarDisabled() {
    Project project =
        createProject("java", "war", "org.springframework.boot", "com.google.cloud.tools.jib");
    TaskContainer tasks = project.getTasks();
    // should depend on bootWar even if disabled
    tasks.named("bootWar").configure(task -> task.setEnabled(false));

    Task warTask = tasks.getByPath(":war");
    Task bootWarTask = tasks.getByPath(":bootWar");
    assertThat(warTask).isNotNull();
    assertThat(bootWarTask).isNotNull();

    for (String taskName : KNOWN_JIB_TASKS) {
      Set<Object> taskDependencies = tasks.getByPath(taskName).getDependsOn();
      assertThat(taskDependencies)
          .comparingElementsUsing(PROVIDES_TASK_OF)
          .containsAtLeast(warTask, bootWarTask);
    }
  }

  @Test
  public void testSpringBootJarProject_nonPackagedMode() {
    Project project =
        createProject("java", "org.springframework.boot", "com.google.cloud.tools.jib");

    Jar jar = (Jar) project.getTasks().getByPath(":jar");
    jar.setEnabled(false); // Spring boot >2.5.0 no longer sets this as disabled by default
    assertThat(jar.getEnabled()).isFalse();
    assertThat(jar.getArchiveClassifier().get())
        .isEqualTo("plain"); // >2.5.0 generates "plain" instead of empty
  }

  @Test
  public void testSpringBootJarProject_packagedMode() {
    Project project =
        createProject("java", "org.springframework.boot", "com.google.cloud.tools.jib");
    JibExtension jibExtension = (JibExtension) project.getExtensions().getByName("jib");
    jibExtension.setContainerizingMode("packaged");

    Jar jar = (Jar) project.getTasks().getByPath(":jar");
    assertThat(jar.getEnabled()).isTrue();
    assertThat(jar.getArchiveClassifier().get()).isEqualTo("plain");
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
    assertThat(jar.getEnabled()).isTrue();
    assertThat(jar.getArchiveClassifier().get()).isEqualTo("jar-classifier");
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
    assertThat(jar.getEnabled()).isTrue();
    assertThat(jar.getArchiveClassifier().get()).isEqualTo("plain");
  }

  @Test
  public void testSpringBootJarProject_packagedMode_jarEnabled() {
    Project project =
        createProject("java", "org.springframework.boot", "com.google.cloud.tools.jib");
    JibExtension jibExtension = (JibExtension) project.getExtensions().getByName("jib");
    jibExtension.setContainerizingMode("packaged");
    project
        .getTasks()
        .named("jar")
        .configure(
            task -> {
              task.setEnabled(true);
              ((Jar) task).getArchiveClassifier().set(""); // pre spring boot 2.5.0 behaviour
            });

    TaskContainer tasks = project.getTasks();
    Exception exception = assertThrows(GradleException.class, () -> tasks.getByPath(":jar"));
    assertThat(exception.getCause())
        .hasMessageThat()
        .startsWith(
            "Both 'bootJar' and 'jar' tasks are enabled, but they write their jar file into the "
                + "same location at ");
    assertThat(exception.getCause())
        .hasMessageThat()
        .endsWith("root.jar. Did you forget to set 'archiveClassifier' on either task?");
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
    assertThat(jar.getEnabled()).isTrue();
    assertThat(jar.getArchiveClassifier().get()).isEqualTo("jar-classifier");
  }

  @Test
  public void testSpringBootJarProject_packagedMode_jarEnabledAndBootJarClassifierSet() {
    Project project =
        createProject("java", "org.springframework.boot", "com.google.cloud.tools.jib");
    JibExtension jibExtension = (JibExtension) project.getExtensions().getByName("jib");
    jibExtension.setContainerizingMode("packaged");
    TaskProvider<Task> bootJarTask = project.getTasks().named("bootJar");
    bootJarTask.configure(task -> ((Jar) task).getArchiveClassifier().set("boot-classifier"));

    Jar jar = (Jar) project.getTasks().getByPath(":jar");
    assertThat(jar.getEnabled()).isTrue();
    assertThat(jar.getArchiveClassifier().get()).isEqualTo("plain");
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
    assertThat(jar.getEnabled()).isTrue();
    assertThat(project.getTasks().getByPath(":bootJar").getEnabled()).isFalse();
    assertThat(jar.getArchiveClassifier().get()).isEqualTo("plain");
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
    assertThat(jar.getEnabled()).isTrue();
    assertThat(project.getTasks().getByPath(":bootJar").getEnabled()).isFalse();
    assertThat(jar.getArchiveClassifier().get()).isEqualTo("jar-classifier");
  }

  @Test
  public void testNonWebAppProject() {
    Project project = createProject("java", "com.google.cloud.tools.jib");

    TaskContainer tasks = project.getTasks();
    Exception exception = assertThrows(UnknownTaskException.class, () -> tasks.getByPath(":war"));
    assertThat(exception).hasMessageThat().isNotNull();
  }

  @Test
  public void testJibTaskGroupIsSet() {
    Project project = createProject("java", "com.google.cloud.tools.jib");

    TaskContainer tasks = project.getTasks();
    KNOWN_JIB_TASKS.forEach(
        taskName -> assertThat(tasks.getByPath(taskName).getGroup()).isEqualTo("Jib"));
  }

  @Test
  public void testLazyEvalForImageAndTags() {
    UnexpectedBuildFailure exception =
        assertThrows(
            UnexpectedBuildFailure.class,
            () -> testProject.build(JibPlugin.BUILD_IMAGE_TASK_NAME, "-Djib.console=plain"));

    String output = exception.getBuildResult().getOutput();
    assertThat(output)
        .contains(
            "Containerizing application to updated-image, updated-image:updated-tag, updated-image:tag2");
  }

  @Test
  public void testLazyEvalForLabels() {
    BuildResult showLabels = testProject.build("showlabels", "-Djib.console=plain");
    assertThat(showLabels.getOutput())
        .contains(
            "labels contain values [firstkey:updated-first-label, secondKey:updated-second-label]");
  }

  @Test
  public void testLazyEvalForEntryPoint() {
    BuildResult showEntrypoint = testProject.build("showentrypoint", "-Djib.console=plain");
    assertThat(showEntrypoint.getOutput()).contains("entrypoint contains updated");
  }

  @Test
  public void testLazyEvalForExtraDirectories() {
    BuildResult checkExtraDirectories =
        testProject.build("check-extra-directories", "-Djib.console=plain");
    assertThat(checkExtraDirectories.getOutput()).contains("[/updated:755]");
    assertThat(checkExtraDirectories.getOutput()).contains("updated-custom-extra-dir");
  }

  @Test
  public void testLazyEvalForExtraDirectories_individualPaths() throws IOException {
    BuildResult checkExtraDirectories =
        testProject.build(
            "check-extra-directories", "-b=build-extra-dirs.gradle", "-Djib.console=plain");

    Path extraDirectoryPath =
        testProject
            .getProjectRoot()
            .resolve("src")
            .resolve("main")
            .resolve("updated-custom-extra-dir")
            .toRealPath();
    assertThat(checkExtraDirectories.getOutput())
        .contains("extraDirectories (from): [" + extraDirectoryPath + "]");
    assertThat(checkExtraDirectories.getOutput())
        .contains("extraDirectories (into): [/updated-custom-into-dir]");
    assertThat(checkExtraDirectories.getOutput())
        .contains("extraDirectories (includes): [[include.txt]]");
    assertThat(checkExtraDirectories.getOutput())
        .contains("extraDirectories (excludes): [[exclude.txt]]");
  }

  @Test
  public void testLazyEvalForContainerCreationAndFileModificationTimes() {
    BuildResult showTimes = testProject.build("showtimes", "-Djib.console=plain");
    String output = showTimes.getOutput();
    assertThat(output).contains("creationTime=2022-07-19T10:23:42Z");
    assertThat(output).contains("filesModificationTime=2022-07-19T11:23:42Z");
  }

  @Test
  public void testLazyEvalForMainClass() {
    BuildResult showLabels = testProject.build("showMainClass");
    assertThat(showLabels.getOutput()).contains("mainClass value updated");
  }

  @Test
  public void testLazyEvalForJvmFlags() {
    BuildResult showLabels = testProject.build("showJvmFlags");
    assertThat(showLabels.getOutput()).contains("jvmFlags value [updated]");
  }

  private Project createProject(String... plugins) {
    Project project =
        ProjectBuilder.builder().withProjectDir(testProjectRoot.getRoot()).withName("root").build();
    Arrays.asList(plugins).forEach(project.getPluginManager()::apply);
    return project;
  }
}
