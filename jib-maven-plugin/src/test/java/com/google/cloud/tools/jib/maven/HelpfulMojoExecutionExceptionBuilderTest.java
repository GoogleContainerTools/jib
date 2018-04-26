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

package com.google.cloud.tools.jib.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link HelpfulMojoExecutionExceptionBuilder}. */
@RunWith(MockitoJUnitRunner.class)
public class HelpfulMojoExecutionExceptionBuilderTest {

  @Mock Throwable mockThrowable;

  @Test
  public void testWithNoHelp() {
    HelpfulMojoExecutionExceptionBuilder helpfulMojoExecutionExceptionBuilder =
        new HelpfulMojoExecutionExceptionBuilder("message header");
    MojoExecutionException mojoExecutionException =
        helpfulMojoExecutionExceptionBuilder.withNoHelp(mockThrowable);
    Assert.assertEquals("message header", mojoExecutionException.getMessage());
    Assert.assertEquals(mockThrowable, mojoExecutionException.getCause());
  }

  @Test
  public void testWithSuggestion() {
    HelpfulMojoExecutionExceptionBuilder helpfulMojoExecutionExceptionBuilder =
        new HelpfulMojoExecutionExceptionBuilder("another message header");
    MojoExecutionException mojoExecutionException =
        helpfulMojoExecutionExceptionBuilder.withSuggestion(mockThrowable, "do something");
    Assert.assertEquals(
        "another message header, perhaps you should do something",
        mojoExecutionException.getMessage());
    Assert.assertEquals(mockThrowable, mojoExecutionException.getCause());
  }
}
