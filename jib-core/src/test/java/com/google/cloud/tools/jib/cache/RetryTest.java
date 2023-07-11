/*
 * Copyright 2019 Google LLC.
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

package com.google.cloud.tools.jib.cache;

import java.util.concurrent.TimeUnit;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

/** Tests for {@link Retry}. */
class RetryTest {
  private int actionCount = 0;

  private boolean successfulAction() {
    ++actionCount;
    return true;
  }

  private boolean unsuccessfulAction() {
    ++actionCount;
    return false;
  }

  private boolean exceptionAction() throws Exception {
    ++actionCount;
    throw new Exception("whee");
  }

  @Test
  void testSuccessfulAction() throws Exception {
    boolean result = Retry.action(this::successfulAction).run();
    Assert.assertTrue(result);
    Assert.assertEquals(1, actionCount);
  }

  @Test
  void testMaximumRetries_default() throws Exception {
    boolean result = Retry.action(this::unsuccessfulAction).run();
    Assert.assertFalse(result);
    Assert.assertEquals(5, actionCount);
  }

  @Test
  void testMaximumRetries_specified() throws Exception {
    boolean result = Retry.action(this::unsuccessfulAction).maximumRetries(2).run();
    Assert.assertFalse(result);
    Assert.assertEquals(2, actionCount);
  }

  @Test
  void testRetryableException() {
    // all exceptions are retryable by default, so should retry 5 times
    try {
      Retry.action(this::exceptionAction).run();
      Assert.fail("should have thrown exception");
    } catch (Exception ex) {
      Assert.assertEquals("whee", ex.getMessage());
      Assert.assertEquals(5, actionCount);
    }
  }

  @Test
  void testNonRetryableException() {
    // the exception is not ok and so should only try 1 time
    try {
      Retry.action(this::exceptionAction).retryOnException(ex -> false).run();
      Assert.fail("should have thrown exception");
    } catch (Exception ex) {
      Assert.assertEquals("whee", ex.getMessage());
      Assert.assertEquals(1, actionCount);
    }
  }

  @Test
  void testInterruptSleep() throws Exception {
    // interrupt the current thread so as to cause the retry's sleep() to throw
    // an InterruptedException
    Thread.currentThread().interrupt();
    try {
      boolean result = Retry.action(this::unsuccessfulAction).sleep(10, TimeUnit.SECONDS).run();
      Assert.assertFalse(result);
      Assert.assertEquals(1, actionCount);
    } finally {
      // This thread should be marked as interrupted (plus clear the flag for the test)
      Assert.assertTrue(Thread.interrupted());
    }
  }

  @Test
  void testInvalid_maximumRetries() {
    try {
      Retry.action(this::successfulAction).maximumRetries(0);
      Assert.fail();
    } catch (IllegalArgumentException ex) {
      /* maximumRetries() ensures the retry value is at least 1. */
    }
  }

  @Test
  void testInvalid_sleep() {
    try {
      Retry.action(this::successfulAction).sleep(-1, TimeUnit.DAYS);
      Assert.fail();
    } catch (IllegalArgumentException ex) {
      /* sleep() ensures the sleep value is non-negative. */
    }
  }
}
