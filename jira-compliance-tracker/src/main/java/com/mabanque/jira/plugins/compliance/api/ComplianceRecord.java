package com.mabanque.jira.plugins.compliance.api;

import java.io.Serializable;

/**
 * Modèle de données représentant l'état de conformité d'un ticket Jira.
 */
public class ComplianceRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_COMPLIANT = "COMPLIANT";
    public static final String STATUS_REJECTED = "REJECTED";

    private String issueKey;
    private String status;
    private String reviewer;
    private String reviewedAt;
    private String comment;

    public ComplianceRecord() {
        this.status = STATUS_PENDING;
    }

    public ComplianceRecord(String issueKey, String status, String reviewer, String reviewedAt, String comment) {
        this.issueKey = issueKey;
        this.status = status;
        this.reviewer = reviewer;
        this.reviewedAt = reviewedAt;
        this.comment = comment;
    }

    public String getIssueKey() {
        return issueKey;
    }

    public void setIssueKey(String issueKey) {
        this.issueKey = issueKey;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReviewer() {
        return reviewer;
    }

    public void setReviewer(String reviewer) {
        this.reviewer = reviewer;
    }

    public String getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(String reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
