package com.mabanque.jira.plugins.clarification.impl;

import com.atlassian.jira.event.type.EventDispatchOption;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.comments.CommentManager;
import com.atlassian.jira.issue.status.Status;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.google.gson.Gson;
import com.mabanque.jira.plugins.clarification.api.ClarificationEntry;
import com.mabanque.jira.plugins.clarification.api.ClarificationException;
import com.mabanque.jira.plugins.clarification.api.ClarificationHistory;
import com.mabanque.jira.plugins.clarification.api.ClarificationPermissions;
import com.mabanque.jira.plugins.clarification.api.ClarificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

@ExportAsService({ClarificationService.class})
@Named("clarificationService")
public class ClarificationServiceImpl implements ClarificationService {

    private static final Logger log = LoggerFactory.getLogger(ClarificationServiceImpl.class);
    private static final String PLUGIN_STORAGE_KEY = "com.mabanque.jira.clarification";
    private static final String KEY_PREFIX = "clarification.issue.";
    private static final String BACKUP_KEY_PREFIX = "clarification.issue.backup.";
    private static final int MAX_COMMENT_LENGTH = 5000;

    private final PluginSettingsFactory pluginSettingsFactory;
    private final IssueManager issueManager;
    private final CommentManager commentManager;
    private final UserManager userManager;
    private final PermissionManager permissionManager;
    private final Gson gson;
    private final Object writeLock = new Object();

    @Inject
    public ClarificationServiceImpl(@ComponentImport PluginSettingsFactory pluginSettingsFactory,
                                    @ComponentImport IssueManager issueManager,
                                    @ComponentImport CommentManager commentManager,
                                    @ComponentImport UserManager userManager,
                                    @ComponentImport PermissionManager permissionManager) {
        this.pluginSettingsFactory = pluginSettingsFactory;
        this.issueManager = issueManager;
        this.commentManager = commentManager;
        this.userManager = userManager;
        this.permissionManager = permissionManager;
        this.gson = new Gson();
    }

    // ------------------------------------------------------------------
    // Lecture
    // ------------------------------------------------------------------

    private PluginSettings getSettings() {
        return pluginSettingsFactory.createSettingsForKey(PLUGIN_STORAGE_KEY);
    }

    @Override
    public ClarificationHistory getHistory(String issueKey) {
        if (issueKey == null || issueKey.trim().isEmpty()) {
            return new ClarificationHistory();
        }

        String cleanKey = issueKey.trim().toUpperCase();
        PluginSettings settings = getSettings();
        try {
            Object raw = settings.get(KEY_PREFIX + cleanKey);
            if (raw instanceof String && !((String) raw).trim().isEmpty()) {
                ClarificationHistory history = gson.fromJson((String) raw, ClarificationHistory.class);
                if (history != null && history.getEntries() != null) {
                    return history;
                }
            }
        } catch (Exception e) {
            log.error("Erreur lors de la lecture de l'historique principal pour " + cleanKey + ", tentative de restauration depuis la sauvegarde", e);
        }

        // Restauration automatique depuis la clé miroir de secours en cas d'anomalie sur la clé principale
        try {
            Object backupRaw = settings.get(BACKUP_KEY_PREFIX + cleanKey);
            if (backupRaw instanceof String && !((String) backupRaw).trim().isEmpty()) {
                ClarificationHistory recovered = gson.fromJson((String) backupRaw, ClarificationHistory.class);
                if (recovered != null && recovered.getEntries() != null) {
                    settings.put(KEY_PREFIX + cleanKey, (String) backupRaw);
                    log.info("[CLARIFICATION] Historique restauré avec succès depuis la sauvegarde pour {}", cleanKey);
                    return recovered;
                }
            }
        } catch (Exception e) {
            log.error("Erreur lors de la lecture de la sauvegarde pour " + cleanKey, e);
        }

        return new ClarificationHistory(cleanKey);
    }

    private void saveHistory(String issueKey, ClarificationHistory history) {
        if (issueKey == null || history == null) {
            return;
        }
        String cleanKey = issueKey.trim().toUpperCase();
        String json = gson.toJson(history);
        if (json == null || json.trim().isEmpty()) {
            return;
        }
        PluginSettings settings = getSettings();
        settings.put(KEY_PREFIX + cleanKey, json);
        settings.put(BACKUP_KEY_PREFIX + cleanKey, json);
    }

    @Override
    public boolean hasPendingClarification(String issueKey) {
        return getHistory(issueKey).hasPending();
    }

    // ------------------------------------------------------------------
    // Droits
    // ------------------------------------------------------------------

    @Override
    public ClarificationPermissions getPermissions(String issueKey, ApplicationUser user) {
        MutableIssue issue = loadIssue(issueKey);
        ClarificationPermissions perms = new ClarificationPermissions();

        ApplicationUser reporter = issue.getReporter();
        ApplicationUser assignee = issue.getAssignee();
        perms.setCurrentUserName(user != null ? user.getName() : null);
        perms.setReporterName(reporter != null ? reporter.getName() : null);
        perms.setReporterDisplayName(reporter != null ? reporter.getDisplayName() : null);
        perms.setAssigneeName(assignee != null ? assignee.getName() : null);
        perms.setAssigneeDisplayName(assignee != null ? assignee.getDisplayName() : null);

        if (user == null) {
            perms.setInfo("Connectez-vous pour interagir.");
            return perms;
        }

        ClarificationEntry pending = getHistory(issue.getKey()).getPendingEntry();
        if (pending != null) {
            perms.setCanReply(sameUser(user, pending.getRecipientUsername()));
            perms.setCanCancel(sameUser(user, pending.getRequestedByUsername()));
            return perms;
        }

        if (isClosed(issue)) {
            perms.setInfo("Ticket clôturé : aucune demande possible.");
        } else if (assignee == null) {
            perms.setInfo("Le ticket doit être assigné pour faire une demande d'infos sup.");
        } else if (!sameUser(user, assignee)) {
            perms.setInfo("Seul le responsable du ticket (" + assignee.getDisplayName() + ") peut faire une demande d'infos sup.");
        } else {
            perms.setCanRequest(true);
        }
        return perms;
    }

    // ------------------------------------------------------------------
    // Demande
    // ------------------------------------------------------------------

    @Override
    public void validateRequest(String issueKey, String comment, String recipientUsername, ApplicationUser requester) {
        MutableIssue issue = loadIssue(issueKey);
        checkRequestAllowed(issue, requester);
        checkComment(comment);
        resolveRecipient(issue, recipientUsername, requester);
    }

    @Override
    public ClarificationEntry requestClarification(String issueKey, String comment, String recipientUsername,
                                                   Long attachmentId, String attachmentName, ApplicationUser requester) {
        synchronized (writeLock) {
            MutableIssue issue = loadIssue(issueKey);
            checkRequestAllowed(issue, requester);
            checkComment(comment);
            ApplicationUser recipient = resolveRecipient(issue, recipientUsername, requester);

            String key = issue.getKey();
            String originStatus = issue.getStatus() != null ? issue.getStatus().getName() : "";

            ClarificationEntry entry = new ClarificationEntry(
                    UUID.randomUUID().toString(),
                    key,
                    comment.trim(),
                    label(requester),
                    requester.getName(),
                    now(),
                    originStatus,
                    requester.getName()
            );
            entry.setRecipient(label(recipient));
            entry.setRecipientUsername(recipient.getName());
            entry.setReassigned(true);
            entry.setRequestAttachmentId(attachmentId);
            entry.setRequestAttachmentName(clean(attachmentName));

            ClarificationHistory history = getHistory(key);
            history.addEntry(entry);
            saveHistory(key, history);

            assign(issue, recipient, requester);

            StringBuilder body = new StringBuilder();
            body.append("❓ *Demande d'informations supplémentaires* adressée à [~").append(recipient.getName()).append("]\n");
            body.append("_Étape : ").append(originStatus).append("_\n\n");
            body.append(entry.getQuestion());
            appendAttachment(body, entry.getRequestAttachmentName());
            addComment(issue, requester, body.toString());

            log.info("[CLARIFICATION] Demande sur {} de {} vers {}", key, requester.getName(), recipient.getName());
            return entry;
        }
    }

    // ------------------------------------------------------------------
    // Réponse
    // ------------------------------------------------------------------

    @Override
    public void validateAnswer(String issueKey, String comment, ApplicationUser responder) {
        MutableIssue issue = loadIssue(issueKey);
        checkAnswerAllowed(issue, responder);
        checkComment(comment);
    }

    @Override
    public ClarificationEntry answerClarification(String issueKey, String comment,
                                                  Long attachmentId, String attachmentName, ApplicationUser responder) {
        synchronized (writeLock) {
            MutableIssue issue = loadIssue(issueKey);
            ClarificationHistory history = getHistory(issue.getKey());
            ClarificationEntry pending = checkAnswerAllowed(issue, responder);
            checkComment(comment);

            // Recharger l'entrée depuis l'historique courant pour la modifier
            pending = history.getPendingEntry();
            pending.setAnswer(comment.trim());
            pending.setAnsweredBy(label(responder));
            pending.setAnsweredByUsername(responder.getName());
            pending.setAnsweredAt(now());
            pending.setAttachmentId(attachmentId);
            pending.setAttachmentName(clean(attachmentName));
            pending.setStatus(ClarificationEntry.STATUS_ANSWERED);
            saveHistory(issue.getKey(), history);

            ApplicationUser requester = activeUser(pending.getRequestedByUsername());
            if (requester != null) {
                assign(issue, requester, responder);
            }

            StringBuilder body = new StringBuilder();
            body.append("💬 *Réponse à la demande d'informations supplémentaires*");
            if (requester != null) {
                body.append(" - retour à [~").append(requester.getName()).append("]");
            }
            body.append("\n\n").append(pending.getAnswer());
            appendAttachment(body, pending.getAttachmentName());
            addComment(issue, responder, body.toString());

            log.info("[CLARIFICATION] Reponse sur {} par {}", issue.getKey(), responder.getName());
            return pending;
        }
    }

    // ------------------------------------------------------------------
    // Annulation
    // ------------------------------------------------------------------

    @Override
    public ClarificationEntry cancelClarification(String issueKey, ApplicationUser requester) {
        synchronized (writeLock) {
            MutableIssue issue = loadIssue(issueKey);
            if (requester == null) {
                throw ClarificationException.forbidden("Vous devez être connecté.");
            }

            ClarificationHistory history = getHistory(issue.getKey());
            ClarificationEntry pending = history.getPendingEntry();
            if (pending == null) {
                throw new ClarificationException(ClarificationException.CONFLICT, "Aucune demande en cours sur ce ticket.");
            }
            if (!sameUser(requester, pending.getRequestedByUsername())) {
                throw ClarificationException.forbidden("Seul l'auteur de la demande peut l'annuler.");
            }

            pending.setStatus(ClarificationEntry.STATUS_CANCELLED);
            pending.setCancelledAt(now());
            saveHistory(issue.getKey(), history);

            assign(issue, requester, requester);
            addComment(issue, requester, "↩️ *Demande d'informations supplémentaires annulée* par son auteur.");

            log.info("[CLARIFICATION] Demande annulee sur {} par {}", issue.getKey(), requester.getName());
            return pending;
        }
    }

    // ------------------------------------------------------------------
    // Règles métier
    // ------------------------------------------------------------------

    private void checkRequestAllowed(MutableIssue issue, ApplicationUser requester) {
        if (requester == null) {
            throw ClarificationException.forbidden("Vous devez être connecté.");
        }
        if (getHistory(issue.getKey()).hasPending()) {
            throw new ClarificationException(ClarificationException.CONFLICT,
                    "Une demande est déjà en attente sur ce ticket.");
        }
        if (isClosed(issue)) {
            throw ClarificationException.forbidden("Le ticket est clôturé.");
        }
        ApplicationUser assignee = issue.getAssignee();
        if (assignee == null || !sameUser(requester, assignee)) {
            throw ClarificationException.forbidden("Seul le responsable du ticket peut faire une demande d'infos sup.");
        }
    }

    private ClarificationEntry checkAnswerAllowed(MutableIssue issue, ApplicationUser responder) {
        if (responder == null) {
            throw ClarificationException.forbidden("Vous devez être connecté.");
        }
        ClarificationEntry pending = getHistory(issue.getKey()).getPendingEntry();
        if (pending == null) {
            throw new ClarificationException(ClarificationException.CONFLICT, "Aucune demande en cours sur ce ticket.");
        }
        if (!sameUser(responder, pending.getRecipientUsername())) {
            throw ClarificationException.forbidden("Seul le destinataire de la demande peut y répondre.");
        }
        return pending;
    }

    private void checkComment(String comment) {
        if (comment == null || comment.trim().isEmpty()) {
            throw ClarificationException.field("comment", "Le commentaire est obligatoire.");
        }
        if (comment.length() > MAX_COMMENT_LENGTH) {
            throw ClarificationException.field("comment", "Le commentaire ne doit pas dépasser " + MAX_COMMENT_LENGTH + " caractères.");
        }
    }

    private ApplicationUser resolveRecipient(MutableIssue issue, String recipientUsername, ApplicationUser requester) {
        if (recipientUsername == null || recipientUsername.trim().isEmpty()) {
            throw ClarificationException.field("recipient", "Choisissez un destinataire.");
        }
        ApplicationUser recipient = userManager.getUserByName(recipientUsername.trim());
        if (recipient == null || !recipient.isActive()) {
            throw ClarificationException.field("recipient", "Utilisateur introuvable ou désactivé.");
        }
        if (sameUser(recipient, requester)) {
            throw ClarificationException.field("recipient", "Vous ne pouvez pas vous adresser la demande à vous-même.");
        }
        if (!permissionManager.hasPermission(ProjectPermissions.BROWSE_PROJECTS, issue, recipient)) {
            throw ClarificationException.field("recipient", recipient.getDisplayName() + " n'a pas accès à ce ticket.");
        }
        return recipient;
    }

    // ------------------------------------------------------------------
    // Utilitaires
    // ------------------------------------------------------------------

    private MutableIssue loadIssue(String issueKey) {
        if (issueKey == null || issueKey.trim().isEmpty()) {
            throw new ClarificationException(ClarificationException.BAD_REQUEST, "Clé de ticket manquante.");
        }
        MutableIssue issue = issueManager.getIssueObject(issueKey.trim().toUpperCase());
        if (issue == null) {
            throw new ClarificationException(ClarificationException.NOT_FOUND, "Ticket introuvable : " + issueKey);
        }
        return issue;
    }

    private boolean isClosed(MutableIssue issue) {
        Status status = issue.getStatus();
        return status != null && status.getStatusCategory() != null
                && "done".equals(status.getStatusCategory().getKey());
    }

    private void assign(MutableIssue issue, ApplicationUser target, ApplicationUser actor) {
        try {
            issue.setAssignee(target);
            issueManager.updateIssue(actor, issue, EventDispatchOption.ISSUE_UPDATED, true);
        } catch (Exception e) {
            log.warn("Impossible de reassigner " + issue.getKey() + " a " + (target != null ? target.getName() : "personne"), e);
        }
    }

    private void addComment(MutableIssue issue, ApplicationUser author, String body) {
        try {
            commentManager.create(issue, author, body, true);
        } catch (Exception e) {
            log.warn("Impossible d'ajouter le commentaire automatique sur " + issue.getKey(), e);
        }
    }

    private void appendAttachment(StringBuilder body, String attachmentName) {
        if (attachmentName != null) {
            body.append("\n\n📎 [^").append(attachmentName).append("]");
        }
    }

    private ApplicationUser activeUser(String username) {
        if (username == null || username.trim().isEmpty()) {
            return null;
        }
        ApplicationUser user = userManager.getUserByName(username.trim());
        return (user != null && user.isActive()) ? user : null;
    }

    private static boolean sameUser(ApplicationUser a, ApplicationUser b) {
        return a != null && b != null && a.getKey().equals(b.getKey());
    }

    private static boolean sameUser(ApplicationUser user, String username) {
        return user != null && username != null && user.getName().equalsIgnoreCase(username.trim());
    }

    private static String label(ApplicationUser user) {
        return user.getDisplayName();
    }

    private static String clean(String value) {
        return (value == null || value.trim().isEmpty()) ? null : value.trim();
    }

    private static String now() {
        return new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date());
    }
}
