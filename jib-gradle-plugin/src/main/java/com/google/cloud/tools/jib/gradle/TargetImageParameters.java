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

import java.util.Collections;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

/** {@link ImageParameters} that configure the target image. */
public class TargetImageParameters implements ImageParameters {

  private final AuthParameters auth;

  @Nullable private String image;
  private Set<String> tags = Collections.emptySet();
  @Nullable private String credHelper;

  @Inject
  public TargetImageParameters(ObjectFactory objectFactory, String imageDescriptor) {
    auth = objectFactory.newInstance(AuthParameters.class, imageDescriptor + ".auth");
  }

  @Nullable
  @Override
  public String getImage() {
    return image;
  }

  @Override
  public void setImage(String image) {
    this.image = image;
  }

  @Input
  @Optional
  public Set<String> getTags() {
    return tags;
  }

  public void setTags(Set<String> tags) {
    this.tags = tags;
  }

  @Nullable
  @Override
  public String getCredHelper() {
    return credHelper;
  }

  @Override
  public void setCredHelper(String credHelper) {
    this.credHelper = credHelper;
  }

  @Override
  public AuthParameters getAuth() {
    return auth;
  }

  @Override
  public void auth(Action<? super AuthParameters> action) {
    action.execute(auth);
  }
}
