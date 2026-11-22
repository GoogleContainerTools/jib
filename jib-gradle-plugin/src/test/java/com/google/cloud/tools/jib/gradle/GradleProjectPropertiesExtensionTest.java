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
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.configuration.ConsoleOutput;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSet;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
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

  // Interface to conveniently provide the main extension body using lambda.
  @FunctionalInterface
  private interface MainExtensionBody<T> {

    ContainerBuildPlan extendContainerBuildPlan(
        ContainerBuildPlan buildPlan,
        Map<String, String> properties,
        Optional<T> extraConfig,
        GradleData gradleData,
        ExtensionLogger logger)
        throws JibPluginExtensionException;
  }

  private static class BaseExtension<T> implements JibGradlePluginExtension<T> {

    private final MainExtensionBody<T> extensionBody;
    private final Class<T> extraConfigType;

    private BaseExtension(MainExtensionBody<T> extensionBody, Class<T> extraConfigType) {
      this.extensionBody = extensionBody;
      this.extraConfigType = extraConfigType;
    }

    @Override
    public Optional<Class<T>> getExtraConfigType() {
      return Optional.ofNullable(extraConfigType);
    }

    @Override
    public ContainerBuildPlan extendContainerBuildPlan(
        ContainerBuildPlan buildPlan,
        Map<String, String> properties,
        Optional<T> extraConfig,
        GradleData gradleData,
        ExtensionLogger logger)
        throws JibPluginExtensionException {
      return extensionBody.extendContainerBuildPlan(
          buildPlan, properties, extraConfig, gradleData, logger);
    }
  }

  private static class FooExtension extends BaseExtension<ExtensionDefinedFooConfig> {

    private FooExtension(MainExtensionBody<ExtensionDefinedFooConfig> extensionBody) {
      super(extensionBody, ExtensionDefinedFooConfig.class);
    }
  }

  private static class BarExtension extends BaseExtension<ExtensionDefinedBarConfig> {

    private BarExtension(MainExtensionBody<ExtensionDefinedBarConfig> extensionBody) {
      super(extensionBody, ExtensionDefinedBarConfig.class);
    }
  }

  private static class BaseExtensionConfig<T> implements ExtensionConfiguration {

    private final String extensionClass;
    private final Map<String, String> properties;
    private final Action<T> extraConfig;

    private BaseExtensionConfig(
        String extensionClass, Map<String, String> properties, Action<T> extraConfig) {
      this.extensionClass = extensionClass;
      this.properties = properties;
      this.extraConfig = extraConfig;
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
      return Optional.ofNullable(extraConfig);
    }
  }

  private static class FooExtensionConfig extends BaseExtensionConfig<ExtensionDefinedFooConfig> {

    private FooExtensionConfig() {
      super(FooExtension.class.getName(), Collections.emptyMap(), null);
    }

    private FooExtensionConfig(Map<String, String> properties) {
      super(FooExtension.class.getName(), properties, null);
    }

    private FooExtensionConfig(ExtensionDefinedFooConfig extraConfig) {
      super(
          FooExtension.class.getName(),
          Collections.emptyMap(),
          new Action<ExtensionDefinedFooConfig>() {
            @Override
            public void execute(ExtensionDefinedFooConfig instance) {
              instance.fooParam = extraConfig.fooParam;
            }
          });
    }
  }

  private static class BarExtensionConfig extends BaseExtensionConfig<ExtensionDefinedBarConfig> {

    private BarExtensionConfig() {
      super(BarExtension.class.getName(), Collections.emptyMap(), null);
    }

    private BarExtensionConfig(ExtensionDefinedBarConfig extraConfig) {
      super(
          BarExtension.class.getName(),
          Collections.emptyMap(),
          new Action<ExtensionDefinedBarConfig>() {
            @Override
            public void execute(ExtensionDefinedBarConfig instance) {
              instance.barParam = extraConfig.barParam;
            }
          });
    }
  }

  // Not to be confused with Jib's plugin extension config. This class is for an extension-defined
  // config specific to a third-party extension.
  private static class ExtensionDefinedFooConfig {

    private String fooParam;

    private ExtensionDefinedFooConfig(String fooParam) {
      this.fooParam = fooParam;
    }
  }

  private static class ExtensionDefinedBarConfig {

    private String barParam;

    private ExtensionDefinedBarConfig(String barParam) {
      this.barParam = barParam;
    }
  }

  @Mock private TempDirectoryProvider mockTempDirectoryProvider;
  @Mock private Logger mockLogger;
  @Mock private ObjectFactory mockObjectFactory;

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
    Mockito.when(mockProject.getObjects()).thenReturn(mockObjectFactory);
    Mockito.when(
            mockObjectFactory.newInstance(
                Mockito.eq(ExtensionDefinedFooConfig.class), Mockito.any()))
        .thenReturn(new ExtensionDefinedFooConfig("uninitialized"));
    Mockito.when(
            mockObjectFactory.newInstance(
                Mockito.eq(ExtensionDefinedBarConfig.class), Mockito.any()))
        .thenReturn(new ExtensionDefinedBarConfig("uninitialized"));

    gradleProjectProperties =
        new GradleProjectProperties(
            mockProject,
            mockLogger,
            mockTempDirectoryProvider,
            () -> loadedExtensions,
            JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME,
            SourceSet.MAIN_SOURCE_SET_NAME);
  }

  @Test
  public void testRunPluginExtensions_noExtensionsConfigured() throws JibPluginExtensionException {
    FooExtension extension =
        new FooExtension((buildPlan, properties, extraConfig, gradleData, logger) -> buildPlan);
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
      MatcherAssert.assertThat(
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

  @Test
  public void testRunPluginExtensions_extensionDefinedConfigurations_emptyConfig()
      throws JibPluginExtensionException {
    FooExtension fooExtension =
        new FooExtension(
            (buildPlan, properties, extraConfig, mavenData, logger) -> {
              Assert.assertEquals(Optional.empty(), extraConfig);
              return buildPlan;
            });
    BarExtension barExtension =
        new BarExtension(
            (buildPlan, properties, extraConfig, mavenData, logger) -> {
              Assert.assertEquals(Optional.empty(), extraConfig);
              return buildPlan;
            });
    loadedExtensions = Arrays.asList(fooExtension, barExtension);

    gradleProjectProperties.runPluginExtensions(
        Arrays.asList(new FooExtensionConfig(), new BarExtensionConfig()), containerBuilder);
  }

  @Test
  public void testRunPluginExtensions_extensionDefinedConfigurations()
      throws JibPluginExtensionException {
    FooExtension fooExtension =
        new FooExtension(
            (buildPlan, properties, extraConfig, mavenData, logger) -> {
              Assert.assertEquals("fooParamValue", extraConfig.get().fooParam);
              return buildPlan;
            });
    BarExtension barExtension =
        new BarExtension(
            (buildPlan, properties, extraConfig, mavenData, logger) -> {
              Assert.assertEquals("barParamValue", extraConfig.get().barParam);
              return buildPlan;
            });
    loadedExtensions = Arrays.asList(fooExtension, barExtension);

    gradleProjectProperties.runPluginExtensions(
        Arrays.asList(
            new FooExtensionConfig(new ExtensionDefinedFooConfig("fooParamValue")),
            new BarExtensionConfig(new ExtensionDefinedBarConfig("barParamValue"))),
        containerBuilder);
  }

  @Test
  public void testRunPluginExtensions_ignoreUnexpectedExtraConfig()
      throws JibPluginExtensionException {
    BaseExtension<Void> extension =
        new BaseExtension<>(
            (buildPlan, properties, extraConfig, mavenData, logger) -> buildPlan, null);
    loadedExtensions = Arrays.asList(extension);

    ExtensionConfiguration extensionConfig =
        new BaseExtensionConfig<>(
            BaseExtension.class.getName(), Collections.emptyMap(), (ignored) -> {});
    try {
      gradleProjectProperties.runPluginExtensions(Arrays.asList(extensionConfig), containerBuilder);
      Assert.fail();
    } catch (IllegalArgumentException ex) {
      Assert.assertEquals(
          "extension BaseExtension does not expect extension-specific configuration; remove the "
              + "inapplicable 'pluginExtension.configuration' from Gradle build script",
          ex.getMessage());
    }
  }

  @Test
  public void testRunPluginExtensions_runtimeExceptionFromExtension() {
    FooExtension extension =
        new FooExtension(
            (buildPlan, properties, extraConfig, mavenData, logger) -> {
              throw new IndexOutOfBoundsException("buggy extension");
            });
    loadedExtensions = Arrays.asList(extension);

    try {
      gradleProjectProperties.runPluginExtensions(
          Arrays.asList(new FooExtensionConfig()), containerBuilder);
      Assert.fail();
    } catch (JibPluginExtensionException ex) {
      Assert.assertEquals(FooExtension.class, ex.getExtensionClass());
      Assert.assertEquals("extension crashed: buggy extension", ex.getMessage());
    }
  }
}
