package dev.javarpg.server.controller;

import dev.javarpg.server.config.JavaRpgProperties;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The root address: the web client's page when it is packaged with the server, otherwise a short page that says
 * how to build it. The page itself is never cached for long; its fingerprinted files under /assets are.
 */
@RestController
public class RootController {

    private static final MediaType HTML = new MediaType("text", "html", StandardCharsets.UTF_8);
    private static final String NOT_BUILT = "classpath:/web/client-not-built.html";

    private final ResourceLoader resources;
    private final JavaRpgProperties props;

    public RootController(ResourceLoader resources, JavaRpgProperties props) {
        this.resources = resources;
        this.props = props;
    }

    @GetMapping("/")
    public ResponseEntity<Resource> root() {
        Resource index = resources.getResource(props.getWeb().getIndex());
        Resource page = index.exists() ? index : resources.getResource(NOT_BUILT);
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).contentType(HTML).body(page);
    }
}
