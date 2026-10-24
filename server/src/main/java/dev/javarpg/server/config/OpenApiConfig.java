package dev.javarpg.server.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The API description (OpenAPI) of the game server, generated from the controllers and the response records.
 * Wording and examples come from openapi/*.yaml; see OperationDocs and SchemaDocs. The server list is the
 * relative address "/" so the served description does not depend on how it was reached and stays identical
 * to the committed copy in docs/api/openapi.yaml.
 */
@Configuration
public class OpenApiConfig {

    private static final String OVERVIEW = """
            The JavaRPG game server. A player walks a village, talks to characters, and solves small Java quests \
            by sending real Java code that the server compiles and runs in an isolated sandbox.

            **Players.** There are no accounts. Creating a player returns a player id (a UUID) and that id is the \
            only credential: keep it private and send it in the address of every player operation.

            **Errors.** Every failure has the same body, {"error": {"code": "...", "message": "..."}}. Show the \
            message to the player and branch on the code. The ErrorBody schema lists every code with its HTTP status.

            **Hidden data.** The server decides every outcome; the client only draws it. Hidden test expectations \
            are never returned, and a quest's explanation, reference solution and world effect stay empty until \
            the player has completed that quest. Fields that behave this way are marked in the schemas.

            **Limits.** Code is limited to 20000 characters and requests to 64 KiB. Creating players, running and \
            submitting have per-minute limits per address, and a player can have one run or submit going at a time.""";

    @Bean
    OpenAPI javaRpgOpenApi() {
        return new OpenAPI()
                .info(new Info().title("JavaRPG API").version("v1").description(OVERVIEW))
                .servers(List.of(new Server().url("/").description("The server that serves this description")))
                .tags(List.of(
                        new Tag().name("health").description("Is the server up, and how does it run player code?"),
                        new Tag().name("world").description("The map, walking the hero and talking to characters."),
                        new Tag().name("players").description("Creating a player and reading a player's state."),
                        new Tag().name("quests").description("Listing quests, opening one, running and submitting code, and hints.")));
    }

    @Bean
    OpenApiCustomizer schemaDocs(OpenApiWording wording) {
        return new SchemaDocs(wording);
    }

    @Bean
    OperationCustomizer operationDocs(OpenApiWording wording) {
        return new OperationDocs(wording);
    }
}
