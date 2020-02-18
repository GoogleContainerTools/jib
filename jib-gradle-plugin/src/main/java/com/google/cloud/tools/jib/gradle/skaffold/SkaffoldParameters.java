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

package com.google.cloud.tools.jib.gradle.skaffold;

import com.google.common.base.Preconditions;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Nested;

/** Skaffold specific JibExtension parameters. */
public class SkaffoldParameters {

  private final SkaffoldWatchParameters watch;
  private final SkaffoldSyncParameters sync;

  @Inject
  public SkaffoldParameters(Project project) {
    ObjectFactory objectFactory = project.getObjects();

    watch = objectFactory.newInstance(SkaffoldWatchParameters.class, project);
    sync = objectFactory.newInstance(SkaffoldSyncParameters.class, project);

    Preconditions.checkNotNull(watch);
  }

  public void watch(Action<? super SkaffoldWatchParameters> action) {
    action.execute(watch);
  }

  public void sync(Action<? super SkaffoldSyncParameters> action) {
    action.execute(sync);
  }

  @Nested
  public SkaffoldWatchParameters getWatch() {
    return watch;
  }

  @Nested
  public SkaffoldSyncParameters getSync() {
    return sync;
  }
}
