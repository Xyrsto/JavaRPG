package dev.javarpg.server.controller;

import dev.javarpg.core.contract.QuestRepository;
import dev.javarpg.server.config.JavaRpgProperties;
import dev.javarpg.server.config.SandboxSafety;
import dev.javarpg.server.dto.ApiDtos.HealthView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final JavaRpgProperties props;
    private final QuestRepository repository;

    public HealthController(JavaRpgProperties props, QuestRepository repository) {
        this.props = props;
        this.repository = repository;
    }

    @GetMapping("/health")
    public HealthView health() {
        String mode = props.getSandbox().getMode();
        return new HealthView("UP", mode, SandboxSafety.isSafe(mode), repository.findAllOrdered().size(),
                SandboxSafety.warningFor(mode));
    }
}
