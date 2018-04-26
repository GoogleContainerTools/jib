/*
 * Copyright 2018 Google LLC. All rights reserved.
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

import com.google.cloud.tools.jib.frontend.HelpfulExceptionBuilder;
import org.gradle.api.GradleException;

/** Builds {@link GradleException} that provides a suggestion on how to fix the error. */
class HelpfulGradleExceptionBuilder extends HelpfulExceptionBuilder<GradleException> {

  /** @param messageHeader the initial message text for the exception message */
  HelpfulGradleExceptionBuilder(String messageHeader) {
    super(messageHeader);
  }

  @Override
  protected GradleException makeException(String message, Throwable cause) {
    return new GradleException(message, cause);
  }
}
