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
import com.google.cloud.tools.jib.api.RegistryImage;
import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.filesystem.TempDirectoryProvider;
import com.google.cloud.tools.jib.maven.extension.JibMavenPluginExtension;
import com.google.cloud.tools.jib.maven.extension.MavenData;
import com.google.cloud.tools.jib.plugins.common.RawConfiguration.ExtensionConfiguration;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger.LogLevel;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Plugin extension test for {@link MavenProjectProperties}. */
@RunWith(MockitoJUnitRunner.class)
public class MavenProjectPropertiesExtensionTest {

  // Interface defined only to give a type name.
  private static class FakeJibMavenPluginExtension implements JibMavenPluginExtension {

    private final JibMavenPluginExtension extension;

    private FakeJibMavenPluginExtension(JibMavenPluginExtension extension) {
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

  private static class FakeExtensionConfiguration implements ExtensionConfiguration {

    private String extensionClass = FakeJibMavenPluginExtension.class.getName();
    private Map<String, String> properties = Collections.emptyMap();

    private FakeExtensionConfiguration() {}

    private FakeExtensionConfiguration(String extensionClass, Map<String, String> properties) {
      this.extensionClass = extensionClass;
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

  @Rule public final TestRepository testRepository = new TestRepository();
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock private PluginDescriptor mockJibPluginDescriptor;
  @Mock private MavenProject mockMavenProject;
  @Mock private MavenSession mockMavenSession;
  @Mock private MavenExecutionRequest mockMavenRequest;
  @Mock private Log mockLog;
  @Mock private TempDirectoryProvider mockTempDirectoryProvider;

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
  public void testRunPluginExtensions_noExtensionsFound()
      throws JibPluginExtensionException, InvalidImageReferenceException {
    JibContainerBuilder originalBuilder = Jib.from(RegistryImage.named("from/nothing"));
    JibContainerBuilder extendedBuilder =
        mavenProjectProperties.runPluginExtensions(
            Collections.emptyList(), Collections.emptyList(), originalBuilder);
    Assert.assertSame(extendedBuilder, originalBuilder);

    mavenProjectProperties.waitForLoggingThread();
    Mockito.verify(mockLog).debug("No Jib plugin extensions discovered on Jib runtime classpath");
  }

  @Test
  public void testRunPluginExtensions_noExtensionsConfigured()
      throws JibPluginExtensionException, InvalidImageReferenceException {
    JibMavenPluginExtension extension = (buildPlan, properties, mavenData, logger) -> buildPlan;

    JibContainerBuilder originalBuilder = Jib.from(RegistryImage.named("from/nothing"));
    JibContainerBuilder extendedBuilder =
        mavenProjectProperties.runPluginExtensions(
            Arrays.asList(extension), Collections.emptyList(), originalBuilder);
    Assert.assertSame(extendedBuilder, originalBuilder);

    mavenProjectProperties.waitForLoggingThread();
    Mockito.verify(mockLog).debug("No Jib plugin extensions configured to load");
  }

  @Test
  public void testRunPluginExtensions()
      throws JibPluginExtensionException, InvalidImageReferenceException {
    JibMavenPluginExtension extension =
        new FakeJibMavenPluginExtension(
            (buildPlan, properties, mavenData, logger) -> {
              logger.log(LogLevel.ERROR, "awesome error from my extension");
              return buildPlan.toBuilder().setUser("user from extension").build();
            });

    JibContainerBuilder extendedBuilder =
        mavenProjectProperties.runPluginExtensions(
            Arrays.asList(extension),
            Arrays.asList(new FakeExtensionConfiguration()),
            Jib.from(RegistryImage.named("from/nothing")));
    Assert.assertEquals("user from extension", extendedBuilder.toContainerBuildPlan().getUser());

    mavenProjectProperties.waitForLoggingThread();
    Mockito.verify(mockLog).error("awesome error from my extension");
    Mockito.verify(mockLog)
        .info(
            Mockito.startsWith(
                "Running extension: com.google.cloud.tools.jib.maven.MavenProjectProperties"));
  }

  @Test
  public void testRunPluginExtensions_exceptionFromExtension()
      throws InvalidImageReferenceException {
    FileNotFoundException fakeException = new FileNotFoundException();
    JibMavenPluginExtension extension =
        new FakeJibMavenPluginExtension(
            (buildPlan, properties, mavenData, logger) -> {
              throw new JibPluginExtensionException(
                  JibMavenPluginExtension.class, "exception from extension", fakeException);
            });

    JibContainerBuilder originalBuilder = Jib.from(RegistryImage.named("scratch"));
    try {
      mavenProjectProperties.runPluginExtensions(
          Arrays.asList(extension),
          Arrays.asList(new FakeExtensionConfiguration()),
          originalBuilder);
      Assert.fail();
    } catch (JibPluginExtensionException ex) {
      Assert.assertEquals("exception from extension", ex.getMessage());
      Assert.assertSame(fakeException, ex.getCause());
    }
  }

  @Test
  public void testRunPluginExtensions_invalidBaseImageFromExtension()
      throws InvalidImageReferenceException {
    JibMavenPluginExtension extension =
        new FakeJibMavenPluginExtension(
            (buildPlan, properties, mavenData, logger) ->
                buildPlan.toBuilder().setBaseImage(" in*val+id").build());

    JibContainerBuilder originalBuilder = Jib.from(RegistryImage.named("from/nothing"));
    try {
      mavenProjectProperties.runPluginExtensions(
          Arrays.asList(extension),
          Arrays.asList(new FakeExtensionConfiguration()),
          originalBuilder);
      Assert.fail();
    } catch (JibPluginExtensionException ex) {
      Assert.assertEquals("invalid base image reference:  in*val+id", ex.getMessage());
      Assert.assertThat(
          ex.getCause(), CoreMatchers.instanceOf(InvalidImageReferenceException.class));
    }
  }
}
