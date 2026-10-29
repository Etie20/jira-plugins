package com.mabanque.jira.plugins.clarification.api;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Historique complet des demandes d'informations et réponses pour un ticket.
 */
public class ClarificationHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    private String issueKey;
    private List<ClarificationEntry> entries;

    public ClarificationHistory() {
        this.entries = new ArrayList<ClarificationEntry>();
    }

    public ClarificationHistory(String issueKey) {
        this.issueKey = issueKey;
        this.entries = new ArrayList<ClarificationEntry>();
    }

    public String getIssueKey() {
        return issueKey;
    }

    public void setIssueKey(String issueKey) {
        this.issueKey = issueKey;
    }

    public List<ClarificationEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<ClarificationEntry> entries) {
        this.entries = entries;
    }

    public void addEntry(ClarificationEntry entry) {
        if (this.entries == null) {
            this.entries = new ArrayList<ClarificationEntry>();
        }
        this.entries.add(entry);
    }

    public boolean hasPending() {
        if (entries == null) return false;
        for (ClarificationEntry entry : entries) {
            if (ClarificationEntry.STATUS_PENDING.equals(entry.getStatus())) {
                return true;
            }
        }
        return false;
    }

    public ClarificationEntry getPendingEntry() {
        if (entries == null) return null;
        for (ClarificationEntry entry : entries) {
            if (ClarificationEntry.STATUS_PENDING.equals(entry.getStatus())) {
                return entry;
            }
        }
        return null;
    }

    public int getTotalCount() {
        return entries != null ? entries.size() : 0;
    }
}
