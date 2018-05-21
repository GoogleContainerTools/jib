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
 *   <li>Be immutable
 *   <li>Construct with the dependent {@link AsyncStep}s and submitting itself to the {@link
 *       ListeningExecutorService} to run after all its dependent {@link AsyncStep}s (for example,
 *       by using {@link Futures#whenAllSucceed})
 *   <li>Implement {@link #call} with the actual work
 *   <li>Implement {@link #getFuture} by
 * </ol>
 *
 * @param <T> the object type passed on by this step
 */
interface AsyncStep<T> extends Callable<T> {

  /** @return the submitted future */
  // TODO: Consider changing this to be orchestrated by an AsyncStepsBuilder.
  ListenableFuture<T> getFuture();
}
