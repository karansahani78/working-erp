package com.educationerp.document.storage;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/** How documents are stored, and how much of them is allowed. */
@Validated
@ConfigurationProperties(prefix = "erp.storage")
public class StorageProperties {

    public static final String LOCAL = "LOCAL";

    @NotBlank
    private String localRoot = "./data/documents";

    /**
     * The ceiling on one upload.
     *
     * <p>Checked before anything is written, because the point of a limit is to refuse the
     * upload rather than to clean up afterwards.
     */
    @Min(1)
    private long maxDocumentBytes = 25L * 1024 * 1024;

    /**
     * Content types we are willing to store.
     *
     * <p>A list rather than a flag: what counts as acceptable changes between a school that
     * only scans PDFs and one that accepts photographs from teachers' phones.
     */
    private List<String> allowedContentTypes = List.of(
            "application/pdf",
            "image/png",
            "image/jpeg",
            "image/webp",
            "image/gif",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
            "text/plain",
            "text/csv");

    public String getLocalRoot() {
        return localRoot;
    }

    public void setLocalRoot(String localRoot) {
        this.localRoot = localRoot;
    }

    public long getMaxDocumentBytes() {
        return maxDocumentBytes;
    }

    public void setMaxDocumentBytes(long maxDocumentBytes) {
        this.maxDocumentBytes = maxDocumentBytes;
    }

    public List<String> getAllowedContentTypes() {
        return allowedContentTypes;
    }

    public void setAllowedContentTypes(List<String> allowedContentTypes) {
        this.allowedContentTypes = allowedContentTypes;
    }
}
