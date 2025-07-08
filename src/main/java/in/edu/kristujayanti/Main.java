package in.edu.kristujayanti;

import in.edu.kristujayanti.handlers.SampleHandler;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import redis.clients.jedis.Jedis;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {
        VertxOptions options = new VertxOptions();
        Vertx vertx = Vertx.vertx(options);
        Jedis jedis = new Jedis("localhost", 6379);
        DeploymentOptions deploymentOptions = new DeploymentOptions();

        vertx.deployVerticle(new SampleHandler(), deploymentOptions)
                .onSuccess(id -> System.out.println("Verticle deployed successfully with ID: " + id))
                .onFailure(err -> System.err.println("Deployment failed: " + err.getMessage()));


        //Initialize Vertx Here
        //Establish Mongodb Connection Here
    }
}