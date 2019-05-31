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

import com.google.cloud.tools.jib.maven.JibPluginConfiguration.PermissionConfiguration;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link JibPluginConfiguration}. */
@RunWith(MockitoJUnitRunner.class)
public class JibPluginConfigurationTest {

  private final MavenProject project = new MavenProject();
  private final Properties sessionProperties = new Properties();
  @Mock private MavenSession session;
  @Mock private Log log;
  private JibPluginConfiguration testPluginConfiguration;

  @Before
  public void setup() {
    Mockito.when(session.getSystemProperties()).thenReturn(sessionProperties);
    testPluginConfiguration =
        new JibPluginConfiguration() {
          @Override
          public void execute() {}

          @Override
          public Log getLog() {
            return log;
          }
        };
    testPluginConfiguration.setProject(project);
    testPluginConfiguration.setSession(session);
  }

  @Test
  public void testDefaults() {
    Assert.assertEquals("", testPluginConfiguration.getAppRoot());
    Assert.assertNull(testPluginConfiguration.getWorkingDirectory());
    Assert.assertTrue(testPluginConfiguration.getExtraClasspath().isEmpty());
    Assert.assertEquals("exploded", testPluginConfiguration.getContainerizingMode());
  }

  @Test
  public void testSystemProperties() {
    sessionProperties.put("jib.from.image", "fromImage");
    Assert.assertEquals("fromImage", testPluginConfiguration.getBaseImage());
    sessionProperties.put("jib.from.credHelper", "credHelper");
    Assert.assertEquals("credHelper", testPluginConfiguration.getBaseImageCredentialHelperName());

    sessionProperties.put("image", "toImage");
    Assert.assertEquals("toImage", testPluginConfiguration.getTargetImage());
    sessionProperties.remove("image");
    sessionProperties.put("jib.to.image", "toImage2");
    Assert.assertEquals("toImage2", testPluginConfiguration.getTargetImage());
    sessionProperties.put("jib.to.tags", "tag1,tag2,tag3");
    Assert.assertEquals(
        ImmutableSet.of("tag1", "tag2", "tag3"),
        testPluginConfiguration.getTargetImageAdditionalTags());
    sessionProperties.put("jib.to.credHelper", "credHelper");
    Assert.assertEquals("credHelper", testPluginConfiguration.getTargetImageCredentialHelperName());

    sessionProperties.put("jib.container.appRoot", "appRoot");
    Assert.assertEquals("appRoot", testPluginConfiguration.getAppRoot());
    sessionProperties.put("jib.container.args", "arg1,arg2,arg3");
    Assert.assertEquals(
        ImmutableList.of("arg1", "arg2", "arg3"), testPluginConfiguration.getArgs());
    sessionProperties.put("jib.container.entrypoint", "entry1,entry2,entry3");
    Assert.assertEquals(
        ImmutableList.of("entry1", "entry2", "entry3"), testPluginConfiguration.getEntrypoint());
    sessionProperties.put("jib.container.environment", "env1=val1,env2=val2");
    Assert.assertEquals(
        ImmutableMap.of("env1", "val1", "env2", "val2"), testPluginConfiguration.getEnvironment());
    sessionProperties.put("jib.container.format", "format");
    Assert.assertEquals("format", testPluginConfiguration.getFormat());
    sessionProperties.put("jib.container.jvmFlags", "flag1,flag2,flag3");
    Assert.assertEquals(
        ImmutableList.of("flag1", "flag2", "flag3"), testPluginConfiguration.getJvmFlags());
    sessionProperties.put("jib.container.labels", "label1=val1,label2=val2");
    Assert.assertEquals(
        ImmutableMap.of("label1", "val1", "label2", "val2"), testPluginConfiguration.getLabels());
    sessionProperties.put("jib.container.mainClass", "main");
    Assert.assertEquals("main", testPluginConfiguration.getMainClass());
    sessionProperties.put("jib.container.ports", "port1,port2,port3");
    Assert.assertEquals(
        ImmutableList.of("port1", "port2", "port3"), testPluginConfiguration.getExposedPorts());
    sessionProperties.put("jib.container.useCurrentTimestamp", "true");
    Assert.assertTrue(testPluginConfiguration.getUseCurrentTimestamp());
    sessionProperties.put("jib.container.user", "myUser");
    Assert.assertEquals("myUser", testPluginConfiguration.getUser());
    sessionProperties.put("jib.container.workingDirectory", "working directory");
    Assert.assertEquals("working directory", testPluginConfiguration.getWorkingDirectory());
    sessionProperties.put("jib.container.extraClasspath", "/foo,/bar");
    Assert.assertEquals(
        ImmutableList.of("/foo", "/bar"), testPluginConfiguration.getExtraClasspath());
    sessionProperties.put("jib.containerizingMode", "packaged");
    Assert.assertEquals("packaged", testPluginConfiguration.getContainerizingMode());

    sessionProperties.put("jib.extraDirectories.paths", "custom-jib");
    Assert.assertEquals(
        Arrays.asList(Paths.get("custom-jib")), testPluginConfiguration.getExtraDirectories());
    sessionProperties.put("jib.extraDirectories.permissions", "/test/file1=123,/another/file=456");
    List<PermissionConfiguration> permissions =
        testPluginConfiguration.getExtraDirectoryPermissions();
    Assert.assertEquals("/test/file1", permissions.get(0).getFile().get());
    Assert.assertEquals("123", permissions.get(0).getMode().get());
    Assert.assertEquals("/another/file", permissions.get(1).getFile().get());
    Assert.assertEquals("456", permissions.get(1).getMode().get());
  }

  @Test
  public void testPomProperties() {
    project.getProperties().setProperty("jib.from.image", "fromImage");
    Assert.assertEquals("fromImage", testPluginConfiguration.getBaseImage());
    project.getProperties().setProperty("jib.from.credHelper", "credHelper");
    Assert.assertEquals("credHelper", testPluginConfiguration.getBaseImageCredentialHelperName());

    project.getProperties().setProperty("image", "toImage");
    Assert.assertEquals("toImage", testPluginConfiguration.getTargetImage());
    project.getProperties().remove("image");
    project.getProperties().setProperty("jib.to.image", "toImage2");
    Assert.assertEquals("toImage2", testPluginConfiguration.getTargetImage());
    project.getProperties().setProperty("jib.to.tags", "tag1,tag2,tag3");
    Assert.assertEquals(
        ImmutableSet.of("tag1", "tag2", "tag3"),
        testPluginConfiguration.getTargetImageAdditionalTags());
    project.getProperties().setProperty("jib.to.credHelper", "credHelper");
    Assert.assertEquals("credHelper", testPluginConfiguration.getTargetImageCredentialHelperName());

    project.getProperties().setProperty("jib.container.appRoot", "appRoot");
    Assert.assertEquals("appRoot", testPluginConfiguration.getAppRoot());
    project.getProperties().setProperty("jib.container.args", "arg1,arg2,arg3");
    Assert.assertEquals(
        ImmutableList.of("arg1", "arg2", "arg3"), testPluginConfiguration.getArgs());
    project.getProperties().setProperty("jib.container.entrypoint", "entry1,entry2,entry3");
    Assert.assertEquals(
        ImmutableList.of("entry1", "entry2", "entry3"), testPluginConfiguration.getEntrypoint());
    project.getProperties().setProperty("jib.container.environment", "env1=val1,env2=val2");
    Assert.assertEquals(
        ImmutableMap.of("env1", "val1", "env2", "val2"), testPluginConfiguration.getEnvironment());
    project.getProperties().setProperty("jib.container.format", "format");
    Assert.assertEquals("format", testPluginConfiguration.getFormat());
    project.getProperties().setProperty("jib.container.jvmFlags", "flag1,flag2,flag3");
    Assert.assertEquals(
        ImmutableList.of("flag1", "flag2", "flag3"), testPluginConfiguration.getJvmFlags());
    project.getProperties().setProperty("jib.container.labels", "label1=val1,label2=val2");
    Assert.assertEquals(
        ImmutableMap.of("label1", "val1", "label2", "val2"), testPluginConfiguration.getLabels());
    project.getProperties().setProperty("jib.container.mainClass", "main");
    Assert.assertEquals("main", testPluginConfiguration.getMainClass());
    project.getProperties().setProperty("jib.container.ports", "port1,port2,port3");
    Assert.assertEquals(
        ImmutableList.of("port1", "port2", "port3"), testPluginConfiguration.getExposedPorts());
    project.getProperties().setProperty("jib.container.useCurrentTimestamp", "true");
    Assert.assertTrue(testPluginConfiguration.getUseCurrentTimestamp());
    project.getProperties().setProperty("jib.container.user", "myUser");
    Assert.assertEquals("myUser", testPluginConfiguration.getUser());
    project.getProperties().setProperty("jib.container.workingDirectory", "working directory");
    Assert.assertEquals("working directory", testPluginConfiguration.getWorkingDirectory());
    project.getProperties().setProperty("jib.container.extraClasspath", "/foo,/bar");
    Assert.assertEquals(
        ImmutableList.of("/foo", "/bar"), testPluginConfiguration.getExtraClasspath());
    project.getProperties().setProperty("jib.containerizingMode", "packaged");
    Assert.assertEquals("packaged", testPluginConfiguration.getContainerizingMode());

    project.getProperties().setProperty("jib.extraDirectories.paths", "custom-jib");
    Assert.assertEquals(
        Arrays.asList(Paths.get("custom-jib")), testPluginConfiguration.getExtraDirectories());
    project
        .getProperties()
        .setProperty("jib.extraDirectories.permissions", "/test/file1=123,/another/file=456");
    List<PermissionConfiguration> permissions =
        testPluginConfiguration.getExtraDirectoryPermissions();
    Assert.assertEquals("/test/file1", permissions.get(0).getFile().get());
    Assert.assertEquals("123", permissions.get(0).getMode().get());
    Assert.assertEquals("/another/file", permissions.get(1).getFile().get());
    Assert.assertEquals("456", permissions.get(1).getMode().get());
  }

  @Test
  public void testEmptyOrNullTags() {
    // https://github.com/GoogleContainerTools/jib/issues/1534
    // Maven turns empty tags into null entries, and its possible
    // to have empty tags in jib.to.tags
    sessionProperties.put("jib.to.tags", "a,,b");
    try {
      testPluginConfiguration.getTargetImageAdditionalTags();
      Assert.fail();
    } catch (IllegalArgumentException ex) {
      Assert.assertEquals("jib.to.tags has empty tag", ex.getMessage());
    }
  }

  @Test
  public void testDeprecatedSystemProperties() {
    sessionProperties.put("jib.extraDirectory.path", "custom-jib");
    Assert.assertEquals(
        Arrays.asList(Paths.get("custom-jib")), testPluginConfiguration.getExtraDirectories());
    sessionProperties.put("jib.extraDirectory.permissions", "/test/file13=650,/another/file24=777");
    List<PermissionConfiguration> permissions =
        testPluginConfiguration.getExtraDirectoryPermissions();
    Assert.assertEquals("/test/file13", permissions.get(0).getFile().get());
    Assert.assertEquals("650", permissions.get(0).getMode().get());
    Assert.assertEquals("/another/file24", permissions.get(1).getFile().get());
    Assert.assertEquals("777", permissions.get(1).getMode().get());

    Mockito.verify(log, Mockito.times(1))
        .warn(
            "The property 'jib.extraDirectory.path' is deprecated; "
                + "use 'jib.extraDirectories.paths' instead");
  }

  @Test
  public void testDeprecatedProperties() {
    Properties projectProperties = project.getProperties();

    projectProperties.setProperty("jib.extraDirectory.path", "this-is-extra");
    Assert.assertEquals(
        Arrays.asList(Paths.get("this-is-extra")), testPluginConfiguration.getExtraDirectories());

    projectProperties.setProperty(
        "jib.extraDirectory.permissions", "/test/file1=654,/dir/file2=321");
    List<PermissionConfiguration> permissions =
        testPluginConfiguration.getExtraDirectoryPermissions();
    Assert.assertEquals("/test/file1", permissions.get(0).getFile().get());
    Assert.assertEquals("654", permissions.get(0).getMode().get());
    Assert.assertEquals("/dir/file2", permissions.get(1).getFile().get());
    Assert.assertEquals("321", permissions.get(1).getMode().get());

    Mockito.verify(log, Mockito.times(1))
        .warn(
            "The property 'jib.extraDirectory.path' is deprecated; "
                + "use 'jib.extraDirectories.paths' instead");
  }

  @Test
  public void testGetExtraDirectories_bothSystemPropertiesUsed() {
    sessionProperties.put("jib.extraDirectory.path", "deprecated-property");
    sessionProperties.put("jib.extraDirectories.paths", "new-property");

    try {
      testPluginConfiguration.getExtraDirectories();
      Assert.fail();
    } catch (IllegalArgumentException ex) {
      Assert.assertEquals(
          "You cannot configure both 'jib.extraDirectory.path' and 'jib.extraDirectories.paths'",
          ex.getMessage());
    }
  }

  @Test
  public void testGetExtraDirectories_bothPropertiesUsed() {
    Properties projectProperties = project.getProperties();
    projectProperties.setProperty("jib.extraDirectory.path", "deprecated-property");
    projectProperties.setProperty("jib.extraDirectories.paths", "new-property");

    try {
      testPluginConfiguration.getExtraDirectories();
      Assert.fail();
    } catch (IllegalArgumentException ex) {
      Assert.assertEquals(
          "You cannot configure both 'jib.extraDirectory.path' and 'jib.extraDirectories.paths'",
          ex.getMessage());
    }
  }

  @Test
  public void testGetExtraDirectoryPermissions_bothSystemPropertiesUsed() {
    sessionProperties.put("jib.extraDirectory.permissions", "deprecated-property");
    sessionProperties.put("jib.extraDirectories.permissions", "new-property");

    try {
      testPluginConfiguration.getExtraDirectoryPermissions();
      Assert.fail();
    } catch (IllegalArgumentException ex) {
      Assert.assertEquals(
          "You cannot configure both 'jib.extraDirectory.permissions' and "
              + "'jib.extraDirectories.permissions'",
          ex.getMessage());
    }
  }

  @Test
  public void testGetExtraDirectoryPermissions_bothPropertiesUsed() {
    Properties projectProperties = project.getProperties();
    projectProperties.setProperty("jib.extraDirectory.permissions", "deprecated-property");
    projectProperties.setProperty("jib.extraDirectories.permissions", "new-property");

    try {
      testPluginConfiguration.getExtraDirectoryPermissions();
      Assert.fail();
    } catch (IllegalArgumentException ex) {
      Assert.assertEquals(
          "You cannot configure both 'jib.extraDirectory.permissions' and "
              + "'jib.extraDirectories.permissions'",
          ex.getMessage());
    }
  }

  @Test
  public void testIsContainerizable_noProperty() {
    Properties projectProperties = project.getProperties();

    projectProperties.remove("jib.containerize");
    Assert.assertTrue(testPluginConfiguration.isContainerizable());

    projectProperties.setProperty("jib.containerize", "");
    Assert.assertTrue(testPluginConfiguration.isContainerizable());
  }

  @Test
  public void testIsContainerizable_artifactId() {
    project.setGroupId("group");
    project.setArtifactId("artifact");
    project.setFile(new File("/repository/project/pom.xml")); // sets baseDir

    Properties projectProperties = project.getProperties();
    projectProperties.setProperty("jib.containerize", ":artifact");
    Assert.assertTrue(testPluginConfiguration.isContainerizable());

    projectProperties.setProperty("jib.containerize", ":artifact2");
    Assert.assertFalse(testPluginConfiguration.isContainerizable());
  }

  @Test
  public void testIsContainerizable_groupAndArtifactId() {
    project.setGroupId("group");
    project.setArtifactId("artifact");
    project.setFile(new File("/repository/project/pom.xml")); // sets baseDir

    Properties projectProperties = project.getProperties();
    projectProperties.setProperty("jib.containerize", "group:artifact");
    Assert.assertTrue(testPluginConfiguration.isContainerizable());

    projectProperties.setProperty("jib.containerize", "group:artifact2");
    Assert.assertFalse(testPluginConfiguration.isContainerizable());
  }

  @Test
  public void testIsContainerizable_directory() {
    project.setGroupId("group");
    project.setArtifactId("artifact");
    project.setFile(new File("/repository/project/pom.xml")); // sets baseDir

    Properties projectProperties = project.getProperties();
    projectProperties.setProperty("jib.containerize", "project");
    Assert.assertTrue(testPluginConfiguration.isContainerizable());

    projectProperties.setProperty("jib.containerize", "project2");
    Assert.assertFalse(testPluginConfiguration.isContainerizable());
  }
}
