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

package com.google.cloud.tools.jib.configuration;

import com.google.cloud.tools.jib.api.CredentialRetriever;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.util.List;

/** Immutable configuration options for an image reference with credentials. */
public class ImageConfiguration {

  /** Builder for instantiating an {@link ImageConfiguration}. */
  public static class Builder {

    private ImageReference imageReference;
    private ImmutableList<CredentialRetriever> credentialRetrievers = ImmutableList.of();

    /**
     * Sets the providers for registry credentials. The order determines the priority in which the
     * retrieval methods are attempted.
     *
     * @param credentialRetrievers the list of {@link CredentialRetriever}s
     * @return this
     */
    public Builder setCredentialRetrievers(List<CredentialRetriever> credentialRetrievers) {
      Preconditions.checkArgument(
          !credentialRetrievers.contains(null), "credential retriever list contains null elements");
      this.credentialRetrievers = ImmutableList.copyOf(credentialRetrievers);
      return this;
    }

    /**
     * Builds the {@link ImageConfiguration}.
     *
     * @return the corresponding {@link ImageConfiguration}
     */
    public ImageConfiguration build() {
      return new ImageConfiguration(imageReference, credentialRetrievers);
    }

    private Builder(ImageReference imageReference) {
      this.imageReference = imageReference;
    }
  }

  /**
   * Constructs a builder for an {@link ImageConfiguration}.
   *
   * @param imageReference the image reference, which is a required field
   * @return the builder
   */
  public static Builder builder(ImageReference imageReference) {
    return new Builder(imageReference);
  }

  private final ImageReference image;
  private final ImmutableList<CredentialRetriever> credentialRetrievers;

  private ImageConfiguration(
      ImageReference image, ImmutableList<CredentialRetriever> credentialRetrievers) {
    this.image = image;
    this.credentialRetrievers = credentialRetrievers;
  }

  public ImageReference getImage() {
    return image;
  }

  public String getImageRegistry() {
    return image.getRegistry();
  }

  public String getImageRepository() {
    return image.getRepository();
  }

  public String getImageTag() {
    return image.getTag();
  }

  public ImmutableList<CredentialRetriever> getCredentialRetrievers() {
    return credentialRetrievers;
  }
}
