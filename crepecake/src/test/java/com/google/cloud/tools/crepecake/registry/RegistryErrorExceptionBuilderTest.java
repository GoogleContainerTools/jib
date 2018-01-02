/*
 * Copyright 2017 Google Inc.
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

package com.google.cloud.tools.crepecake.registry;

import com.google.api.client.http.HttpResponseException;
import com.google.cloud.tools.crepecake.registry.json.ErrorEntryTemplate;
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
        new RegistryErrorExceptionBuilder("do something", mockHttpResponseException);
    String errorCode = ErrorCodes.MANIFEST_UNKNOWN.name();
    ErrorEntryTemplate errorEntryTemplateManifestUnknown =
        new ErrorEntryTemplate(errorCode, "some error happened");
    ErrorEntryTemplate errorEntryTemplateOther =
        new ErrorEntryTemplate(ErrorCodes.UNSUPPORTED.name(), "some other error happened");
    ErrorEntryTemplate errorEntryTemplateUnknown =
        new ErrorEntryTemplate("unknown", "some unknown error happened");

    builder.addErrorEntry(errorEntryTemplateManifestUnknown);
    builder.addErrorEntry(errorEntryTemplateOther);
    builder.addErrorEntry(errorEntryTemplateUnknown);

    try {
      throw builder.build();
    } catch (RegistryErrorException ex) {
      Assert.assertEquals(
          "Tried to do something but failed because: some error happened, other: some other error happened, unknown: some unknown error happened",
          ex.getMessage());
    }
  }
}
