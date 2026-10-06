package com.educationerp.search.web;

import com.educationerp.common.api.ApiResponse;
import com.educationerp.search.GlobalSearchService;
import com.educationerp.search.SearchDtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * The search box.
 *
 * <p>One endpoint for everything, because one box is what a person types into. The result is
 * already filtered to what the caller may read, so nothing here needs to hide anything.
 */
@RestController
@RequestMapping("/api/v1/search")
public class GlobalSearchController {

    private final GlobalSearchService search;

    public GlobalSearchController(GlobalSearchService search) {
        this.search = search;
    }

    /** Everything, as a flat list, most likely first. */
    @GetMapping
    public ApiResponse<SearchDtos.SearchResponse> search(@RequestParam String q) {
        List<SearchDtos.SearchHit> hits = search.search(q);
        return ApiResponse.ok(new SearchDtos.SearchResponse(q.trim(), hits.size(), hits));
    }

    /** The same results grouped by kind of record, for a panel with a heading per section. */
    @GetMapping("/grouped")
    public ApiResponse<Map<String, List<SearchDtos.SearchHit>>> grouped(@RequestParam String q) {
        return ApiResponse.ok(search.grouped(q));
    }

    /** One kind of record, for a screen that wants students and nothing else. */
    @GetMapping("/{source}")
    public ApiResponse<List<SearchDtos.SearchHit>> within(
            @PathVariable SearchDtos.Source source,
            @RequestParam String q) {
        return ApiResponse.ok(search.search(source, q));
    }
}
