/*
 * Copyright 2019 Google LLC.
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

package com.google.cloud.tools.jib.maven.skaffold;

import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Kotlin-related tests for {@link FilesMojoV2}. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FilesMojoV2KotlinTest {

  private final PluginExecution pluginExecution1 = new PluginExecution();
  private final PluginExecution pluginExecution2 = new PluginExecution();
  private final Xpp3Dom configuration1 = new Xpp3Dom("configuration");
  private final Xpp3Dom configuration2 = new Xpp3Dom("configuration");
  private final Xpp3Dom sourceDirs1 = new Xpp3Dom("sourceDirs");
  private final Xpp3Dom sourceDirs2 = new Xpp3Dom("sourceDirs");
  private final Xpp3Dom sourceDir1 = new Xpp3Dom("sourceDir");
  private final Xpp3Dom sourceDir2 = new Xpp3Dom("sourceDir");
  private final Xpp3Dom sourceDir3 = new Xpp3Dom("sourceDir");
  private final Xpp3Dom sourceDir4 = new Xpp3Dom("sourceDir");

  @Mock private MavenProject mavenProject;
  @Mock private Plugin kotlinPlugin;

  @BeforeEach
  void setUp() {
    Mockito.when(mavenProject.getPlugin("org.jetbrains.kotlin:kotlin-maven-plugin"))
        .thenReturn(kotlinPlugin);
    Mockito.when(mavenProject.getBasedir()).thenReturn(new File("/base"));

    pluginExecution1.setConfiguration(configuration1);
    pluginExecution2.setConfiguration(configuration2);
  }

  @Test
  void getKotlinSourceDirectories_noKotlinPlugin() {
    Mockito.when(mavenProject.getPlugin(Mockito.anyString())).thenReturn(null);
    Assert.assertEquals(ImmutableSet.of(), FilesMojoV2.getKotlinSourceDirectories(mavenProject));
  }

  @Test
  void getKotlinSourceDirectories_noExecutions() {
    Mockito.when(kotlinPlugin.getExecutions()).thenReturn(Collections.emptyList());

    Assert.assertEquals(
        ImmutableSet.of(Paths.get("/base/src/main/kotlin")),
        FilesMojoV2.getKotlinSourceDirectories(mavenProject));
  }

  @Test
  void getKotlinSourceDirectories_noConfiguration() {
    Mockito.when(kotlinPlugin.getExecutions()).thenReturn(Arrays.asList(pluginExecution1));
    pluginExecution1.setConfiguration(null);

    Assert.assertEquals(
        ImmutableSet.of(Paths.get("/base/src/main/kotlin")),
        FilesMojoV2.getKotlinSourceDirectories(mavenProject));
  }

  @Test
  void getKotlinSourceDirectories_noSourceDirs() {
    Mockito.when(kotlinPlugin.getExecutions()).thenReturn(Arrays.asList(pluginExecution1));

    Assert.assertEquals(
        ImmutableSet.of(Paths.get("/base/src/main/kotlin")),
        FilesMojoV2.getKotlinSourceDirectories(mavenProject));
  }

  @Test
  void getKotlinSourceDirectories_noSourceDirsChildren() {
    Mockito.when(kotlinPlugin.getExecutions()).thenReturn(Arrays.asList(pluginExecution1));
    configuration1.addChild(sourceDirs1);

    Assert.assertEquals(
        ImmutableSet.of(Paths.get("/base/src/main/kotlin")),
        FilesMojoV2.getKotlinSourceDirectories(mavenProject));
  }

  @Test
  void getKotlinSourceDirectories_nullSourceDir() {
    Mockito.when(kotlinPlugin.getExecutions()).thenReturn(Arrays.asList(pluginExecution1));
    configuration1.addChild(sourceDirs1);
    sourceDirs1.addChild(sourceDir1);

    Assert.assertEquals(
        ImmutableSet.of(Paths.get("/base/src/main/kotlin")),
        FilesMojoV2.getKotlinSourceDirectories(mavenProject));
  }

  @Test
  void getKotlinSourceDirectories_emptySourceDir() {
    Mockito.when(kotlinPlugin.getExecutions()).thenReturn(Arrays.asList(pluginExecution1));
    configuration1.addChild(sourceDirs1);
    sourceDirs1.addChild(sourceDir1);
    sourceDir1.setValue("");

    Assert.assertEquals(
        ImmutableSet.of(Paths.get("/base/src/main/kotlin")),
        FilesMojoV2.getKotlinSourceDirectories(mavenProject));
  }

  @Test
  void getKotlinSourceDirectories_relativePath() {
    Mockito.when(kotlinPlugin.getExecutions()).thenReturn(Arrays.asList(pluginExecution1));
    configuration1.addChild(sourceDirs1);
    sourceDirs1.addChild(sourceDir1);
    sourceDir1.setValue("kotlin/src");

    Assert.assertEquals(
        ImmutableSet.of(Paths.get("/base/src/main/kotlin"), Paths.get("/base/kotlin/src")),
        FilesMojoV2.getKotlinSourceDirectories(mavenProject));
  }

  @Test
  void getKotlinSourceDirectories_absolutePath() {
    Mockito.when(kotlinPlugin.getExecutions()).thenReturn(Arrays.asList(pluginExecution1));
    configuration1.addChild(sourceDirs1);
    sourceDirs1.addChild(sourceDir1);
    sourceDir1.setValue("/absolute/src");

    Assert.assertEquals(
        ImmutableSet.of(Paths.get("/base/src/main/kotlin"), Paths.get("/absolute/src")),
        FilesMojoV2.getKotlinSourceDirectories(mavenProject));
  }

  @Test
  void getKotlinSourceDirectories_complex() {
    Mockito.when(kotlinPlugin.getExecutions())
        .thenReturn(Arrays.asList(pluginExecution1, pluginExecution2));
    configuration1.addChild(sourceDirs1);
    configuration2.addChild(sourceDirs2);
    sourceDirs1.addChild(sourceDir1);
    sourceDirs1.addChild(sourceDir2);
    sourceDirs2.addChild(sourceDir3);
    sourceDirs2.addChild(sourceDir4);
    sourceDir1.setValue("/absolute/src1");
    sourceDir2.setValue("relative/src2");
    sourceDir3.setValue("/absolute/src3");
    sourceDir4.setValue("relative/src4");

    Assert.assertEquals(
        ImmutableSet.of(
            Paths.get("/base/src/main/kotlin"),
            Paths.get("/absolute/src1"),
            Paths.get("/absolute/src3"),
            Paths.get("/base/relative/src2"),
            Paths.get("/base/relative/src4")),
        FilesMojoV2.getKotlinSourceDirectories(mavenProject));
  }

  @Test
  void getKotlinSourceDirectories_noDuplicates() {
    Mockito.when(kotlinPlugin.getExecutions())
        .thenReturn(Arrays.asList(pluginExecution1, pluginExecution2));
    configuration1.addChild(sourceDirs1);
    configuration2.addChild(sourceDirs2);
    sourceDirs1.addChild(sourceDir1);
    sourceDirs1.addChild(sourceDir2);
    sourceDirs2.addChild(sourceDir3);
    sourceDirs2.addChild(sourceDir4);
    sourceDir1.setValue("src/main/kotlin");
    sourceDir2.setValue("/base/another/src");
    sourceDir3.setValue("another/src");
    sourceDir4.setValue("/base/src/main/kotlin");

    Assert.assertEquals(
        ImmutableSet.of(Paths.get("/base/src/main/kotlin"), Paths.get("/base/another/src")),
        FilesMojoV2.getKotlinSourceDirectories(mavenProject));
  }

  @Test
  void getKotlinSourceDirectories_excludeTestCompileGoal() {
    Mockito.when(kotlinPlugin.getExecutions())
        .thenReturn(Arrays.asList(pluginExecution1, pluginExecution2));
    pluginExecution1.setGoals(Arrays.asList("compile"));
    pluginExecution2.setGoals(Arrays.asList("tomato", "test-compile"));
    configuration1.addChild(sourceDirs1);
    configuration2.addChild(sourceDirs2);
    sourceDirs1.addChild(sourceDir1);
    sourceDirs2.addChild(sourceDir2);
    sourceDir1.setValue("/included");
    sourceDir2.setValue("/should/not/be/included");

    Assert.assertEquals(
        ImmutableSet.of(Paths.get("/base/src/main/kotlin"), Paths.get("/included")),
        FilesMojoV2.getKotlinSourceDirectories(mavenProject));
  }
}
