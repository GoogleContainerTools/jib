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

import com.google.cloud.tools.jib.plugins.common.ConfigurationPropertyValidator;
import com.google.cloud.tools.jib.plugins.common.PropertyNames;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import javax.annotation.Nullable;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

/**
 * Object in {@link BuildDockerTask} that configures the Docker executable and the additional
 * environment variables to use when executing the executable.
 */
public class DockerClientParameters {

  @Nullable private Path executable;
  @Nullable private Map<String, String> environment;

  @Input
  @Nullable
  @Optional
  public String getExecutable() {
    if (System.getProperty(PropertyNames.DOCKER_CLIENT_EXECUTABLE) != null) {
      return System.getProperty(PropertyNames.DOCKER_CLIENT_EXECUTABLE);
    }
    return executable == null ? null : executable.toString();
  }

  @Internal
  @Nullable
  Path getExecutablePath() {
    return executable;
  }

  public void setExecutable(String executable) {
    this.executable = Paths.get(executable);
  }

  @Input
  @Nullable
  @Optional
  public Map<String, String> getEnvironment() {
    if (System.getProperty(PropertyNames.DOCKER_CLIENT_ENVIRONMENT) != null) {
      return ConfigurationPropertyValidator.parseMapProperty(
          System.getProperty(PropertyNames.DOCKER_CLIENT_ENVIRONMENT));
    }
    return environment;
  }

  public void setEnvironment(@Nullable Map<String, String> environment) {
    this.environment = environment;
  }
}
