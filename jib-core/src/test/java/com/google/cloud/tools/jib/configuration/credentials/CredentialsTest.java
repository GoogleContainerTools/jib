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

package com.google.cloud.tools.jib.configuration.credentials;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link Credentials}. */
public class CredentialsTest {

  @Test
  public void testCredentialsHash() {
    Credentials credentialsA1 = new Credentials("username", "password");
    Credentials credentialsA2 = new Credentials("username", "password");
    Credentials credentialsB1 = new Credentials("", "");
    Credentials credentialsB2 = new Credentials("", "");

    Assert.assertEquals(credentialsA1, credentialsA2);
    Assert.assertEquals(credentialsB1, credentialsB2);
    Assert.assertNotEquals(credentialsA1, credentialsB1);
    Assert.assertNotEquals(credentialsA1, credentialsB2);

    Set<Credentials> credentialsSet =
        new HashSet<>(Arrays.asList(credentialsA1, credentialsA2, credentialsB1, credentialsB2));
    Assert.assertEquals(new HashSet<>(Arrays.asList(credentialsA2, credentialsB1)), credentialsSet);
  }
}
