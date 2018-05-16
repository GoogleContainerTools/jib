/*
 * Copyright 2018 Google LLC. All Rights Reserved.
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

import com.google.cloud.tools.jib.builder.BuildLogger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.gradle.api.logging.Logger;

/** Implementation of {@link BuildLogger} for Gradle plugins. */
// We don't care about the return values of the logging futures.
@SuppressWarnings("FutureReturnValueIgnored")
class GradleBuildLogger implements BuildLogger {

  /** This executor keeps all log messages in order. */
  private static final ExecutorService executorService = Executors.newSingleThreadExecutor();

  private final Logger logger;

  GradleBuildLogger(Logger logger) {
    this.logger = logger;
  }

  @Override
  public void lifecycle(CharSequence message) {
    executorService.submit(() -> logger.lifecycle(message.toString()));
  }

  @Override
  public void debug(CharSequence message) {
    executorService.submit(() -> logger.debug(message.toString()));
  }

  @Override
  public void info(CharSequence message) {
    executorService.submit(() -> logger.info(message.toString()));
  }

  @Override
  public void warn(CharSequence message) {
    executorService.submit(() -> logger.warn("warning: " + message));
  }

  @Override
  public void error(CharSequence message) {
    executorService.submit(() -> logger.error(message.toString()));
  }
}
