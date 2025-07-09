package in.edu.kristujayanti.handlers;

import in.edu.kristujayanti.services.SampleService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
public class SampleHandler extends AbstractVerticle {
    public void start(Promise<Void> startPromise) {
        HttpServer server = vertx.createHttpServer();
        Vertx vertx = Vertx.vertx();
        Router router = Router.router(vertx);
//        router.route().handler(BodyHandler.create());
        SampleService smp= new SampleService();

        router.post("/upload").handler(BodyHandler.create());
        router.post("/upload").handler(smp::handleupload);
        router.get("/getqp").handler(smp::getqp);
        router.post("/usersign").handler(smp::usersign);






        Future<HttpServer> fut=server.requestHandler(router).listen(8080);
        if(fut.succeeded()){
            System.out.println("Server running at http://localhost:8080");
        }
        else{
            System.out.println("server failed to run.");
        }
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        System.out.println("Server stopping...");
        stopPromise.complete();
    }

    //Handler Logic And Initialize the Service Here
}
