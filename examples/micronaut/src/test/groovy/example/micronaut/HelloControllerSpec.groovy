package example.micronaut

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class HelloControllerSpec extends Specification {


    @Shared
    @AutoCleanup // <1>
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer) // <2>

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL()) // <3>

    void "test hello world response"() {
        when:
        HttpRequest request = HttpRequest.GET('/hello') // <4>
        String rsp  = client.toBlocking().retrieve(request)

        then:
        rsp == "Hello World"
    }

}