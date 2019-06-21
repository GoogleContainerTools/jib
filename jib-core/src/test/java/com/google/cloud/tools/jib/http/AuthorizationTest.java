/*
 * Copyright 2019 Google Inc.
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

package com.google.cloud.tools.jib.http;

import com.google.api.client.util.Base64;
import com.google.common.collect.Multimap;
import java.nio.charset.StandardCharsets;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Tests for {@link Authorization}.
 *
 * <p>JWTs were generated from <a href="https://jwt.io">jwt.io</a>'s JWT debugger with HS256.
 */
public class AuthorizationTest {
  @Test
  public void testDecode_dockerToken() {
    // a genuine token from accessing docker.io's openjdk
    Multimap<String, String> decoded =
        Authorization.decodeTokenRepositoryGrants(
            "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsIng1YyI6WyJNSUlDK2pDQ0FwK2dBd0lCQWdJQkFEQUtCZ2dxaGtqT1BRUURBakJHTVVRd1FnWURWUVFERXpzeVYwNVpPbFZMUzFJNlJFMUVVanBTU1U5Rk9reEhOa0U2UTFWWVZEcE5SbFZNT2tZelNFVTZOVkF5VlRwTFNqTkdPa05CTmxrNlNrbEVVVEFlRncweE9UQXhNVEl3TURJeU5EVmFGdzB5TURBeE1USXdNREl5TkRWYU1FWXhSREJDQmdOVkJBTVRPMUpMTkZNNlMwRkxVVHBEV0RWRk9rRTJSMVE2VTBwTVR6cFFNbEpMT2tOWlZVUTZTMEpEU0RwWFNVeE1Pa3hUU2xrNldscFFVVHBaVWxsRU1JSUJJakFOQmdrcWhraUc5dzBCQVFFRkFBT0NBUThBTUlJQkNnS0NBUUVBcjY2bXkveXpHN21VUzF3eFQ3dFplS2pqRzcvNnBwZFNMY3JCcko5VytwcndzMGtIUDVwUHRkMUpkcFdEWU1OZWdqQXhpUWtRUUNvd25IUnN2ODVUalBUdE5wUkdKVTRkeHJkeXBvWGc4TVhYUEUzL2lRbHhPS2VNU0prNlRKbG5wNGFtWVBHQlhuQXRoQzJtTlR5ak1zdFh2ZmNWN3VFYWpRcnlOVUcyUVdXQ1k1Ujl0a2k5ZG54Z3dCSEF6bG8wTzJCczFmcm5JbmJxaCtic3ZSZ1FxU3BrMWhxYnhSU3AyRlNrL2tBL1gyeUFxZzJQSUJxWFFMaTVQQ3krWERYZElJczV6VG9ZbWJUK0pmbnZaMzRLcG5mSkpNalpIRW4xUVJtQldOZXJZcVdtNVhkQVhUMUJrQU9aditMNFVwSTk3NFZFZ2ppY1JINVdBeWV4b1BFclRRSURBUUFCbzRHeU1JR3ZNQTRHQTFVZER3RUIvd1FFQXdJSGdEQVBCZ05WSFNVRUNEQUdCZ1JWSFNVQU1FUUdBMVVkRGdROUJEdFNTelJUT2t0QlMxRTZRMWcxUlRwQk5rZFVPbE5LVEU4NlVESlNTenBEV1ZWRU9rdENRMGc2VjBsTVREcE1VMHBaT2xwYVVGRTZXVkpaUkRCR0JnTlZIU01FUHpBOWdEc3lWMDVaT2xWTFMxSTZSRTFFVWpwU1NVOUZPa3hITmtFNlExVllWRHBOUmxWTU9rWXpTRVU2TlZBeVZUcExTak5HT2tOQk5sazZTa2xFVVRBS0JnZ3Foa2pPUFFRREFnTkpBREJHQWlFQXFOSXEwMFdZTmM5Z2tDZGdSUzRSWUhtNTRZcDBTa05Rd2lyMm5hSWtGd3dDSVFEMjlYdUl5TmpTa1cvWmpQaFlWWFB6QW9TNFVkRXNvUUhyUVZHMDd1N3ZsUT09Il19"
                + ".eyJhY2Nlc3MiOlt7InR5cGUiOiJyZXBvc2l0b3J5IiwibmFtZSI6ImxpYnJhcnkvb3BlbmpkayIsImFjdGlvbnMiOlsicHVsbCJdfV0sImF1ZCI6InJlZ2lzdHJ5LmRvY2tlci5pbyIsImV4cCI6MTU2MTA0MzkwNSwiaWF0IjoxNTYxMDQzNjA1LCJpc3MiOiJhdXRoLmRvY2tlci5pbyIsImp0aSI6Ikc5bWpiOE9GeU5STFlpY3ZUMFZxIiwibmJmIjoxNTYxMDQzMzA1LCJzdWIiOiIifQ"
                + ".jblwG_taIVf3IRiv200ivsc8q_IUj-M9QePKPAULfXdSZlY6H9n_XWtT6lw43k-J6QHfmnY4Yuh3eZq61KS7AT9yggM1VuolRCvYztSZ-MZHMIlvSE2KCc0wXa5gNQarjmDJloYduZuyLaKaRUUbO4osk1MuruODY_c2g2j16ce0Z8XVJ-7R8_J_Z8g0GdtFAfPO4bqpg9dj31MA8AKl3h-ru8NXcs3y1PkrYHpEGCgpcGcUQwLY7uiIrzjr0trCUbsLsv6iq2XTXnN_tTrfvL1R3yTB6gITvXZdsnU3r_UIDTzexTtlZWdntucJAGKX9HMA_jYEcTZ4ZhyEzETGpw");
    Assert.assertEquals(1, decoded.size());
    Assert.assertTrue(decoded.containsEntry("library/openjdk", "pull"));
    Assert.assertFalse(decoded.containsEntry("library/openjdk", "push"));
    Assert.assertFalse(decoded.containsEntry("randorepo", "push"));
  }

  @Test
  public void testDecode_nonToken() {
    String base64Text =
        Base64.encodeBase64String(
            "something other than a JWT token".getBytes(StandardCharsets.UTF_8));
    Multimap<String, String> decoded = Authorization.decodeTokenRepositoryGrants(base64Text);
    Assert.assertNull(decoded);
  }

  @Test
  public void testDecode_invalidToken_accessString() {
    // a JWT with an "access" field that is not an array: {"access": "string"}
    String jwt =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhY2Nlc3MiOiJzdHJpbmcifQ.12ODBkkfh6J79qEejxwlD5AfOa9mjObPCzOnUL75NSQ";
    Multimap<String, String> decoded = Authorization.decodeTokenRepositoryGrants(jwt);
    Assert.assertNull(decoded);
  }

  @Test
  public void testDecode_invalidToken_accessArray() {
    // a JWT with an "access" field that is an array of non-claim objects: {"access":["string"]}
    String jwt =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhY2Nlc3MiOlsic3RyaW5nIl19.gWZ9J4sO_w0hIVVxrfuuUC2lNhqkU3P0_z46xMCXfwU";
    Multimap<String, String> decoded = Authorization.decodeTokenRepositoryGrants(jwt);
    Assert.assertNull(decoded);
  }

  @Test
  @Ignore("Annotate AccessClaim.actions to disallow coercion of integers to strings")
  public void testDecode_invalidToken_actionsArray() {
    // a JWT with an "access" field that is an action array of non-strings:
    // {"access":[{"type": "repository","name": "library/openjdk","actions":[1]}]}
    String jwt =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhY2Nlc3MiOlt7InR5cGUiOiJyZXBvc2l0b3J5IiwibmFtZSI6ImxpYnJhcnkvb3BlbmpkayIsImFjdGlvbnMiOlsxXX1dfQ.12HZGeFvthXw0PP9ZKdttJRh2qsRfFNTeZV3_lZiI10";
    Multimap<String, String> decoded = Authorization.decodeTokenRepositoryGrants(jwt);
    Assert.assertNull(decoded);
  }

  @Test
  public void testDecode_invalidToken_randoJwt() {
    // the JWT example token from jwt.io
    String jwt =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
    Multimap<String, String> decoded = Authorization.decodeTokenRepositoryGrants(jwt);
    Assert.assertNull(decoded);
  }

  /** Basic credential should allow access to all. */
  @Test
  public void testCanAccess_basicCredential() {
    Authorization fixture = Authorization.fromBasicCredentials("foo", "bar");
    Assert.assertTrue(fixture.canAccess("random", "pull"));
  }

  /** Basic token should allow access to all. */
  @Test
  public void testCanAccess_basicToken() {
    Authorization fixture = Authorization.fromBasicToken("gobbledygook");
    Assert.assertTrue(fixture.canAccess("random", "pull"));
  }

  @Test
  public void testCanAccess_bearer_withToken() {
    // a synthetic token for accessing docker.io's openjdk with push and pull
    // {"access":[{"type":"repository","name":"library/openjdk","actions":["pull","push"]}]}
    String token =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhY2Nlc3MiOlt7InR5cGUiOiJyZXBvc2l0b3J5IiwibmFtZSI6ImxpYnJhcnkvb3BlbmpkayIsImFjdGlvbnMiOlsicHVsbCIsInB1c2giXX1dfQ.VEn96Ug4eseKHX3WwP3PlgR9P7Y6VuYmMm-YRUjngFg";
    Authorization authorization = Authorization.fromBearerToken(token);
    Assert.assertNotNull(authorization);
    Assert.assertTrue(authorization.canAccess("library/openjdk", "pull"));
    Assert.assertTrue(authorization.canAccess("library/openjdk", "push"));
    Assert.assertFalse(authorization.canAccess("library/openjdk", "other"));
    Assert.assertFalse(authorization.canAccess("randorepo", "push"));
  }

  @Test
  public void testCanAccess_bearer_withNonToken() {
    // non-Docker Bearer Tokens are assumed to allow access to all
    // the JWT example token from jwt.io
    String jwt =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
    Authorization authorization = Authorization.fromBearerToken(jwt);
    Assert.assertNotNull(authorization);
    Assert.assertTrue(authorization.canAccess("library/openjdk", "pull"));
    Assert.assertTrue(authorization.canAccess("library/openjdk", "push"));
    Assert.assertTrue(authorization.canAccess("randorepo", "push"));
  }
}
