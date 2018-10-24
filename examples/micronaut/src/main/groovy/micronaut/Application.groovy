package micronaut

import io.micronaut.runtime.Micronaut
import groovy.transform.CompileStatic

@CompileStatic
class Application {
    static void main(String[] args) {
        Micronaut.run(Application)
    }
}