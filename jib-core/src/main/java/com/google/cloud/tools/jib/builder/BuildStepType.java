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
  AUTHENTICATE_PUSH,
  BUILD_AND_CACHE_APPLICATION_LAYER,
  BUILD_IMAGE,
  LOAD_DOCKER,
  PULL_AND_CACHE_BASE_IMAGE_LAYER,
  PULL_BASE_IMAGE,
  PUSH_BLOB,
  PUSH_CONTAINER_CONFIGURATION,
  PUSH_IMAGE,
  PUSH_LAYERS,
  RETRIEVE_REGISTRY_CREDENTIALS,
  WRITE_TAR_FILE
}
