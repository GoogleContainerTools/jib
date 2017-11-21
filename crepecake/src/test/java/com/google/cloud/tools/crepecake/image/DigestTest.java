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

package com.google.cloud.tools.crepecake.image;

import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link Digest}. */
public class DigestTest {

  @Test
  public void testCreateFromHash_pass() throws DigestException {
    final String goodHash = createGoodHash('a');

    Digest digest = Digest.fromHash(goodHash);

    Assert.assertEquals(goodHash, digest.getHash());
    Assert.assertEquals("sha256:" + goodHash, digest.toString());
  }

  @Test
  public void testCreateFromHash_fail() {
    final String badHash = "not a valid hash";

    try {
      Digest.fromHash(badHash);
      Assert.fail("Invalid hash should have caused digest creation failure.");
    } catch (DigestException ex) {
      // pass
    }
  }

  @Test
  public void testCreateFromDigest_pass() throws DigestException {
    final String goodHash = createGoodHash('a');
    final String goodDigest = "sha256:" + createGoodHash('a');

    Digest digest = Digest.fromDigest(goodDigest);

    Assert.assertEquals(goodHash, digest.getHash());
    Assert.assertEquals(goodDigest, digest.toString());
  }

  @Test
  public void testCreateFromDigest_fail() {
    final String badDigest = "sha256:not a valid digest";

    try {
      Digest.fromDigest(badDigest);
      Assert.fail("Invalid digest should have caused digest creation failure.");
    } catch (DigestException ex) {
      // pass
    }
  }

  @Test
  public void testUseAsMapKey() throws DigestException {
    final Digest digestA1 = Digest.fromHash(createGoodHash('a'));
    final Digest digestA2 = Digest.fromHash(createGoodHash('a'));
    final Digest digestA3 = Digest.fromDigest("sha256:" + createGoodHash('a'));
    final Digest digestB = Digest.fromHash(createGoodHash('b'));

    Map<Digest, String> digestMap = new HashMap<>();

    digestMap.put(digestA1, "digest with a");
    Assert.assertEquals("digest with a", digestMap.get(digestA1));
    Assert.assertEquals("digest with a", digestMap.get(digestA2));
    Assert.assertEquals("digest with a", digestMap.get(digestA3));
    Assert.assertNull(digestMap.get(digestB));

    digestMap.put(digestA2, "digest with a");
    Assert.assertEquals("digest with a", digestMap.get(digestA1));
    Assert.assertEquals("digest with a", digestMap.get(digestA2));
    Assert.assertEquals("digest with a", digestMap.get(digestA3));
    Assert.assertNull(digestMap.get(digestB));

    digestMap.put(digestA3, "digest with a");
    Assert.assertEquals("digest with a", digestMap.get(digestA1));
    Assert.assertEquals("digest with a", digestMap.get(digestA2));
    Assert.assertEquals("digest with a", digestMap.get(digestA3));
    Assert.assertNull(digestMap.get(digestB));

    digestMap.put(digestB, "digest with b");
    Assert.assertEquals("digest with a", digestMap.get(digestA1));
    Assert.assertEquals("digest with a", digestMap.get(digestA2));
    Assert.assertEquals("digest with a", digestMap.get(digestA3));
    Assert.assertEquals("digest with b", digestMap.get(digestB));
  }

  /** Creates a 32 byte hexademical string to fit valid hash pattern. */
  private static String createGoodHash(char character) {
    StringBuffer goodHashBuffer = new StringBuffer(64);
    for (int i = 0; i < 64; i++) {
      goodHashBuffer.append(character);
    }
    return goodHashBuffer.toString();
  }
}
