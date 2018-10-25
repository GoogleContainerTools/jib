/*
 * Copyright 2018 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.tools.jib.maven;

import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.JibContainerBuilderTestHelper;
import com.google.cloud.tools.jib.api.RegistryImage;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.configuration.ContainerConfiguration;
import com.google.cloud.tools.jib.configuration.FilePermissions;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.cloud.tools.jib.maven.JibPluginConfiguration.AuthConfiguration;
import com.google.cloud.tools.jib.maven.JibPluginConfiguration.PermissionConfiguration;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link PluginConfigurationProcessor}. */
@RunWith(MockitoJUnitRunner.class)
public class PluginConfigurationProcessorTest {

  private static BuildConfiguration getBuildConfiguration(JibContainerBuilder jibContainerBuilder)
      throws InvalidImageReferenceException, IOException, CacheDirectoryCreationException {
    return JibContainerBuilderTestHelper.toBuildConfiguration(
        jibContainerBuilder,
        BuildConfiguration.builder(),
        Containerizer.to(RegistryImage.named("ignored")));
  }

  @Mock private Log mockLog;
  @Mock private JibPluginConfiguration mockJibPluginConfiguration;
  @Mock private MavenProjectProperties mockProjectProperties;
  @Mock private MavenSession mockMavenSession;
  @Mock private Settings mockMavenSettings;
  @Mock private MavenProject mavenProject;

  @Before
  public void setUp() throws Exception {
    Mockito.when(mockJibPluginConfiguration.getSession()).thenReturn(mockMavenSession);
    Mockito.when(mockMavenSession.getSettings()).thenReturn(mockMavenSettings);

    Mockito.when(mockJibPluginConfiguration.getBaseImageAuth()).thenReturn(new AuthConfiguration());
    Mockito.when(mockJibPluginConfiguration.getEntrypoint()).thenReturn(Collections.emptyList());
    Mockito.when(mockJibPluginConfiguration.getJvmFlags()).thenReturn(Collections.emptyList());
    Mockito.when(mockJibPluginConfiguration.getArgs()).thenReturn(Collections.emptyList());
    Mockito.when(mockJibPluginConfiguration.getExposedPorts()).thenReturn(Collections.emptyList());
    Mockito.when(mockJibPluginConfiguration.getAppRoot()).thenReturn("/app");
    Mockito.when(mockJibPluginConfiguration.getProject()).thenReturn(mavenProject);

    Mockito.when(mockProjectProperties.getJavaLayerConfigurations())
        .thenReturn(JavaLayerConfigurations.builder().build());
    Mockito.when(mockProjectProperties.getMainClass(mockJibPluginConfiguration))
        .thenReturn("java.lang.Object");
    Mockito.when(mockProjectProperties.getEventHandlers()).thenReturn(new EventHandlers());
  }

  /** Test with our default mocks, which try to mimic the bare Maven configuration. */
  @Test
  public void testPluginConfigurationProcessor_defaults()
      throws MojoExecutionException, InvalidImageReferenceException, IOException,
          CacheDirectoryCreationException {
    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mockLog, mockJibPluginConfiguration, mockProjectProperties);
    JibContainerBuilder jibContainerBuilder = processor.getJibContainerBuilder();
    BuildConfiguration buildConfiguration = getBuildConfiguration(jibContainerBuilder);
    Assert.assertNotNull(buildConfiguration.getContainerConfiguration());
    Assert.assertEquals(
        Arrays.asList("java", "-cp", "/app/resources:/app/classes:/app/libs/*", "java.lang.Object"),
        buildConfiguration.getContainerConfiguration().getEntrypoint());

    Assert.assertEquals(
        ImageReference.parse("gcr.io/distroless/java").toString(),
        processor.getBaseImageReference().toString());
    Mockito.verifyZeroInteractions(mockLog);
  }

  @Test
  public void testPluginConfigurationProcessor_warPackaging()
      throws MojoExecutionException, InvalidImageReferenceException {
    Mockito.when(mavenProject.getPackaging()).thenReturn("war");

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mockLog, mockJibPluginConfiguration, mockProjectProperties);

    Assert.assertEquals(
        ImageReference.parse("gcr.io/distroless/java/jetty").toString(),
        processor.getBaseImageReference().toString());
    Mockito.verifyZeroInteractions(mockLog);
  }

  @Test
  public void testEntrypoint()
      throws MojoExecutionException, IOException, InvalidImageReferenceException,
          CacheDirectoryCreationException {
    Mockito.when(mockJibPluginConfiguration.getEntrypoint())
        .thenReturn(Arrays.asList("custom", "entrypoint"));

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mockLog, mockJibPluginConfiguration, mockProjectProperties);

    JibContainerBuilder jibContainerBuilder = processor.getJibContainerBuilder();
    BuildConfiguration buildConfiguration = getBuildConfiguration(jibContainerBuilder);
    Assert.assertNotNull(buildConfiguration.getContainerConfiguration());
    Assert.assertEquals(
        Arrays.asList("custom", "entrypoint"),
        buildConfiguration.getContainerConfiguration().getEntrypoint());
    Assert.assertEquals(
        Arrays.asList("custom", "entrypoint"),
        buildConfiguration.getContainerConfiguration().getEntrypoint());
    Mockito.verifyZeroInteractions(mockLog);
  }

  @Test
  public void testEntrypoint_defaultWarPackaging()
      throws MojoExecutionException, IOException, InvalidImageReferenceException,
          CacheDirectoryCreationException {
    Mockito.when(mockJibPluginConfiguration.getEntrypoint()).thenReturn(ImmutableList.of());
    Mockito.when(mockJibPluginConfiguration.getProject()).thenReturn(mavenProject);
    Mockito.when(mavenProject.getPackaging()).thenReturn("war");

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mockLog, mockJibPluginConfiguration, mockProjectProperties);

    JibContainerBuilder jibContainerBuilder = processor.getJibContainerBuilder();
    BuildConfiguration buildConfiguration = getBuildConfiguration(jibContainerBuilder);
    Assert.assertNull(buildConfiguration.getContainerConfiguration().getEntrypoint());
    Assert.assertNotNull(buildConfiguration.getContainerConfiguration());
    Assert.assertNull(buildConfiguration.getContainerConfiguration().getEntrypoint());
    Mockito.verifyZeroInteractions(mockLog);
  }

  @Test
  public void testEntrypoint_defaulNonWarPackaging()
      throws MojoExecutionException, IOException, InvalidImageReferenceException,
          CacheDirectoryCreationException {
    Mockito.when(mockJibPluginConfiguration.getEntrypoint()).thenReturn(ImmutableList.of());
    Mockito.when(mockJibPluginConfiguration.getProject()).thenReturn(mavenProject);
    Mockito.when(mavenProject.getPackaging()).thenReturn(null);

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mockLog, mockJibPluginConfiguration, mockProjectProperties);
    JibContainerBuilder jibContainerBuilder = processor.getJibContainerBuilder();
    BuildConfiguration buildConfiguration = getBuildConfiguration(jibContainerBuilder);
    Assert.assertNotNull(buildConfiguration.getContainerConfiguration());
    Assert.assertEquals(
        Arrays.asList("java", "-cp", "/app/resources:/app/classes:/app/libs/*", "java.lang.Object"),
        buildConfiguration.getContainerConfiguration().getEntrypoint());
    Mockito.verifyZeroInteractions(mockLog);
  }

  @Test
  public void testEntrypoint_warningOnJvmFlags()
      throws MojoExecutionException, IOException, InvalidImageReferenceException,
          CacheDirectoryCreationException {
    Mockito.when(mockJibPluginConfiguration.getEntrypoint())
        .thenReturn(Arrays.asList("custom", "entrypoint"));
    Mockito.when(mockJibPluginConfiguration.getJvmFlags())
        .thenReturn(Collections.singletonList("jvmFlag"));

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mockLog, mockJibPluginConfiguration, mockProjectProperties);
    JibContainerBuilder jibContainerBuilder = processor.getJibContainerBuilder();
    BuildConfiguration buildConfiguration = getBuildConfiguration(jibContainerBuilder);
    Assert.assertNotNull(buildConfiguration.getContainerConfiguration());
    Assert.assertEquals(
        Arrays.asList("custom", "entrypoint"),
        buildConfiguration.getContainerConfiguration().getEntrypoint());
    Mockito.verify(mockLog)
        .warn("<mainClass> and <jvmFlags> are ignored when <entrypoint> is specified");
  }

  @Test
  public void testEntrypoint_warningOnMainclass()
      throws MojoExecutionException, IOException, InvalidImageReferenceException,
          CacheDirectoryCreationException {
    Mockito.when(mockJibPluginConfiguration.getEntrypoint())
        .thenReturn(Arrays.asList("custom", "entrypoint"));
    Mockito.when(mockJibPluginConfiguration.getMainClass()).thenReturn("java.util.Object");

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mockLog, mockJibPluginConfiguration, mockProjectProperties);
    JibContainerBuilder jibContainerBuilder = processor.getJibContainerBuilder();
    BuildConfiguration buildConfiguration = getBuildConfiguration(jibContainerBuilder);
    Assert.assertNotNull(buildConfiguration.getContainerConfiguration());
    Assert.assertEquals(
        Arrays.asList("custom", "entrypoint"),
        buildConfiguration.getContainerConfiguration().getEntrypoint());
    Mockito.verify(mockLog)
        .warn("<mainClass> and <jvmFlags> are ignored when <entrypoint> is specified");
  }

  @Test
  public void testEntrypointClasspath_nonDefaultAppRoot()
      throws MojoExecutionException, IOException, InvalidImageReferenceException,
          CacheDirectoryCreationException {
    Mockito.when(mockJibPluginConfiguration.getAppRoot()).thenReturn("/my/app");

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mockLog, mockJibPluginConfiguration, mockProjectProperties);
    JibContainerBuilder jibContainerBuilder = processor.getJibContainerBuilder();

    ContainerConfiguration containerConfiguration =
        getBuildConfiguration(jibContainerBuilder).getContainerConfiguration();
    Assert.assertNotNull(containerConfiguration);
    Assert.assertNotNull(containerConfiguration.getEntrypoint());
    Assert.assertEquals(
        "/my/app/resources:/my/app/classes:/my/app/libs/*",
        containerConfiguration.getEntrypoint().get(2));
  }

  @Test
  public void testUser()
      throws MojoExecutionException, IOException, InvalidImageReferenceException,
          CacheDirectoryCreationException {
    Mockito.when(mockJibPluginConfiguration.getUser()).thenReturn("customUser");

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mockLog, mockJibPluginConfiguration, mockProjectProperties);
    BuildConfiguration buildConfiguration =
        getBuildConfiguration(processor.getJibContainerBuilder());

    Assert.assertNotNull(buildConfiguration.getContainerConfiguration());
    Assert.assertEquals("customUser", buildConfiguration.getContainerConfiguration().getUser());
  }

  @Test
  public void testUser_null()
      throws MojoExecutionException, IOException, InvalidImageReferenceException,
          CacheDirectoryCreationException {
    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mockLog, mockJibPluginConfiguration, mockProjectProperties);
    BuildConfiguration buildConfiguration =
        getBuildConfiguration(processor.getJibContainerBuilder());

    Assert.assertNotNull(buildConfiguration.getContainerConfiguration());
    Assert.assertNull(buildConfiguration.getContainerConfiguration().getUser());
  }

  @Test
  public void testGetAppRootChecked() throws MojoExecutionException {
    Mockito.when(mockJibPluginConfiguration.getAppRoot()).thenReturn("/some/root");

    Assert.assertEquals(
        AbsoluteUnixPath.get("/some/root"),
        PluginConfigurationProcessor.getAppRootChecked(mockJibPluginConfiguration));
  }

  @Test
  public void testGetAppRootChecked_errorOnNonAbsolutePath() {
    Mockito.when(mockJibPluginConfiguration.getAppRoot()).thenReturn("relative/path");

    try {
      PluginConfigurationProcessor.getAppRootChecked(mockJibPluginConfiguration);
      Assert.fail();
    } catch (MojoExecutionException ex) {
      Assert.assertEquals(
          "<container><appRoot> is not an absolute Unix-style path: relative/path",
          ex.getMessage());
    }
  }

  @Test
  public void testGetAppRootChecked_errorOnWindowsPath() {
    Mockito.when(mockJibPluginConfiguration.getAppRoot()).thenReturn("\\windows\\path");

    try {
      PluginConfigurationProcessor.getAppRootChecked(mockJibPluginConfiguration);
      Assert.fail();
    } catch (MojoExecutionException ex) {
      Assert.assertEquals(
          "<container><appRoot> is not an absolute Unix-style path: \\windows\\path",
          ex.getMessage());
    }
  }

  @Test
  public void testGetAppRootChecked_errorOnWindowsPathWithDriveLetter() {
    Mockito.when(mockJibPluginConfiguration.getAppRoot()).thenReturn("C:\\windows\\path");

    try {
      PluginConfigurationProcessor.getAppRootChecked(mockJibPluginConfiguration);
      Assert.fail();
    } catch (MojoExecutionException ex) {
      Assert.assertEquals(
          "<container><appRoot> is not an absolute Unix-style path: C:\\windows\\path",
          ex.getMessage());
    }
  }

  @Test
  public void testGetAppRootChecked_defaultNonWarPackaging() throws MojoExecutionException {
    Mockito.when(mockJibPluginConfiguration.getAppRoot()).thenReturn("");
    Mockito.when(mockJibPluginConfiguration.getProject()).thenReturn(mavenProject);
    Mockito.when(mavenProject.getPackaging()).thenReturn(null);

    Assert.assertEquals(
        AbsoluteUnixPath.get("/app"),
        PluginConfigurationProcessor.getAppRootChecked(mockJibPluginConfiguration));
  }

  @Test
  public void testGetAppRootChecked_defaultJarPackaging() throws MojoExecutionException {
    Mockito.when(mockJibPluginConfiguration.getAppRoot()).thenReturn("");
    Mockito.when(mockJibPluginConfiguration.getProject()).thenReturn(mavenProject);
    Mockito.when(mavenProject.getPackaging()).thenReturn("jar");

    Assert.assertEquals(
        AbsoluteUnixPath.get("/app"),
        PluginConfigurationProcessor.getAppRootChecked(mockJibPluginConfiguration));
  }

  @Test
  public void testGetAppRootChecked_defaultWarPackaging() throws MojoExecutionException {
    Mockito.when(mockJibPluginConfiguration.getAppRoot()).thenReturn("");
    Mockito.when(mockJibPluginConfiguration.getProject()).thenReturn(mavenProject);
    Mockito.when(mavenProject.getPackaging()).thenReturn("war");

    Assert.assertEquals(
        AbsoluteUnixPath.get("/jetty/webapps/ROOT"),
        PluginConfigurationProcessor.getAppRootChecked(mockJibPluginConfiguration));
  }

  @Test
  public void testGetBaseImage_defaultWarPackaging() {
    Mockito.when(mockJibPluginConfiguration.getProject()).thenReturn(mavenProject);
    Mockito.when(mavenProject.getPackaging()).thenReturn("war");

    Assert.assertEquals(
        "gcr.io/distroless/java/jetty",
        PluginConfigurationProcessor.getBaseImage(mockJibPluginConfiguration));
  }

  @Test
  public void testGetBaseImage_defaultNonWarPackaging() {
    Mockito.when(mockJibPluginConfiguration.getProject()).thenReturn(mavenProject);
    Mockito.when(mavenProject.getPackaging()).thenReturn(null);

    Assert.assertEquals(
        "gcr.io/distroless/java",
        PluginConfigurationProcessor.getBaseImage(mockJibPluginConfiguration));
  }

  @Test
  public void testGetBaseImage_nonDefault() {
    Mockito.when(mockJibPluginConfiguration.getBaseImage()).thenReturn("tomcat");

    Assert.assertEquals(
        "tomcat", PluginConfigurationProcessor.getBaseImage(mockJibPluginConfiguration));
  }

  @Test
  public void testConvertPermissionsList() {
    Assert.assertEquals(
        ImmutableMap.of(
            AbsoluteUnixPath.get("/test/folder/file1"),
            FilePermissions.fromOctalString("123"),
            AbsoluteUnixPath.get("/test/file2"),
            FilePermissions.fromOctalString("456")),
        PluginConfigurationProcessor.convertPermissionsList(
            ImmutableList.of(
                new PermissionConfiguration("/test/folder/file1", "123"),
                new PermissionConfiguration("/test/file2", "456"))));

    try {
      PluginConfigurationProcessor.convertPermissionsList(
          ImmutableList.of(new PermissionConfiguration("a path", "not valid permission")));
      Assert.fail();
    } catch (IllegalArgumentException ignored) {
      // pass
    }
  }

  // TODO should test other behaviours
}
