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

package com.google.cloud.tools.jib.builder;

/** Types corresponding to steps in the containerization process. */
public enum BuildStepType {

  /** Root type that corresponds to build startup. */
  ALL,

  /** Authentication step for pushing to target registry. */
  AUTHENTICATE_PUSH,

  /** Step for building/caching an application layer. */
  BUILD_AND_CACHE_APPLICATION_LAYER,

  /** Step for building image layers/configuration. */
  BUILD_IMAGE,

  /** Step for loading the image into the Docker daemon. */
  LOAD_DOCKER,

  /** Step for pulling/caching a base image layer. */
  PULL_AND_CACHE_BASE_IMAGE_LAYER,

  /** Step for pulling the base image manifest. */
  PULL_BASE_IMAGE,

  /** Step for pushing the container configuration to the target registry. */
  PUSH_CONTAINER_CONFIGURATION,

  /** Step for pushing the image manifest to the target registry. */
  PUSH_IMAGE,

  /** Step for pushing the image layers to the target registry. */
  PUSH_LAYERS,

  /** Step for retrieving credentials for the base image registry. */
  RETRIEVE_REGISTRY_CREDENTIALS_BASE,

  /** Step for retrieving credentials for the target image registry. */
  RETRIEVE_REGISTRY_CREDENTIALS_TARGET,

  /** Step for writing the image tarball to disk. */
  WRITE_TAR_FILE
}
