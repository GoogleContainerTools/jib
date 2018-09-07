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

package com.google.cloud.tools.jib.api;

import com.google.cloud.tools.jib.configuration.credentials.CredentialRetriever;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import javax.annotation.Nullable;

/**
 * Builds to a container registry.
 *
 * The registry portion of the image reference determines which registry to push the image to. The repository portion is the namespace to push the image to. The tag is a label to easily identify an image among all the images in the repository. See {@link ImageReference} for more details.
 *
 * When configuring credentials (via {@link #setCredential} for example), make sure the credentials are valid push credentials for the repository specified via the image reference.
 */
public class RegistryImage {

  /**
   * Instantiate with the image reference to push to.
   *
   * @param imageReference the image reference
   * @return a new {@link DockerDaemonImage}
   */
  public static RegistryImage named(ImageReference imageReference) {
    return new RegistryImage(imageReference);
  }

  /**
   * Instantiate with the image reference to push to.
   *
   * @param imageReference the image reference
   * @return a new {@link DockerDaemonImage}
   */
  public static RegistryImage named(String imageReference) throws InvalidImageReferenceException {
    return named(ImageReference.parse(imageReference));
  }

  private final ImageReference imageReference;
  @Nullable
  private CredentialRetrievers credentialRetrievers;

  /** Instantiate with {@link #named}. */
  private RegistryImage(ImageReference imageReference) {
    this.imageReference = imageReference;
  }

  RegistryImage setCredential(String username, String password) {

  }

  RegistryImage setCredentialRetrievers(CredentialRetrievers credentialRetrievers) {

  }

  RegistryImage addCredentialRetriever(CredentialRetriever credentialRetriever) {

  }
}
