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
import java.util.Properties;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoFailureException;
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
  @Mock private Properties mockMavenProperties;
  @Mock private JavaLayerConfigurations mockJavaLayerConfigurations;
  @Mock private Plugin mockJarPlugin;
  @Mock private Plugin mockCompilerPlugin;
  @Mock private Log mockLog;

  private Xpp3Dom jarPluginConfiguration;
  private Xpp3Dom archive;
  private Xpp3Dom manifest;
  private Xpp3Dom jarPluginMainClass;

  @Mock private Xpp3Dom compilerPluginConfiguration;
  @Mock private Xpp3Dom compilerTarget;
  @Mock private Xpp3Dom compilerRelease;

  private MavenProjectProperties mavenProjectProperties;

  @Before
  public void setup() {
    mavenProjectProperties =
        new MavenProjectProperties(mockMavenProject, mockLog, mockJavaLayerConfigurations);
    jarPluginConfiguration = new Xpp3Dom("");
    archive = new Xpp3Dom("archive");
    manifest = new Xpp3Dom("manifest");
    jarPluginMainClass = new Xpp3Dom("mainClass");

    Mockito.when(mockMavenProject.getProperties()).thenReturn(mockMavenProperties);
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

  @Test
  public void testIsWarProject() {
    Assert.assertFalse(mavenProjectProperties.isWarProject());
  }

  @Test
  public void testGetVersionFromString() {
    Assert.assertEquals(8, MavenProjectProperties.getVersionFromString("1.8"));
    Assert.assertEquals(8, MavenProjectProperties.getVersionFromString("1.8.0_123"));
    Assert.assertEquals(11, MavenProjectProperties.getVersionFromString("11"));
    Assert.assertEquals(11, MavenProjectProperties.getVersionFromString("11.0.1"));

    Assert.assertEquals(0, MavenProjectProperties.getVersionFromString("asdfasdf"));
    Assert.assertEquals(0, MavenProjectProperties.getVersionFromString(""));
    Assert.assertEquals(0, MavenProjectProperties.getVersionFromString("11abc"));
    Assert.assertEquals(0, MavenProjectProperties.getVersionFromString("1.abc"));
  }

  @Test
  public void testValidateBaseImageVersion_nonDefaultBaseImage() throws MojoFailureException {
    mavenProjectProperties.validateAgainstDefaultBaseImageVersion("non-default");
  }

  @Test
  public void testValidateBaseImageVersion_allNull() throws MojoFailureException {
    mavenProjectProperties.validateAgainstDefaultBaseImageVersion(null);
  }

  @Test
  public void testValidateBaseImageVersion_targetProperty() throws MojoFailureException {
    Mockito.when(mockMavenProperties.getProperty("maven.compiler.target")).thenReturn("1.8");
    mavenProjectProperties.validateAgainstDefaultBaseImageVersion(null);

    Mockito.when(mockMavenProperties.getProperty("maven.compiler.target")).thenReturn("11");
    try {
      mavenProjectProperties.validateAgainstDefaultBaseImageVersion(null);
      Assert.fail();
    } catch (MojoFailureException ex) {
      Assert.assertEquals(
          "Jib's default base image uses Java 8, but project is using Java 11; perhaps you should configure a Java 11-compatible base image using the '<from><image>' parameter, or set maven-compiler-plugin's target or release version to 1.8 in your build configuration",
          ex.getMessage());
    }
  }

  @Test
  public void testValidateBaseImageVersion_releaseProperty() throws MojoFailureException {
    Mockito.when(mockMavenProperties.getProperty("maven.compiler.release")).thenReturn("8");
    mavenProjectProperties.validateAgainstDefaultBaseImageVersion(null);

    Mockito.when(mockMavenProperties.getProperty("maven.compiler.release")).thenReturn("11.0");
    try {
      mavenProjectProperties.validateAgainstDefaultBaseImageVersion(null);
      Assert.fail();
    } catch (MojoFailureException ex) {
      Assert.assertEquals(
          "Jib's default base image uses Java 8, but project is using Java 11; perhaps you should configure a Java 11-compatible base image using the '<from><image>' parameter, or set maven-compiler-plugin's target or release version to 1.8 in your build configuration",
          ex.getMessage());
    }
  }

  @Test
  public void testValidateBaseImageVersion_compilerPluginTarget() throws MojoFailureException {
    Mockito.when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-compiler-plugin"))
        .thenReturn(mockCompilerPlugin);
    Mockito.when(mockCompilerPlugin.getConfiguration()).thenReturn(compilerPluginConfiguration);
    Mockito.when(compilerPluginConfiguration.getChild("target")).thenReturn(compilerTarget);
    Mockito.when(compilerTarget.getValue()).thenReturn("1.8");
    mavenProjectProperties.validateAgainstDefaultBaseImageVersion(null);

    Mockito.when(compilerTarget.getValue()).thenReturn("11");
    try {
      mavenProjectProperties.validateAgainstDefaultBaseImageVersion(null);
      Assert.fail();
    } catch (MojoFailureException ex) {
      Assert.assertEquals(
          "Jib's default base image uses Java 8, but project is using Java 11; perhaps you should configure a Java 11-compatible base image using the '<from><image>' parameter, or set maven-compiler-plugin's target or release version to 1.8 in your build configuration",
          ex.getMessage());
    }
  }

  @Test
  public void testValidateBaseImageVersion_compilerPluginRelease() throws MojoFailureException {
    Mockito.when(mockMavenProject.getPlugin("org.apache.maven.plugins:maven-compiler-plugin"))
        .thenReturn(mockCompilerPlugin);
    Mockito.when(mockCompilerPlugin.getConfiguration()).thenReturn(compilerPluginConfiguration);
    Mockito.when(compilerPluginConfiguration.getChild("release")).thenReturn(compilerRelease);
    Mockito.when(compilerRelease.getValue()).thenReturn("1.8");
    mavenProjectProperties.validateAgainstDefaultBaseImageVersion(null);

    Mockito.when(compilerRelease.getValue()).thenReturn("11");
    try {
      mavenProjectProperties.validateAgainstDefaultBaseImageVersion(null);
      Assert.fail();
    } catch (MojoFailureException ex) {
      Assert.assertEquals(
          "Jib's default base image uses Java 8, but project is using Java 11; perhaps you should configure a Java 11-compatible base image using the '<from><image>' parameter, or set maven-compiler-plugin's target or release version to 1.8 in your build configuration",
          ex.getMessage());
    }
  }
}
