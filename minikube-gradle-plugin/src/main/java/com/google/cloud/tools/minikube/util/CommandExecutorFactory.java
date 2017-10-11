/*
 * Copyright 2017 Google Inc.
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

package com.google.cloud.tools.minikube.util;

import org.gradle.api.logging.Logger;

/** {@link CommandExecutor} Factory. */
public class CommandExecutorFactory {
  private final Logger logger;

  /**
   * Creates a new factory.
   *
   * @param logger for logging messages during the command execution
   */
  public CommandExecutorFactory(Logger logger) {
    this.logger = logger;
  }

  public CommandExecutor newCommandExecutor() {
    return new CommandExecutor().setLogger(logger);
  }
}
