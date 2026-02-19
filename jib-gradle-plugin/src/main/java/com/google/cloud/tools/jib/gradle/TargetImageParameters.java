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

import com.google.cloud.tools.jib.plugins.common.ConfigurationPropertyValidator;
import com.google.cloud.tools.jib.plugins.common.PropertyNames;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;

/** Object in {@link JibExtension} that configures the target image. */
public class TargetImageParameters {

  private final AuthParameters auth;
  private final Property<String> image;
  private final SetProperty<String> tags;
  private final CredHelperParameters credHelper;
  private boolean enablePlatformTags;

  @Inject
  public TargetImageParameters(ObjectFactory objectFactory) {
    auth = objectFactory.newInstance(AuthParameters.class, "to.auth");
    image = objectFactory.property(String.class);
    tags = objectFactory.setProperty(String.class).empty();
    credHelper =
        objectFactory.newInstance(CredHelperParameters.class, PropertyNames.TO_CRED_HELPER);
  }

  @Input
  @Nullable
  @Optional
  public String getImage() {
    if (System.getProperty(PropertyNames.TO_IMAGE) != null) {
      return System.getProperty(PropertyNames.TO_IMAGE);
    }
    return image.getOrNull();
  }

  public void setImage(String image) {
    this.image.set(image);
  }

  public void setImage(Provider<String> image) {
    this.image.set(image);
  }

  @Input
  @Optional
  public Set<String> getTags() {
    String property = System.getProperty(PropertyNames.TO_TAGS);
    Set<String> tagsValue;
    if (property != null) {
      tagsValue = ImmutableSet.copyOf(ConfigurationPropertyValidator.parseListProperty(property));
    } else {
      try {
        tagsValue = tags.get();
      } catch (NullPointerException ex) {
        throw new IllegalArgumentException("jib.to.tags contains null tag");
      }
    }
    if (tagsValue.stream().anyMatch(str -> str.isEmpty())) {
      throw new IllegalArgumentException("jib.to.tags contains empty tag");
    }
    return tagsValue;
  }

  public void setTags(List<String> tags) {
    this.tags.set(tags);
  }

  public void setTags(Set<String> tags) {
    this.tags.set(tags);
  }

  public void setTags(Provider<Set<String>> tags) {
    this.tags.set(tags);
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

  @Nested
  @Optional
  public AuthParameters getAuth() {
    // System properties are handled in ConfigurationPropertyValidator
    return auth;
  }

  public void auth(Action<? super AuthParameters> action) {
    action.execute(auth);
  }

  @Input
  public boolean getEnablePlatformTags() {
    if (System.getProperty(PropertyNames.ENABLE_PLATFORM_TAGS) != null) {
      return Boolean.parseBoolean(System.getProperty(PropertyNames.ENABLE_PLATFORM_TAGS));
    }
    return enablePlatformTags;
  }

  public void setEnablePlatformTags(boolean expand) {
    enablePlatformTags = expand;
  }
}
