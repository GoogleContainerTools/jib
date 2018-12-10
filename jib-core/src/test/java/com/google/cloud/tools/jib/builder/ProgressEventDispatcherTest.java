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

package com.google.cloud.tools.jib.builder;

import com.google.cloud.tools.jib.event.EventDispatcher;
import com.google.cloud.tools.jib.event.events.ProgressEvent;
import com.google.common.base.VerifyException;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link ProgressEventDispatcher}. */
@RunWith(MockitoJUnitRunner.class)
public class ProgressEventDispatcherTest {

  @Mock private EventDispatcher mockEventDispatcher;

  @Test
  public void testDispatch() {
    try (ProgressEventDispatcher progressEventDispatcher =
        ProgressEventDispatcher.newRoot(mockEventDispatcher, "ignored", 10)) {
      try (ProgressEventDispatcher ignored =
          progressEventDispatcher.newChildProducer().create("ignored", 20)) {
        // empty
      }
    }

    ArgumentCaptor<ProgressEvent> progressEventArgumentCaptor =
        ArgumentCaptor.forClass(ProgressEvent.class);
    Mockito.verify(mockEventDispatcher, Mockito.times(4))
        .dispatch(progressEventArgumentCaptor.capture());
    List<ProgressEvent> progressEvents = progressEventArgumentCaptor.getAllValues();

    Assert.assertSame(progressEvents.get(0).getAllocation(), progressEvents.get(3).getAllocation());
    Assert.assertSame(progressEvents.get(1).getAllocation(), progressEvents.get(2).getAllocation());

    Assert.assertEquals(0, progressEvents.get(0).getUnits());
    Assert.assertEquals(0, progressEvents.get(1).getUnits());
    Assert.assertEquals(20, progressEvents.get(2).getUnits());
    Assert.assertEquals(9, progressEvents.get(3).getUnits());
  }

  @Test
  public void testDispatch_tooManyChildren() {
    try {
      try (ProgressEventDispatcher progressEventDispatcher =
          ProgressEventDispatcher.newRoot(mockEventDispatcher, "allocation description", 1)) {
        progressEventDispatcher.newChildProducer();
        progressEventDispatcher.newChildProducer();
      }
      Assert.fail();

    } catch (VerifyException ex) {
      Assert.assertEquals(
          "Remaining allocation units less than 0 for 'allocation description': -1",
          ex.getMessage());
    }
  }
}
