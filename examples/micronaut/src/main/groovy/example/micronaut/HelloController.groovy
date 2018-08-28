package example.micronaut

import groovy.transform.CompileStatic
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

@CompileStatic
@Controller("/hello") // <1>
class HelloController {
    @Get("/") // <2>
    String index() {
        "Hello World" // <3>
    }
}
