package com.wildme.wildbook_lite.stats;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wildme.wildbook_lite.stats.dto.ProjectStatsResponse;

@RestController
@RequestMapping("/api/projects/{projectId}/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping
    public ProjectStatsResponse projectStats(@PathVariable Long projectId) {
        return statsService.projectStats(projectId);
    }
}
