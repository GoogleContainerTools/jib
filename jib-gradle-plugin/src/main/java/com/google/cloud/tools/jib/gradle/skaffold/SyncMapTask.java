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

package com.google.cloud.tools.jib.gradle.skaffold;

import com.google.cloud.tools.jib.filesystem.TempDirectoryProvider;
import com.google.cloud.tools.jib.gradle.GradleProjectProperties;
import com.google.cloud.tools.jib.gradle.GradleRawConfiguration;
import com.google.cloud.tools.jib.gradle.JibExtension;
import com.google.cloud.tools.jib.plugins.common.ContainerizingMode;
import com.google.cloud.tools.jib.plugins.common.InvalidContainerizingModeException;
import com.google.cloud.tools.jib.plugins.common.PluginConfigurationProcessor;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;

/**
 * Prints out a map of local files to their location on the container.
 *
 * <p>Expected use: {@code ./gradlew _jibSkaffoldSyncMap -q} or {@code ./gradlew
 * :<subproject>:_jibSkaffoldSyncMap -q}
 */
public class SyncMapTask extends DefaultTask {

  private final JibExtension jibExtension;

  @Inject
  public SyncMapTask(JibExtension jibExtension) {
    this.jibExtension = jibExtension;
  }

  /** Task Action, lists files and container targets. */
  @TaskAction
  public void listFilesAndTargets() {
    try (TempDirectoryProvider tempDirectoryProvider = new TempDirectoryProvider()) {
      GradleProjectProperties projectProperties =
          GradleProjectProperties.getForProject(
              getProject(),
              getLogger(),
              tempDirectoryProvider,
              jibExtension.getConfigurationName().get());

      GradleRawConfiguration configuration = new GradleRawConfiguration(jibExtension);

      // TODO: move these shared checks with SyncMapMojo into plugins-common
      if (projectProperties.isWarProject()) {
        throw new GradleException(
            "Skaffold sync is currently only available for 'jar' style Jib projects, but the project "
                + getProject().getName()
                + " is configured to generate a 'war'");
      }
      try {
        if (!ContainerizingMode.EXPLODED.equals(
            ContainerizingMode.from(jibExtension.getContainerizingMode()))) {
          throw new GradleException(
              "Skaffold sync is currently only available for Jib projects in 'exploded' containerizing mode, but the containerizing mode of "
                  + getProject().getName()
                  + " is '"
                  + jibExtension.getContainerizingMode()
                  + "'");
        }
      } catch (InvalidContainerizingModeException ex) {
        throw new GradleException("Invalid containerizing mode", ex);
      }

      try {
        String syncMapJson =
            PluginConfigurationProcessor.getSkaffoldSyncMap(
                configuration,
                projectProperties,
                jibExtension.getSkaffold().getSync().getExcludes());

        System.out.println();
        System.out.println("BEGIN JIB JSON: SYNCMAP/1");
        System.out.println(syncMapJson);

      } catch (Exception ex) {
        throw new GradleException("Failed to generate a Jib file map for sync with Skaffold", ex);
      }
    }
  }
}
