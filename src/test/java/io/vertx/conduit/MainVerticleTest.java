package io.vertx.conduit;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


@DisplayName("Spike Test")
@ExtendWith(VertxExtension.class)
class MainVerticleTest {

  @Test
  @DisplayName("Server Started Test")
  void testServerStart(Vertx vertx, VertxTestContext testContext) {
    WebClient webClient = WebClient.create(vertx);

    Checkpoint deploymentCheckpoint = testContext.checkpoint();
    Checkpoint requestCheckpoint = testContext.checkpoint();

    vertx.deployVerticle(new MainVerticle(), testContext.succeeding(id -> {
      deploymentCheckpoint.flag();

      webClient.get(8080, "localhost", "/")
        .as(BodyCodec.string())
        .send(testContext.succeeding(resp -> {
          testContext.verify(() -> {
            assertEquals(200, resp.statusCode());
            assertEquals("Hello, CodeOne!", resp.body());
            requestCheckpoint.flag();
          });
        }));
    }));
  }

  @Test
  @DisplayName("Retrieve User")
  void testRetreivingUser(Vertx vertx, VertxTestContext tc) {
    WebClient webClient = WebClient.create(vertx);

    Checkpoint deploymentCheckpoint = tc.checkpoint();
    Checkpoint requestCheckpoint = tc.checkpoint();

    vertx.deployVerticle(new MainVerticle(), tc.succeeding(id -> {
      deploymentCheckpoint.flag();

      webClient.get(8080, "localhost", "/api/user")
        .as(BodyCodec.jsonObject())
        .send(tc.succeeding(resp -> {
          tc.verify(() -> {
            assertEquals(200, resp.statusCode());
            System.out.println(resp.body());
            User user = new User(resp.body());
            System.out.println(user);
            assertNotNull(user);
            assertEquals("Jacob", user.getUsername());
            assertEquals("jake@jake.jake", user.getEmail());
            assertEquals( "I work at state farm", user.getBio());
            requestCheckpoint.flag();
          });
        }));
    }));

  }


}
