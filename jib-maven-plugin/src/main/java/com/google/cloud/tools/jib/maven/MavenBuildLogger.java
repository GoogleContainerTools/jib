/*
 * Copyright 2018 Google Inc.
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

import com.google.cloud.tools.jib.builder.BuildLogger;
import org.apache.maven.plugin.logging.Log;

/** Implementation of {@link BuildLogger} for Maven plugins. */
class MavenBuildLogger implements BuildLogger {

  private final Log log;

  MavenBuildLogger(Log log) {
    this.log = log;
  }

  @Override
  public void debug(CharSequence charSequence) {
    log.debug(charSequence);
  }

  @Override
  public void info(CharSequence charSequence) {
    log.info(charSequence);
  }

  @Override
  public void warn(CharSequence charSequence) {
    log.warn(charSequence);
  }

  @Override
  public void error(CharSequence charSequence) {
    log.error(charSequence);
  }
}
