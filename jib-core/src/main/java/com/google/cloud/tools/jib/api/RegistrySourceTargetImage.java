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

import com.google.cloud.tools.jib.builder.BuildSteps;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import com.google.cloud.tools.jib.configuration.credentials.Credential;
import com.google.cloud.tools.jib.configuration.credentials.CredentialRetriever;
import com.google.cloud.tools.jib.image.ImageReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

class RegistrySourceTargetImage implements RegistryImage, SourceImage, TargetImage {

  private final ImageReference imageReference;
  private final List<CredentialRetriever> credentialRetrievers = new ArrayList<>();

  RegistrySourceTargetImage(ImageReference imageReference) {
    this.imageReference = imageReference;
  }

  @Override
  public RegistrySourceTargetImage addCredential(String username, String password) {
    addCredentialRetriever(() -> Optional.of(Credential.from(username, password)));
    return this;
  }

  @Override
  public RegistrySourceTargetImage addCredentialRetriever(CredentialRetriever credentialRetriever) {
    credentialRetrievers.add(credentialRetriever);
    return this;
  }

  @Override
  public ImageConfiguration toImageConfiguration() {
    return ImageConfiguration.builder(imageReference)
        .setCredentialRetrievers(credentialRetrievers)
        .build();
  }

  @Override
  public BuildSteps toBuildSteps(BuildConfiguration buildConfiguration) {
    return BuildSteps.forBuildToDockerRegistry(buildConfiguration);
  }
}
