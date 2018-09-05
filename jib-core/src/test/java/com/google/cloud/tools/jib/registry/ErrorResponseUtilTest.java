/*
 * Copyright 2018 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.tools.jib.registry;

import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import org.apache.http.HttpStatus;
import org.junit.Assert;
import org.junit.Test;

/** Test for {@link ErrorReponseUtil}. */
public class ErrorResponseUtilTest {

  @Test
  public void testGetErrorCode_knownErrorCode() throws HttpResponseException {
    HttpResponseException httpResponseException =
        new HttpResponseException.Builder(
                HttpStatus.SC_BAD_REQUEST, "Bad Request", new HttpHeaders())
            .setContent(
                "{\"errors\":[{\"code\":\"MANIFEST_INVALID\",\"message\":\"manifest invalid\",\"detail\":{}}]}")
            .build();

    Assert.assertSame(
        ErrorCodes.MANIFEST_INVALID, ErrorResponseUtil.getErrorCode(httpResponseException));
  }

  /** An unknown {@link ErrorCodes} should cause original exception to be rethrown. */
  @Test
  public void testGetErrorCode_unknownErrorCode() {
    HttpResponseException httpResponseException =
        new HttpResponseException.Builder(
                HttpStatus.SC_BAD_REQUEST, "Bad Request", new HttpHeaders())
            .setContent(
                "{\"errors\":[{\"code\":\"INVALID_ERROR_CODE\",\"message\":\"invalid code\",\"detail\":{}}]}")
            .build();
    try {
      ErrorResponseUtil.getErrorCode(httpResponseException);
      Assert.fail();
    } catch (HttpResponseException ex) {
      Assert.assertSame(httpResponseException, ex);
    }
  }

  /** Multiple error objects should cause original exception to be rethrown. */
  @Test
  public void testGetErrorCode_multipleErrors() {
    HttpResponseException httpResponseException =
        new HttpResponseException.Builder(
                HttpStatus.SC_BAD_REQUEST, "Bad Request", new HttpHeaders())
            .setContent(
                "{\"errors\":["
                    + "{\"code\":\"MANIFEST_INVALID\",\"message\":\"message 1\",\"detail\":{}},"
                    + "{\"code\":\"TAG_INVALID\",\"message\":\"message 2\",\"detail\":{}}"
                    + "]}")
            .build();
    try {
      ErrorResponseUtil.getErrorCode(httpResponseException);
      Assert.fail();
    } catch (HttpResponseException ex) {
      Assert.assertSame(httpResponseException, ex);
    }
  }

  /** An non-error object should cause original exception to be rethrown. */
  @Test
  public void testGetErrorCode_invalidErrorObject() {
    HttpResponseException httpResponseException =
        new HttpResponseException.Builder(
                HttpStatus.SC_BAD_REQUEST, "Bad Request", new HttpHeaders())
            .setContent("{\"type\":\"other\",\"message\":\"some other object\"}")
            .build();
    try {
      ErrorResponseUtil.getErrorCode(httpResponseException);
      Assert.fail();
    } catch (HttpResponseException ex) {
      Assert.assertSame(httpResponseException, ex);
    }
  }
}
