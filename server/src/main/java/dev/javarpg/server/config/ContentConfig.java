package dev.javarpg.server.config;

import dev.javarpg.core.contract.QuestRepository;
import dev.javarpg.core.world.WorldDef;
import dev.javarpg.server.repository.FileQuestRepository;
import dev.javarpg.server.repository.WorldLoader;
import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Game content beans: the world map and the quests, both loaded and validated at start-up. */
@Configuration
public class ContentConfig {

    @Bean
    public WorldDef worldDef(JavaRpgProperties props) {
        return new WorldLoader().load(Path.of(props.getWorld().getFile()));
    }

    @Bean
    public QuestRepository questRepository(JavaRpgProperties props, WorldDef worldDef) {
        return new FileQuestRepository(Path.of(props.getContent().getDir()), worldDef);
    }
}
