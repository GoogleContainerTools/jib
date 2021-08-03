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

package com.google.cloud.tools.jib.api.buildplan;

/** Indicates the format of the image. */
public enum ImageFormat {

  /** See <a href="https://docs.docker.com/registry/spec/manifest-v2-2/">Docker V2.2</a>. */
  DOCKER,

  /** See <a href="https://github.com/opencontainers/image-spec/blob/master/manifest.md">OCI</a>. */
  OCI
}
