package com.wildme.wildbook_lite.search;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wildme.wildbook_lite.search.dto.SearchResponse;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * GET /api/search/encounters?projectId=1&q=humpback%20whale&limit=20
     *
     * `q` supports websearch syntax: phrases in quotes, OR keyword, leading - to exclude:
     *   "humpback whale" -calf
     */
    @GetMapping("/encounters")
    public SearchResponse encounters(
            @RequestParam Long projectId,
            @RequestParam String q,
            @RequestParam(defaultValue = "20") int limit) {
        return searchService.searchEncounters(projectId, q, limit);
    }
}
