package dev.javarpg.server.controller;

import dev.javarpg.server.dto.ApiDtos.HintResponse;
import dev.javarpg.server.dto.ApiDtos.QuestDetailView;
import dev.javarpg.server.dto.ApiDtos.QuestListResponse;
import dev.javarpg.server.dto.ApiDtos.RunResponse;
import dev.javarpg.server.dto.ApiDtos.SourceRequest;
import dev.javarpg.server.dto.ApiDtos.SubmitResponse;
import dev.javarpg.server.service.QuestViewService;
import dev.javarpg.server.service.SubmissionService;
import dev.javarpg.server.util.InputChecks;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class QuestController {

    private final QuestViewService views;
    private final SubmissionService submissions;

    public QuestController(QuestViewService views, SubmissionService submissions) {
        this.views = views;
        this.submissions = submissions;
    }

    @GetMapping("/players/{playerId}/quests")
    public QuestListResponse list(@PathVariable String playerId) {
        InputChecks.requirePlayerId(playerId);
        return views.list(playerId);
    }

    @GetMapping("/players/{playerId}/quests/{questId}")
    public QuestDetailView detail(@PathVariable String playerId, @PathVariable String questId) {
        InputChecks.requirePlayerId(playerId);
        InputChecks.requireQuestId(questId);
        return views.detail(playerId, questId);
    }

    @PostMapping("/players/{playerId}/quests/{questId}/run")
    public RunResponse run(@PathVariable String playerId, @PathVariable String questId,
                           @RequestBody SourceRequest body) {
        InputChecks.requirePlayerId(playerId);
        InputChecks.requireQuestId(questId);
        return submissions.run(playerId, questId, body.source());
    }

    @PostMapping("/players/{playerId}/quests/{questId}/submit")
    public SubmitResponse submit(@PathVariable String playerId, @PathVariable String questId,
                                 @RequestBody SourceRequest body) {
        InputChecks.requirePlayerId(playerId);
        InputChecks.requireQuestId(questId);
        return submissions.submit(playerId, questId, body.source());
    }

    @PostMapping("/players/{playerId}/quests/{questId}/hints/next")
    public HintResponse hint(@PathVariable String playerId, @PathVariable String questId) {
        InputChecks.requirePlayerId(playerId);
        InputChecks.requireQuestId(questId);
        return submissions.nextHint(playerId, questId);
    }
}
