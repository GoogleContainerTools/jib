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

import com.google.cloud.tools.jib.JibLogger;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.http.Authorizations;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link ImageParameters}. */
@RunWith(MockitoJUnitRunner.class)
public class ImageParametersTest {

  @Mock private JibLogger mockLogger;

  @Test
  public void testGetImageAuthorization() {
    Project fakeProject = ProjectBuilder.builder().build();

    ImageParameters imageParameters = new ImageParameters(fakeProject.getObjects());
    imageParameters.auth(
        auth -> {
          auth.setUsername("vwxyz");
          auth.setPassword("98765");
        });

    // Auth set
    Authorization expected = Authorizations.withBasicCredentials("vwxyz", "98765");
    Authorization actual = imageParameters.getImageAuthorization(mockLogger, "to");
    Assert.assertNotNull(actual);
    Assert.assertEquals(expected.toString(), actual.toString());
    Mockito.verify(mockLogger, Mockito.never()).warn(Mockito.any());

    // Auth completely missing
    imageParameters = new ImageParameters(fakeProject.getObjects());
    actual = imageParameters.getImageAuthorization(mockLogger, "to");
    Assert.assertNull(actual);

    // Password missing
    imageParameters = new ImageParameters(fakeProject.getObjects());
    imageParameters.auth(auth -> auth.setUsername("vwxyz"));
    actual = imageParameters.getImageAuthorization(mockLogger, "to");
    Assert.assertNull(actual);
    Mockito.verify(mockLogger).warn("jib.to.auth.password is null; ignoring jib.to.auth section.");

    // Username missing
    imageParameters = new ImageParameters(fakeProject.getObjects());
    imageParameters.auth(auth -> auth.setPassword("98765"));
    actual = imageParameters.getImageAuthorization(mockLogger, "to");
    Assert.assertNull(actual);
    Mockito.verify(mockLogger).warn("jib.to.auth.username is null; ignoring jib.to.auth section.");
  }
}
