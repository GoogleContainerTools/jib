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
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;

/** Allows to add {@link ExtraDirectoryParameters} objects to the list property of the same type. */
public class ExtraDirectoryParametersSpec {

  private final ListProperty<ExtraDirectoryParameters> paths;
  private final ObjectFactory objectFactory;

  /**
   * Spec init.
   *
   * @param project Project handle
   * @param paths Provider for extra dirs
   */
  @Inject
  public ExtraDirectoryParametersSpec(
      Project project, ListProperty<ExtraDirectoryParameters> paths) {
    this.objectFactory = project.getObjects();
    this.paths = paths;
  }

  /**
   * Adds a new extra directory configuration to the list.
   *
   * @param action closure representing an extra directory configuration
   */
  public void path(Action<? super ExtraDirectoryParameters> action) {
    ExtraDirectoryParameters extraDirectory =
        objectFactory.newInstance(ExtraDirectoryParameters.class);
    action.execute(extraDirectory);
    paths.add(extraDirectory);
  }

  @Input
  @Nested
  public ListProperty<ExtraDirectoryParameters> getPaths() {
    return paths;
  }
}
