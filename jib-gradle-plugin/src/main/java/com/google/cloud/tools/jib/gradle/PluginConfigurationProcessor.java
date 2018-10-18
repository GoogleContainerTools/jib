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
import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.plugins.common.ProjectProperties;
import java.util.logging.Level;
import org.gradle.api.GradleException;
import org.gradle.internal.logging.events.LogEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.slf4j.OutputEventListenerBackedLoggerContext;
import org.slf4j.LoggerFactory;

/** Configures and provides builders for the image building tasks. */
// TODO: remove and use NPluginConfigurationProcess
class PluginConfigurationProcessor {

  /**
   * Gets the value of the {@code container.appRoot} parameter. Throws {@link GradleException} if it
   * is not an absolute path in Unix-style.
   *
   * @param jibExtension the {@link JibExtension} providing the configuration data
   * @return the app root value
   * @throws GradleException if the app root is not an absolute path in Unix-style
   */
  static AbsoluteUnixPath getAppRootChecked(JibExtension jibExtension) {
    String appRoot = jibExtension.getContainer().getAppRoot();
    try {
      return AbsoluteUnixPath.get(appRoot);
    } catch (IllegalArgumentException ex) {
      throw new GradleException("container.appRoot is not an absolute Unix-style path: " + appRoot);
    }
  }

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

  static void configureContainerizer(
      Containerizer containerizer, JibExtension jibExtension, ProjectProperties projectProperties) {
    containerizer
        .setToolName(GradleProjectProperties.TOOL_NAME)
        .setEventHandlers(projectProperties.getEventHandlers())
        .setAllowInsecureRegistries(jibExtension.getAllowInsecureRegistries())
        .setBaseImageLayersCache(Containerizer.DEFAULT_BASE_CACHE_DIRECTORY)
        .setApplicationLayersCache(projectProperties.getCacheDirectory());

    jibExtension.getTo().getTags().forEach(containerizer::withAdditionalTag);

    if (jibExtension.getUseOnlyProjectCache()) {
      containerizer.setBaseImageLayersCache(projectProperties.getCacheDirectory());
    }
  }
}
