/*
 * Copyright 2018 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package example.ktor

import com.codahale.metrics.JmxReporter
import io.ktor.application.*
import io.ktor.features.CallLogging
import io.ktor.features.DefaultHeaders
import io.ktor.features.StatusPages
import io.ktor.html.respondHtml
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.metrics.Metrics
import io.ktor.request.receiveOrNull
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.routing.routing
import kotlinx.html.*
import java.util.concurrent.TimeUnit

/**
 * Ktor Example Application
 *
 * See documentation at https://ktor.io/servers/index.html
 */
fun Application.main() {
    init()
    router()
}

// Variable for maintaining health-check status
var healthy = true

/**
 * For enabling Metrics - https://ktor.io/servers/features/metrics.html
 */
val metricsEnabled = (true == System.getenv("KTOR_METRICS_ENABLED")?.toBoolean())

/**
 * For enabling route tracing - https://ktor.io/servers/features/routing.html#tracing
 */
val routeTracing = (true == System.getenv("KTOR_ROUTE_TRACING")?.toBoolean())

/**
 * A method for defining the routes - https://ktor.io/servers/structure.html#extracting-routes
 */
private fun Application.router() {
    log.info("Route tracing enabled? $routeTracing")
    routing {
        // If enabled, will print out a tree of the routes that were matched while trying to process the URI
        if (routeTracing) {
            trace { application.log.trace(it.buildText()) }
        }

        get("/") {
            call.respondText("Hello World!")
        }

        get("/info") {
            // The `respondHtml` extension method is available at the `ktor-html-builder` artifact.
            // It provides a DSL for building HTML to a Writer, potentially in a chunked way.
            // More information about this DSL: https://ktor.io/servers/features/templates/html-dsl.html
            call.respondHtml {
                head {
                    title { +"Ktor: Jib" }
                }
                body {
                    val runtime = Runtime.getRuntime()
                    h1 { +"Hello from Ktor sample application built with Jib" }
                    val runtimeString = "Runtime.getRuntime()"
                    p { +"$runtimeString.availableProcessors(): ${runtime.availableProcessors()}" }
                    p { +"$runtimeString.freeMemory(): ${runtime.freeMemory()}" }
                    p { +"$runtimeString.totalMemory(): ${runtime.totalMemory()}" }
                    p { +"$runtimeString.maxMemory(): ${runtime.maxMemory()}" }
                    p { +"System.getProperty(\"user.name\"): ${System.getProperty("user.name")}" }
                }
            }
        }

        route("/healthz") {
            // Some simple toy health check example
            post("/") {
                when(call.receiveOrNull<String>()) {
                    "infect" -> {
                        healthy = false
                        call.respond(HttpStatusCode.Accepted)
                    }
                    "heal" -> {
                        healthy = true
                        call.respond(HttpStatusCode.Accepted)
                    }
                    else -> {
                        // Showcase the StatusPage installation feature
                        throw IllegalAccessException("POST /healthz only accepts 'infect' or 'heal'")
                    }
                }
            }
            get("/") {
                var txt = "ok"
                var status = HttpStatusCode.OK
                if (!healthy) {
                    txt = "failure"
                    status = HttpStatusCode.ServiceUnavailable
                }
                call.respondText(txt.toUpperCase(), status = status)
            }
        }
    }
}

/**
 * A method for installing features - https://ktor.io/servers/features.html#installing
 */
private fun Application.init() {
    // This adds automatically Date and Server headers to each response, and would allow you to configure
    // additional headers served to each response.
    install(DefaultHeaders)

    // This allows handling exceptions and status codes in a specific way.
    install(StatusPages) {
        // For all thrown Exceptions, log, and return the message of it as text
        exception<Throwable> { e ->
            val message = e.localizedMessage
            application.log.error(message)
            call.respondText(message,
                    ContentType.Text.Plain, HttpStatusCode.InternalServerError)
        }
    }

    // This uses use the logger to log every call (request/response)
    install(CallLogging)

    // Conditionally enable the Metrics API
    log.info("Metrics Registry enabled? $metricsEnabled")
    if (metricsEnabled) {
        install(Metrics) {
            JmxReporter.forRegistry(registry)
                    .convertRatesTo(TimeUnit.SECONDS)
                    .convertDurationsTo(TimeUnit.MILLISECONDS)
                    .build()
                    .start()
        }
    }
}