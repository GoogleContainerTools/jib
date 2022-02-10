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

import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;

abstract class ImageParameters {
  protected final AuthParameters auth;
  protected Property<String> image;
  protected CredHelperParameters credHelper;

  protected ImageParameters(
      ObjectFactory objectFactory, AuthParameters auth, CredHelperParameters credHelper) {
    this.auth = auth;
    this.credHelper = credHelper;
    this.image = objectFactory.property(String.class);
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

  @Nested
  @Optional
  public CredHelperParameters getCredHelper() {
    return credHelper;
  }

  public void setCredHelper(String helper) {
    this.credHelper.setHelper(helper);
  }

  public void credHelper(Action<? super CredHelperParameters> action) {
    action.execute(credHelper);
  }

  public void setImage(String image) {
    this.image.set(image);
  }

  public void setImage(Provider<String> image) {
    this.image.set(image);
  }

  protected String getImage(String propertyName) {
    if (System.getProperty(propertyName) != null) {
      return System.getProperty(propertyName);
    }
    return image.getOrNull();
  }
}
