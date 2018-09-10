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

package com.google.cloud.tools.jib.async;

import com.google.common.util.concurrent.Futures;
import java.util.concurrent.ExecutionException;

/**
 * Static utility for checking at runtime that the caller attempts to get a result only from a
 * completed {@link AsyncStep} by otherwise throwing a runtime exception.
 */
public class NonBlockingSteps {

  /**
   * Gets the completed computation result of {@code asyncStep}.
   *
   * @param <T> the type of the computation result of {@code asyncStep}
   * @param asyncStep completed {@link AsyncStep}
   * @return the completed computation result
   * @throws ExecutionException if the {@code Future} failed with an exception
   * @throws IllegalStateException if {@code asyncStep} has not been completed
   */
  public static <T> T get(AsyncStep<T> asyncStep) throws ExecutionException {
    return Futures.getDone(asyncStep.getFuture());
  }

  private NonBlockingSteps() {}
}
