/*
 * Copyright 2018 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ca.mt.jibexamples.first;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/** A compelling demo app. */
public class Main {

  public static void main(String[] args) {
    Properties props = System.getProperties();
    List<Map.Entry<Object, Object>> sorted = new ArrayList<>(props.entrySet());
    Collections.sort(sorted, (a, b) -> a.getKey().toString().compareTo(b.getKey().toString()));
    Iterator iter = sorted.iterator();
    while (iter.hasNext()) {
      Map.Entry entry = (Map.Entry) iter.next();
      if (args.length == 0) {
        System.out.println(entry.getKey() + " --- " + entry.getValue());
      } else {
        for (String arg : args) {
          if (((String) entry.getKey()).startsWith(arg)) {
            System.out.println(entry.getKey() + " --- " + entry.getValue());
          }
        }
      }
    }
  }
}
