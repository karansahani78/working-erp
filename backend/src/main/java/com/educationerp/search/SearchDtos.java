package com.educationerp.search;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the search box returns.
 *
 * <p>A hit says what it is, which record it is and how it can be opened, rather than only
 * offering the label. A search result a person cannot act on is a dead end.
 */
public final class SearchDtos {

    /** The kinds of record that can be searched. */
    public enum Source {
        STUDENT,
        EMPLOYEE,
        APPLICANT,
        COURSE,
        INVOICE,
        PAYMENT,
        BOOK,
        ASSET
    }

    public record SearchHit(
            Source source,
            String sourceLabel,
            UUID id,
            String heading,
            String detail,
            String reference) {
    }

    /** One search, with the term echoed so a caller can show what was searched for. */
    public record SearchResponse(String term, int count, List<SearchHit> results) {
    }

    private SearchDtos() {
    }

    static String text(Map<String, Object> row, String column) {
        Object value = row.get(column);
        return value == null ? "" : value.toString();
    }
}