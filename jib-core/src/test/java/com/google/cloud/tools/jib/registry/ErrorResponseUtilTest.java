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

package com.google.cloud.tools.jib.registry;

import com.google.cloud.tools.jib.http.ResponseException;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Test for {@link ErrorReponseUtil}. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ErrorResponseUtilTest {

  @Mock private ResponseException responseException;

  @Test
  void testGetErrorCode_knownErrorCode() throws ResponseException {
    Mockito.when(responseException.getContent())
        .thenReturn(
            "{\"errors\":[{\"code\":\"MANIFEST_INVALID\",\"message\":\"manifest invalid\",\"detail\":{}}]}");

    Assert.assertSame(
        ErrorCodes.MANIFEST_INVALID, ErrorResponseUtil.getErrorCode(responseException));
  }

  /** An unknown {@link ErrorCodes} should cause original exception to be rethrown. */
  @Test
  void testGetErrorCode_unknownErrorCode() {
    Mockito.when(responseException.getContent())
        .thenReturn(
            "{\"errors\":[{\"code\":\"INVALID_ERROR_CODE\",\"message\":\"invalid code\",\"detail\":{}}]}");
    try {
      ErrorResponseUtil.getErrorCode(responseException);
      Assert.fail();
    } catch (ResponseException ex) {
      Assert.assertSame(responseException, ex);
    }
  }

  /** Multiple error objects should cause original exception to be rethrown. */
  @Test
  void testGetErrorCode_multipleErrors() {
    Mockito.when(responseException.getContent())
        .thenReturn(
            "{\"errors\":["
                + "{\"code\":\"MANIFEST_INVALID\",\"message\":\"message 1\",\"detail\":{}},"
                + "{\"code\":\"TAG_INVALID\",\"message\":\"message 2\",\"detail\":{}}"
                + "]}");
    try {
      ErrorResponseUtil.getErrorCode(responseException);
      Assert.fail();
    } catch (ResponseException ex) {
      Assert.assertSame(responseException, ex);
    }
  }

  /** An non-error object should cause original exception to be rethrown. */
  @Test
  void testGetErrorCode_invalidErrorObject() {
    Mockito.when(responseException.getContent())
        .thenReturn("{\"type\":\"other\",\"message\":\"some other object\"}");
    try {
      ErrorResponseUtil.getErrorCode(responseException);
      Assert.fail();
    } catch (ResponseException ex) {
      Assert.assertSame(responseException, ex);
    }
  }
}
