package server;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.PfxOptions;

import static io.vertx.core.http.ClientAuth.REQUIRED;

public class DemoServer {

    public static void main(String[] args) {
        var options = new HttpServerOptions()
                .setPort(8443)
                .setSsl(true)
                .setKeyCertOptions(new PfxOptions()
                        .setPath("secrets/server-tls-keystore.p12")
                        .setPassword("password"))
                .setTrustOptions(new PfxOptions()
                        .setPath("secrets/server-truststore.p12")
                        .setPassword("password"))
                .setClientAuth(REQUIRED);

        var vertx = Vertx.vertx();
        var requestHandler = new RequestHandler(vertx, new MessageService());
        vertx.createHttpServer(options)
                .requestHandler(requestHandler)
                .listen()
                .onSuccess(server -> System.out.println("Server started"))
                .onFailure(throwable -> {
                    throwable.printStackTrace();
                    vertx.close().onSuccess(event -> System.out.println("Server stopped"));
                });
    }
}