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

import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.Jib;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.filesystem.TempDirectoryProvider;
import com.google.cloud.tools.jib.maven.extension.JibMavenPluginExtension;
import com.google.cloud.tools.jib.maven.extension.MavenData;
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
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Plugin extension test for {@link MavenProjectProperties}. */
@RunWith(MockitoJUnitRunner.class)
public class MavenProjectPropertiesExtensionTest {

  // Interface to conveniently provide the main extension body using lambda.
  @FunctionalInterface
  private interface MainExtensionBody<T> {

    ContainerBuildPlan extendContainerBuildPlan(
        ContainerBuildPlan buildPlan,
        Map<String, String> properties,
        Optional<T> extraConfig,
        MavenData mavenData,
        ExtensionLogger logger)
        throws JibPluginExtensionException;
  }

  private static class BaseExtension<T> implements JibMavenPluginExtension<T> {

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
        MavenData mavenData,
        ExtensionLogger logger)
        throws JibPluginExtensionException {
      return extensionBody.extendContainerBuildPlan(
          buildPlan, properties, extraConfig, mavenData, logger);
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
    private final T extraConfig;

    private BaseExtensionConfig(
        String extensionClass, Map<String, String> properties, T extraConfig) {
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
      super(FooExtension.class.getName(), Collections.emptyMap(), extraConfig);
    }
  }

  private static class BarExtensionConfig extends BaseExtensionConfig<ExtensionDefinedBarConfig> {

    private BarExtensionConfig() {
      super(BarExtension.class.getName(), Collections.emptyMap(), null);
    }

    private BarExtensionConfig(ExtensionDefinedBarConfig extraConfig) {
      super(BarExtension.class.getName(), Collections.emptyMap(), extraConfig);
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

  @Mock private PluginDescriptor mockJibPluginDescriptor;
  @Mock private MavenProject mockMavenProject;
  @Mock private MavenSession mockMavenSession;
  @Mock private MavenExecutionRequest mockMavenRequest;
  @Mock private Log mockLog;
  @Mock private TempDirectoryProvider mockTempDirectoryProvider;

  private List<JibMavenPluginExtension<?>> loadedExtensions = Collections.emptyList();
  private final JibContainerBuilder containerBuilder = Jib.fromScratch();

  private MavenProjectProperties mavenProjectProperties;

  @Before
  public void setUp() {
    Mockito.when(mockLog.isDebugEnabled()).thenReturn(true);
    Mockito.when(mockLog.isWarnEnabled()).thenReturn(true);
    Mockito.when(mockLog.isErrorEnabled()).thenReturn(true);

    Mockito.when(mockMavenSession.getRequest()).thenReturn(mockMavenRequest);
    mavenProjectProperties =
        new MavenProjectProperties(
            mockJibPluginDescriptor,
            mockMavenProject,
            mockMavenSession,
            mockLog,
            mockTempDirectoryProvider,
            Collections.emptyList(),
            () -> loadedExtensions);
  }

  @Test
  public void testRunPluginExtensions_noExtensionsConfigured() throws JibPluginExtensionException {
    FooExtension extension =
        new FooExtension((buildPlan, properties, extraConfig, mavenData, logger) -> buildPlan);
    loadedExtensions = Arrays.asList(extension);

    JibContainerBuilder extendedBuilder =
        mavenProjectProperties.runPluginExtensions(Collections.emptyList(), containerBuilder);
    Assert.assertSame(extendedBuilder, containerBuilder);

    mavenProjectProperties.waitForLoggingThread();
    Mockito.verify(mockLog).debug("No Jib plugin extensions configured to load");
  }

  @Test
  public void testRunPluginExtensions_configuredExtensionNotFound() {
    try {
      mavenProjectProperties.runPluginExtensions(
          Arrays.asList(new FooExtensionConfig()), containerBuilder);
      Assert.fail();
    } catch (JibPluginExtensionException ex) {
      Assert.assertEquals(
          "extension configured but not discovered on Jib runtime classpath: com.google.cloud."
              + "tools.jib.maven.MavenProjectPropertiesExtensionTest$FooExtension",
          ex.getMessage());
    }
  }

  @Test
  public void testRunPluginExtensions() throws JibPluginExtensionException {
    FooExtension extension =
        new FooExtension(
            (buildPlan, properties, extraConfig, mavenData, logger) -> {
              logger.log(LogLevel.ERROR, "awesome error from my extension");
              return buildPlan.toBuilder().setUser("user from extension").build();
            });
    loadedExtensions = Arrays.asList(extension);

    JibContainerBuilder extendedBuilder =
        mavenProjectProperties.runPluginExtensions(
            Arrays.asList(new FooExtensionConfig()), containerBuilder);
    Assert.assertEquals("user from extension", extendedBuilder.toContainerBuildPlan().getUser());

    mavenProjectProperties.waitForLoggingThread();
    Mockito.verify(mockLog).error("awesome error from my extension");
    Mockito.verify(mockLog)
        .info(
            Mockito.startsWith(
                "Running extension: com.google.cloud.tools.jib.maven.MavenProjectProperties"));
  }

  @Test
  public void testRunPluginExtensions_exceptionFromExtension() {
    FileNotFoundException fakeException = new FileNotFoundException();
    FooExtension extension =
        new FooExtension(
            (buildPlan, properties, extraConfig, mavenData, logger) -> {
              throw new JibPluginExtensionException(
                  FooExtension.class, "exception from extension", fakeException);
            });
    loadedExtensions = Arrays.asList(extension);

    try {
      mavenProjectProperties.runPluginExtensions(
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
            (buildPlan, properties, extraConfig, mavenData, logger) ->
                buildPlan.toBuilder().setBaseImage(" in*val+id").build());
    loadedExtensions = Arrays.asList(extension);

    try {
      mavenProjectProperties.runPluginExtensions(
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
            (buildPlan, properties, extraConfig, mavenData, logger) ->
                buildPlan.toBuilder().setBaseImage("foo").build());
    BarExtension barExtension =
        new BarExtension(
            (buildPlan, properties, extraConfig, mavenData, logger) ->
                buildPlan.toBuilder().setBaseImage("bar").build());
    loadedExtensions = Arrays.asList(fooExtension, barExtension);

    JibContainerBuilder extendedBuilder1 =
        mavenProjectProperties.runPluginExtensions(
            Arrays.asList(new FooExtensionConfig(), new BarExtensionConfig()), containerBuilder);
    Assert.assertEquals("bar", extendedBuilder1.toContainerBuildPlan().getBaseImage());

    JibContainerBuilder extendedBuilder2 =
        mavenProjectProperties.runPluginExtensions(
            Arrays.asList(new BarExtensionConfig(), new FooExtensionConfig()), containerBuilder);
    Assert.assertEquals("foo", extendedBuilder2.toContainerBuildPlan().getBaseImage());
  }

  @Test
  public void testRunPluginExtensions_customProperties() throws JibPluginExtensionException {
    FooExtension extension =
        new FooExtension(
            (buildPlan, properties, extraConfig, mavenData, logger) ->
                buildPlan.toBuilder().setUser(properties.get("user")).build());
    loadedExtensions = Arrays.asList(extension);

    JibContainerBuilder extendedBuilder =
        mavenProjectProperties.runPluginExtensions(
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

    mavenProjectProperties.runPluginExtensions(
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

    mavenProjectProperties.runPluginExtensions(
        Arrays.asList(
            new FooExtensionConfig(new ExtensionDefinedFooConfig("fooParamValue")),
            new BarExtensionConfig(new ExtensionDefinedBarConfig("barParamValue"))),
        containerBuilder);
  }

  @Test
  public void testRunPluginExtensions_wrongExtraConfigType() {
    FooExtension extension =
        new FooExtension((buildPlan, properties, extraConfig, mavenData, logger) -> buildPlan);
    loadedExtensions = Arrays.asList(extension);

    ExtensionConfiguration extensionConfig =
        new BaseExtensionConfig<>(
            FooExtension.class.getName(), Collections.emptyMap(), "string <configuration>");
    try {
      mavenProjectProperties.runPluginExtensions(Arrays.asList(extensionConfig), containerBuilder);
      Assert.fail();
    } catch (JibPluginExtensionException ex) {
      Assert.assertEquals(FooExtension.class, ex.getExtensionClass());
      Assert.assertEquals(
          "extension-specific <configuration> for FooExtension is not of type com.google.cloud"
              + ".tools.jib.maven.MavenProjectPropertiesExtensionTest$ExtensionDefinedFooConfig "
              + "but java.lang.String; specify the correct type with <pluginExtension>"
              + "<configuration implementation=\"com.google.cloud.tools.jib.maven."
              + "MavenProjectPropertiesExtensionTest$ExtensionDefinedFooConfig\">",
          ex.getMessage());
    }
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
            BaseExtension.class.getName(), Collections.emptyMap(), "unwanted <configuration>");
    try {
      mavenProjectProperties.runPluginExtensions(Arrays.asList(extensionConfig), containerBuilder);
      Assert.fail();
    } catch (IllegalArgumentException ex) {
      Assert.assertEquals(
          "extension BaseExtension does not expect extension-specific configruation; remove the "
              + "inapplicable <pluginExtension><configuration> from pom.xml",
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
      mavenProjectProperties.runPluginExtensions(
          Arrays.asList(new FooExtensionConfig()), containerBuilder);
      Assert.fail();
    } catch (JibPluginExtensionException ex) {
      Assert.assertEquals(FooExtension.class, ex.getExtensionClass());
      Assert.assertEquals("extension crashed: buggy extension", ex.getMessage());
    }
  }

  @Test
  public void testRunPluginExtensions_injected() throws JibPluginExtensionException {
    FooExtension injectedExtension =
        new FooExtension(
            (buildPlan, properties, extraConfig, mavenData, logger) -> {
              logger.log(LogLevel.ERROR, "awesome error from my extension");
              return buildPlan.toBuilder().setUser("user from extension").build();
            });

    mavenProjectProperties =
        new MavenProjectProperties(
            mockJibPluginDescriptor,
            mockMavenProject,
            mockMavenSession,
            mockLog,
            mockTempDirectoryProvider,
            Arrays.asList(injectedExtension),
            () -> Collections.emptyList());

    JibContainerBuilder extendedBuilder =
        mavenProjectProperties.runPluginExtensions(
            Arrays.asList(new FooExtensionConfig()), containerBuilder);
    Assert.assertEquals("user from extension", extendedBuilder.toContainerBuildPlan().getUser());

    mavenProjectProperties.waitForLoggingThread();
    Mockito.verify(mockLog).error("awesome error from my extension");
    Mockito.verify(mockLog)
        .info(
            Mockito.startsWith(
                "Running extension: com.google.cloud.tools.jib.maven.MavenProjectProperties"));
  }

  @Test
  public void testRunPluginExtensions_preferInjectionOverServiceLoader()
      throws JibPluginExtensionException {
    FooExtension injectedExtension =
        new FooExtension(
            (buildPlan, properties, extraConfig, mavenData, logger) -> {
              logger.log(LogLevel.ERROR, "awesome error from my extension");
              return buildPlan.toBuilder().setUser("user from injected extension").build();
            });

    FooExtension loadedExtension =
        new FooExtension(
            (buildPlan, properties, extraConfig, mavenData, logger) -> {
              return buildPlan.toBuilder().setBaseImage("loadedExtBaseImage").build();
            });

    mavenProjectProperties =
        new MavenProjectProperties(
            mockJibPluginDescriptor,
            mockMavenProject,
            mockMavenSession,
            mockLog,
            mockTempDirectoryProvider,
            Arrays.asList(injectedExtension),
            () -> Arrays.asList(loadedExtension));

    JibContainerBuilder extendedBuilder =
        mavenProjectProperties.runPluginExtensions(
            Arrays.asList(new FooExtensionConfig()), containerBuilder);
    Assert.assertEquals(
        "user from injected extension", extendedBuilder.toContainerBuildPlan().getUser());
    Assert.assertEquals("scratch", extendedBuilder.toContainerBuildPlan().getBaseImage());

    mavenProjectProperties.waitForLoggingThread();
    Mockito.verify(mockLog).error("awesome error from my extension");
    Mockito.verify(mockLog)
        .info(
            Mockito.startsWith(
                "Running extension: com.google.cloud.tools.jib.maven.MavenProjectProperties"));
  }
}
