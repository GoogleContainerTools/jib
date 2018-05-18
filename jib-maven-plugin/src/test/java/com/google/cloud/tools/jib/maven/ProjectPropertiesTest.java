/*
 * Copyright 2018 Google LLC. All rights reserved.
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

package com.google.cloud.tools.jib.maven;

import com.google.cloud.tools.jib.builder.SourceFilesConfiguration;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import org.apache.maven.model.Build;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link ProjectProperties}. */
@RunWith(MockitoJUnitRunner.class)
public class ProjectPropertiesTest {

  @Mock private MavenProject mockMavenProject;
  @Mock private Log mockLog;
  @Mock private Plugin mockJarPlugin;
  @Mock private SourceFilesConfiguration mockSourceFilesConfiguration;

  @Mock private Build mockBuild;

  private final Xpp3Dom fakeJarPluginConfiguration = new Xpp3Dom("");
  private final Xpp3Dom jarPluginMainClass = new Xpp3Dom("mainClass");
  private final List<Path> classesPath = Collections.singletonList(Paths.get("a/b/c"));

  private ProjectProperties testProjectProperties;

  @Before
  public void setUp() {
    Xpp3Dom archive = new Xpp3Dom("archive");
    Xpp3Dom manifest = new Xpp3Dom("manifest");
    fakeJarPluginConfiguration.addChild(archive);
    archive.addChild(manifest);
    manifest.addChild(jarPluginMainClass);

    Mockito.when(mockJarPlugin.getConfiguration()).thenReturn(fakeJarPluginConfiguration);
    Mockito.when(mockSourceFilesConfiguration.getClassesFiles()).thenReturn(classesPath);

    testProjectProperties =
        new ProjectProperties(mockMavenProject, mockLog, mockSourceFilesConfiguration);
  }

  @Test
  public void testGetMainClass() throws MojoExecutionException {
    Mockito.when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-jar-plugin"))
        .thenReturn(mockJarPlugin);

    jarPluginMainClass.setValue("some.main.class");

    Assert.assertEquals("some.main.class", testProjectProperties.getMainClass(null));
    Assert.assertEquals("configured", testProjectProperties.getMainClass("configured"));
  }

  @Test
  public void testGetMainClass_noJarTask() {
    assertGetMainClassFails();
  }

  @Test
  public void testGetMainClass_couldNotFindInJarTask() {
    Mockito.when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-jar-plugin"))
        .thenReturn(mockJarPlugin);

    assertGetMainClassFails();
  }

  @Test
  public void testGetMainClass_notValid() throws MojoExecutionException {
    Mockito.when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-jar-plugin"))
        .thenReturn(mockJarPlugin);

    jarPluginMainClass.setValue("${start-class}");

    Assert.assertEquals("${start-class}", testProjectProperties.getMainClass(null));
    Mockito.verify(mockLog).warn("'mainClass' is not a valid Java class : ${start-class}");
  }

  private void assertGetMainClassFails() {
    try {
      testProjectProperties.getMainClass(null);
      Assert.fail("Main class not expected");

    } catch (MojoExecutionException ex) {
      Mockito.verify(mockLog)
          .debug(
              "Could not find main class specified in maven-jar-plugin; attempting to infer main class.");
      Assert.assertThat(
          ex.getMessage(),
          CoreMatchers.containsString("add a `mainClass` configuration to jib-maven-plugin"));
    }
  }
}
