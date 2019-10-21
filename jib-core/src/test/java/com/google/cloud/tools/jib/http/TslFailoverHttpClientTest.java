package com.google.cloud.tools.jib.http;

import com.google.cloud.tools.jib.api.InsecureRegistryException;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.global.JibSystemProperties;
import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URL;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class TslFailoverHttpClientTest {

  @Test
  public void testGet_nonHttpsServer_insecureConnectionAndFailoverDisabled()
      throws MalformedURLException, IOException {
    Connection httpClient = new Connection(false, ignored -> {});
    try (Response response =
        httpClient.get(new URL("http://example.com"), new Request.Builder().build())) {
      Assert.fail("Should disallow non-HTTP attempt");
    } catch (SSLException ex) {
      Assert.assertEquals(
          "insecure HTTP connection not allowed: http://example.com", ex.getMessage());
    }
  }

  @Test
  public void testCall_secureCallerOnUnverifiableServer() throws IOException, RegistryException {
    Mockito.when(mockConnection.send(Mockito.eq("httpMethod"), Mockito.any()))
        .thenThrow(Mockito.mock(SSLPeerUnverifiedException.class)); // unverifiable HTTPS server

    try {
      secureEndpointCaller.call();
      Assert.fail("Secure caller should fail if cannot verify server");
    } catch (InsecureRegistryException ex) {
      Assert.assertEquals(
          "Failed to verify the server at https://serverUrl/v2/api because only secure connections are allowed.",
          ex.getMessage());
    }
  }

  @Test
  public void testCall_insecureCallerOnUnverifiableServer() throws IOException, RegistryException {
    Mockito.when(mockConnection.send(Mockito.eq("httpMethod"), Mockito.any()))
        .thenThrow(Mockito.mock(SSLPeerUnverifiedException.class)); // unverifiable HTTPS server
    Mockito.when(mockInsecureConnection.send(Mockito.eq("httpMethod"), Mockito.any()))
        .thenReturn(mockResponse); // OK with non-verifying connection

    RegistryEndpointCaller<String> insecureCaller = createRegistryEndpointCaller(true, -1);
    Assert.assertEquals("body", insecureCaller.call());

    ArgumentCaptor<URL> urlCaptor = ArgumentCaptor.forClass(URL.class);
    Mockito.verify(mockConnectionFactory).apply(urlCaptor.capture());
    Assert.assertEquals(new URL("https://serverUrl/v2/api"), urlCaptor.getAllValues().get(0));

    Mockito.verify(mockInsecureConnectionFactory).apply(urlCaptor.capture());
    Assert.assertEquals(new URL("https://serverUrl/v2/api"), urlCaptor.getAllValues().get(1));

    Mockito.verifyNoMoreInteractions(mockConnectionFactory);
    Mockito.verifyNoMoreInteractions(mockInsecureConnectionFactory);

    Mockito.verify(mockEventHandlers)
        .dispatch(
            LogEvent.info(
                "Cannot verify server at https://serverUrl/v2/api. Attempting again with no TLS verification."));
  }

  @Test
  public void testCall_insecureCallerOnHttpServer() throws IOException, RegistryException {
    Mockito.when(mockConnection.send(Mockito.eq("httpMethod"), Mockito.any()))
        .thenThrow(Mockito.mock(SSLPeerUnverifiedException.class)) // server is not HTTPS
        .thenReturn(mockResponse);
    Mockito.when(mockInsecureConnection.send(Mockito.eq("httpMethod"), Mockito.any()))
        .thenThrow(Mockito.mock(SSLPeerUnverifiedException.class)); // server is not HTTPS

    RegistryEndpointCaller<String> insecureEndpointCaller = createRegistryEndpointCaller(true, -1);
    Assert.assertEquals("body", insecureEndpointCaller.call());

    ArgumentCaptor<URL> urlCaptor = ArgumentCaptor.forClass(URL.class);
    Mockito.verify(mockConnectionFactory, Mockito.times(2)).apply(urlCaptor.capture());
    Assert.assertEquals(new URL("https://serverUrl/v2/api"), urlCaptor.getAllValues().get(0));
    Assert.assertEquals(new URL("http://serverUrl/v2/api"), urlCaptor.getAllValues().get(1));

    Mockito.verify(mockInsecureConnectionFactory).apply(urlCaptor.capture());
    Assert.assertEquals(new URL("https://serverUrl/v2/api"), urlCaptor.getAllValues().get(2));

    Mockito.verifyNoMoreInteractions(mockConnectionFactory);
    Mockito.verifyNoMoreInteractions(mockInsecureConnectionFactory);

    Mockito.verify(mockEventHandlers)
        .dispatch(
            LogEvent.info(
                "Cannot verify server at https://serverUrl/v2/api. Attempting again with no TLS verification."));
    Mockito.verify(mockEventHandlers)
        .dispatch(
            LogEvent.info(
                "Failed to connect to https://serverUrl/v2/api over HTTPS. Attempting again with HTTP: http://serverUrl/v2/api"));
  }

  @Test
  public void testCall_insecureCallerOnHttpServerAndNoPortSpecified()
      throws IOException, RegistryException {
    Mockito.when(mockConnection.send(Mockito.eq("httpMethod"), Mockito.any()))
        .thenThrow(Mockito.mock(ConnectException.class)) // server is not listening on 443
        .thenReturn(mockResponse); // respond when connected through 80

    RegistryEndpointCaller<String> insecureEndpointCaller = createRegistryEndpointCaller(true, -1);
    Assert.assertEquals("body", insecureEndpointCaller.call());

    ArgumentCaptor<URL> urlCaptor = ArgumentCaptor.forClass(URL.class);
    Mockito.verify(mockConnectionFactory, Mockito.times(2)).apply(urlCaptor.capture());
    Assert.assertEquals(new URL("https://serverUrl/v2/api"), urlCaptor.getAllValues().get(0));
    Assert.assertEquals(new URL("http://serverUrl/v2/api"), urlCaptor.getAllValues().get(1));

    Mockito.verifyNoMoreInteractions(mockConnectionFactory);
    Mockito.verifyNoMoreInteractions(mockInsecureConnectionFactory);

    Mockito.verify(mockEventHandlers)
        .dispatch(
            LogEvent.info(
                "Failed to connect to https://serverUrl/v2/api over HTTPS. Attempting again with HTTP: http://serverUrl/v2/api"));
  }

  @Test
  public void testCall_secureCallerOnNonListeningServerAndNoPortSpecified()
      throws IOException, RegistryException {
    Mockito.when(mockConnection.send(Mockito.eq("httpMethod"), Mockito.any()))
        .thenThrow(Mockito.mock(ConnectException.class)); // server is not listening on 443

    try {
      secureEndpointCaller.call();
      Assert.fail("Should not fall back to HTTP if not allowInsecureRegistries");
    } catch (ConnectException ex) {
      Assert.assertNull(ex.getMessage());
    }

    ArgumentCaptor<URL> urlCaptor = ArgumentCaptor.forClass(URL.class);
    Mockito.verify(mockConnectionFactory).apply(urlCaptor.capture());
    Assert.assertEquals(new URL("https://serverUrl/v2/api"), urlCaptor.getAllValues().get(0));

    Mockito.verifyNoMoreInteractions(mockConnectionFactory);
    Mockito.verifyNoMoreInteractions(mockInsecureConnectionFactory);
  }

  @Test
  public void testCall_insecureCallerOnNonListeningServerAndPortSpecified()
      throws IOException, RegistryException {
    Mockito.when(mockConnection.send(Mockito.eq("httpMethod"), Mockito.any()))
        .thenThrow(Mockito.mock(ConnectException.class)); // server is not listening on 5000

    RegistryEndpointCaller<String> insecureEndpointCaller =
        createRegistryEndpointCaller(true, 5000);
    try {
      insecureEndpointCaller.call();
      Assert.fail("Should not fall back to HTTP if port was explicitly given and cannot connect");
    } catch (ConnectException ex) {
      Assert.assertNull(ex.getMessage());
    }

    ArgumentCaptor<URL> urlCaptor = ArgumentCaptor.forClass(URL.class);
    Mockito.verify(mockConnectionFactory).apply(urlCaptor.capture());
    Assert.assertEquals(new URL("https://serverUrl:5000/v2/api"), urlCaptor.getAllValues().get(0));

    Mockito.verifyNoMoreInteractions(mockConnectionFactory);
    Mockito.verifyNoMoreInteractions(mockInsecureConnectionFactory);
  }

  @Test
  public void testCall_timeoutFromConnectException() throws IOException, RegistryException {
    ConnectException mockConnectException = Mockito.mock(ConnectException.class);
    Mockito.when(mockConnectException.getMessage()).thenReturn("Connection timed out");
    Mockito.when(mockConnection.send(Mockito.eq("httpMethod"), Mockito.any()))
        .thenThrow(mockConnectException) // server times out on 443
        .thenReturn(mockResponse); // respond when connected through 80

    try {
      RegistryEndpointCaller<String> insecureEndpointCaller =
          createRegistryEndpointCaller(true, -1);
      insecureEndpointCaller.call();
      Assert.fail("Should not fall back to HTTP if timed out even for ConnectionException");

    } catch (ConnectException ex) {
      Assert.assertSame(mockConnectException, ex);
      Mockito.verify(mockConnection).send(Mockito.anyString(), Mockito.any());
    }
  }

  @Test
  public void testHttpTimeout_propertyNotSet() throws IOException, RegistryException {
    MockConnection mockConnection = new MockConnection((httpMethod, request) -> mockResponse);
    Mockito.when(mockConnectionFactory.apply(Mockito.any())).thenReturn(mockConnection);

    System.clearProperty(JibSystemProperties.HTTP_TIMEOUT);
    secureEndpointCaller.call();

    // We fall back to the default timeout:
    // https://github.com/GoogleContainerTools/jib/pull/656#discussion_r203562639
    Assert.assertEquals(20000, mockConnection.getRequestedHttpTimeout().intValue());
  }

  @Test
  public void testHttpTimeout_stringValue() throws IOException, RegistryException {
    MockConnection mockConnection = new MockConnection((httpMethod, request) -> mockResponse);
    Mockito.when(mockConnectionFactory.apply(Mockito.any())).thenReturn(mockConnection);

    System.setProperty(JibSystemProperties.HTTP_TIMEOUT, "random string");
    secureEndpointCaller.call();

    Assert.assertEquals(20000, mockConnection.getRequestedHttpTimeout().intValue());
  }

  @Test
  public void testHttpTimeout_negativeValue() throws IOException, RegistryException {
    MockConnection mockConnection = new MockConnection((httpMethod, request) -> mockResponse);
    Mockito.when(mockConnectionFactory.apply(Mockito.any())).thenReturn(mockConnection);

    System.setProperty(JibSystemProperties.HTTP_TIMEOUT, "-1");
    secureEndpointCaller.call();

    // We let the negative value pass through:
    // https://github.com/GoogleContainerTools/jib/pull/656#discussion_r203562639
    Assert.assertEquals(Integer.valueOf(-1), mockConnection.getRequestedHttpTimeout());
  }

  @Test
  public void testHttpTimeout_0accepted() throws IOException, RegistryException {
    System.setProperty(JibSystemProperties.HTTP_TIMEOUT, "0");

    MockConnection mockConnection = new MockConnection((httpMethod, request) -> mockResponse);
    Mockito.when(mockConnectionFactory.apply(Mockito.any())).thenReturn(mockConnection);

    secureEndpointCaller.call();

    Assert.assertEquals(Integer.valueOf(0), mockConnection.getRequestedHttpTimeout());
  }

  @Test
  public void testHttpTimeout() throws IOException, RegistryException {
    System.setProperty(JibSystemProperties.HTTP_TIMEOUT, "7593");

    MockConnection mockConnection = new MockConnection((httpMethod, request) -> mockResponse);
    Mockito.when(mockConnectionFactory.apply(Mockito.any())).thenReturn(mockConnection);

    secureEndpointCaller.call();

    Assert.assertEquals(Integer.valueOf(7593), mockConnection.getRequestedHttpTimeout());
  }
}
