/*
 * Copyright 2020 Google LLC.
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

package com.google.cloud.tools.jib.builder.steps;

import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.api.buildplan.Platform;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.image.json.ContainerConfigurationTemplate;
import com.google.common.base.Verify;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

/** Provides helper methods to check platforms. */
public class PlatformChecker {

  /**
   * Assuming the base image is not a manifest list, checks and warns misconfigured platforms.
   *
   * @param buildContext the {@link BuildContext}
   * @param containerConfig container configuration JSON of the base image
   */
  static void checkManifestPlatform(
      BuildContext buildContext, ContainerConfigurationTemplate containerConfig) {
    EventHandlers eventHandlers = buildContext.getEventHandlers();
    Optional<Path> path = buildContext.getBaseImageConfiguration().getTarPath();
    String baseImageName =
        path.map(Path::toString)
            .orElse(buildContext.getBaseImageConfiguration().getImage().toString());

    Set<Platform> platforms = buildContext.getContainerConfiguration().getPlatforms();
    Verify.verify(!platforms.isEmpty());

    if (platforms.size() != 1) {
      eventHandlers.dispatch(
          LogEvent.warn(
              "platforms configured, but '" + baseImageName + "' is not a manifest list"));
    } else {
      Platform platform = platforms.iterator().next();
      if (!platform.getArchitecture().equals(containerConfig.getArchitecture())
          || !platform.getOs().equals(containerConfig.getOs())) {

        // Unfortunately, "platforms" has amd64/linux by default even if the user didn't explicitly
        // configure it. Skip reporting to suppress false alarm.
        if (!(platform.getArchitecture().equals("amd64") && platform.getOs().equals("linux"))) {
          String warning =
              "the configured platform (%s/%s) doesn't match the platform (%s/%s) of the base "
                  + "image (%s)";
          eventHandlers.dispatch(
              LogEvent.warn(
                  String.format(
                      warning,
                      platform.getArchitecture(),
                      platform.getOs(),
                      containerConfig.getArchitecture(),
                      containerConfig.getOs(),
                      baseImageName)));
        }
      }
    }
  }
}
