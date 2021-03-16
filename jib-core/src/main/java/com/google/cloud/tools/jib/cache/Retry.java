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

import com.google.common.base.Preconditions;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Retries an action until it succeeds, or has retried too often and failed. By default the action
 * will be run up to 5 times. The action is deemed successful if it runs to completion without
 * throwing an exception, and returns true.
 *
 * <ul>
 *   <li>Exceptions are caught and, if deemed {@link #retryOnException(Predicate) retryable} then
 *       the action will be re-attempted. By default, any exception is considered retryable.
 *   <li>The retry instance can be configured to {@link #sleep(long, TimeUnit) sleep between
 *       retries}.
 *   <li>The maximum retry count {@link #maximumRetries(int) is configurable} (5 times by default).
 * </ul>
 *
 * @param <E> the class of exceptions that may be thrown
 */
public class Retry<E extends Exception> {

  /** A runnable action that may throw an exception of type {@code E}. */
  @FunctionalInterface
  public interface Action<E extends Exception> {
    /**
     * Perform the action.
     *
     * @return {@code true} if the action was successful and {@code false} otherwise
     * @throws E exception thrown during the action
     */
    boolean run() throws E;
  }

  /**
   * Create a retryable action.
   *
   * @param action the action to be run
   * @param <E> the class of exceptions that may be thrown
   * @return the instance
   */
  public static <E extends Exception> Retry<E> action(Action<E> action) {
    return new Retry<>(action);
  }

  private final Action<E> action;
  private int maximumRetries = 5;
  private Predicate<Exception> retryOnException = ignored -> true; // continue to retry
  private long sleepMilliseconds = -1; // no sleep

  private Retry(Action<E> action) {
    this.action = action;
  }

  /**
   * Configure the maximum number of retries.
   *
   * @param maximumRetries the number of retries, must be zero or more
   * @return this Retry instance
   */
  public Retry<E> maximumRetries(int maximumRetries) {
    Preconditions.checkArgument(maximumRetries > 0);
    this.maximumRetries = maximumRetries;
    return this;
  }

  /**
   * Provide a predicate to determine if a thrown exception can be retried.
   *
   * @param retryOnException determine if provided exception is retryable.
   * @return the instance for further configuration
   */
  public Retry<E> retryOnException(Predicate<Exception> retryOnException) {
    this.retryOnException = retryOnException;
    return this;
  }

  /**
   * Set the sleep time between retries.
   *
   * @param duration the time to sleep
   * @param unit the unit of time of duration
   * @return the instance for further configuration
   */
  public Retry<E> sleep(long duration, TimeUnit unit) {
    Preconditions.checkArgument(duration >= 0);
    this.sleepMilliseconds = unit.convert(duration, TimeUnit.MILLISECONDS);
    return this;
  }

  /**
   * Run the action until it runs successfully, to a {@link #maximumRetries(int) maximum number of
   * retries} (default: 5). If an exception occurs then the action will be retried providing {@link
   * #retryOnException(Predicate) the exception is retryable}.
   *
   * @return true if the action was run successfully, or {@code false} if the action was unable to
   *     complete
   * @throws E exception thrown during the action
   */
  public boolean run() throws E {
    for (int i = 0; i < maximumRetries; i++) {
      try {
        // sleep between attempts, but not on the first attempt
        if (i > 0 && sleepMilliseconds >= 0) {
          Thread.sleep(sleepMilliseconds);
        }

        // Do we need to continue?
        if (action.run()) {
          return true;
        }

      } catch (InterruptedException ex) {
        // Restore the interrupted status
        Thread.currentThread().interrupt();
        return false;

      } catch (Exception ex) {
        // if this is the last iteration, no more retries
        if (i + 1 == maximumRetries || !retryOnException.test(ex)) {
          throw ex;
        }
      }
    }
    // we did not complete
    return false;
  }
}
