package io.vertx.conduit;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jdbc.JDBCAuth;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

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
    authProvider.setAuthenticationQuery("SELECT PASSWORD, PASSWORD_SALT FROM USER WHERE EMAIL = ?");

    Router baseRouter = Router.router(vertx);
    baseRouter.route("/").handler(this::indexHandler);

    Router apiRouter = Router.router(vertx);
    apiRouter.route("/*").handler(BodyHandler.create());
    apiRouter.get("/users").handler(this::allUsersHandler);
    apiRouter.post("/users/login").handler(this::loginHandler);
    apiRouter.get("/user").handler(this::usersHandler);

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

  private void usersHandler(RoutingContext context) {

    final String username = "Jacob";

    jdbcClient.getConnection(ar -> {

      SQLConnection connection = ar.result();
      select(username, connection, result -> {
        if (result.succeeded()) {
          User user = result.result();
          context.response()
            .setStatusCode(200)
            .putHeader("content-type", "application/json; charset=utf-8")
            .end(Json.encodePrettily(result.result()));
        }else{
          context.response()
            .setStatusCode(404).end();
        }
      });

    });
  }

  private void select(String username, SQLConnection connection, Handler<AsyncResult<User>> resultHandler) {
    connection.queryWithParams("SELECT * FROM USER WHERE \"username\"=?", new JsonArray().add(username), ar -> {
      if (ar.failed()) {
        resultHandler.handle(Future.failedFuture("User not found"));
      } else {
        if (ar.result().getNumRows() >= 1) {
          JsonObject user = ar.result().getRows().get(0);
          resultHandler.handle(Future.succeededFuture(new User(ar.result().getRows().get(0))));
        } else {
          resultHandler.handle(Future.failedFuture("User not found"));
        }
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
              .map(json -> json.getString(1))
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

//    String salt = authProvider.generateSalt();
//    String password = authProvider.computeHash("jakejake", salt);

    JsonObject user = context.getBodyAsJson().getJsonObject("user");
    user.put("username", "placeholder");

    JsonObject authInfo = new JsonObject()
      .put("username", user.getString("email"))
      .put("password", user.getString("password"));
    System.out.println(user);

    HttpServerResponse response = context.response();

    authProvider.authenticate(authInfo, ar -> {
      if (ar.succeeded()) {


        JsonObject returnValue = new JsonObject()
        .put("user", new JsonObject()
          .put("email", "jake@jake.jake")
          .put("password", "jakejake")
          .put("token", "jwt.token.here")
          .put("username", "jake")
          .put("bio", "I work at statefarm")
          .put("image", ""));
        System.out.println(returnValue);

        response.setStatusCode(200)
        .putHeader("Content-Type", "application/json; charset=utf-8")
        .putHeader("Content-Length", String.valueOf(returnValue.toString().length()))
        .end(returnValue.encode());
      }else{
        response.setStatusCode(200)
          .putHeader("Content-Type", "text/html")
          .end("Authentication Failed: " + ar.cause());
      }
    });

/*
    authProvider.authenticate(authInfo, (AsyncResult<User> ar) -> {
      if (ar.succeeded()) {
        System.out.println("Authentication succeeded");
        jdbcClient.getConnection(ar2 ->{
          if (ar2.succeeded()) {
            SQLConnection connection = ar2.result();
            connection.queryWithParams("SELECT * FROM USER WHERE EMAIL = ?", new JsonArray().add(user.getString("email")), fetch ->{
              if (fetch.succeeded()) {
                JsonObject res = new JsonObject();
                ResultSet resultSet = fetch.result();
                if (resultSet.getNumRows() == 0) {
                  res.put("found", false);
                } else {
                  res.put("found", true);
                  JsonArray row = resultSet.getResults().get(0);
                  res.put("username", row.getString(1));
                  res.put("email", row.getString(2));
                  res.put("bio", row.getString(3));
                  res.put("image", row.getString(4));
                  response.setStatusCode(200)
                    .putHeader("Content-Type", "application/json; charset=utf-8")
                    .putHeader("Content-Length", String.valueOf(res.toString().length()))
                    .end(res.encode());
                }
                response.setStatusCode(200)
                  .putHeader("Content-Type", "text/html")
                  .putHeader("Content-Length", String.valueOf("Not Found".length()))
                  .end(fetch.cause().toString());
              } else{
                response.setStatusCode(200)
                  .putHeader("Content-Type", "text/html")
                  .end(fetch.cause().toString());
              }
            });
          }else{
            response.setStatusCode(200)
              .putHeader("Content-Type", "text/html")
              .end(ar2.cause().toString());
          }
        });

      }else{
        response.setStatusCode(200)
          .putHeader("Content-Type", "text/html")
          .end("Authentication Failed: " + ar.cause());
      }
    });
*/
  }

  private void indexHandler(RoutingContext context) {
    HttpServerResponse response = context.response();
    response.putHeader("Content-Type", "text/html").end("Hello, CodeOne!");
  }

}
