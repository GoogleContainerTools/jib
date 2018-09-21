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

package com.google.cloud.tools.jib.builder;

import com.google.cloud.tools.jib.JibLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Implementation of {@link JibLogger} for testing purposes. */
public class TestJibLogger implements JibLogger {

  private static final Logger LOGGER = LoggerFactory.getLogger(TestJibLogger.class);

  @Override
  public void debug(CharSequence message) {
    LOGGER.debug(message.toString());
  }

  @Override
  public void info(CharSequence message) {
    LOGGER.info(message.toString());
  }

  @Override
  public void warn(CharSequence message) {
    LOGGER.warn(message.toString());
  }

  @Override
  public void error(CharSequence message) {
    LOGGER.error(message.toString());
  }

  @Override
  public void lifecycle(CharSequence message) {
    info(message);
  }
}
