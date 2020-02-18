/*
 * Copyright 2019 Google LLC.
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

import com.google.cloud.tools.jib.gradle.JibPlugin;
import com.google.common.base.Strings;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;

/**
 * This internal Skaffold-related goal checks that the Jib plugin version is within some specified
 * range. It is only required so that older versions of Jib (prior to the introduction of the {@code
 * jib.requiredVersion} property) will error in such a way that it indicates the jib version is out
 * of date. This goal can be removed once there are no users of Jib prior to 1.4.0.
 *
 * <p>Expected use: {@code ./gradlew _skaffoldFailIfJibOutOfDate -Djib.requiredVersion='[1.4,2)'
 * jibDockerBuild --image=xxx}
 */
public class CheckJibVersionTask extends DefaultTask {

  /** Task Action, check if jib and skaffold versions are compatible. */
  @TaskAction
  public void checkVersion() {
    if (Strings.isNullOrEmpty(System.getProperty(JibPlugin.REQUIRED_VERSION_PROPERTY_NAME))) {
      throw new GradleException(
          JibPlugin.SKAFFOLD_CHECK_REQUIRED_VERSION_TASK_NAME
              + " requires "
              + JibPlugin.REQUIRED_VERSION_PROPERTY_NAME
              + " to be set");
    }
    // no-op as Jib version compatibility is actually checked in JibPlugin
  }
}
