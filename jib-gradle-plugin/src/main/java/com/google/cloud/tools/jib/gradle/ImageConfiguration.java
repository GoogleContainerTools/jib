/*
 * Copyright 2018 Google LLC. All Rights Reserved.
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

import javax.annotation.Nullable;
import org.gradle.api.Action;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

/**
 * A bean that configures an image to be used in the build steps. This is configurable with Groovy
 * closures and can be validated when used as a task input.
 *
 * <p>{@code image} (required) is the image reference and {@code credHelper} (optional) is the name
 * (after {@code docker-credential} of the credential helper for accessing the {@code image}.
 */
public class ImageConfiguration {

  private final AuthConfiguration auth = new AuthConfiguration();

  @Nullable private String image;
  @Nullable private String credHelper;

  @Input
  @Nullable
  public String getImage() {
    return image;
  }

  public void setImage(String image) {
    this.image = image;
  }

  @Input
  @Nullable
  @Optional
  public String getCredHelper() {
    return credHelper;
  }

  public void setCredHelper(String credHelper) {
    this.credHelper = credHelper;
  }

  AuthConfiguration getAuth() {
    return auth;
  }

  public void auth(Action<? super AuthConfiguration> action) {
    action.execute(auth);
  }
}
