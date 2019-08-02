/*
 * Copyright 2019 Google Inc.
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

package com.google.cloud.tools.jib.cache;

import java.util.concurrent.TimeUnit;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link Retry}. */
public class RetryTest {
  private volatile int actionCount = 0;
  private final Retry.Action<Exception> countingAction =
      () -> {
        synchronized (this) {
          ++actionCount;
        }
      };
  private final Retry.Action<Exception> countingExceptionAction =
      () -> {
        countingAction.run();
        throw new Exception("whee");
      };

  @Test
  public void testBase() throws Exception {
    boolean result = Retry.action(countingAction).run();
    Assert.assertTrue(result);
    Assert.assertEquals(1, actionCount);
  }

  @Test
  public void testImmediateStop() throws Exception {
    // if the stop condition is true then the action is never invoked
    boolean result = Retry.action(countingAction).until(() -> true).run();
    Assert.assertTrue(result);
    Assert.assertEquals(0, actionCount);
  }

  @Test
  public void testMaximumRetries_default() throws Exception {
    boolean result = Retry.action(countingAction).until(() -> false).run();
    Assert.assertFalse(result);
    Assert.assertEquals(5, actionCount);
  }

  @Test
  public void testMaximumRetries_specified() throws Exception {
    // if the stop condition is true then the action is never invoked
    boolean result = Retry.action(countingAction).maximumRetries(2).until(() -> false).run();
    Assert.assertFalse(result);
    Assert.assertEquals(2, actionCount);
  }

  @Test
  public void testRetryableException() {
    // all exceptions are retryable by default, so retry 5 times
    try {
      Retry.<Exception>action(countingExceptionAction).until(() -> false).run();
      Assert.fail("should have thrown exception");
    } catch (Exception ex) {
      Assert.assertEquals("whee", ex.getMessage());
      Assert.assertEquals(5, actionCount);
    }
  }

  @Test
  public void testNonRetryableException() {
    // the exception is not ok and so should only try 1 time
    try {
      Retry.<Exception>action(countingExceptionAction)
          .retryOnException(ex -> false)
          .until(() -> false)
          .run();
      Assert.fail("should have thrown exception");
    } catch (Exception ex) {
      Assert.assertEquals("whee", ex.getMessage());
      Assert.assertEquals(1, actionCount);
    }
  }

  @Test
  public void testInterruptSleep() throws Exception {
    // interrupt the current thread so as to cause the retry's sleep() to throw
    // an InterruptedException
    Thread.currentThread().interrupt();
    boolean result =
        Retry.action(countingAction).until(() -> false).sleep(10, TimeUnit.SECONDS).run();
    Assert.assertFalse(result);
    Assert.assertEquals(1, actionCount);
    // This thread should be marked as interrupted (plus clear the flag for the test)
    Assert.assertTrue(Thread.interrupted());
  }

  @Test
  public void testInvalid_maximumRetries() {
    try {
      Retry.action(() -> {}).maximumRetries(0);
      Assert.fail();
    } catch (IllegalArgumentException ex) {
      /* expected */
    }
  }

  @Test
  public void testInvalid_sleep() {
    try {
      Retry.action(() -> {}).sleep(-1, TimeUnit.DAYS);
      Assert.fail();
    } catch (IllegalArgumentException ex) {
      /* expected */
    }
  }
}
