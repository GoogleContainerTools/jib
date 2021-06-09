/*
 * Copyright 2017 Google LLC.
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

package com.google.cloud.tools.jib.registry;

import com.google.api.client.http.HttpResponseException;
import com.google.cloud.tools.jib.registry.json.ErrorEntryTemplate;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link RegistryErrorExceptionBuilder}. */
@RunWith(MockitoJUnitRunner.class)
public class RegistryErrorExceptionBuilderTest {

  @Mock private HttpResponseException mockHttpResponseException;

  @Test
  public void testAddErrorEntry() {
    RegistryErrorExceptionBuilder builder =
        new RegistryErrorExceptionBuilder("do something", mockHttpResponseException)
            .addReason(
                new ErrorEntryTemplate(ErrorCodes.MANIFEST_INVALID.name(), "manifest invalid"))
            .addReason(new ErrorEntryTemplate(ErrorCodes.BLOB_UNKNOWN.name(), "blob unknown"))
            .addReason(
                new ErrorEntryTemplate(ErrorCodes.MANIFEST_UNKNOWN.name(), "manifest unknown"))
            .addReason(new ErrorEntryTemplate(ErrorCodes.TAG_INVALID.name(), "tag invalid"))
            .addReason(
                new ErrorEntryTemplate(
                    ErrorCodes.MANIFEST_UNVERIFIED.name(), "manifest unverified"))
            .addReason(
                new ErrorEntryTemplate(ErrorCodes.UNSUPPORTED.name(), "some other error happened"))
            .addReason(new ErrorEntryTemplate("unknown", "some unknown error happened"));

    try {
      throw builder.build();

    } catch (RegistryErrorException ex) {
      Assert.assertEquals(
          "Tried to do something but failed because: manifest invalid (something went wrong), blob "
              + "unknown (something went wrong), manifest unknown, tag invalid, manifest "
              + "unverified, other: some other error happened, unknown error code: unknown (some "
              + "unknown error happened)",
          ex.getMessage());
    }
  }
}
