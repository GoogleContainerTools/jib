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
// TODO: Move to com.google.cloud.tools.jib once that package is cleaned up.

import com.google.cloud.tools.jib.configuration.credentials.Credential;
import com.google.cloud.tools.jib.configuration.credentials.CredentialRetriever;
import com.google.cloud.tools.jib.frontend.CredentialRetrieverFactory;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Builds to a container registry.
 *
 * <p>The registry portion of the image reference determines which registry to push the image to.
 * The repository portion is the namespace to push the image to. The tag is a label to easily
 * identify an image among all the images in the repository. See {@link ImageReference} for more
 * details.
 *
 * <p>When configuring credentials (via {@link #addCredential} for example), make sure the
 * credentials are valid push credentials for the repository specified via the image reference.
 */
// TODO: Add tests once JibContainerBuilder#containerize() is added.
public class RegistryImage {

  /**
   * Instantiate with the image reference to push to.
   *
   * @param imageReference the image reference
   * @return a new {@link RegistryImage}
   */
  public static RegistryImage named(ImageReference imageReference) {
    return new RegistryImage(imageReference);
  }

  /**
   * Instantiate with the image reference to push to.
   *
   * @param imageReference the image reference
   * @return a new {@link RegistryImage}
   * @throws InvalidImageReferenceException if {@code imageReference} is not a valid image reference
   */
  public static RegistryImage named(String imageReference) throws InvalidImageReferenceException {
    return named(ImageReference.parse(imageReference));
  }

  private final ImageReference imageReference;
  private List<CredentialRetriever> credentialRetrievers = new ArrayList<>();

  /** Instantiate with {@link #named}. */
  private RegistryImage(ImageReference imageReference) {
    this.imageReference = imageReference;
  }

  /**
   * Adds a username-password credential to use to push the image. This is a shorthand for {@code
   * addCredentialRetriever(() -> Optional.of(Credential.basic(username, password)))}.
   *
   * @param username the username
   * @param password the password
   * @return this
   */
  public RegistryImage addCredential(String username, String password) {
    addCredentialRetriever(() -> Optional.of(Credential.basic(username, password)));
    return this;
  }

  /**
   * Adds {@link CredentialRetriever} to fetch push credentials for the image. Credential retrievers
   * are attempted in the order in which they are specified until credentials are successfully
   * retrieved.
   *
   * <p>Example usage:
   *
   * <pre>{@code
   * .addCredentialRetriever(() -> {
   *   if (!Files.exists("secret.txt") {
   *     return Optional.empty();
   *   }
   *   try {
   *     String password = fetchPasswordFromFile("secret.txt");
   *     return Credential.basic("myaccount", password);
   *
   *   } catch (IOException ex) {
   *     throw new CredentialRetrievalException("Failed to load password", ex);
   *   }
   * })
   * }</pre>
   *
   * @param credentialRetriever the {@link CredentialRetriever} to add
   * @return this
   * @see CredentialRetrieverFactory for useful pre-defined {@link CredentialRetriever}s
   */
  public RegistryImage addCredentialRetriever(CredentialRetriever credentialRetriever) {
    credentialRetrievers.add(credentialRetriever);
    return this;
  }

  ImageReference getImageReference() {
    return imageReference;
  }

  List<CredentialRetriever> getCredentialRetrievers() {
    return Collections.unmodifiableList(credentialRetrievers);
  }
}
