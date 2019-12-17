package name;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import common.SharedUtils;

@RestController
public class NameController {

  @RequestMapping("/")
  public String getText() {
    return "Jib Multimodule: " + SharedUtils.getText();
  }
}
