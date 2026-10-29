package com.mabanque.jira.plugins.clarification.api;

import java.io.Serializable;

/**
 * Modèle représentant un échange de demande d'informations supplémentaires sur un ticket.
 */
public class ClarificationEntry implements Serializable {

    private static final long serialVersionUID = 2L;

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ANSWERED = "ANSWERED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private String id;
    private String issueKey;
    private String question;
    private String requestedBy;
    private String requestedByUsername;
    private String requestedAt;
    private String originStatus;
    private String originAssignee;

    // Destinataire de la demande et réassignation
    private String recipient;
    private String recipientUsername;
    private boolean reassigned;
    private String requestAttachmentName;

    // Réponse et pièce jointe
    private String answer;
    private String answeredBy;
    private String answeredByUsername;
    private String answeredAt;
    private String attachmentName;

    // Identifiants Jira des pièces jointes (pour générer un lien de téléchargement)
    private Long requestAttachmentId;
    private Long attachmentId;

    // Annulation par l'auteur de la demande
    private String cancelledAt;

    private String status;

    public ClarificationEntry() {
        this.status = STATUS_PENDING;
    }

    public ClarificationEntry(String id, String issueKey, String question,
                              String requestedBy, String requestedByUsername, String requestedAt,
                              String originStatus, String originAssignee) {
        this.id = id;
        this.issueKey = issueKey;
        this.question = question;
        this.requestedBy = requestedBy;
        this.requestedByUsername = requestedByUsername;
        this.requestedAt = requestedAt;
        this.originStatus = originStatus;
        this.originAssignee = originAssignee;
        this.status = STATUS_PENDING;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getIssueKey() {
        return issueKey;
    }

    public void setIssueKey(String issueKey) {
        this.issueKey = issueKey;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }

    public String getRequestedByUsername() {
        return requestedByUsername;
    }

    public void setRequestedByUsername(String requestedByUsername) {
        this.requestedByUsername = requestedByUsername;
    }

    public String getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(String requestedAt) {
        this.requestedAt = requestedAt;
    }

    public String getOriginStatus() {
        return originStatus;
    }

    public void setOriginStatus(String originStatus) {
        this.originStatus = originStatus;
    }

    public String getOriginAssignee() {
        return originAssignee;
    }

    public void setOriginAssignee(String originAssignee) {
        this.originAssignee = originAssignee;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getRecipientUsername() {
        return recipientUsername;
    }

    public void setRecipientUsername(String recipientUsername) {
        this.recipientUsername = recipientUsername;
    }

    public boolean isReassigned() {
        return reassigned;
    }

    public void setReassigned(boolean reassigned) {
        this.reassigned = reassigned;
    }

    public String getRequestAttachmentName() {
        return requestAttachmentName;
    }

    public void setRequestAttachmentName(String requestAttachmentName) {
        this.requestAttachmentName = requestAttachmentName;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getAnsweredBy() {
        return answeredBy;
    }

    public void setAnsweredBy(String answeredBy) {
        this.answeredBy = answeredBy;
    }

    public String getAnsweredByUsername() {
        return answeredByUsername;
    }

    public void setAnsweredByUsername(String answeredByUsername) {
        this.answeredByUsername = answeredByUsername;
    }

    public String getAnsweredAt() {
        return answeredAt;
    }

    public void setAnsweredAt(String answeredAt) {
        this.answeredAt = answeredAt;
    }

    public String getAttachmentName() {
        return attachmentName;
    }

    public void setAttachmentName(String attachmentName) {
        this.attachmentName = attachmentName;
    }

    public Long getRequestAttachmentId() {
        return requestAttachmentId;
    }

    public void setRequestAttachmentId(Long requestAttachmentId) {
        this.requestAttachmentId = requestAttachmentId;
    }

    public Long getAttachmentId() {
        return attachmentId;
    }

    public void setAttachmentId(Long attachmentId) {
        this.attachmentId = attachmentId;
    }

    public String getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(String cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
