package com.socgen.jira.plugins.attachments_download.api;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;
import java.util.Date;

/**
 * Metadata descriptor of an attachment selected for download.
 */
@XmlRootElement(name = "attachment")
@XmlAccessorType(XmlAccessType.FIELD)
public class ExportableAttachment implements Serializable {

    private static final long serialVersionUID = 1L;

    @XmlElement
    private Long attachmentId;

    @XmlElement
    private String filename;

    @XmlElement
    private Long filesize;

    @XmlElement
    private String formattedFilesize;

    @XmlElement
    private String author;

    @XmlElement
    private Date created;

    @XmlElement
    private String mimetype;

    @XmlElement
    private String issueKey;

    @XmlElement
    private String issueSummary;

    @XmlElement
    private String issueStatus;

    @XmlElement
    private String projectKey;

    @XmlElement
    private String archivePath;

    public ExportableAttachment() {
    }

    public ExportableAttachment(Long attachmentId, String filename, Long filesize, String author,
                                Date created, String mimetype, String issueKey, String issueSummary,
                                String issueStatus, String projectKey, String archivePath) {
        this.attachmentId = attachmentId;
        this.filename = filename;
        this.filesize = filesize;
        this.formattedFilesize = formatFileSize(filesize);
        this.author = author;
        this.created = created;
        this.mimetype = mimetype;
        this.issueKey = issueKey;
        this.issueSummary = issueSummary;
        this.issueStatus = issueStatus;
        this.projectKey = projectKey;
        this.archivePath = archivePath;
    }

    public static String formatFileSize(Long bytes) {
        if (bytes == null || bytes < 0) {
            return "0 B";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format(java.util.Locale.US, "%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    public Long getAttachmentId() {
        return attachmentId;
    }

    public void setAttachmentId(Long attachmentId) {
        this.attachmentId = attachmentId;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public Long getFilesize() {
        return filesize;
    }

    public void setFilesize(Long filesize) {
        this.filesize = filesize;
        this.formattedFilesize = formatFileSize(filesize);
    }

    public String getFormattedFilesize() {
        return formattedFilesize;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public String getMimetype() {
        return mimetype;
    }

    public void setMimetype(String mimetype) {
        this.mimetype = mimetype;
    }

    public String getIssueKey() {
        return issueKey;
    }

    public void setIssueKey(String issueKey) {
        this.issueKey = issueKey;
    }

    public String getIssueSummary() {
        return issueSummary;
    }

    public void setIssueSummary(String issueSummary) {
        this.issueSummary = issueSummary;
    }

    public String getIssueStatus() {
        return issueStatus;
    }

    public void setIssueStatus(String issueStatus) {
        this.issueStatus = issueStatus;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public void setProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }

    public String getArchivePath() {
        return archivePath;
    }

    public void setArchivePath(String archivePath) {
        this.archivePath = archivePath;
    }
}
