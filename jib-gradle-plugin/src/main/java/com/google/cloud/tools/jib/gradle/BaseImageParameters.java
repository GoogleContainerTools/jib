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
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;

/** Object in {@link JibExtension} that configures the base image. */
public class BaseImageParameters extends ImageParameters {

  private final PlatformParametersSpec platformParametersSpec;
  private final ListProperty<PlatformParameters> platforms;

  @Inject
  public BaseImageParameters(ObjectFactory objectFactory) {
    super(
        objectFactory,
        objectFactory.newInstance(AuthParameters.class, "from.auth"),
        objectFactory.newInstance(CredHelperParameters.class, PropertyNames.FROM_CRED_HELPER));
    platforms = objectFactory.listProperty(PlatformParameters.class);
    platformParametersSpec = objectFactory.newInstance(PlatformParametersSpec.class, platforms);

    PlatformParameters amd64Linux = new PlatformParameters();
    amd64Linux.setArchitecture("amd64");
    amd64Linux.setOs("linux");
    platforms.add(amd64Linux);
  }

  @Nested
  @Optional
  public ListProperty<PlatformParameters> getPlatforms() {
    String property = System.getProperty(PropertyNames.FROM_PLATFORMS);
    if (property != null) {
      List<PlatformParameters> parsed =
          ConfigurationPropertyValidator.parseListProperty(property).stream()
              .map(PlatformParameters::of)
              .collect(Collectors.toList());
      if (!parsed.equals(platforms.get())) {
        platforms.set(parsed);
      }
    }
    return platforms;
  }

  public void platforms(Action<? super PlatformParametersSpec> action) {
    platforms.empty();
    action.execute(platformParametersSpec);
  }

  @Input
  @Nullable
  @Optional
  public String getImage() {
    return getImage(PropertyNames.FROM_IMAGE);
  }
}
