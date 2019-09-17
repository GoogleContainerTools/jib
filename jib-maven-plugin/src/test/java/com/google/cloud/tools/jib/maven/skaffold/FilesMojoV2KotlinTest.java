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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Kotlin-related tests for {@link FilesMojoV2}. */
@RunWith(MockitoJUnitRunner.class)
public class FilesMojoV2KotlinTest {

  @Mock private MavenProject mavenProject;
  @Mock private Plugin kotlinPlugin;
  @Mock private PluginExecution pluginExecution1;
  @Mock private PluginExecution pluginExecution2;
  @Mock private Xpp3Dom configuration1;
  @Mock private Xpp3Dom configuration2;
  @Mock private Xpp3Dom sourceDirs1;
  @Mock private Xpp3Dom sourceDirs2;
  @Mock private Xpp3Dom sourceDir1;
  @Mock private Xpp3Dom sourceDir2;
  @Mock private Xpp3Dom sourceDir3;
  @Mock private Xpp3Dom sourceDir4;

  @Before
  public void setUp() {
    Mockito.when(mavenProject.getPlugin("org.jetbrains.kotlin:kotlin-maven-plugin"))
        .thenReturn(kotlinPlugin);
    Mockito.when(mavenProject.getBasedir()).thenReturn(new File("/base"));
  }

  @Test
  public void getKotlinSourceDirectories_noKotlinPlugin() {
    Mockito.when(mavenProject.getPlugin(Mockito.anyString())).thenReturn(null);
    Assert.assertEquals(ImmutableSet.of(), FilesMojoV2.getKotlinSourceDirectories(mavenProject));
  }

  @Test
  public void getKotlinSourceDirectories_noExecutions() {
    Mockito.when(kotlinPlugin.getExecutions()).thenReturn(Collections.emptyList());

    Assert.assertEquals(
        ImmutableSet.of(Paths.get("/base/src/main/kotlin")),
        FilesMojoV2.getKotlinSourceDirectories(mavenProject));
  }

  @Test
  public void getKotlinSourceDirectories_noConfiguration() {
    Mockito.when(kotlinPlugin.getExecutions()).thenReturn(Arrays.asList(pluginExecution1));

    Assert.assertEquals(
        ImmutableSet.of(Paths.get("/base/src/main/kotlin")),
        FilesMojoV2.getKotlinSourceDirectories(mavenProject));
  }

  @Test
  public void getKotlinSourceDirectories_noSourceDirs() {
    Mockito.when(kotlinPlugin.getExecutions()).thenReturn(Arrays.asList(pluginExecution1));
    Mockito.when(pluginExecution1.getConfiguration()).thenReturn(configuration1);

    Assert.assertEquals(
        ImmutableSet.of(Paths.get("/base/src/main/kotlin")),
        FilesMojoV2.getKotlinSourceDirectories(mavenProject));
  }

  @Test
  public void getKotlinSourceDirectories_noSourceDirsChildren() {
    Mockito.when(kotlinPlugin.getExecutions()).thenReturn(Arrays.asList(pluginExecution1));
    Mockito.when(pluginExecution1.getConfiguration()).thenReturn(configuration1);
    Mockito.when(configuration1.getChild("sourceDirs")).thenReturn(sourceDirs1);
    Mockito.when(sourceDirs1.getChildren()).thenReturn(new Xpp3Dom[0]);

    Assert.assertEquals(
        ImmutableSet.of(Paths.get("/base/src/main/kotlin")),
        FilesMojoV2.getKotlinSourceDirectories(mavenProject));
  }

  @Test
  public void getKotlinSourceDirectories_nullSourceDir() {
    Mockito.when(kotlinPlugin.getExecutions()).thenReturn(Arrays.asList(pluginExecution1));
    Mockito.when(pluginExecution1.getConfiguration()).thenReturn(configuration1);
    Mockito.when(configuration1.getChild("sourceDirs")).thenReturn(sourceDirs1);
    Mockito.when(sourceDirs1.getChildren()).thenReturn(new Xpp3Dom[] {sourceDir1});
    Mockito.when(sourceDir1.getValue()).thenReturn(null);

    Assert.assertEquals(
        ImmutableSet.of(Paths.get("/base/src/main/kotlin")),
        FilesMojoV2.getKotlinSourceDirectories(mavenProject));
  }

  @Test
  public void getKotlinSourceDirectories_emptySourceDir() {
    Mockito.when(kotlinPlugin.getExecutions()).thenReturn(Arrays.asList(pluginExecution1));
    Mockito.when(pluginExecution1.getConfiguration()).thenReturn(configuration1);
    Mockito.when(configuration1.getChild("sourceDirs")).thenReturn(sourceDirs1);
    Mockito.when(sourceDirs1.getChildren()).thenReturn(new Xpp3Dom[] {sourceDir1});
    Mockito.when(sourceDir1.getValue()).thenReturn("");

    Assert.assertEquals(
        ImmutableSet.of(Paths.get("/base/src/main/kotlin")),
        FilesMojoV2.getKotlinSourceDirectories(mavenProject));
  }

  @Test
  public void getKotlinSourceDirectories_relativePath() {
    Mockito.when(kotlinPlugin.getExecutions()).thenReturn(Arrays.asList(pluginExecution1));
    Mockito.when(pluginExecution1.getConfiguration()).thenReturn(configuration1);
    Mockito.when(configuration1.getChild("sourceDirs")).thenReturn(sourceDirs1);
    Mockito.when(sourceDirs1.getChildren()).thenReturn(new Xpp3Dom[] {sourceDir1});
    Mockito.when(sourceDir1.getValue()).thenReturn("kotlin/src");

    Assert.assertEquals(
        ImmutableSet.of(Paths.get("/base/src/main/kotlin"), Paths.get("/base/kotlin/src")),
        FilesMojoV2.getKotlinSourceDirectories(mavenProject));
  }

  @Test
  public void getKotlinSourceDirectories_absolutePath() {
    Mockito.when(kotlinPlugin.getExecutions()).thenReturn(Arrays.asList(pluginExecution1));
    Mockito.when(pluginExecution1.getConfiguration()).thenReturn(configuration1);
    Mockito.when(configuration1.getChild("sourceDirs")).thenReturn(sourceDirs1);
    Mockito.when(sourceDirs1.getChildren()).thenReturn(new Xpp3Dom[] {sourceDir1});
    Mockito.when(sourceDir1.getValue()).thenReturn("/absolute/src");

    Assert.assertEquals(
        ImmutableSet.of(Paths.get("/base/src/main/kotlin"), Paths.get("/absolute/src")),
        FilesMojoV2.getKotlinSourceDirectories(mavenProject));
  }

  @Test
  public void getKotlinSourceDirectories_complex() {
    Mockito.when(kotlinPlugin.getExecutions())
        .thenReturn(Arrays.asList(pluginExecution1, pluginExecution2));
    Mockito.when(pluginExecution1.getConfiguration()).thenReturn(configuration1);
    Mockito.when(pluginExecution2.getConfiguration()).thenReturn(configuration2);
    Mockito.when(configuration1.getChild("sourceDirs")).thenReturn(sourceDirs1);
    Mockito.when(configuration2.getChild("sourceDirs")).thenReturn(sourceDirs2);
    Mockito.when(sourceDirs1.getChildren()).thenReturn(new Xpp3Dom[] {sourceDir1, sourceDir2});
    Mockito.when(sourceDirs2.getChildren()).thenReturn(new Xpp3Dom[] {sourceDir3, sourceDir4});
    Mockito.when(sourceDir1.getValue()).thenReturn("/absolute/src1");
    Mockito.when(sourceDir2.getValue()).thenReturn("relative/src2");
    Mockito.when(sourceDir3.getValue()).thenReturn("/absolute/src3");
    Mockito.when(sourceDir4.getValue()).thenReturn("relative/src4");

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
  public void getKotlinSourceDirectories_noDuplicates() {
    Mockito.when(kotlinPlugin.getExecutions())
        .thenReturn(Arrays.asList(pluginExecution1, pluginExecution2));
    Mockito.when(pluginExecution1.getConfiguration()).thenReturn(configuration1);
    Mockito.when(pluginExecution2.getConfiguration()).thenReturn(configuration2);
    Mockito.when(configuration1.getChild("sourceDirs")).thenReturn(sourceDirs1);
    Mockito.when(configuration2.getChild("sourceDirs")).thenReturn(sourceDirs2);
    Mockito.when(sourceDirs1.getChildren()).thenReturn(new Xpp3Dom[] {sourceDir1, sourceDir2});
    Mockito.when(sourceDirs2.getChildren()).thenReturn(new Xpp3Dom[] {sourceDir3, sourceDir4});
    Mockito.when(sourceDir1.getValue()).thenReturn("src/main/kotlin");
    Mockito.when(sourceDir2.getValue()).thenReturn("/base/another/src");
    Mockito.when(sourceDir3.getValue()).thenReturn("another/src");
    Mockito.when(sourceDir4.getValue()).thenReturn("/base/src/main/kotlin");

    Assert.assertEquals(
        ImmutableSet.of(Paths.get("/base/src/main/kotlin"), Paths.get("/base/another/src")),
        FilesMojoV2.getKotlinSourceDirectories(mavenProject));
  }
}
