package example.vertx;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainVerticle extends AbstractVerticle {

    public static void main(String[] args) {
        Vertx.vertx().deployVerticle(new MainVerticle());
    }

    private final Logger logger = LoggerFactory.getLogger(MainVerticle.class);

    @Override
    public void start(Future<Void> startFuture) {
        Router router = Router.router(vertx);

        router.get("/").handler(this::hello);
        router.get("/time").handler(this::now);

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(8080, asyncStart -> {
                    if (asyncStart.succeeded()) {
                        startFuture.complete();
                        logger.info("HTTP server running on port 8080");
                    } else {
                        logger.error("Woops", asyncStart.cause());
                        startFuture.fail(asyncStart.cause());
                    }
                });
    }

    private void hello(RoutingContext context) {
        logger.info("Hello request from {}", context.request().remoteAddress());
        context.response()
                .putHeader("Content-Type", "text/plain")
                .end("Hello from Vert.x!");
    }

    private void now(RoutingContext context) {
        logger.info("Time request from {}", context.request().remoteAddress());
        JsonObject data = new JsonObject()
                .put("powered-by", "vertx")
                .put("current-time", System.currentTimeMillis());
        context.response()
                .putHeader("Content-Type", "application/json")
                .end(data.encode());
    }
}
