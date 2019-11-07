/*
 * Copyright 2019 Google LLC.
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

package com.google.cloud.tools.jib.maven.skaffold;

import com.google.cloud.tools.jib.filesystem.TempDirectoryProvider;
import com.google.cloud.tools.jib.maven.JibPluginConfiguration;
import com.google.cloud.tools.jib.maven.MavenProjectProperties;
import com.google.cloud.tools.jib.maven.MavenRawConfiguration;
import com.google.cloud.tools.jib.maven.MojoCommon;
import com.google.cloud.tools.jib.plugins.common.ContainerizingMode;
import com.google.cloud.tools.jib.plugins.common.InvalidContainerizingModeException;
import com.google.cloud.tools.jib.plugins.common.PluginConfigurationProcessor;
import com.google.common.annotations.VisibleForTesting;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(
    name = SyncMapMojo.GOAL_NAME,
    requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class SyncMapMojo extends JibPluginConfiguration {

  @VisibleForTesting static final String GOAL_NAME = "_skaffold-sync-map";

  @Override
  public void execute() throws MojoExecutionException {
    checkJibVersion();
    if (MojoCommon.shouldSkipJibExecution(this)) {
      return;
    }

    // add check that means this is only for jars
    if (!"jar".equals(getProject().getPackaging())) {
      throw new MojoExecutionException(
          "Skaffold sync is currently only available for 'jar' style Jib projects, but the packaging of "
              + getProject().getArtifactId()
              + " is '"
              + getProject().getPackaging()
              + "'");
    }
    // add check for exploded containerization
    try {
      if (!ContainerizingMode.EXPLODED.equals(ContainerizingMode.from(getContainerizingMode()))) {
        throw new MojoExecutionException(
            "Skaffold sync is currently only available for Jib projects in 'exploded' containerizing mode, but the containerizing mode of "
                + getProject().getArtifactId()
                + " is '"
                + getContainerizingMode()
                + "'");
      }
    } catch (InvalidContainerizingModeException ex) {
      throw new MojoExecutionException("Invalid containerizing mode", ex);
    }

    try (TempDirectoryProvider tempDirectoryProvider = new TempDirectoryProvider()) {
      MavenProjectProperties projectProperties =
          MavenProjectProperties.getForProject(
              getProject(), getSession(), getLog(), tempDirectoryProvider);

      MavenRawConfiguration configuration = new MavenRawConfiguration(this);

      try {
        String syncMapJson =
            PluginConfigurationProcessor.getSkaffoldSyncMap(configuration, projectProperties);

        System.out.println("\nBEGIN JIB JSON");
        System.out.println(syncMapJson);

      } catch (Exception ex) {
        throw new MojoExecutionException(
            "Failed to generate a Jib file map for sync with Skaffold", ex);
      }
    }
  }
}
