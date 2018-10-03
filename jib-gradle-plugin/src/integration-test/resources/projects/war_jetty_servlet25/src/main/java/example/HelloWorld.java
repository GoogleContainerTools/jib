package example;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class HelloWorld extends HttpServlet {

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    try {
      URL worldFile = getServletContext().getResource("/WEB-INF/classes/world");
      Path path = Paths.get(worldFile.toURI());
      String world = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);

      response.setContentType("text/plain");
      response.setCharacterEncoding("UTF-8");

      response.getWriter().print("Hello " + world);

    } catch (URISyntaxException e) {
      throw new IOException(e);
    }
  }
}
