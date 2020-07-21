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
  @Nullable private String image;
  @Nullable private String credHelper;
  private final PlatformParametersSpec platformParametersSpec;
  private final ListProperty<PlatformParameters> platforms;

  @Inject
  public BaseImageParameters(ObjectFactory objectFactory) {
    auth = objectFactory.newInstance(AuthParameters.class, "from.auth");
    platforms = objectFactory.listProperty(PlatformParameters.class).empty();
    platformParametersSpec =
        objectFactory.newInstance(PlatformParametersSpec.class, objectFactory, platforms);

    PlatformParameters platform = new PlatformParameters();
    platform.setOs("linux");
    platform.setArchitecture("amd64");
    platforms.add(platform);
  }

  @Nested
  @Optional
  public ListProperty<PlatformParameters> getPlatforms() {
    return platforms;
  }

  public void platforms(Action<? super PlatformParametersSpec> action) {
    action.execute(platformParametersSpec);
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
