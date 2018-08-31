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

package com.google.cloud.tools.jib.maven;

import com.google.cloud.tools.jib.JibLogger;
import org.apache.maven.plugin.logging.Log;

/** Implementation of {@link JibLogger} for Maven plugins. */
class MavenJibLogger implements JibLogger {

  /** Disables annoying Apache HTTP client logging. */
  static void disableHttpLogging() {
    System.setProperty(
        "org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
    System.setProperty("org.apache.commons.logging.simplelog.defaultlog", "error");
  }

  private final Log log;

  MavenJibLogger(Log log) {
    this.log = log;
  }

  @Override
  public void debug(CharSequence charSequence) {
    log.debug(charSequence);
  }

  @Override
  public void info(CharSequence charSequence) {
    // Use lifecycle for progress-indicating messages.
    debug(charSequence);
  }

  @Override
  public void warn(CharSequence charSequence) {
    log.warn(charSequence);
  }

  @Override
  public void error(CharSequence charSequence) {
    log.error(charSequence);
  }

  @Override
  public void lifecycle(CharSequence message) {
    log.info(message);
  }
}
