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

package com.google.cloud.tools.jib.api;
// TODO: Move to com.google.cloud.tools.jib once that package is cleaned up.

import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;

/** Build containers with Jib. */
// TODO: Add tests once JibContainerBuilder#containerize() is added.
public class Jib {

  /**
   * Starts building the container from a base image.
   *
   * @param baseImageReference the base image reference
   * @return a new {@link JibContainerBuilder} to continue building the container
   * @throws InvalidImageReferenceException if the {@code baseImageReference} is not a valid image
   *     reference
   */
  public static JibContainerBuilder from(String baseImageReference)
      throws InvalidImageReferenceException {
    return from(ImageReference.parse(baseImageReference));
  }

  /**
   * Starts building the container from a base image.
   *
   * @param baseImageReference the base image reference
   * @return a new {@link JibContainerBuilder} to continue building the container
   */
  public static JibContainerBuilder from(ImageReference baseImageReference) {
    return new JibContainerBuilder(baseImageReference);
  }

  private Jib() {}
}
