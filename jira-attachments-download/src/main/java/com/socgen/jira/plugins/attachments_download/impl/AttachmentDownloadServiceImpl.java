package com.socgen.jira.plugins.attachments_download.impl;

import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.issue.AttachmentManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.attachment.Attachment;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.query.Query;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.web.bean.PagerFilter;
import com.socgen.jira.plugins.attachments_download.api.AttachmentDownloadService;
import com.socgen.jira.plugins.attachments_download.api.AttachmentExportSummary;
import com.socgen.jira.plugins.attachments_download.api.AttachmentFilterCriteria;
import com.socgen.jira.plugins.attachments_download.api.ExportableAttachment;
import com.socgen.jira.plugins.attachments_download.api.GroupingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Enterprise implementation of {@link AttachmentDownloadService} optimized for Jira Data Center.
 * Ensures cluster-safety via {@link AttachmentManager#streamAttachmentContent(Attachment, com.atlassian.jira.util.io.InputStreamConsumer)}
 * and strict user permission checks.
 */
public class AttachmentDownloadServiceImpl implements AttachmentDownloadService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentDownloadServiceImpl.class);
    private static final int BUFFER_SIZE = 8192;

    private final SearchService searchService;
    private final AttachmentManager attachmentManager;
    private final PermissionManager permissionManager;

    public AttachmentDownloadServiceImpl(final SearchService searchService,
                                         final AttachmentManager attachmentManager,
                                         final PermissionManager permissionManager) {
        this.searchService = Objects.requireNonNull(searchService, "searchService must not be null");
        this.attachmentManager = Objects.requireNonNull(attachmentManager, "attachmentManager must not be null");
        this.permissionManager = Objects.requireNonNull(permissionManager, "permissionManager must not be null");
    }

    @Override
    public List<ExportableAttachment> collectAttachments(AttachmentFilterCriteria criteria, ApplicationUser user) throws Exception {
        Map<ExportableAttachment, Attachment> pairMap = resolveMatchingAttachments(criteria, user);
        return new ArrayList<>(pairMap.keySet());
    }

    @Override
    public AttachmentExportSummary preview(AttachmentFilterCriteria criteria, ApplicationUser user) throws Exception {
        String jql = buildJqlQuery(criteria);
        Map<ExportableAttachment, Attachment> pairMap = resolveMatchingAttachments(criteria, user);

        List<ExportableAttachment> items = new ArrayList<>(pairMap.keySet());
        Set<String> distinctIssues = new HashSet<>();
        long totalSize = 0;
        for (ExportableAttachment item : items) {
            distinctIssues.add(item.getIssueKey());
            if (item.getFilesize() != null) {
                totalSize += item.getFilesize();
            }
        }

        return new AttachmentExportSummary(distinctIssues.size(), items.size(), totalSize, jql, items);
    }

    @Override
    public AttachmentExportSummary writeZipToStream(AttachmentFilterCriteria criteria, ApplicationUser user, OutputStream outputStream) throws IOException {
        String jql;
        Map<ExportableAttachment, Attachment> pairMap;
        try {
            jql = buildJqlQuery(criteria);
            pairMap = resolveMatchingAttachments(criteria, user);
        } catch (Exception e) {
            log.error("Failed to query attachments for user {}: {}", user != null ? user.getName() : "anonymous", e.getMessage(), e);
            throw new IOException("Failed to resolve attachments: " + e.getMessage(), e);
        }

        Set<String> distinctIssues = new HashSet<>();
        long totalBytes = 0;
        List<ExportableAttachment> items = new ArrayList<>(pairMap.keySet());
        for (ExportableAttachment item : items) {
            distinctIssues.add(item.getIssueKey());
            if (item.getFilesize() != null) {
                totalBytes += item.getFilesize();
            }
        }

        AttachmentExportSummary summary = new AttachmentExportSummary(
                distinctIssues.size(), items.size(), totalBytes, jql, items
        );

        ZipOutputStream zipOut = new ZipOutputStream(new BufferedOutputStream(outputStream));
        zipOut.setLevel(Deflater.DEFAULT_COMPRESSION);

        try {
            // Write manifest/summary file at the root of the ZIP
            if (criteria.isIncludeManifest()) {
                writeManifestEntry(zipOut, summary, criteria, user);
            }

            // Stream each attachment into the ZIP
            for (Map.Entry<ExportableAttachment, Attachment> entry : pairMap.entrySet()) {
                ExportableAttachment descriptor = entry.getKey();
                Attachment attachment = entry.getValue();

                ZipEntry zipEntry = new ZipEntry(descriptor.getArchivePath());
                if (descriptor.getCreated() != null) {
                    zipEntry.setTime(descriptor.getCreated().getTime());
                }

                zipOut.putNextEntry(zipEntry);

                // Stream directly using Jira DC AttachmentManager without loading into heap memory
                attachmentManager.streamAttachmentContent(attachment, in -> {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        zipOut.write(buffer, 0, len);
                    }
                    return null;
                });

                zipOut.closeEntry();
            }

            zipOut.finish();
            zipOut.flush();
        } finally {
            // Flush stream but do not close outputStream as it may be owned by response container
            try {
                zipOut.flush();
            } catch (IOException ignored) {
            }
        }

        return summary;
    }

    private Map<ExportableAttachment, Attachment> resolveMatchingAttachments(AttachmentFilterCriteria criteria, ApplicationUser user) throws Exception {
        String jql = buildJqlQuery(criteria);
        log.debug("Executing JQL for attachments download: {}", jql);

        SearchService.ParseResult parseResult = searchService.parseQuery(user, jql);
        if (!parseResult.isValid()) {
            throw new IllegalArgumentException("Invalid JQL query: " + parseResult.getErrors());
        }

        Query query = parseResult.getQuery();
        PagerFilter<?> pagerFilter = PagerFilter.newPageAlignedFilter(0, criteria.getMaxIssues());
        SearchResults<Issue> searchResults = searchService.search(user, query, pagerFilter);

        Map<ExportableAttachment, Attachment> result = new HashMap<>();
        Set<String> usedPathsInZip = new HashSet<>();

        for (Issue issue : searchResults.getResults()) {
            // Enforce Browse Project permission
            if (!permissionManager.hasPermission(ProjectPermissions.BROWSE_PROJECTS, issue, user)) {
                log.debug("User {} does not have permission to browse issue {}", user != null ? user.getName() : "anonymous", issue.getKey());
                continue;
            }

            List<Attachment> attachments = attachmentManager.getAttachments(issue);
            if (attachments == null || attachments.isEmpty()) {
                continue;
            }

            for (Attachment att : attachments) {
                if (matchesFilter(att, criteria)) {
                    String archivePath = computeUniqueArchivePath(att, issue, criteria.getGroupingStrategy(), usedPathsInZip);
                    ExportableAttachment descriptor = new ExportableAttachment(
                            att.getId(),
                            att.getFilename(),
                            att.getFilesize(),
                            att.getAuthorObject() != null ? att.getAuthorObject().getDisplayName() : att.getAuthor(),
                            att.getCreated(),
                            att.getMimetype(),
                            issue.getKey(),
                            issue.getSummary(),
                            issue.getStatus() != null ? issue.getStatus().getName() : "",
                            issue.getProjectObject() != null ? issue.getProjectObject().getKey() : "",
                            archivePath
                    );
                    result.put(descriptor, att);
                }
            }
        }

        return result;
    }

    private boolean matchesFilter(Attachment att, AttachmentFilterCriteria criteria) {
        if (att == null) {
            return false;
        }

        // File extension filter
        if (!criteria.getFileExtensions().isEmpty()) {
            String filename = att.getFilename();
            String ext = getFileExtension(filename);
            if (ext == null || !criteria.getFileExtensions().contains(ext.toLowerCase())) {
                return false;
            }
        }

        // Min file size
        if (criteria.getMinSizeBytes() != null && att.getFilesize() != null) {
            if (att.getFilesize() < criteria.getMinSizeBytes()) {
                return false;
            }
        }

        // Max file size
        if (criteria.getMaxSizeBytes() != null && att.getFilesize() != null) {
            if (att.getFilesize() > criteria.getMaxSizeBytes()) {
                return false;
            }
        }

        // Date created after
        if (criteria.getAttachmentCreatedAfter() != null && att.getCreated() != null) {
            if (att.getCreated().before(criteria.getAttachmentCreatedAfter())) {
                return false;
            }
        }

        // Date created before
        if (criteria.getAttachmentCreatedBefore() != null && att.getCreated() != null) {
            if (att.getCreated().after(criteria.getAttachmentCreatedBefore())) {
                return false;
            }
        }

        return true;
    }

    private String computeUniqueArchivePath(Attachment att, Issue issue, GroupingStrategy strategy, Set<String> usedPaths) {
        String projectKey = issue.getProjectObject() != null ? sanitizePathSegment(issue.getProjectObject().getKey()) : "UNKNOWN";
        String issueKey = sanitizePathSegment(issue.getKey());
        String statusName = issue.getStatus() != null ? sanitizePathSegment(issue.getStatus().getName()) : "NO_STATUS";
        String summary = sanitizePathSegment(truncate(issue.getSummary(), 50));
        String rawFilename = sanitizePathSegment(att.getFilename());

        if (rawFilename.isEmpty()) {
            rawFilename = "attachment_" + att.getId();
        }

        String directory;
        switch (strategy) {
            case BY_ISSUE:
                directory = issueKey;
                break;
            case BY_STATUS_AND_ISSUE:
                directory = statusName + "/" + issueKey;
                break;
            case FLAT_WITH_PREFIX:
                directory = "";
                break;
            case BY_PROJECT_AND_ISSUE:
            default:
                directory = projectKey + "/" + issueKey + " - " + summary;
                break;
        }

        String baseFilename;
        if (strategy == GroupingStrategy.FLAT_WITH_PREFIX) {
            baseFilename = issueKey + "_" + rawFilename;
        } else {
            baseFilename = rawFilename;
        }

        String basePath = directory.isEmpty() ? baseFilename : directory + "/" + baseFilename;
        String uniquePath = basePath;
        int counter = 1;

        while (usedPaths.contains(uniquePath)) {
            String nameWithoutExt = getBaseName(baseFilename);
            String ext = getFileExtension(baseFilename);
            String newFilename = ext.isEmpty() ? nameWithoutExt + " (" + counter + ")" : nameWithoutExt + " (" + counter + ")." + ext;
            uniquePath = directory.isEmpty() ? newFilename : directory + "/" + newFilename;
            counter++;
        }

        usedPaths.add(uniquePath);
        return uniquePath;
    }

    private String buildJqlQuery(AttachmentFilterCriteria criteria) {
        List<String> conditions = new ArrayList<>();

        if (criteria.getProjectKey() != null && !criteria.getProjectKey().trim().isEmpty()) {
            appendInClause(conditions, "project", criteria.getProjectKey());
        }

        if (criteria.getStatus() != null && !criteria.getStatus().trim().isEmpty()) {
            appendInClause(conditions, "status", criteria.getStatus());
        }

        if (criteria.getIssueType() != null && !criteria.getIssueType().trim().isEmpty()) {
            appendInClause(conditions, "issuetype", criteria.getIssueType());
        }

        if (criteria.getCustomJql() != null && !criteria.getCustomJql().trim().isEmpty()) {
            conditions.add("(" + criteria.getCustomJql().trim() + ")");
        }

        // Optimize: match only issues that actually have attachments
        conditions.add("attachments IS NOT EMPTY");

        StringBuilder jqlBuilder = new StringBuilder();
        for (int i = 0; i < conditions.size(); i++) {
            if (i > 0) {
                jqlBuilder.append(" AND ");
            }
            jqlBuilder.append(conditions.get(i));
        }

        jqlBuilder.append(" ORDER BY key ASC");
        return jqlBuilder.toString();
    }

    private void appendInClause(List<String> conditions, String field, String commaSeparatedValues) {
        if (commaSeparatedValues == null || commaSeparatedValues.trim().isEmpty()) {
            return;
        }
        String[] parts = commaSeparatedValues.split(",");
        List<String> cleaned = new ArrayList<>();
        for (String p : parts) {
            String val = p.trim();
            if (!val.isEmpty()) {
                cleaned.add("\"" + escapeJql(val) + "\"");
            }
        }
        if (cleaned.size() == 1) {
            conditions.add(field + " = " + cleaned.get(0));
        } else if (cleaned.size() > 1) {
            conditions.add(field + " IN (" + String.join(", ", cleaned) + ")");
        }
    }

    private void writeManifestEntry(ZipOutputStream zipOut, AttachmentExportSummary summary,
                                    AttachmentFilterCriteria criteria, ApplicationUser user) throws IOException {
        ZipEntry manifestEntry = new ZipEntry("export-summary.txt");
        manifestEntry.setTime(System.currentTimeMillis());
        zipOut.putNextEntry(manifestEntry);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
        StringBuilder sb = new StringBuilder();
        sb.append("================================================================================\n");
        sb.append("                 JIRA ATTACHMENTS BULK EXPORT MANIFEST                          \n");
        sb.append("================================================================================\n\n");
        sb.append("Export Date      : ").append(sdf.format(new Date())).append("\n");
        sb.append("Requested By     : ").append(user != null ? user.getDisplayName() + " (" + user.getUsername() + ")" : "Anonymous").append("\n");
        sb.append("Grouping Strategy: ").append(criteria.getGroupingStrategy().name()).append("\n");
        sb.append("Applied JQL Query: ").append(summary.getAppliedJql()).append("\n");
        sb.append("Total Issues     : ").append(summary.getTotalIssues()).append("\n");
        sb.append("Total Files      : ").append(summary.getTotalAttachments()).append("\n");
        sb.append("Total Size       : ").append(summary.getFormattedTotalSize()).append(" (").append(summary.getTotalSizeBytes()).append(" bytes)\n\n");
        sb.append("--------------------------------------------------------------------------------\n");
        sb.append(String.format("%-12s | %-12s | %-10s | %-30s | %s\n", "ISSUE KEY", "STATUS", "SIZE", "FILENAME", "ARCHIVE PATH"));
        sb.append("--------------------------------------------------------------------------------\n");

        for (ExportableAttachment item : summary.getAttachments()) {
            sb.append(String.format("%-12s | %-12s | %-10s | %-30s | %s\n",
                    item.getIssueKey(),
                    truncate(item.getIssueStatus(), 12),
                    item.getFormattedFilesize(),
                    truncate(item.getFilename(), 30),
                    item.getArchivePath()
            ));
        }

        sb.append("================================================================================\n");

        byte[] manifestBytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        zipOut.write(manifestBytes);
        zipOut.closeEntry();
    }

    private String sanitizePathSegment(String input) {
        if (input == null) {
            return "";
        }
        // Replace invalid filesystem and ZIP characters
        return input.replaceAll("[\\\\/:*?\"<>|\\x00-\\x1F]", "_").trim();
    }

    private String getFileExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex > 0 && dotIndex < filename.length() - 1) ? filename.substring(dotIndex + 1) : "";
    }

    private String getBaseName(String filename) {
        if (filename == null) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex > 0) ? filename.substring(0, dotIndex) : filename;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength - 3) + "...";
    }

    private String escapeJql(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
