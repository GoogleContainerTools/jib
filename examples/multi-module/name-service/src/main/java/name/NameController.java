package name;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
public class NameController {

  @RequestMapping("/")
  public String getName() {
    return "Jib Multimodule";
  }
}
