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
import com.google.cloud.tools.jib.filesystem.TempDirectoryProvider;
import com.google.cloud.tools.jib.maven.extension.JibMavenPluginExtension;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger.LogLevel;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.Collections;
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
        mavenProjectProperties.runPluginExtensions(Collections.emptyIterator(), originalBuilder);
    Assert.assertSame(extendedBuilder, originalBuilder);

    mavenProjectProperties.waitForLoggingThread();
    Mockito.verify(mockLog).debug("No Jib plugin extensions discovered");
  }

  @Test
  public void testRunPluginExtensions()
      throws JibPluginExtensionException, InvalidImageReferenceException {
    JibMavenPluginExtension extension =
        (buildPlan, mavenData, logger) -> {
          logger.log(LogLevel.ERROR, "awesome error from my extension");
          return buildPlan.toBuilder().setUser("user from extension").build();
        };

    JibContainerBuilder extendedBuilder =
        mavenProjectProperties.runPluginExtensions(
            Arrays.asList(extension).iterator(), Jib.from(RegistryImage.named("from/nothing")));
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
        (buildPlan, mavenData, logger) -> {
          throw new JibPluginExtensionException(
              JibMavenPluginExtension.class, "exception from extension", fakeException);
        };

    JibContainerBuilder originalBuilder = Jib.from(RegistryImage.named("scratch"));
    try {
      mavenProjectProperties.runPluginExtensions(
          Arrays.asList(extension).iterator(), originalBuilder);
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
        (buildPlan, mavenData, logger) -> buildPlan.toBuilder().setBaseImage(" in*val+id").build();

    JibContainerBuilder originalBuilder = Jib.from(RegistryImage.named("from/nothing"));
    try {
      mavenProjectProperties.runPluginExtensions(
          Arrays.asList(extension).iterator(), originalBuilder);
      Assert.fail();
    } catch (JibPluginExtensionException ex) {
      Assert.assertEquals("invalid base image reference:  in*val+id", ex.getMessage());
      Assert.assertThat(
          ex.getCause(), CoreMatchers.instanceOf(InvalidImageReferenceException.class));
    }
  }
}
