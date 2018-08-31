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
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.concurrent.Callable;

/**
 * Holds the future for an asynchronously-running step. Implementations should:
 *
 * <ol>
 *   <li>Be immutable
 *   <li>Construct with the dependent {@link AsyncStep}s and submit a {@link Callable} to the {@link
 *       ListeningExecutorService} to run after all its dependent {@link AsyncStep}s (for example,
 *       by using {@link Futures#whenAllSucceed})
 *   <li>Have {@link #getFuture} return the submitted future
 * </ol>
 *
 * @param <T> the object type passed on by this step
 */
public interface AsyncStep<T> {

  /** @return the submitted future */
  // TODO: Consider changing this to be orchestrated by an AsyncStepsBuilder.
  ListenableFuture<T> getFuture();
}
