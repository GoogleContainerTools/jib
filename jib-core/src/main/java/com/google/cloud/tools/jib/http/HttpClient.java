package com.google.cloud.tools.jib.http;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.apache.ApacheHttpTransport;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.global.JibSystemProperties;
import com.google.cloud.tools.jib.http.Request.Builder;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.net.ssl.SSLException;

public class HttpClient {
  private final String httpMethod;
  private final URL url;
  private final Builder requestBuilder;
  private final boolean allowInsecureConnection;
  private final Function<URL, Connection> connectionFactory;
  private Function<URL, Connection> insecureConnectionFactory;
  private final Consumer<LogEvent> logger;

  private Connection connection;

  private static boolean isHttpsProtocol(URL url) {
    return "https".equals(url.getProtocol());
  }

  //  public static InsecureConnectionFailoverUtil of(boolean allowInsecureConnection) throws
  // GeneralSecurityException {
  //    return new InsecureConnectionFailoverUtil(null, null, null, allowInsecureConnection, null,
  //    allowInsecureConnection ? null : Connection.getInsecureConnectionFactory();
  //  }

  public HttpClient(
      String httpMethod,
      URL url,
      Request.Builder requestBuilder,
      boolean allowInsecureConnection,
      Consumer<LogEvent> logger) {
    this.httpMethod = httpMethod;
    this.url = url;
    this.requestBuilder = requestBuilder;
    this.allowInsecureConnection = allowInsecureConnection;
    this.connectionFactory = Connection.getConnectionFactory();
    this.logger = logger;
  }

  public Response2 call() throws IOException {
    if (!isHttpsProtocol(url) && !allowInsecureConnection) {
      throw new IOException("non-HTTPS connection not allowed: " + url);
    }

    try {
      return call(url, connectionFactory);

    } catch (SSLException ex) {
      return handleUnverifiableServerException();

    } catch (ConnectException ex) {
      // It is observed that Open/Oracle JDKs sometimes throw SocketTimeoutException but other times
      // ConnectException for connection timeout. (Could be a JDK bug.) Note SocketTimeoutException
      // does not extend ConnectException (or vice versa), and we want to be consistent to error out
      // on timeouts: https://github.com/GoogleContainerTools/jib/issues/1895#issuecomment-527544094
      if (ex.getMessage() != null && ex.getMessage().contains("timed out")) {
        throw ex;
      }

      if (allowInsecureConnection && isHttpsProtocol(url) && url.getPort() == -1) {
        // Fall back to HTTP only if "url" had no port specified (i.e., we tried the default HTTPS
        // port 443) and we could not connect to 443. It's worth trying port 80.
        return fallBackToHttp(url);
      }
      throw ex;
    }
  }

  private Response2 handleUnverifiableServerException() throws IOException {
    if (!allowInsecureConnection) {
      throw new IOException("insecure connection not allowed: " + url);
    }

    try {
      logger.accept(
          LogEvent.info(
              "Cannot verify server at " + url + ". Attempting again with no TLS verification."));
      return call(url, getInsecureConnectionFactory());

    } catch (SSLException ex) {
      return fallBackToHttp(url);
    }
  }

  private Response2 fallBackToHttp(URL url) throws IOException {
    GenericUrl httpUrl = new GenericUrl(url);
    httpUrl.setScheme("http");
    logger.accept(
        LogEvent.info(
            "Failed to connect to " + url + " over HTTPS. Attempting again with HTTP: " + httpUrl));
    return call(httpUrl.toURL(), connectionFactory);
  }

  private Function<URL, Connection> getInsecureConnectionFactory() throws IOException {
    try {
      if (insecureConnectionFactory == null) {
        insecureConnectionFactory = Connection.getInsecureConnectionFactory();
      }
      return insecureConnectionFactory;

    } catch (GeneralSecurityException ex) {
      throw new IOException("cannot turn off TLS peer verification", ex);
    }
  }

  private Response2 call(URL url, Function<URL, Connection> connectionFactory) throws IOException {
    // Only sends authorization if using HTTPS or explicitly forcing over HTTP.
    boolean sendCredentials = isHttpsProtocol(url) || JibSystemProperties.sendCredentialsOverHttp();
    if (!sendCredentials) {
      requestBuilder.setAuthorization(null);
    }

    HttpRequestFactory requestFactory = new ApacheHttpTransport().createRequestFactory();
    requestFactory.buildRequest(requestMethod, url, content)

    Connection connection = connectionFactory.apply(url);
    return connection.send(httpMethod, requestBuilder.build());
  }
}
