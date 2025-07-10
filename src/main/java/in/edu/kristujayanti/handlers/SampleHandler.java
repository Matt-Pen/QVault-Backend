package in.edu.kristujayanti.handlers;

import in.edu.kristujayanti.services.SampleService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;

public class SampleHandler extends AbstractVerticle {
    public void start(Promise<Void> startPromise) {
        HttpServer server = vertx.createHttpServer();

        Router router = Router.router(vertx);
        router.route().handler(CorsHandler.create("*")
                .allowedMethod(HttpMethod.OPTIONS)
                .allowedMethod(HttpMethod.POST)
                .allowedMethod(HttpMethod.GET)
                .allowedMethod(HttpMethod.DELETE)
                .allowedMethod(HttpMethod.PATCH)
                .allowedHeader("Content-Type")
                .allowedHeader("Authorization"));

        router.route().handler(BodyHandler.create());
        SampleService smp= new SampleService();

        router.post("/usersign").handler(smp::usersign);
        router.post("/userlog").handler(smp::userlog);
        router.post("/resetpass").handler(smp::resetpass);
        router.post("/upload").handler(BodyHandler.create());
        router.post("/upload").handler(smp::handleupload);
        router.get("/getqp").handler(smp::getqp2);
        router.delete("/delqp").handler(smp::delqp);
        router.get("/test").handler(ctx->
                ctx.response().end("Heloooo"));
        router.get("/courseclick").handler(smp::searchfilterpage);
        router.get("/filterclick").handler(smp::searchfilterpagefilter);







        Future<HttpServer> fut=server.requestHandler(router).listen(8080,"0.0.0.0");
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
