/*
 * Copyright 2017 Google Inc.
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

package com.google.cloud.tools.crepecake.cache;

import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link TimestampedCachedLayer}. */
@RunWith(MockitoJUnitRunner.class)
public class TimestampedCachedLayerTest {

  @Mock private CachedLayer mockCachedLayer;

  @Test
  public void testNew() {
    long expectedLastModifiedTime = 12345;
    TimestampedCachedLayer timestampedCachedLayer =
        new TimestampedCachedLayer(mockCachedLayer, expectedLastModifiedTime);
    Assert.assertThat(
        expectedLastModifiedTime, CoreMatchers.is(timestampedCachedLayer.getLastModifiedTime()));
  }
}
