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

package com.google.cloud.tools.jib.registry;

import java.net.URL;

/**
 * Throw when attempting to access an insecure registry when only secure connections are allowed.
 */
public class InsecureRegistryException extends RegistryException {

  InsecureRegistryException(URL insecureUrl) {
    super(
        "Failed to verify the server at "
            + insecureUrl
            + " because only secure connections are allowed.");
  }
}
