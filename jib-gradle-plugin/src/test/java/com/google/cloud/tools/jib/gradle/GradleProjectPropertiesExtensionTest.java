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

import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.Jib;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.filesystem.TempDirectoryProvider;
import com.google.cloud.tools.jib.gradle.extension.GradleData;
import com.google.cloud.tools.jib.gradle.extension.JibGradlePluginExtension;
import com.google.cloud.tools.jib.plugins.common.RawConfiguration.ExtensionConfiguration;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger.LogLevel;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import com.google.common.collect.ImmutableMap;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.configuration.ConsoleOutput;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Plugin extension test for {@link GradleProjectProperties}. */
@RunWith(MockitoJUnitRunner.class)
public class GradleProjectPropertiesExtensionTest {

  private static class FooExtension implements JibGradlePluginExtension<Void> {

    private final JibGradlePluginExtension<Void> extension;

    private FooExtension(JibGradlePluginExtension<Void> extension) {
      this.extension = extension;
    }

    @Override
    public ContainerBuildPlan extendContainerBuildPlan(
        ContainerBuildPlan buildPlan,
        Map<String, String> properties,
        Optional<Void> extraConfig,
        GradleData gradleData,
        ExtensionLogger logger)
        throws JibPluginExtensionException {
      return extension.extendContainerBuildPlan(
          buildPlan, properties, extraConfig, gradleData, logger);
    }
  }

  private static class BarExtension extends FooExtension {

    private BarExtension(JibGradlePluginExtension<Void> extension) {
      super(extension);
    }
  }

  private static class FooExtensionConfig implements ExtensionConfiguration {

    private String extensionClass = FooExtension.class.getName();
    private Map<String, String> properties = Collections.emptyMap();

    private FooExtensionConfig() {}

    private FooExtensionConfig(String extensionClass) {
      this.extensionClass = extensionClass;
    }

    private FooExtensionConfig(Map<String, String> properties) {
      this.properties = properties;
    }

    @Override
    public Map<String, String> getProperties() {
      return properties;
    }

    @Override
    public String getExtensionClass() {
      return extensionClass;
    }

    @Override
    public Optional<Object> getExtraConfiguration() {
      return Optional.empty();
    }
  }

  private static class BarExtensionConfig extends FooExtensionConfig {

    private BarExtensionConfig() {
      super(BarExtension.class.getName());
    }
  }

  @Mock private TempDirectoryProvider mockTempDirectoryProvider;
  @Mock private Logger mockLogger;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private Project mockProject;

  private List<JibGradlePluginExtension<?>> loadedExtensions = Collections.emptyList();
  private final JibContainerBuilder containerBuilder = Jib.fromScratch();

  private GradleProjectProperties gradleProjectProperties;

  @Before
  public void setUp() {
    Mockito.when(mockLogger.isDebugEnabled()).thenReturn(true);
    Mockito.when(mockLogger.isInfoEnabled()).thenReturn(true);
    Mockito.when(mockLogger.isWarnEnabled()).thenReturn(true);
    Mockito.when(mockLogger.isErrorEnabled()).thenReturn(true);

    Mockito.when(mockProject.getGradle().getStartParameter().getConsoleOutput())
        .thenReturn(ConsoleOutput.Plain);

    gradleProjectProperties =
        new GradleProjectProperties(
            mockProject, mockLogger, mockTempDirectoryProvider, () -> loadedExtensions);
  }

  @Test
  public void testRunPluginExtensions_noExtensionsConfigured() throws JibPluginExtensionException {
    JibGradlePluginExtension<?> extension =
        (buildPlan, properties, extraConfig, gradleData, logger) -> buildPlan;
    loadedExtensions = Arrays.asList(extension);

    JibContainerBuilder extendedBuilder =
        gradleProjectProperties.runPluginExtensions(Collections.emptyList(), containerBuilder);
    Assert.assertSame(extendedBuilder, containerBuilder);

    gradleProjectProperties.waitForLoggingThread();
    Mockito.verify(mockLogger).debug("No Jib plugin extensions configured to load");
  }

  @Test
  public void testRunPluginExtensions_configuredExtensionNotFound() {
    try {
      gradleProjectProperties.runPluginExtensions(
          Arrays.asList(new FooExtensionConfig()), containerBuilder);
      Assert.fail();
    } catch (JibPluginExtensionException ex) {
      Assert.assertEquals(
          "extension configured but not discovered on Jib runtime classpath: com.google.cloud."
              + "tools.jib.gradle.GradleProjectPropertiesExtensionTest$FooExtension",
          ex.getMessage());
    }
  }

  @Test
  public void testRunPluginExtensions() throws JibPluginExtensionException {
    FooExtension extension =
        new FooExtension(
            (buildPlan, properties, extraConfig, gradleData, logger) -> {
              logger.log(LogLevel.ERROR, "awesome error from my extension");
              return buildPlan.toBuilder().setUser("user from extension").build();
            });
    loadedExtensions = Arrays.asList(extension);

    JibContainerBuilder extendedBuilder =
        gradleProjectProperties.runPluginExtensions(
            Arrays.asList(new FooExtensionConfig()), containerBuilder);
    Assert.assertEquals("user from extension", extendedBuilder.toContainerBuildPlan().getUser());

    gradleProjectProperties.waitForLoggingThread();
    Mockito.verify(mockLogger).error("awesome error from my extension");
    Mockito.verify(mockLogger)
        .lifecycle(
            Mockito.startsWith(
                "Running extension: com.google.cloud.tools.jib.gradle.GradleProjectProperties"));
  }

  @Test
  public void testRunPluginExtensions_exceptionFromExtension() {
    FileNotFoundException fakeException = new FileNotFoundException();
    FooExtension extension =
        new FooExtension(
            (buildPlan, properties, extraConfig, gradleData, logger) -> {
              throw new JibPluginExtensionException(
                  JibGradlePluginExtension.class, "exception from extension", fakeException);
            });
    loadedExtensions = Arrays.asList(extension);

    try {
      gradleProjectProperties.runPluginExtensions(
          Arrays.asList(new FooExtensionConfig()), containerBuilder);
      Assert.fail();
    } catch (JibPluginExtensionException ex) {
      Assert.assertEquals("exception from extension", ex.getMessage());
      Assert.assertSame(fakeException, ex.getCause());
    }
  }

  @Test
  public void testRunPluginExtensions_invalidBaseImageFromExtension() {
    FooExtension extension =
        new FooExtension(
            (buildPlan, properties, extraConfig, gradleData, logger) ->
                buildPlan.toBuilder().setBaseImage(" in*val+id").build());
    loadedExtensions = Arrays.asList(extension);

    try {
      gradleProjectProperties.runPluginExtensions(
          Arrays.asList(new FooExtensionConfig()), containerBuilder);
      Assert.fail();
    } catch (JibPluginExtensionException ex) {
      Assert.assertEquals("invalid base image reference:  in*val+id", ex.getMessage());
      Assert.assertThat(
          ex.getCause(), CoreMatchers.instanceOf(InvalidImageReferenceException.class));
    }
  }

  @Test
  public void testRunPluginExtensions_extensionOrder() throws JibPluginExtensionException {
    FooExtension fooExtension =
        new FooExtension(
            (buildPlan, properties, extraConfig, gradleData, logger) ->
                buildPlan.toBuilder().setBaseImage("foo").build());
    BarExtension barExtension =
        new BarExtension(
            (buildPlan, properties, extraConfig, gradleData, logger) ->
                buildPlan.toBuilder().setBaseImage("bar").build());
    loadedExtensions = Arrays.asList(fooExtension, barExtension);

    JibContainerBuilder extendedBuilder1 =
        gradleProjectProperties.runPluginExtensions(
            Arrays.asList(new FooExtensionConfig(), new BarExtensionConfig()), containerBuilder);
    Assert.assertEquals("bar", extendedBuilder1.toContainerBuildPlan().getBaseImage());

    JibContainerBuilder extendedBuilder2 =
        gradleProjectProperties.runPluginExtensions(
            Arrays.asList(new BarExtensionConfig(), new FooExtensionConfig()), containerBuilder);
    Assert.assertEquals("foo", extendedBuilder2.toContainerBuildPlan().getBaseImage());
  }

  @Test
  public void testRunPluginExtensions_customProperties() throws JibPluginExtensionException {
    FooExtension extension =
        new FooExtension(
            (buildPlan, properties, extraConfig, gradleData, logger) ->
                buildPlan.toBuilder().setUser(properties.get("user")).build());
    loadedExtensions = Arrays.asList(extension);

    JibContainerBuilder extendedBuilder =
        gradleProjectProperties.runPluginExtensions(
            Arrays.asList(new FooExtensionConfig(ImmutableMap.of("user", "65432"))),
            containerBuilder);
    Assert.assertEquals("65432", extendedBuilder.toContainerBuildPlan().getUser());
  }
}
