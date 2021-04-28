/*
 * Copyright 2021 Google LLC.
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.tools.jib.ProjectInfo;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.plugins.common.ProjectProperties;
import com.google.common.util.concurrent.Futures;
import java.util.Optional;
import java.util.concurrent.Future;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MojoCommonTest {

  @Mock private ProjectProperties mockProjectProperties;

  @Test
  public void testFinishUpdateChecker_correctMessageLogged() {
    when(mockProjectProperties.getToolName()).thenReturn("tool-name");
    when(mockProjectProperties.getToolVersion()).thenReturn("2.0.0");
    Future<Optional<String>> updateCheckFuture = Futures.immediateFuture(Optional.of("2.1.0"));
    MojoCommon.finishUpdateChecker(mockProjectProperties, updateCheckFuture);

    verify(mockProjectProperties)
        .log(
            LogEvent.lifecycle(
                "\u001B[33mA new version of tool-name (2.1.0) is available (currently using 2.0.0). "
                    + "Update your build configuration to use the latest features and fixes!\u001B[0m"));
    verify(mockProjectProperties)
        .log(
            LogEvent.lifecycle(
                "\u001B[33m"
                    + ProjectInfo.GITHUB_URL
                    + "/blob/master/jib-maven-plugin/CHANGELOG.md\u001B[0m"));
    verify(mockProjectProperties)
        .log(
            LogEvent.lifecycle(
                "Please see "
                    + ProjectInfo.GITHUB_URL
                    + "/blob/master/docs/privacy.md for info on disabling this update check."));
  }
}
