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

package com.google.cloud.tools.jib.configuration;

import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.registry.credentials.RegistryCredentials;
import javax.annotation.Nullable;

/** Immutable configuration options for an image reference with credentials. */
public class ImageConfiguration {

  /** Builder for instantiating an {@link ImageConfiguration}. */
  public static class Builder {

    @Nullable private ImageReference imageReference;
    @Nullable private String credentialHelper;
    @Nullable private RegistryCredentials knownRegistryCredentials;

    /**
     * Sets the image reference.
     *
     * @param imageReference the image reference containing the registry, repository, and tag.
     * @return this
     */
    public Builder setImage(@Nullable ImageReference imageReference) {
      this.imageReference = imageReference;
      return this;
    }

    /**
     * Sets the credential helper name used for authenticating with the image's registry.
     *
     * @param credentialHelper the credential helper's suffix.
     * @return this
     */
    public Builder setCredentialHelper(@Nullable String credentialHelper) {
      this.credentialHelper = credentialHelper;
      return this;
    }

    /**
     * Sets known credentials used for authenticating with the image's registry.
     *
     * @param knownRegistryCrendentials the credentials.
     * @return this
     */
    public Builder setKnownRegistryCredentials(
        @Nullable RegistryCredentials knownRegistryCrendentials) {
      this.knownRegistryCredentials = knownRegistryCrendentials;
      return this;
    }

    /**
     * Builds the {@link ImageConfiguration}.
     *
     * @return the corresponding {@link ImageConfiguration}
     */
    public ImageConfiguration build() {
      return new ImageConfiguration(imageReference, credentialHelper, knownRegistryCredentials);
    }

    private Builder() {}
  }

  public static Builder builder() {
    return new Builder();
  }

  @Nullable private final ImageReference image;
  @Nullable private final String credentialHelper;
  @Nullable private final RegistryCredentials knownRegistryCredentials;

  ImageConfiguration() {
    this(null, null, null);
  }

  private ImageConfiguration(
      @Nullable ImageReference image,
      @Nullable String credentialHelper,
      @Nullable RegistryCredentials knownRegistryCredentials) {
    this.image = image;
    this.credentialHelper = credentialHelper;
    this.knownRegistryCredentials = knownRegistryCredentials;
  }

  @Nullable
  ImageReference getImage() {
    return image;
  }

  @Nullable
  String getCredentialHelper() {
    return credentialHelper;
  }

  @Nullable
  RegistryCredentials getKnownRegistryCredentials() {
    return knownRegistryCredentials;
  }
}
