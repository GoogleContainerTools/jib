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

package com.google.cloud.tools.jib.maven;

import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Test for {@link MavenProjectProperties}. */
@RunWith(MockitoJUnitRunner.class)
public class MavenProjectPropertiesTest {

  @Mock private MavenProject mockMavenProject;
  @Mock private JavaLayerConfigurations mockJavaLayerConfigurations;
  @Mock private Plugin mockJarPlugin;
  @Mock private Log mockLog;

  private Xpp3Dom jarPluginConfiguration;
  private Xpp3Dom archive;
  private Xpp3Dom manifest;
  private Xpp3Dom jarPluginMainClass;

  private MavenProjectProperties mavenProjectProperties;

  @Before
  public void setup() {
    mavenProjectProperties =
        new MavenProjectProperties(mockMavenProject, mockLog, mockJavaLayerConfigurations);
    jarPluginConfiguration = new Xpp3Dom("");
    archive = new Xpp3Dom("archive");
    manifest = new Xpp3Dom("manifest");
    jarPluginMainClass = new Xpp3Dom("mainClass");
  }

  @Test
  public void testGetMainClassFromJar_success() {
    Mockito.when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-jar-plugin"))
        .thenReturn(mockJarPlugin);
    Mockito.when(mockJarPlugin.getConfiguration()).thenReturn(jarPluginConfiguration);
    jarPluginConfiguration.addChild(archive);
    archive.addChild(manifest);
    manifest.addChild(jarPluginMainClass);
    jarPluginMainClass.setValue("some.main.class");

    Assert.assertEquals("some.main.class", mavenProjectProperties.getMainClassFromJar());
  }

  @Test
  public void testGetMainClassFromJar_missingMainClass() {
    Mockito.when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-jar-plugin"))
        .thenReturn(mockJarPlugin);
    Mockito.when(mockJarPlugin.getConfiguration()).thenReturn(jarPluginConfiguration);
    jarPluginConfiguration.addChild(archive);
    archive.addChild(manifest);

    Assert.assertNull(mavenProjectProperties.getMainClassFromJar());
  }

  @Test
  public void testGetMainClassFromJar_missingManifest() {
    Mockito.when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-jar-plugin"))
        .thenReturn(mockJarPlugin);
    Mockito.when(mockJarPlugin.getConfiguration()).thenReturn(jarPluginConfiguration);
    jarPluginConfiguration.addChild(archive);

    Assert.assertNull(mavenProjectProperties.getMainClassFromJar());
  }

  @Test
  public void testGetMainClassFromJar_missingArchive() {
    Mockito.when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-jar-plugin"))
        .thenReturn(mockJarPlugin);
    Mockito.when(mockJarPlugin.getConfiguration()).thenReturn(jarPluginConfiguration);

    Assert.assertNull(mavenProjectProperties.getMainClassFromJar());
  }

  @Test
  public void testGetMainClassFromJar_missingConfiguration() {
    Mockito.when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-jar-plugin"))
        .thenReturn(mockJarPlugin);

    Assert.assertNull(mavenProjectProperties.getMainClassFromJar());
  }

  @Test
  public void testGetMainClassFromJar_missingPlugin() {
    Assert.assertNull(mavenProjectProperties.getMainClassFromJar());
  }
}
