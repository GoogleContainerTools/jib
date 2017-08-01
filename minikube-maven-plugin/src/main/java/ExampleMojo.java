import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Placeholder mojo to get the build going
 */
@Mojo(name = "example")
public class ExampleMojo extends AbstractMojo
{
  public void execute() throws MojoExecutionException
  {
    getLog().info( "Hello world." );
  }
}
