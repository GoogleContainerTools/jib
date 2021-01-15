/*
 * Copyright 2020 Google LLC.
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

import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;

/** Allows to add {@link PlatformParameters} objects to the list property of the same type. */
public class PlatformParametersSpec {

  private final ObjectFactory objectFactory;
  private final ListProperty<PlatformParameters> platforms;

  @Inject
  public PlatformParametersSpec(
      ObjectFactory objectFactory, ListProperty<PlatformParameters> platforms) {
    this.objectFactory = objectFactory;
    this.platforms = platforms;
  }

  /**
   * Adds a new platform configuration to the platforms list.
   *
   * @param action closure representing a platform configuration
   */
  public void platform(Action<? super PlatformParameters> action) {
    PlatformParameters platform = objectFactory.newInstance(PlatformParameters.class);
    action.execute(platform);
    platforms.add(platform);
  }
}
