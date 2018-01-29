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

import org.apache.maven.plugin.MojoExecutionException;

class MojoExceptionBuilder {

  private Throwable cause;
  private String suggestion;

  MojoExceptionBuilder(Throwable cause) {
    this.cause = cause;
  }

  MojoExceptionBuilder suggest(String suggestion) {
    this.suggestion = suggestion;
    return this;
  }

  MojoExecutionException build() {
    StringBuilder message = new StringBuilder("Build image failed");
    if (suggestion != null) {
      message.append("\nPerhaps you should ");
      message.append(suggestion);
    }
    return new MojoExecutionException(message.toString(), cause);
  }
}
