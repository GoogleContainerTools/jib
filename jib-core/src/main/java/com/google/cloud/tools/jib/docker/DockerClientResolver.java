/*
 * Copyright 2022 Google LLC.
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

package com.google.cloud.tools.jib.docker;

import com.google.cloud.tools.jib.api.DockerClient;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

public class DockerClientResolver {

  private static final ServiceLoader<DockerClient> dockerClients =
      ServiceLoader.load(DockerClient.class);

  /**
   * Look for supported DockerClient.
   *
   * @param parameters needed by the dockerClient
   * @return dockerClient if any is found
   */
  public static Optional<DockerClient> resolve(Map<String, String> parameters) {
    for (DockerClient dockerClient : dockerClients) {
      if (dockerClient.supported(parameters)) {
        return Optional.of(dockerClient);
      }
    }
    return Optional.empty();
  }
}
