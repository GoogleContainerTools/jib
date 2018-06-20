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

package com.google.cloud.tools.jib.json;

/** Empty class used for empty "{}" blocks in json. */
public class EmptyStruct implements JsonTemplate {
  private static final EmptyStruct SINGLETON = new EmptyStruct();

  public static EmptyStruct get() {
    return SINGLETON;
  }

  @Override
  public boolean equals(Object other) {
    return other != null && other.getClass() == EmptyStruct.class;
  }

  @Override
  public int hashCode() {
    return 0;
  }

  private EmptyStruct() {}
}
