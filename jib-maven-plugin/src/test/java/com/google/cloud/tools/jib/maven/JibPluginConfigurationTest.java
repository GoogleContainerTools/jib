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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import com.google.cloud.tools.jib.maven.JibPluginConfiguration.PermissionConfiguration;
import com.google.cloud.tools.jib.maven.JibPluginConfiguration.PlatformParameters;
import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link JibPluginConfiguration}. */
@RunWith(MockitoJUnitRunner.class)
public class JibPluginConfigurationTest {

  private final MavenProject project = new MavenProject();
  private final Properties sessionProperties = new Properties();
  @Mock private MavenSession session;
  @Mock private Log log;
  @Mock private Build build;
  private JibPluginConfiguration testPluginConfiguration;

  @Before
  public void setup() {
    when(session.getSystemProperties()).thenReturn(sessionProperties);
    when(build.getDirectory()).thenReturn("/test/directory");
    testPluginConfiguration =
        new JibPluginConfiguration() {
          @Override
          public void execute() {}

          @Override
          public Log getLog() {
            return log;
          }
        };
    project.setBuild(build);
    project.setFile(new File("/repository/project/pom.xml")); // sets baseDir
    testPluginConfiguration.setProject(project);
    testPluginConfiguration.setSession(session);
  }

  @Test
  public void testDefaults() {
    assertThat(testPluginConfiguration.getPlatforms().get(0).getOsName()).hasValue("linux");
    assertThat(testPluginConfiguration.getPlatforms().get(0).getArchitectureName())
        .hasValue("amd64");
    assertThat(testPluginConfiguration.getAppRoot()).isEmpty();
    assertThat(testPluginConfiguration.getWorkingDirectory()).isNull();
    assertThat(testPluginConfiguration.getExtraClasspath()).isEmpty();
    assertThat(testPluginConfiguration.getExpandClasspathDependencies()).isFalse();
    assertThat(testPluginConfiguration.getContainerizingMode()).isEqualTo("exploded");
    assertThat(testPluginConfiguration.getFilesModificationTime()).isEqualTo("EPOCH_PLUS_SECOND");
    assertThat(testPluginConfiguration.getCreationTime()).isEqualTo("EPOCH");
    assertThat(testPluginConfiguration.getInjectedPluginExtensions()).isEmpty();
    assertThat(testPluginConfiguration.getBaseImageCredHelperConfig().getHelperName())
        .isEqualTo(Optional.empty());
    assertThat(testPluginConfiguration.getBaseImageCredHelperConfig().getEnvironment()).isEmpty();
    assertThat(testPluginConfiguration.getTargetImageCredentialHelperConfig().getHelperName())
        .isEqualTo(Optional.empty());
    assertThat(testPluginConfiguration.getTargetImageCredentialHelperConfig().getEnvironment())
        .isEmpty();
    assertThat(testPluginConfiguration.getEnablePlatformTags()).isFalse();
  }

  @Test
  public void testSystemProperties() {
    sessionProperties.put("jib.from.image", "fromImage");
    assertThat(testPluginConfiguration.getBaseImage()).isEqualTo("fromImage");
    sessionProperties.put("jib.from.credHelper", "credHelper");
    assertThat(testPluginConfiguration.getBaseImageCredHelperConfig().getHelperName().get())
        .isEqualTo("credHelper");

    sessionProperties.put("jib.from.platforms", "linux/amd64,darwin/arm64");
    List<PlatformParameters> platforms = testPluginConfiguration.getPlatforms();
    assertThat(platforms).hasSize(2);
    assertThat(platforms.get(0).getOsName()).hasValue("linux");
    assertThat(platforms.get(0).getArchitectureName()).hasValue("amd64");
    assertThat(platforms.get(1).getOsName()).hasValue("darwin");
    assertThat(platforms.get(1).getArchitectureName()).hasValue("arm64");

    sessionProperties.put("image", "toImage");
    assertThat(testPluginConfiguration.getTargetImage()).isEqualTo("toImage");
    sessionProperties.remove("image");
    sessionProperties.put("jib.to.image", "toImage2");
    assertThat(testPluginConfiguration.getTargetImage()).isEqualTo("toImage2");
    sessionProperties.put("jib.to.tags", "tag1,tag2,tag3");
    assertThat(testPluginConfiguration.getTargetImageAdditionalTags())
        .containsExactly("tag1", "tag2", "tag3");
    sessionProperties.put("jib.to.credHelper", "credHelper");
    assertThat(testPluginConfiguration.getTargetImageCredentialHelperConfig().getHelperName().get())
        .isEqualTo("credHelper");

    sessionProperties.put("jib.container.appRoot", "appRoot");
    assertThat(testPluginConfiguration.getAppRoot()).isEqualTo("appRoot");
    sessionProperties.put("jib.container.args", "arg1,arg2,arg3");
    assertThat(testPluginConfiguration.getArgs()).containsExactly("arg1", "arg2", "arg3").inOrder();
    sessionProperties.put("jib.container.entrypoint", "entry1,entry2,entry3");
    assertThat(testPluginConfiguration.getEntrypoint())
        .containsExactly("entry1", "entry2", "entry3")
        .inOrder();
    sessionProperties.put("jib.container.environment", "env1=val1,env2=val2");
    assertThat(testPluginConfiguration.getEnvironment())
        .containsExactly("env1", "val1", "env2", "val2")
        .inOrder();
    sessionProperties.put("jib.container.format", "format");
    assertThat(testPluginConfiguration.getFormat()).isEqualTo("format");
    sessionProperties.put("jib.container.jvmFlags", "flag1,flag2,flag3");
    assertThat(testPluginConfiguration.getJvmFlags())
        .containsExactly("flag1", "flag2", "flag3")
        .inOrder();
    sessionProperties.put("jib.container.labels", "label1=val1,label2=val2");
    assertThat(testPluginConfiguration.getLabels())
        .containsExactly("label1", "val1", "label2", "val2")
        .inOrder();
    sessionProperties.put("jib.container.mainClass", "main");
    assertThat(testPluginConfiguration.getMainClass()).isEqualTo("main");
    sessionProperties.put("jib.container.ports", "port1,port2,port3");
    assertThat(testPluginConfiguration.getExposedPorts())
        .containsExactly("port1", "port2", "port3")
        .inOrder();
    ;
    sessionProperties.put("jib.container.user", "myUser");
    assertThat(testPluginConfiguration.getUser()).isEqualTo("myUser");
    sessionProperties.put("jib.container.workingDirectory", "/working/directory");
    assertThat(testPluginConfiguration.getWorkingDirectory()).isEqualTo("/working/directory");
    sessionProperties.put("jib.container.filesModificationTime", "2011-12-03T22:42:05Z");
    assertThat(testPluginConfiguration.getFilesModificationTime())
        .isEqualTo("2011-12-03T22:42:05Z");
    sessionProperties.put("jib.container.creationTime", "2011-12-03T22:42:05Z");
    assertThat(testPluginConfiguration.getCreationTime()).isEqualTo("2011-12-03T22:42:05Z");
    sessionProperties.put("jib.container.extraClasspath", "/foo,/bar");
    assertThat(testPluginConfiguration.getExtraClasspath())
        .containsExactly("/foo", "/bar")
        .inOrder();
    sessionProperties.put("jib.container.expandClasspathDependencies", "true");
    assertThat(testPluginConfiguration.getExpandClasspathDependencies()).isTrue();
    sessionProperties.put("jib.containerizingMode", "packaged");
    assertThat(testPluginConfiguration.getContainerizingMode()).isEqualTo("packaged");

    sessionProperties.put("jib.dockerClient.executable", "test-exec");
    assertThat(testPluginConfiguration.getDockerClientExecutable())
        .isEqualTo(Paths.get("test-exec"));
    sessionProperties.put("jib.dockerClient.environment", "env1=val1,env2=val2");
    assertThat(testPluginConfiguration.getDockerClientEnvironment())
        .containsExactly("env1", "val1", "env2", "val2")
        .inOrder();

    sessionProperties.put("jib.to.enablePlatformTags", "true");
    assertThat(testPluginConfiguration.getEnablePlatformTags()).isTrue();
  }

  @Test
  public void testSystemPropertiesWithInvalidPlatform() {
    sessionProperties.put("jib.from.platforms", "linux /amd64");
    assertThrows(IllegalArgumentException.class, testPluginConfiguration::getPlatforms);
  }

  @Test
  public void testSystemPropertiesExtraDirectories() {
    sessionProperties.put("jib.extraDirectories.paths", "custom-jib");
    assertThat(testPluginConfiguration.getExtraDirectories()).hasSize(1);
    assertThat(testPluginConfiguration.getExtraDirectories().get(0).getFrom())
        .isEqualTo(Paths.get("custom-jib"));
    assertThat(testPluginConfiguration.getExtraDirectories().get(0).getInto()).isEqualTo("/");
    sessionProperties.put("jib.extraDirectories.permissions", "/test/file1=123,/another/file=456");
    List<PermissionConfiguration> permissions =
        testPluginConfiguration.getExtraDirectoryPermissions();
    assertThat(permissions.get(0).getFile()).hasValue("/test/file1");
    assertThat(permissions.get(0).getMode()).hasValue("123");
    assertThat(permissions.get(1).getFile()).hasValue("/another/file");
    assertThat(permissions.get(1).getMode()).hasValue("456");
  }

  @Test
  public void testSystemPropertiesOutputPaths() {
    // Absolute paths
    sessionProperties.put("jib.outputPaths.digest", "/digest/path");
    assertThat(testPluginConfiguration.getDigestOutputPath()).isEqualTo(Paths.get("/digest/path"));
    sessionProperties.put("jib.outputPaths.imageId", "/id/path");
    assertThat(testPluginConfiguration.getImageIdOutputPath()).isEqualTo(Paths.get("/id/path"));
    sessionProperties.put("jib.outputPaths.tar", "/tar/path");
    assertThat(testPluginConfiguration.getTarOutputPath()).isEqualTo(Paths.get("/tar/path"));
    // Relative paths
    sessionProperties.put("jib.outputPaths.digest", "digest/path");
    assertThat(testPluginConfiguration.getDigestOutputPath())
        .isEqualTo(Paths.get("/repository/project/digest/path"));
    sessionProperties.put("jib.outputPaths.imageId", "id/path");
    assertThat(testPluginConfiguration.getImageIdOutputPath())
        .isEqualTo(Paths.get("/repository/project/id/path"));
    sessionProperties.put("jib.outputPaths.imageJson", "json/path");
    assertThat(testPluginConfiguration.getImageJsonOutputPath())
        .isEqualTo(Paths.get("/repository/project/json/path"));
    sessionProperties.put("jib.outputPaths.tar", "tar/path");
    assertThat(testPluginConfiguration.getTarOutputPath())
        .isEqualTo(Paths.get("/repository/project/tar/path"));
  }

  @Test
  public void testPomProperties() {
    project.getProperties().setProperty("jib.from.image", "fromImage");
    assertThat(testPluginConfiguration.getBaseImage()).isEqualTo("fromImage");
    project.getProperties().setProperty("jib.from.credHelper", "credHelper");
    assertThat(testPluginConfiguration.getBaseImageCredHelperConfig().getHelperName().get())
        .isEqualTo("credHelper");

    project.getProperties().setProperty("image", "toImage");
    assertThat(testPluginConfiguration.getTargetImage()).isEqualTo("toImage");
    project.getProperties().remove("image");
    project.getProperties().setProperty("jib.to.image", "toImage2");
    assertThat(testPluginConfiguration.getTargetImage()).isEqualTo("toImage2");
    project.getProperties().setProperty("jib.to.tags", "tag1,tag2,tag3");
    assertThat(testPluginConfiguration.getTargetImageAdditionalTags())
        .containsExactly("tag1", "tag2", "tag3");
    project.getProperties().setProperty("jib.to.credHelper", "credHelper");
    assertThat(testPluginConfiguration.getTargetImageCredentialHelperConfig().getHelperName().get())
        .isEqualTo("credHelper");

    project.getProperties().setProperty("jib.container.appRoot", "appRoot");
    assertThat(testPluginConfiguration.getAppRoot()).isEqualTo("appRoot");
    project.getProperties().setProperty("jib.container.args", "arg1,arg2,arg3");
    assertThat(testPluginConfiguration.getArgs()).containsExactly("arg1", "arg2", "arg3").inOrder();
    project.getProperties().setProperty("jib.container.entrypoint", "entry1,entry2,entry3");
    assertThat(testPluginConfiguration.getEntrypoint())
        .containsExactly("entry1", "entry2", "entry3")
        .inOrder();
    project.getProperties().setProperty("jib.container.environment", "env1=val1,env2=val2");
    assertThat(testPluginConfiguration.getEnvironment())
        .containsExactly("env1", "val1", "env2", "val2")
        .inOrder();
    project.getProperties().setProperty("jib.container.format", "format");
    assertThat(testPluginConfiguration.getFormat()).isEqualTo("format");
    project.getProperties().setProperty("jib.container.jvmFlags", "flag1,flag2,flag3");
    assertThat(testPluginConfiguration.getJvmFlags())
        .containsExactly("flag1", "flag2", "flag3")
        .inOrder();
    project.getProperties().setProperty("jib.container.labels", "label1=val1,label2=val2");
    assertThat(testPluginConfiguration.getLabels())
        .containsExactly("label1", "val1", "label2", "val2")
        .inOrder();
    project.getProperties().setProperty("jib.container.mainClass", "main");
    assertThat(testPluginConfiguration.getMainClass()).isEqualTo("main");
    project.getProperties().setProperty("jib.container.ports", "port1,port2,port3");
    assertThat(testPluginConfiguration.getExposedPorts())
        .containsExactly("port1", "port2", "port3")
        .inOrder();
    project.getProperties().setProperty("jib.container.user", "myUser");
    assertThat(testPluginConfiguration.getUser()).isEqualTo("myUser");
    project.getProperties().setProperty("jib.container.workingDirectory", "/working/directory");
    assertThat(testPluginConfiguration.getWorkingDirectory()).isEqualTo("/working/directory");
    project
        .getProperties()
        .setProperty("jib.container.filesModificationTime", "2011-12-03T22:42:05Z");
    assertThat(testPluginConfiguration.getFilesModificationTime())
        .isEqualTo("2011-12-03T22:42:05Z");
    project.getProperties().setProperty("jib.container.creationTime", "2011-12-03T22:42:05Z");
    assertThat(testPluginConfiguration.getCreationTime()).isEqualTo("2011-12-03T22:42:05Z");
    project.getProperties().setProperty("jib.container.extraClasspath", "/foo,/bar");
    assertThat(testPluginConfiguration.getExtraClasspath())
        .containsExactly("/foo", "/bar")
        .inOrder();
    project.getProperties().setProperty("jib.container.expandClasspathDependencies", "true");
    assertThat(testPluginConfiguration.getExpandClasspathDependencies()).isTrue();
    project.getProperties().setProperty("jib.containerizingMode", "packaged");
    assertThat(testPluginConfiguration.getContainerizingMode()).isEqualTo("packaged");

    project.getProperties().setProperty("jib.dockerClient.executable", "test-exec");
    assertThat(testPluginConfiguration.getDockerClientExecutable())
        .isEqualTo(Paths.get("test-exec"));
    project.getProperties().setProperty("jib.dockerClient.environment", "env1=val1,env2=val2");
    assertThat(testPluginConfiguration.getDockerClientEnvironment())
        .containsExactly("env1", "val1", "env2", "val2")
        .inOrder();

    project.getProperties().setProperty("jib.to.enablePlatformTags", "true");
    assertThat(testPluginConfiguration.getEnablePlatformTags()).isTrue();
  }

  @Test
  public void testPomPropertiesExtraDirectories() {
    project.getProperties().setProperty("jib.extraDirectories.paths", "custom-jib");
    assertThat(testPluginConfiguration.getExtraDirectories()).hasSize(1);
    assertThat(testPluginConfiguration.getExtraDirectories().get(0).getFrom())
        .isEqualTo(Paths.get("custom-jib"));
    assertThat(testPluginConfiguration.getExtraDirectories().get(0).getInto()).isEqualTo("/");
    project
        .getProperties()
        .setProperty("jib.extraDirectories.permissions", "/test/file1=123,/another/file=456");
    List<PermissionConfiguration> permissions =
        testPluginConfiguration.getExtraDirectoryPermissions();
    assertThat(permissions.get(0).getFile()).hasValue("/test/file1");
    assertThat(permissions.get(0).getMode()).hasValue("123");
    assertThat(permissions.get(1).getFile()).hasValue("/another/file");
    assertThat(permissions.get(1).getMode()).hasValue("456");
  }

  @Test
  public void testPomPropertiesOutputPaths() {
    project.getProperties().setProperty("jib.outputPaths.digest", "/digest/path");
    assertThat(testPluginConfiguration.getDigestOutputPath()).isEqualTo(Paths.get("/digest/path"));
    project.getProperties().setProperty("jib.outputPaths.imageId", "/id/path");
    assertThat(testPluginConfiguration.getImageIdOutputPath()).isEqualTo(Paths.get("/id/path"));
    project.getProperties().setProperty("jib.outputPaths.imageJson", "/json/path");
    assertThat(testPluginConfiguration.getImageJsonOutputPath()).isEqualTo(Paths.get("/json/path"));
    project.getProperties().setProperty("jib.outputPaths.tar", "tar/path");
    assertThat(testPluginConfiguration.getTarOutputPath())
        .isEqualTo(Paths.get("/repository/project/tar/path"));
  }

  @Test
  public void testEmptyOrNullTags() {
    // https://github.com/GoogleContainerTools/jib/issues/1534
    // Maven turns empty tags into null entries, and its possible to have empty tags in jib.to.tags
    sessionProperties.put("jib.to.tags", "a,,b");
    Exception ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> testPluginConfiguration.getTargetImageAdditionalTags());
    assertThat(ex.getMessage()).isEqualTo("jib.to.tags has empty tag");
  }

  @Test
  public void testIsContainerizable_noProperty() {
    Properties projectProperties = project.getProperties();

    projectProperties.remove("jib.containerize");
    assertThat(testPluginConfiguration.isContainerizable()).isTrue();

    projectProperties.setProperty("jib.containerize", "");
    assertThat(testPluginConfiguration.isContainerizable()).isTrue();
  }

  @Test
  public void testIsContainerizable_artifactId() {
    project.setGroupId("group");
    project.setArtifactId("artifact");

    Properties projectProperties = project.getProperties();
    projectProperties.setProperty("jib.containerize", ":artifact");
    assertThat(testPluginConfiguration.isContainerizable()).isTrue();

    projectProperties.setProperty("jib.containerize", ":artifact2");
    assertThat(testPluginConfiguration.isContainerizable()).isFalse();
  }

  @Test
  public void testIsContainerizable_groupAndArtifactId() {
    project.setGroupId("group");
    project.setArtifactId("artifact");

    Properties projectProperties = project.getProperties();
    projectProperties.setProperty("jib.containerize", "group:artifact");
    assertThat(testPluginConfiguration.isContainerizable()).isTrue();

    projectProperties.setProperty("jib.containerize", "group:artifact2");
    assertThat(testPluginConfiguration.isContainerizable()).isFalse();
  }

  @Test
  public void testIsContainerizable_directory() {
    project.setGroupId("group");
    project.setArtifactId("artifact");

    Properties projectProperties = project.getProperties();
    projectProperties.setProperty("jib.containerize", "project");
    assertThat(testPluginConfiguration.isContainerizable()).isTrue();

    projectProperties.setProperty("jib.containerize", "project2");
    assertThat(testPluginConfiguration.isContainerizable()).isFalse();
  }
}
