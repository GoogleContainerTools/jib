/*
 * Copyright 2018 Google LLC. All rights reserved.
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

import com.google.cloud.tools.jib.JibLogger;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.http.Authorizations;
import com.google.common.base.Strings;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;

/**
 * A bean that configures an image to be used in the build steps. This is configurable with Groovy
 * closures and can be validated when used as a task input.
 *
 * <p>{@code image} (required) is the image reference and {@code credHelper} (optional) is the name
 * (after {@code docker-credential} of the credential helper for accessing the {@code image}.
 */
public class ImageParameters {

  private AuthParameters auth;

  @Nullable private String image;
  @Nullable private String credHelper;

  @Inject
  public ImageParameters(ObjectFactory objectFactory) {
    auth = objectFactory.newInstance(AuthParameters.class);
  }

  @Input
  @Nullable
  @Optional
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

  @Nested
  @Optional
  AuthParameters getAuth() {
    return auth;
  }

  public void auth(Action<? super AuthParameters> action) {
    action.execute(auth);
  }

  /**
   * Converts the {@link ImageParameters} to an {@link Authorization}.
   *
   * @param logger the {@link JibLogger} used to print warnings
   * @param imageProperty the image configuration's name (i.e. "from" or "to")
   * @return the {@link Authorization}, or null if the username and password aren't both configured
   */
  @Internal
  @Nullable
  Authorization getImageAuthorization(JibLogger logger, String imageProperty) {
    if (Strings.isNullOrEmpty(auth.getUsername()) || Strings.isNullOrEmpty(auth.getPassword())) {
      if (!Strings.isNullOrEmpty(auth.getPassword())) {
        logger.warn(
            "jib."
                + imageProperty
                + ".auth.username is null; ignoring jib."
                + imageProperty
                + ".auth section.");
      } else if (!Strings.isNullOrEmpty(auth.getUsername())) {
        logger.warn(
            "jib."
                + imageProperty
                + ".auth.password is null; ignoring jib."
                + imageProperty
                + ".auth section.");
      }
      return null;
    }
    return Authorizations.withBasicCredentials(auth.getUsername(), auth.getPassword());
  }
}
