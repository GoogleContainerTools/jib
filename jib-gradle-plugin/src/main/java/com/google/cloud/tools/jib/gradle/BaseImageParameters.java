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

import com.google.cloud.tools.jib.plugins.common.PropertyNames;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;

/** Object in {@link JibExtension} that configures the base image. */
public class BaseImageParameters {

  private final AuthParameters auth;
  @Nullable private String credHelper;
  @Nullable private String image;
  private final PlatformsParametersSpec platformsParametersSpec;
  private ListProperty<PlatformsParameters> platforms;

  @Inject
  public BaseImageParameters(ObjectFactory objectFactory) {
    auth = objectFactory.newInstance(AuthParameters.class, "from.auth");
    platforms = objectFactory.listProperty(PlatformsParameters.class).empty();
    platformsParametersSpec =
        objectFactory.newInstance(PlatformsParametersSpec.class, objectFactory, platforms);

    PlatformsParameters platform = new PlatformsParameters();
    platform.os = "linux";
    platform.architecture = "amd64";
    platforms.add(platform);
  }

  @Input
  ListProperty<PlatformsParameters> getPlatforms() {
    return platforms;
  }

  public void platforms(Action<? super PlatformsParametersSpec> action) {
    action.execute(platformsParametersSpec);
  }

  @Input
  @Nullable
  @Optional
  public String getImage() {
    if (System.getProperty(PropertyNames.FROM_IMAGE) != null) {
      return System.getProperty(PropertyNames.FROM_IMAGE);
    }
    return image;
  }

  public void setImage(String image) {
    this.image = image;
  }

  @Input
  @Nullable
  @Optional
  public String getCredHelper() {
    if (System.getProperty(PropertyNames.FROM_CRED_HELPER) != null) {
      return System.getProperty(PropertyNames.FROM_CRED_HELPER);
    }
    return credHelper;
  }

  public void setCredHelper(String credHelper) {
    this.credHelper = credHelper;
  }

  @Nested
  @Optional
  public AuthParameters getAuth() {
    // System properties are handled in ConfigurationPropertyValidator
    return auth;
  }

  public void auth(Action<? super AuthParameters> action) {
    action.execute(auth);
  }
}
