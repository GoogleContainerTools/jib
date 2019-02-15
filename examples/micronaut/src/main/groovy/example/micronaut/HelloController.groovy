package example.micronaut

import groovy.transform.CompileStatic
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces

@CompileStatic
@Controller("/hello") // <1>
class HelloController {

    @Produces(MediaType.TEXT_PLAIN)
    @Get("/") // <2>
    String index() {
        "Hello World" // <3>
    }
}
