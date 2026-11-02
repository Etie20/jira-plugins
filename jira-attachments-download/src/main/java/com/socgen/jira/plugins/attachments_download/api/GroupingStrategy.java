package com.socgen.jira.plugins.attachments_download.api;

/**
 * Strategy for organizing and classifying attachment files inside the generated ZIP archive.
 */
public enum GroupingStrategy {

    /**
     * Group by project and issue:
     * Structure: [ProjectKey]/[IssueKey] - [SanitizedSummary]/[filename]
     */
    BY_PROJECT_AND_ISSUE,

    /**
     * Group directly by issue key:
     * Structure: [IssueKey]/[filename]
     */
    BY_ISSUE,

    /**
     * Group by issue status then issue key:
     * Structure: [StatusName]/[IssueKey]/[filename]
     */
    BY_STATUS_AND_ISSUE,

    /**
     * Flat structure with issue key prefix:
     * Structure: [IssueKey]_[filename]
     */
    FLAT_WITH_PREFIX;

    public static GroupingStrategy fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return BY_PROJECT_AND_ISSUE;
        }
        for (GroupingStrategy strategy : values()) {
            if (strategy.name().equalsIgnoreCase(value.trim())) {
                return strategy;
            }
        }
        return BY_PROJECT_AND_ISSUE;
    }
}
