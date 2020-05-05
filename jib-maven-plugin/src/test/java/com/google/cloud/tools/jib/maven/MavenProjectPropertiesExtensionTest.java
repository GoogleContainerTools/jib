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
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.hamcrest.CoreMatchers;
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

  private static class FooExtension implements JibMavenPluginExtension {

    private final JibMavenPluginExtension extension;

    private FooExtension(JibMavenPluginExtension extension) {
      this.extension = extension;
    }

    @Override
    public ContainerBuildPlan extendContainerBuildPlan(
        ContainerBuildPlan buildPlan,
        Map<String, String> properties,
        MavenData mavenData,
        ExtensionLogger logger)
        throws JibPluginExtensionException {
      return extension.extendContainerBuildPlan(buildPlan, properties, mavenData, logger);
    }
  }

  private static class BarExtension extends FooExtension {

    private BarExtension(JibMavenPluginExtension extension) {
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
  }

  private static class BarExtensionConfig extends FooExtensionConfig {

    private BarExtensionConfig() {
      super(BarExtension.class.getName());
    }
  }

  @Mock private PluginDescriptor mockJibPluginDescriptor;
  @Mock private MavenProject mockMavenProject;
  @Mock private MavenSession mockMavenSession;
  @Mock private MavenExecutionRequest mockMavenRequest;
  @Mock private Log mockLog;
  @Mock private TempDirectoryProvider mockTempDirectoryProvider;

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
            mockTempDirectoryProvider);
  }

  @Test
  public void testRunPluginExtensions_noExtensionsConfigured() throws JibPluginExtensionException {
    JibMavenPluginExtension extension = (buildPlan, properties, mavenData, logger) -> buildPlan;

    JibContainerBuilder extendedBuilder =
        mavenProjectProperties.runPluginExtensions(
            Arrays.asList(extension), Collections.emptyList(), containerBuilder);
    Assert.assertSame(extendedBuilder, containerBuilder);

    mavenProjectProperties.waitForLoggingThread();
    Mockito.verify(mockLog).debug("No Jib plugin extensions configured to load");
  }

  @Test
  public void testRunPluginExtensions_configuredExtensionNotFound() {
    try {
      mavenProjectProperties.runPluginExtensions(
          Collections.emptyList(), Arrays.asList(new FooExtensionConfig()), containerBuilder);
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
            (buildPlan, properties, mavenData, logger) -> {
              logger.log(LogLevel.ERROR, "awesome error from my extension");
              return buildPlan.toBuilder().setUser("user from extension").build();
            });

    JibContainerBuilder extendedBuilder =
        mavenProjectProperties.runPluginExtensions(
            Arrays.asList(extension), Arrays.asList(new FooExtensionConfig()), containerBuilder);
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
            (buildPlan, properties, mavenData, logger) -> {
              throw new JibPluginExtensionException(
                  FooExtension.class, "exception from extension", fakeException);
            });

    try {
      mavenProjectProperties.runPluginExtensions(
          Arrays.asList(extension), Arrays.asList(new FooExtensionConfig()), containerBuilder);
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
            (buildPlan, properties, mavenData, logger) ->
                buildPlan.toBuilder().setBaseImage(" in*val+id").build());

    try {
      mavenProjectProperties.runPluginExtensions(
          Arrays.asList(extension), Arrays.asList(new FooExtensionConfig()), containerBuilder);
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
            (buildPlan, properties, mavenData, logger) ->
                buildPlan.toBuilder().setBaseImage("foo").build());
    BarExtension barExtension =
        new BarExtension(
            (buildPlan, properties, mavenData, logger) ->
                buildPlan.toBuilder().setBaseImage("bar").build());
    List<JibMavenPluginExtension> extensions = Arrays.asList(fooExtension, barExtension);

    JibContainerBuilder extendedBuilder1 =
        mavenProjectProperties.runPluginExtensions(
            extensions,
            Arrays.asList(new FooExtensionConfig(), new BarExtensionConfig()),
            containerBuilder);
    Assert.assertEquals("bar", extendedBuilder1.toContainerBuildPlan().getBaseImage());

    JibContainerBuilder extendedBuilder2 =
        mavenProjectProperties.runPluginExtensions(
            extensions,
            Arrays.asList(new BarExtensionConfig(), new FooExtensionConfig()),
            containerBuilder);
    Assert.assertEquals("foo", extendedBuilder2.toContainerBuildPlan().getBaseImage());
  }

  @Test
  public void testRunPluginExtensions_customProperties() throws JibPluginExtensionException {
    FooExtension extension =
        new FooExtension(
            (buildPlan, properties, mavenData, logger) ->
                buildPlan.toBuilder().setUser(properties.get("user")).build());

    JibContainerBuilder extendedBuilder =
        mavenProjectProperties.runPluginExtensions(
            Arrays.asList(extension),
            Arrays.asList(new FooExtensionConfig(ImmutableMap.of("user", "65432"))),
            containerBuilder);
    Assert.assertEquals("65432", extendedBuilder.toContainerBuildPlan().getUser());
  }
}
