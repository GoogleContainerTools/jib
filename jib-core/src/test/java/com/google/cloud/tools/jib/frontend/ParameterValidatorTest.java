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

package com.google.cloud.tools.jib.frontend;

import com.google.cloud.tools.jib.builder.BuildLogger;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link ParameterValidator}. */
@RunWith(MockitoJUnitRunner.class)
public class ParameterValidatorTest {

  @Mock private BuildLogger mockLogger;

  @Test
  public void testCheckListForNullOrEmpty() {
    List<String> goodInput = Arrays.asList("a", "b", "c");
    ParameterValidator.checkListForNullOrEmpty(goodInput, "list", mockLogger);
    Mockito.verify(mockLogger, Mockito.never())
        .warn("Empty string found in list parameter 'list'.");

    List<String> warnInput = Arrays.asList("a", "", "");
    ParameterValidator.checkListForNullOrEmpty(warnInput, "list", mockLogger);
    Mockito.verify(mockLogger, Mockito.times(1))
        .warn("Empty string found in list parameter 'list'.");

    try {
      List<String> errorInput = Arrays.asList("a", null, "c");
      ParameterValidator.checkListForNullOrEmpty(errorInput, "list", mockLogger);
      Assert.fail();
    } catch (IllegalStateException ex) {
      Assert.assertEquals(
          "Null element found in list parameter 'list'. Make sure your configuration is free of "
              + "null parameters.",
          ex.getMessage());
    }

    try {
      List<String> warnAndErrorInput = Arrays.asList("", null, "c");
      ParameterValidator.checkListForNullOrEmpty(warnAndErrorInput, "list", mockLogger);
      Assert.fail();
    } catch (IllegalStateException ex) {
      Assert.assertEquals(
          "Null element found in list parameter 'list'. Make sure your configuration is free of "
              + "null parameters.",
          ex.getMessage());
    }
  }
}
