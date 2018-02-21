/*
 * Copyright 2018 Google Inc.
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

package com.google.cloud.tools.jib.builder.configuration;

import com.google.cloud.tools.jib.image.ImageReference;

/** The target image to build and push as. */
class TargetImageParameter implements ConfigurationParameter<ImageReference> {

  private ImageReference targetImageReference;

  @Override
  public ConfigurationParameter<ImageReference> set(ImageReference imageReference) {
    targetImageReference = imageReference;
    return this;
  }

  @Override
  public ImageReference get() {
    return targetImageReference;
  }

  @Override
  public ValidationResult validate() {
    if (targetImageReference != null) {
      return ValidationResult.valid();
    }

    return ValidationResult.invalid("target image is required but not set");
  }
}
