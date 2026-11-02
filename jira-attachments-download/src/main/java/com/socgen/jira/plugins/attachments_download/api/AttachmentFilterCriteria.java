package com.socgen.jira.plugins.attachments_download.api;

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * Encapsulates all filter, sorting and packaging criteria for bulk attachment downloads.
 */
public class AttachmentFilterCriteria {

    private String projectKey;
    private String status;
    private String issueType;
    private String customJql;
    private Set<String> fileExtensions = new HashSet<>();
    private Long minSizeBytes;
    private Long maxSizeBytes;
    private Date attachmentCreatedAfter;
    private Date attachmentCreatedBefore;
    private int maxIssues = 200;
    private GroupingStrategy groupingStrategy = GroupingStrategy.BY_PROJECT_AND_ISSUE;
    private boolean includeManifest = true;

    public AttachmentFilterCriteria() {
    }

    public String getProjectKey() {
        return projectKey;
    }

    public void setProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getIssueType() {
        return issueType;
    }

    public void setIssueType(String issueType) {
        this.issueType = issueType;
    }

    public String getCustomJql() {
        return customJql;
    }

    public void setCustomJql(String customJql) {
        this.customJql = customJql;
    }

    public Set<String> getFileExtensions() {
        return Collections.unmodifiableSet(fileExtensions);
    }

    public void setFileExtensions(Set<String> fileExtensions) {
        this.fileExtensions = new HashSet<>();
        if (fileExtensions != null) {
            for (String ext : fileExtensions) {
                if (ext != null && !ext.trim().isEmpty()) {
                    String clean = ext.trim().toLowerCase();
                    if (clean.startsWith(".")) {
                        clean = clean.substring(1);
                    }
                    this.fileExtensions.add(clean);
                }
            }
        }
    }

    public void addFileExtension(String extension) {
        if (extension != null && !extension.trim().isEmpty()) {
            String clean = extension.trim().toLowerCase();
            if (clean.startsWith(".")) {
                clean = clean.substring(1);
            }
            this.fileExtensions.add(clean);
        }
    }

    public Long getMinSizeBytes() {
        return minSizeBytes;
    }

    public void setMinSizeBytes(Long minSizeBytes) {
        this.minSizeBytes = minSizeBytes;
    }

    public Long getMaxSizeBytes() {
        return maxSizeBytes;
    }

    public void setMaxSizeBytes(Long maxSizeBytes) {
        this.maxSizeBytes = maxSizeBytes;
    }

    public Date getAttachmentCreatedAfter() {
        return attachmentCreatedAfter;
    }

    public void setAttachmentCreatedAfter(Date attachmentCreatedAfter) {
        this.attachmentCreatedAfter = attachmentCreatedAfter;
    }

    public Date getAttachmentCreatedBefore() {
        return attachmentCreatedBefore;
    }

    public void setAttachmentCreatedBefore(Date attachmentCreatedBefore) {
        this.attachmentCreatedBefore = attachmentCreatedBefore;
    }

    public int getMaxIssues() {
        return maxIssues;
    }

    public void setMaxIssues(int maxIssues) {
        this.maxIssues = Math.max(1, Math.min(maxIssues, 1000));
    }

    public GroupingStrategy getGroupingStrategy() {
        return groupingStrategy;
    }

    public void setGroupingStrategy(GroupingStrategy groupingStrategy) {
        this.groupingStrategy = groupingStrategy != null ? groupingStrategy : GroupingStrategy.BY_PROJECT_AND_ISSUE;
    }

    public boolean isIncludeManifest() {
        return includeManifest;
    }

    public void setIncludeManifest(boolean includeManifest) {
        this.includeManifest = includeManifest;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final AttachmentFilterCriteria criteria = new AttachmentFilterCriteria();

        public Builder projectKey(String projectKey) {
            criteria.setProjectKey(projectKey);
            return this;
        }

        public Builder status(String status) {
            criteria.setStatus(status);
            return this;
        }

        public Builder issueType(String issueType) {
            criteria.setIssueType(issueType);
            return this;
        }

        public Builder customJql(String customJql) {
            criteria.setCustomJql(customJql);
            return this;
        }

        public Builder fileExtensions(Set<String> extensions) {
            criteria.setFileExtensions(extensions);
            return this;
        }

        public Builder addExtension(String extension) {
            criteria.addFileExtension(extension);
            return this;
        }

        public Builder minSizeBytes(Long minSizeBytes) {
            criteria.setMinSizeBytes(minSizeBytes);
            return this;
        }

        public Builder maxSizeBytes(Long maxSizeBytes) {
            criteria.setMaxSizeBytes(maxSizeBytes);
            return this;
        }

        public Builder createdAfter(Date date) {
            criteria.setAttachmentCreatedAfter(date);
            return this;
        }

        public Builder createdBefore(Date date) {
            criteria.setAttachmentCreatedBefore(date);
            return this;
        }

        public Builder maxIssues(int maxIssues) {
            criteria.setMaxIssues(maxIssues);
            return this;
        }

        public Builder groupingStrategy(GroupingStrategy groupingStrategy) {
            criteria.setGroupingStrategy(groupingStrategy);
            return this;
        }

        public Builder includeManifest(boolean includeManifest) {
            criteria.setIncludeManifest(includeManifest);
            return this;
        }

        public AttachmentFilterCriteria build() {
            return criteria;
        }
    }
}
