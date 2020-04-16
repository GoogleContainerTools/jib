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
import com.google.cloud.tools.jib.api.RegistryImage;
import com.google.cloud.tools.jib.filesystem.TempDirectoryProvider;
import com.google.cloud.tools.jib.gradle.extension.JibGradlePluginExtension;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger.LogLevel;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.Collections;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.configuration.ConsoleOutput;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Plugin extension test for {@link GradleProjectProperties}. */
@RunWith(MockitoJUnitRunner.class)
public class GradleProjectPropertiesExtensionTest {

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock private TempDirectoryProvider mockTempDirectoryProvider;
  @Mock private Logger mockLogger;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private Project mockProject;

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
        new GradleProjectProperties(mockProject, mockLogger, mockTempDirectoryProvider);
  }

  @Test
  public void testRunPluginExtensions_noExtensionsFound()
      throws JibPluginExtensionException, InvalidImageReferenceException {
    JibContainerBuilder originalBuilder = Jib.from(RegistryImage.named("from/nothing"));
    JibContainerBuilder extendedBuilder =
        gradleProjectProperties.runPluginExtensions(Collections.emptyIterator(), originalBuilder);
    Assert.assertSame(extendedBuilder, originalBuilder);

    gradleProjectProperties.waitForLoggingThread();
    Mockito.verify(mockLogger).debug("No Jib plugin extensions discovered");
  }

  @Test
  public void testRunPluginExtensions()
      throws JibPluginExtensionException, InvalidImageReferenceException {
    JibGradlePluginExtension extension =
        (buildPlan, gradleData, logger) -> {
          logger.log(LogLevel.ERROR, "awesome error from my extension");
          return buildPlan.toBuilder().setUser("user from extension").build();
        };

    JibContainerBuilder extendedBuilder =
        gradleProjectProperties.runPluginExtensions(
            Arrays.asList(extension).iterator(), Jib.from(RegistryImage.named("from/nothing")));
    Assert.assertEquals("user from extension", extendedBuilder.toContainerBuildPlan().getUser());

    gradleProjectProperties.waitForLoggingThread();
    Mockito.verify(mockLogger).error("awesome error from my extension");
    Mockito.verify(mockLogger)
        .lifecycle(
            Mockito.startsWith(
                "Running extension: com.google.cloud.tools.jib.gradle.GradleProjectProperties"));
  }

  @Test
  public void testRunPluginExtensions_exceptionFromExtension()
      throws InvalidImageReferenceException {
    FileNotFoundException fakeException = new FileNotFoundException();
    JibGradlePluginExtension extension =
        (buildPlan, gradleData, logger) -> {
          throw new JibPluginExtensionException(
              JibGradlePluginExtension.class, "exception from extension", fakeException);
        };

    JibContainerBuilder originalBuilder = Jib.from(RegistryImage.named("scratch"));
    try {
      gradleProjectProperties.runPluginExtensions(
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
    JibGradlePluginExtension extension =
        (buildPlan, gradleData, logger) -> buildPlan.toBuilder().setBaseImage(" in*val+id").build();

    JibContainerBuilder originalBuilder = Jib.from(RegistryImage.named("from/nothing"));
    try {
      gradleProjectProperties.runPluginExtensions(
          Arrays.asList(extension).iterator(), originalBuilder);
      Assert.fail();
    } catch (JibPluginExtensionException ex) {
      Assert.assertEquals("invalid base image reference:  in*val+id", ex.getMessage());
      Assert.assertThat(
          ex.getCause(), CoreMatchers.instanceOf(InvalidImageReferenceException.class));
    }
  }
}
