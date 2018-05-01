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

package com.google.cloud.tools.jib.frontend;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link HelpfulExceptionBuilder}. */
@RunWith(MockitoJUnitRunner.class)
public class HelpfulExceptionBuilderTest {

  private final HelpfulExceptionBuilder testHelpfulExceptionBuilder =
      new HelpfulExceptionBuilder<Exception>("message header") {
        @Override
        protected Exception makeException(String message, Throwable cause) {
          return new Exception(message, cause);
        }
      };

  @Mock private Throwable mockThrowable;

  @Test
  public void testWithNoHelp() {
    Exception exception = testHelpfulExceptionBuilder.withNoHelp(mockThrowable);
    Assert.assertEquals("message header", exception.getMessage());
    Assert.assertEquals(mockThrowable, exception.getCause());
  }

  @Test
  public void testWithSuggestion() {
    Exception exception = testHelpfulExceptionBuilder.withSuggestion(mockThrowable, "do something");
    Assert.assertEquals("message header, perhaps you should do something", exception.getMessage());
    Assert.assertEquals(mockThrowable, exception.getCause());
  }
}
