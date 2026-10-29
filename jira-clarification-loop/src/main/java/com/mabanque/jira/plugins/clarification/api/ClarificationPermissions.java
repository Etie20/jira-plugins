package com.mabanque.jira.plugins.clarification.api;

/**
 * Droits de l'utilisateur connecté sur le module de précisions d'un ticket,
 * accompagnés du contexte utile à l'affichage (responsable, demandeur, utilisateur courant).
 *
 * Règles :
 * - Seul le responsable actuel du ticket (assigné) peut demander une précision.
 * - Seul le destinataire d'une demande en cours peut y répondre.
 * - Seul l'auteur d'une demande en cours peut l'annuler.
 * - Aucune nouvelle demande n'est possible tant qu'une demande est en attente ou si le ticket est clôturé.
 */
public class ClarificationPermissions {

    private boolean canRequest;
    private boolean canReply;
    private boolean canCancel;
    private String info;

    private String currentUserName;
    private String reporterName;
    private String reporterDisplayName;
    private String assigneeName;
    private String assigneeDisplayName;

    public boolean isCanRequest() {
        return canRequest;
    }

    public void setCanRequest(boolean canRequest) {
        this.canRequest = canRequest;
    }

    public boolean isCanReply() {
        return canReply;
    }

    public void setCanReply(boolean canReply) {
        this.canReply = canReply;
    }

    public boolean isCanCancel() {
        return canCancel;
    }

    public void setCanCancel(boolean canCancel) {
        this.canCancel = canCancel;
    }

    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info;
    }

    public String getCurrentUserName() {
        return currentUserName;
    }

    public void setCurrentUserName(String currentUserName) {
        this.currentUserName = currentUserName;
    }

    public String getReporterName() {
        return reporterName;
    }

    public void setReporterName(String reporterName) {
        this.reporterName = reporterName;
    }

    public String getReporterDisplayName() {
        return reporterDisplayName;
    }

    public void setReporterDisplayName(String reporterDisplayName) {
        this.reporterDisplayName = reporterDisplayName;
    }

    public String getAssigneeName() {
        return assigneeName;
    }

    public void setAssigneeName(String assigneeName) {
        this.assigneeName = assigneeName;
    }

    public String getAssigneeDisplayName() {
        return assigneeDisplayName;
    }

    public void setAssigneeDisplayName(String assigneeDisplayName) {
        this.assigneeDisplayName = assigneeDisplayName;
    }
}
