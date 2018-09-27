package io.vertx.conduit;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jdbc.JDBCAuth;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

import java.util.List;
import java.util.stream.Collectors;

public class MainVerticle extends AbstractVerticle {

  private JDBCAuth authProvider;

  private JDBCClient jdbcClient;

  @Override
  public void start(Future<Void> future) {

    jdbcClient = JDBCClient.createShared(vertx, new JsonObject()
      .put("url", "jdbc:hsqldb:file:db/spike")
      .put("driver_class", "org.hsqldb.jdbcDriver")
      .put("max_pool_size", 30));

    authProvider = JDBCAuth.create(vertx, jdbcClient);

    Router baseRouter = Router.router(vertx);
    baseRouter.route("/").handler(this::indexHandler);

    Router apiRouter = Router.router(vertx);
    apiRouter.route("/*").handler(BodyHandler.create());
    apiRouter.get("/users").handler(this::allUsersHandler);
    apiRouter.post("/users/login").handler(this::loginHandler);

    baseRouter.mountSubRouter("/api", apiRouter);

    vertx.createHttpServer()
      .requestHandler(baseRouter::accept)
      .listen(8080, result ->{
        if (result.succeeded()) {
          future.complete();
        }else{
          future.fail(result.cause());
        }
      });
  }

  private void allUsersHandler(RoutingContext context) {
    jdbcClient.getConnection(ar -> {
      if (ar.succeeded()) {
        SQLConnection connection = ar.result();
        connection.query("select * from user", res -> {
          connection.close();
          if (res.succeeded()) {
            JsonArray users = new JsonArray(res.result()
              .getResults()
              .stream()
              .map(json -> json.getString(0))
              .sorted()
              .collect(Collectors.toList()));
            HttpServerResponse response = context.response();
            response.setStatusCode(200)
              .putHeader("Content-Type", "application/json; charset=utf-8")
              .putHeader("Content-Length", String.valueOf(users.toString().length()))
              .end(users.encode());
          }else{
            HttpServerResponse response = context.response();
            response.putHeader("Content-Type", "text/html").end(res.cause().toString());
          }
        });
      }else{
        HttpServerResponse response = context.response();
        response.putHeader("Content-Type", "text/html").end(ar.cause().toString());
      }
    });
  }

  private void loginHandler(RoutingContext context) {

    String salt = authProvider.generateSalt();
    String password = authProvider.computeHash("jakejake", salt);

    JsonObject returnValue = new JsonObject()
      .put("login", new JsonObject()
        .put("password", password)
        .put("salt", salt));
    System.out.println(returnValue);

    HttpServerResponse response = context.response();
    response.setStatusCode(200)
      .putHeader("Content-Type", "application/json; charset=utf-8")
      .putHeader("Content-Length", String.valueOf(returnValue.toString().length()))
      .end(returnValue.toString());

  }

  private void indexHandler(RoutingContext context) {
    HttpServerResponse response = context.response();
    response.putHeader("Content-Type", "text/html").end("Hello, CodeOne!");
  }

}
