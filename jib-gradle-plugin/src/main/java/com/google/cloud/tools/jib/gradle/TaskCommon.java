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

import com.google.api.client.http.HttpTransport;
import com.google.cloud.tools.jib.plugins.common.RawConfiguration;
import java.util.logging.Level;
import org.gradle.api.Project;
import org.gradle.internal.logging.events.LogEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.slf4j.OutputEventListenerBackedLoggerContext;
import org.slf4j.LoggerFactory;

/** Collection of common methods to share between Gradle tasks. */
class TaskCommon {

  /** Disables annoying Apache HTTP client logging. */
  static void disableHttpLogging() {
    // Disables Apache HTTP client logging.
    OutputEventListenerBackedLoggerContext context =
        (OutputEventListenerBackedLoggerContext) LoggerFactory.getILoggerFactory();
    OutputEventListener defaultOutputEventListener = context.getOutputEventListener();
    context.setOutputEventListener(
        event -> {
          LogEvent logEvent = (LogEvent) event;
          if (!logEvent.getCategory().contains("org.apache")) {
            defaultOutputEventListener.onOutput(event);
          }
        });

    // Disables Google HTTP client logging.
    java.util.logging.Logger.getLogger(HttpTransport.class.getName()).setLevel(Level.OFF);
  }

  static boolean isWarContainerization(Project project, RawConfiguration rawConfiguration) {
    if (!rawConfiguration.getPackagingOverride().isPresent()) {
      return GradleProjectProperties.getWarTask(project) != null;
    }
    return "war".equals(rawConfiguration.getPackagingOverride().get());
  }

  private TaskCommon() {}
}
