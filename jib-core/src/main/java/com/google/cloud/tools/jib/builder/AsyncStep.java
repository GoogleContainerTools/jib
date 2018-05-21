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

package com.google.cloud.tools.jib.builder;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.concurrent.Callable;

/**
 * A step to run asynchronously. Implementations should:
 *
 * <ol>
 *   <li>Construct with the dependent {@link AsyncStep}s.
 *   <li>Implement {@link #call} with the actual work
 *   <li>Implement {@link #getFuture} by submitting itself to the {@link ListeningExecutorService}
 *       to run after all its dependent {@link AsyncStep}s for the first call, and returning that
 *       future for subsequent calls.
 * </ol>
 *
 * @param <T> the object type passed on by this step
 */
interface AsyncStep<T> extends Callable<T> {

  /**
   * Submits to a {@link ListeningExecutorService} to run after its dependencies. For example, by
   * using {@link Futures#whenAllSucceed}.
   */
  // TODO: Consider changing this to be orchestrated by an AsyncStepsBuilder.
  ListenableFuture<T> getFuture();
}
