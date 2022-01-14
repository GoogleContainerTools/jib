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

import com.google.cloud.tools.jib.api.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.filesystem.TempDirectoryProvider;
import com.google.cloud.tools.jib.plugins.common.BuildStepsExecutionException;
import com.google.cloud.tools.jib.plugins.common.ExtraDirectoryNotFoundException;
import com.google.cloud.tools.jib.plugins.common.HelpfulSuggestions;
import com.google.cloud.tools.jib.plugins.common.IncompatibleBaseImageJavaVersionException;
import com.google.cloud.tools.jib.plugins.common.InvalidAppRootException;
import com.google.cloud.tools.jib.plugins.common.InvalidContainerVolumeException;
import com.google.cloud.tools.jib.plugins.common.InvalidContainerizingModeException;
import com.google.cloud.tools.jib.plugins.common.InvalidCreationTimeException;
import com.google.cloud.tools.jib.plugins.common.InvalidFilesModificationTimeException;
import com.google.cloud.tools.jib.plugins.common.InvalidPlatformException;
import com.google.cloud.tools.jib.plugins.common.InvalidWorkingDirectoryException;
import com.google.cloud.tools.jib.plugins.common.MainClassInferenceException;
import com.google.cloud.tools.jib.plugins.common.PluginConfigurationProcessor;
import com.google.cloud.tools.jib.plugins.common.globalconfig.GlobalConfig;
import com.google.cloud.tools.jib.plugins.common.globalconfig.InvalidGlobalConfigException;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Futures;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.Future;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Builds a container image and exports to disk at the configured location ({@code
 * ${project.build.directory}/jib-image.tar} by default).
 */
@Mojo(
    name = BuildTarMojo.GOAL_NAME,
    requiresDependencyResolution = ResolutionScope.RUNTIME_PLUS_SYSTEM,
    threadSafe = true)
public class BuildTarMojo extends JibPluginConfiguration {

  @VisibleForTesting static final String GOAL_NAME = "buildTar";

  private static final String HELPFUL_SUGGESTIONS_PREFIX = "Building image tarball failed";

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    checkJibVersion();
    if (MojoCommon.shouldSkipJibExecution(this)) {
      return;
    }

    MavenSettingsProxyProvider.activateHttpAndHttpsProxies(
        getSession().getSettings(), getSettingsDecrypter());

    TempDirectoryProvider tempDirectoryProvider = new TempDirectoryProvider();
    MavenProjectProperties projectProperties =
        MavenProjectProperties.getForProject(
            Preconditions.checkNotNull(descriptor),
            getProject(),
            getSession(),
            getLog(),
            tempDirectoryProvider,
            getInjectedPluginExtensions());

    Future<Optional<String>> updateCheckFuture = Futures.immediateFuture(Optional.empty());
    try {
      GlobalConfig globalConfig = GlobalConfig.readConfig();
      updateCheckFuture = MojoCommon.newUpdateChecker(projectProperties, globalConfig, getLog());

      PluginConfigurationProcessor.createJibBuildRunnerForTarImage(
              new MavenRawConfiguration(this),
              new MavenSettingsServerCredentials(
                  getSession().getSettings(), getSettingsDecrypter()),
              projectProperties,
              globalConfig,
              new MavenHelpfulSuggestions(HELPFUL_SUGGESTIONS_PREFIX))
          .runBuild();

    } catch (InvalidAppRootException ex) {
      throw new MojoExecutionException(
          "<container><appRoot> is not an absolute Unix-style path: " + ex.getInvalidPathValue(),
          ex);

    } catch (InvalidContainerizingModeException ex) {
      throw new MojoExecutionException(
          "invalid value for <containerizingMode>: " + ex.getInvalidContainerizingMode(), ex);

    } catch (InvalidWorkingDirectoryException ex) {
      throw new MojoExecutionException(
          "<container><workingDirectory> is not an absolute Unix-style path: "
              + ex.getInvalidPathValue(),
          ex);
    } catch (InvalidPlatformException ex) {
      throw new MojoExecutionException(
          "<from><platforms> contains a platform configuration that is missing required values or has invalid values: "
              + ex.getMessage()
              + ": "
              + ex.getInvalidPlatform(),
          ex);
    } catch (InvalidContainerVolumeException ex) {
      throw new MojoExecutionException(
          "<container><volumes> is not an absolute Unix-style path: " + ex.getInvalidVolume(), ex);

    } catch (InvalidFilesModificationTimeException ex) {
      throw new MojoExecutionException(
          "<container><filesModificationTime> should be an ISO 8601 date-time (see "
              + "DateTimeFormatter.ISO_DATE_TIME) or special keyword \"EPOCH_PLUS_SECOND\": "
              + ex.getInvalidFilesModificationTime(),
          ex);

    } catch (InvalidCreationTimeException ex) {
      throw new MojoExecutionException(
          "<container><creationTime> should be an ISO 8601 date-time (see "
              + "DateTimeFormatter.ISO_DATE_TIME) or a special keyword (\"EPOCH\", "
              + "\"USE_CURRENT_TIMESTAMP\"): "
              + ex.getInvalidCreationTime(),
          ex);

    } catch (JibPluginExtensionException ex) {
      String extensionName = ex.getExtensionClass().getName();
      throw new MojoExecutionException(
          "error running extension '" + extensionName + "': " + ex.getMessage(), ex);

    } catch (IncompatibleBaseImageJavaVersionException ex) {
      throw new MojoExecutionException(
          HelpfulSuggestions.forIncompatibleBaseImageJavaVersionForMaven(
              ex.getBaseImageMajorJavaVersion(), ex.getProjectMajorJavaVersion()),
          ex);

    } catch (InvalidImageReferenceException ex) {
      throw new MojoExecutionException(
          HelpfulSuggestions.forInvalidImageReference(ex.getInvalidReference()), ex);

    } catch (IOException
        | CacheDirectoryCreationException
        | MainClassInferenceException
        | InvalidGlobalConfigException ex) {
      throw new MojoExecutionException(ex.getMessage(), ex);

    } catch (BuildStepsExecutionException ex) {
      throw new MojoExecutionException(ex.getMessage(), ex.getCause());

    } catch (ExtraDirectoryNotFoundException ex) {
      throw new MojoExecutionException(
          "<extraDirectories><paths> contain \"from\" directory that doesn't exist locally: "
              + ex.getPath(),
          ex);
    } finally {
      tempDirectoryProvider.close();
      MojoCommon.finishUpdateChecker(projectProperties, updateCheckFuture);
      projectProperties.waitForLoggingThread();
      getLog().info("");
    }
  }
}
