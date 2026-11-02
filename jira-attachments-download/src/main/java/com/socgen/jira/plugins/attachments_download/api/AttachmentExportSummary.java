package com.socgen.jira.plugins.attachments_download.api;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Summary metrics of matching issues and attachments for preview or export.
 */
@XmlRootElement(name = "exportSummary")
@XmlAccessorType(XmlAccessType.FIELD)
public class AttachmentExportSummary implements Serializable {

    private static final long serialVersionUID = 1L;

    @XmlElement
    private int totalIssues;

    @XmlElement
    private int totalAttachments;

    @XmlElement
    private long totalSizeBytes;

    @XmlElement
    private String formattedTotalSize;

    @XmlElement
    private String appliedJql;

    @XmlElement(name = "attachments")
    private List<ExportableAttachment> attachments = new ArrayList<>();

    public AttachmentExportSummary() {
    }

    public AttachmentExportSummary(int totalIssues, int totalAttachments, long totalSizeBytes,
                                   String appliedJql, List<ExportableAttachment> attachments) {
        this.totalIssues = totalIssues;
        this.totalAttachments = totalAttachments;
        this.totalSizeBytes = totalSizeBytes;
        this.formattedTotalSize = ExportableAttachment.formatFileSize(totalSizeBytes);
        this.appliedJql = appliedJql;
        this.attachments = attachments != null ? attachments : new ArrayList<>();
    }

    public int getTotalIssues() {
        return totalIssues;
    }

    public void setTotalIssues(int totalIssues) {
        this.totalIssues = totalIssues;
    }

    public int getTotalAttachments() {
        return totalAttachments;
    }

    public void setTotalAttachments(int totalAttachments) {
        this.totalAttachments = totalAttachments;
    }

    public long getTotalSizeBytes() {
        return totalSizeBytes;
    }

    public void setTotalSizeBytes(long totalSizeBytes) {
        this.totalSizeBytes = totalSizeBytes;
        this.formattedTotalSize = ExportableAttachment.formatFileSize(totalSizeBytes);
    }

    public String getFormattedTotalSize() {
        return formattedTotalSize;
    }

    public String getAppliedJql() {
        return appliedJql;
    }

    public void setAppliedJql(String appliedJql) {
        this.appliedJql = appliedJql;
    }

    public List<ExportableAttachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<ExportableAttachment> attachments) {
        this.attachments = attachments;
    }
}
