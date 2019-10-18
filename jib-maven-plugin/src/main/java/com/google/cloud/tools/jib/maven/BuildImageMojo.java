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

import com.google.cloud.tools.jib.api.ImageFormat;
import com.google.cloud.tools.jib.filesystem.TempDirectoryProvider;
import com.google.cloud.tools.jib.plugins.common.HelpfulSuggestions;
import com.google.cloud.tools.jib.plugins.common.PluginConfigurationProcessor;
import com.google.cloud.tools.jib.plugins.common.PropertyNames;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import java.util.Arrays;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/** Builds a container image. */
@Mojo(
    name = BuildImageMojo.GOAL_NAME,
    requiresDependencyResolution = ResolutionScope.RUNTIME_PLUS_SYSTEM,
    threadSafe = true)
public class BuildImageMojo extends JibPluginConfiguration {

  @VisibleForTesting static final String GOAL_NAME = "build";

  private static final String HELPFUL_SUGGESTIONS_PREFIX = "Build image failed";

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    checkJibVersion();
    if (isSkipped()) {
      getLog().info("Skipping containerization because jib-maven-plugin: skip = true");
      return;
    } else if (!isContainerizable()) {
      getLog()
          .info(
              "Skipping containerization of this module (not specified in "
                  + PropertyNames.CONTAINERIZE
                  + ")");
      return;
    }
    if ("pom".equals(getProject().getPackaging())) {
      getLog().info("Skipping containerization because packaging is 'pom'...");
      return;
    }

    MojoCommon.checkUseCurrentTimestampDeprecation(this);

    // Validates 'format'.
    if (Arrays.stream(ImageFormat.values()).noneMatch(value -> value.name().equals(getFormat()))) {
      throw new MojoFailureException(
          "<format> parameter is configured with value '"
              + getFormat()
              + "', but the only valid configuration options are '"
              + ImageFormat.Docker
              + "' and '"
              + ImageFormat.OCI
              + "'.");
    }

    // Parses 'to' into image reference.
    if (Strings.isNullOrEmpty(getTargetImage())) {
      throw new MojoFailureException(
          HelpfulSuggestions.forToNotConfigured(
              "Missing target image parameter",
              "<to><image>",
              "pom.xml",
              "mvn compile jib:build -Dimage=<your image name>"));
    }

    MavenSettingsProxyProvider.activateHttpAndHttpsProxies(
        getSession().getSettings(), getSettingsDecrypter());

    TempDirectoryProvider tempDirectoryProvider = new TempDirectoryProvider();
    MavenProjectProperties projectProperties =
        MavenProjectProperties.getForProject(
            getProject(), getSession(), getLog(), tempDirectoryProvider);
    try {
      PluginConfigurationProcessor.createJibBuildRunnerForRegistryImage(
              new MavenRawConfiguration(this),
              new MavenSettingsServerCredentials(
                  getSession().getSettings(), getSettingsDecrypter()),
              projectProperties,
              new MavenHelpfulSuggestions(HELPFUL_SUGGESTIONS_PREFIX))
          .runBuild();

    } catch (Exception ex) {
      MojoCommon.rethrowJibException(ex);

    } finally {
      tempDirectoryProvider.close();
      projectProperties.waitForLoggingThread();
      getLog().info("");
    }
  }
}
