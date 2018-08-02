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
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Test for {@link BuildImageTask}.
 *
 * <p>TODO: This only tests the {@link BuildImageTask#getImageAuthorization(JibLogger, String,
 * AuthParameters)} method, which is copy-pasted between the 3 build tasks. When we refactor, we'll
 * need to move this test.
 */
@RunWith(MockitoJUnitRunner.class)
public class BuildImageTaskTest {

  @Mock private JibLogger mockLogger;

  @Test
  public void testGetImageAuthorization() {

    // Auth set
    AuthParameters auth = new AuthParameters();
    auth.setUsername("vwxyz");
    auth.setPassword("98765");
    Authorization expected = Authorizations.withBasicCredentials("vwxyz", "98765");
    Authorization actual = BuildImageTask.getImageAuthorization(mockLogger, "to", auth);
    Assert.assertNotNull(actual);
    Assert.assertEquals(expected.toString(), actual.toString());
    Mockito.verify(mockLogger, Mockito.never()).warn(Mockito.any());

    // Auth completely missing
    auth = new AuthParameters();
    actual = BuildImageTask.getImageAuthorization(mockLogger, "to", auth);
    Assert.assertNull(actual);

    // Password missing
    auth = new AuthParameters();
    auth.setUsername("vwxyz");
    actual = BuildImageTask.getImageAuthorization(mockLogger, "to", auth);
    Assert.assertNull(actual);
    Mockito.verify(mockLogger).warn("jib.to.auth.password is null; ignoring jib.to.auth section.");

    // Username missing
    auth = new AuthParameters();
    auth.setPassword("98765");
    actual = BuildImageTask.getImageAuthorization(mockLogger, "to", auth);
    Assert.assertNull(actual);
    Mockito.verify(mockLogger).warn("jib.to.auth.username is null; ignoring jib.to.auth section.");
  }
}
