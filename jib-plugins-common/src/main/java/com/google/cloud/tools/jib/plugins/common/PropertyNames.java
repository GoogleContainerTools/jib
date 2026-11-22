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

package com.google.cloud.tools.jib.plugins.common;

/** Names of system properties used to set configuration via commandline. */
public class PropertyNames {

  public static final String FROM_IMAGE = "jib.from.image";
  public static final String FROM_CRED_HELPER = "jib.from.credHelper";
  public static final String FROM_AUTH_USERNAME = "jib.from.auth.username";
  public static final String FROM_AUTH_PASSWORD = "jib.from.auth.password";
  public static final String FROM_PLATFORMS = "jib.from.platforms";
  public static final String TO_IMAGE = "jib.to.image";
  public static final String TO_IMAGE_ALTERNATE = "image";
  public static final String TO_TAGS = "jib.to.tags";
  public static final String TO_CRED_HELPER = "jib.to.credHelper";
  public static final String TO_AUTH_USERNAME = "jib.to.auth.username";
  public static final String TO_AUTH_PASSWORD = "jib.to.auth.password";
  public static final String CONTAINER_APP_ROOT = "jib.container.appRoot";
  public static final String CONTAINER_ARGS = "jib.container.args";
  public static final String CONTAINER_EXTRA_CLASSPATH = "jib.container.extraClasspath";
  public static final String EXPAND_CLASSPATH_DEPENDENCIES =
      "jib.container.expandClasspathDependencies";
  public static final String CONTAINER_ENTRYPOINT = "jib.container.entrypoint";
  public static final String CONTAINER_ENVIRONMENT = "jib.container.environment";
  public static final String CONTAINER_FORMAT = "jib.container.format";
  public static final String CONTAINER_JVM_FLAGS = "jib.container.jvmFlags";
  public static final String CONTAINER_LABELS = "jib.container.labels";
  public static final String CONTAINER_MAIN_CLASS = "jib.container.mainClass";
  public static final String CONTAINER_USER = "jib.container.user";
  public static final String CONTAINER_WORKING_DIRECTORY = "jib.container.workingDirectory";
  public static final String CONTAINER_VOLUMES = "jib.container.volumes";
  public static final String CONTAINER_PORTS = "jib.container.ports";
  public static final String CONTAINER_FILES_MODIFICATION_TIME =
      "jib.container.filesModificationTime";
  public static final String CONTAINER_CREATION_TIME = "jib.container.creationTime";
  public static final String ALLOW_INSECURE_REGISTRIES = "jib.allowInsecureRegistries";
  public static final String EXTRA_DIRECTORIES_PATHS = "jib.extraDirectories.paths";
  public static final String EXTRA_DIRECTORIES_PERMISSIONS = "jib.extraDirectories.permissions";
  public static final String DOCKER_CLIENT_EXECUTABLE = "jib.dockerClient.executable";
  public static final String DOCKER_CLIENT_ENVIRONMENT = "jib.dockerClient.environment";
  public static final String OUTPUT_PATHS_DIGEST = "jib.outputPaths.digest";
  public static final String OUTPUT_PATHS_IMAGE_TAG_DIGEST = "jib.outputPaths.imageTagDigest";
  public static final String OUTPUT_PATHS_IMAGE_ID = "jib.outputPaths.imageId";
  public static final String OUTPUT_PATHS_IMAGE_JSON = "jib.outputPaths.imageJson";
  public static final String OUTPUT_PATHS_TAR = "jib.outputPaths.tar";
  public static final String CONTAINERIZING_MODE = "jib.containerizingMode";
  public static final String CONFIGURATION_NAME = "jib.configurationName";
  public static final String SKIP = "jib.skip";

  public static final String CONTAINERIZE = "jib.containerize";
  public static final String CONSOLE = "jib.console";
  public static final String USE_ONLY_PROJECT_CACHE = "jib.useOnlyProjectCache";
  public static final String BASE_IMAGE_CACHE = "jib.baseImageCache";
  public static final String APPLICATION_CACHE = "jib.applicationCache";
  public static final String ALWAYS_CACHE_BASE_IMAGE = "jib.alwaysCacheBaseImage";
  public static final String DISABLE_UPDATE_CHECKS = "jib.disableUpdateChecks";
  public static final String CONFIG_DIRECTORY = "jib.configDirectory";

  private PropertyNames() {}
}
