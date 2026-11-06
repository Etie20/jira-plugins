package com.socgen.jira.plugins.clarification.impl;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.event.type.EventDispatchOption;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.ModifiedValue;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.comments.CommentManager;
import com.atlassian.jira.issue.context.GlobalIssueContext;
import com.atlassian.jira.issue.context.JiraContextNode;
import com.atlassian.jira.issue.customfields.CustomFieldSearcher;
import com.atlassian.jira.issue.customfields.CustomFieldType;
import com.atlassian.jira.issue.customfields.manager.OptionsManager;
import com.atlassian.jira.issue.customfields.option.Option;
import com.atlassian.jira.issue.customfields.option.Options;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.config.FieldConfig;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.issue.status.Status;
import com.atlassian.jira.issue.util.DefaultIssueChangeHolder;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.jira.workflow.JiraWorkflow;
import com.atlassian.jira.workflow.WorkflowManager;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.google.gson.Gson;
import com.opensymphony.workflow.loader.StepDescriptor;
import com.opensymphony.workflow.loader.WorkflowDescriptor;
import com.socgen.jira.plugins.clarification.api.ClarificationEntry;
import com.socgen.jira.plugins.clarification.api.ClarificationException;
import com.socgen.jira.plugins.clarification.api.ClarificationHistory;
import com.socgen.jira.plugins.clarification.api.ClarificationPermissions;
import com.socgen.jira.plugins.clarification.api.ClarificationService;
import com.socgen.jira.plugins.clarification.api.ProjectConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@ExportAsService({ClarificationService.class})
@Named("clarificationService")
public class ClarificationServiceImpl implements ClarificationService {

    private static final Logger log = LoggerFactory.getLogger(ClarificationServiceImpl.class);
    private static final String PLUGIN_STORAGE_KEY = "com.socgen.jira.clarification";
    private static final String KEY_PREFIX = "clarification.issue.";
    private static final String BACKUP_KEY_PREFIX = "clarification.issue.backup.";
    private static final String PENDING_INDEX_KEY = "clarification.pending.index";
    private static final int MAX_COMMENT_LENGTH = 5000;

    public static final String SLA_FIELD_NAME = "Attente Infos Sup";
    public static final String SLA_FIELD_DESC = "Indique si une demande d'informations supplémentaires est en attente sur ce ticket (Oui / Non) — utilisé pour mettre en pause les SLA (Time to SLA).";
    public static final String SLA_VALUE_PENDING = "Oui";
    public static final String SLA_VALUE_NONE = "Non";

    private final PluginSettingsFactory pluginSettingsFactory;
    private final IssueManager issueManager;
    private final CommentManager commentManager;
    private final UserManager userManager;
    private final PermissionManager permissionManager;
    private final ProjectConfigService projectConfigService;
    private final Gson gson;
    private final Object writeLock = new Object();

    @Inject
    public ClarificationServiceImpl(@ComponentImport PluginSettingsFactory pluginSettingsFactory,
                                    @ComponentImport IssueManager issueManager,
                                    @ComponentImport CommentManager commentManager,
                                    @ComponentImport UserManager userManager,
                                    @ComponentImport PermissionManager permissionManager,
                                    ProjectConfigService projectConfigService) {
        this.pluginSettingsFactory = pluginSettingsFactory;
        this.issueManager = issueManager;
        this.commentManager = commentManager;
        this.userManager = userManager;
        this.permissionManager = permissionManager;
        this.projectConfigService = projectConfigService;
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

        // Maintenir l'index des tickets en attente pour la fonction JQL pendingInfoSup()
        if (history.hasPending()) {
            addToPendingIndex(settings, cleanKey);
        } else {
            removeFromPendingIndex(settings, cleanKey);
        }
    }

    private void addToPendingIndex(PluginSettings settings, String issueKey) {
        Object raw = settings.get(PENDING_INDEX_KEY);
        Set<String> keys = new LinkedHashSet<String>();
        if (raw instanceof String && !((String) raw).trim().isEmpty()) {
            for (String k : ((String) raw).split(",")) {
                if (k != null && !k.trim().isEmpty()) {
                    keys.add(k.trim());
                }
            }
        }
        keys.add(issueKey);
        settings.put(PENDING_INDEX_KEY, String.join(",", keys));
    }

    private void removeFromPendingIndex(PluginSettings settings, String issueKey) {
        Object raw = settings.get(PENDING_INDEX_KEY);
        if (!(raw instanceof String) || ((String) raw).trim().isEmpty()) {
            return;
        }
        Set<String> keys = new LinkedHashSet<String>();
        for (String k : ((String) raw).split(",")) {
            if (k != null && !k.trim().isEmpty() && !k.trim().equals(issueKey)) {
                keys.add(k.trim());
            }
        }
        if (keys.isEmpty()) {
            settings.remove(PENDING_INDEX_KEY);
        } else {
            settings.put(PENDING_INDEX_KEY, String.join(",", keys));
        }
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

        // Vérifier si le module est activé pour ce projet/type de ticket
        String projectKey = issue.getProjectObject() != null ? issue.getProjectObject().getKey() : null;
        String issueTypeId = issue.getIssueType() != null ? issue.getIssueType().getId() : null;
        if (projectKey != null && !projectConfigService.isEnabledFor(projectKey, issueTypeId)) {
            perms.setInfo("Module non activé pour ce projet/type de ticket.");
            return perms;
        }

        ProjectConfig config = projectKey != null ? projectConfigService.getConfig(projectKey) : null;
        if (config != null) {
            perms.setBlockedTransitionNames(config.getBlockedTransitionNames());
            perms.setTransitionsLocked(isCurrentStatusFrozenWhenPending(issue, config));
        }

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

        String disallowedReason = getDisallowedStatusReason(issue, config);
        if (disallowedReason != null) {
            perms.setInfo(disallowedReason);
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
            String originStatus = getCurrentStatusDisplayName(issue);

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

            updateIssueAssigneeAndSla(issue, recipient, requester, true);

            StringBuilder body = new StringBuilder();
            body.append("❓ *Demande d'informations supplémentaires* adressée à [~").append(recipient.getName()).append("]\n");
            body.append("_État : ").append(originStatus).append("_\n\n");
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
            updateIssueAssigneeAndSla(issue, requester, responder, history.hasPending());

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

            updateIssueAssigneeAndSla(issue, requester, requester, history.hasPending());
            addComment(issue, requester, "↩️ *Demande d'informations supplémentaires annulée* par son auteur.");

            log.info("[CLARIFICATION] Demande annulee sur {} par {}", issue.getKey(), requester.getName());
            return pending;
        }
    }

    // ------------------------------------------------------------------
    // Synchronisation du champ personnalisé SLA ("Attente Infos Sup")
    // ------------------------------------------------------------------

    /**
     * Recherche ou crée automatiquement le champ personnalisé global « Attente Infos Sup »
     * permettant à Time to SLA de mettre en pause les SLA tant que la valeur vaut « Oui ».
     */
    public static CustomField ensureSlaCustomField() {
        try {
            CustomFieldManager cfm = ComponentAccessor.getCustomFieldManager();
            if (cfm == null) {
                return null;
            }
            Collection<CustomField> existing = cfm.getCustomFieldObjectsByName(SLA_FIELD_NAME);
            if (existing != null && !existing.isEmpty()) {
                return existing.iterator().next();
            }
            Collection<CustomField> existingIgnoreCase = cfm.getCustomFieldObjectsByNameIgnoreCase(SLA_FIELD_NAME);
            if (existingIgnoreCase != null && !existingIgnoreCase.isEmpty()) {
                return existingIgnoreCase.iterator().next();
            }

            CustomFieldType<?, ?> cfType = cfm.getCustomFieldType("com.atlassian.jira.plugin.system.customfieldtypes:textfield");
            if (cfType == null) {
                cfType = cfm.getCustomFieldType("com.atlassian.jira.plugin.system.customfieldtypes:readonlyfield");
            }
            if (cfType == null) {
                return null;
            }

            CustomFieldSearcher searcher = cfm.getCustomFieldSearcher("com.atlassian.jira.plugin.system.customfieldtypes:textsearcher");
            if (searcher == null) {
                searcher = cfm.getDefaultSearcher(cfType);
            }

            List<JiraContextNode> contexts = Collections.singletonList(GlobalIssueContext.getInstance());
            List<IssueType> issueTypes = Collections.singletonList((IssueType) null);

            CustomField created = cfm.createCustomField(SLA_FIELD_NAME, SLA_FIELD_DESC, cfType, searcher, contexts, issueTypes);
            log.info("[DEMANDE INFOS SUP] Champ personnalisé '{}' ({}) créé automatiquement pour Time to SLA.",
                    SLA_FIELD_NAME, created != null ? created.getId() : "null");
            return created;
        } catch (Exception e) {
            log.warn("Impossible de créer ou récupérer le champ personnalisé '" + SLA_FIELD_NAME + "'", e);
            return null;
        }
    }

    private void updateIssueAssigneeAndSla(MutableIssue issue, ApplicationUser targetAssignee, ApplicationUser actor, boolean hasPending) {
        try {
            if (targetAssignee != null) {
                issue.setAssignee(targetAssignee);
            }

            String projectKey = issue.getProjectObject() != null ? issue.getProjectObject().getKey() : null;
            boolean pauseSlaEnabled = projectKey == null || projectConfigService.getConfig(projectKey).isPauseSla();

            CustomField slaField = null;
            Object oldVal = null;
            Object targetVal = null;

            if (pauseSlaEnabled) {
                slaField = ensureSlaCustomField();
                if (slaField != null) {
                    oldVal = issue.getCustomFieldValue(slaField);
                    String targetStr = hasPending ? SLA_VALUE_PENDING : SLA_VALUE_NONE;
                    targetVal = resolveCustomFieldValue(slaField, issue, targetStr);
                    issue.setCustomFieldValue(slaField, targetVal);
                }
            }

            issueManager.updateIssue(actor, issue, EventDispatchOption.ISSUE_UPDATED, true);

            if (slaField != null && targetVal != null) {
                try {
                    MutableIssue reloaded = issueManager.getIssueObject(issue.getId());
                    Object persistedVal = reloaded != null ? reloaded.getCustomFieldValue(slaField) : null;
                    if (persistedVal == null || !String.valueOf(persistedVal).equalsIgnoreCase(String.valueOf(targetVal))) {
                        slaField.updateValue(null, issue, new ModifiedValue<Object>(oldVal, targetVal), new DefaultIssueChangeHolder());
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            log.warn("Impossible de mettre à jour le ticket " + issue.getKey(), e);
        }
    }

    private Object resolveCustomFieldValue(CustomField slaField, MutableIssue issue, String targetStr) {
        try {
            CustomFieldType<?, ?> type = slaField.getCustomFieldType();
            if (type != null && type.getKey() != null && type.getKey().contains("select")) {
                FieldConfig fieldConfig = slaField.getRelevantConfig(issue);
                OptionsManager optionsManager = ComponentAccessor.getOptionsManager();
                if (fieldConfig != null && optionsManager != null) {
                    Options options = optionsManager.getOptions(fieldConfig);
                    if (options != null) {
                        Option opt = options.getOptionForValue(targetStr, null);
                        if (opt == null) {
                            opt = optionsManager.createOption(fieldConfig, null, Long.valueOf(options.size() + 1L), targetStr);
                        }
                        return opt;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return targetStr;
    }

    // ------------------------------------------------------------------
    // Règles métier basées sur l'État / Étape du ticket
    // ------------------------------------------------------------------

    /**
     * Vérifie si l'état courant du ticket fige le ticket (verrouille l'état et les transitions)
     * lorsqu'une demande d'infos sup est en attente.
     * Par défaut, tous les états figent le ticket sauf ceux décochés dans {@code unblockedStatusNames}.
     */
    private boolean isCurrentStatusFrozenWhenPending(MutableIssue issue, ProjectConfig config) {
        if (config == null || config.getUnblockedStatusNames().isEmpty()) {
            return true;
        }
        Set<String> candidateNames = getCurrentStatusAndStepNamesLower(issue);
        for (String unblocked : config.getUnblockedStatusNames()) {
            if (unblocked != null && candidateNames.contains(unblocked.trim().toLowerCase())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Vérifie si l'état courant du ticket permet de créer une demande d'infos sup.
     * - Par défaut ({@code requestStatusesCustomized == false}) : tous les états finaux (catégorie Jira "done"
     *   ou étape terminale du workflow sans transition sortante) sont interdits, et les autres sont autorisés.
     * - Si l'administrateur a personnalisé la sélection ({@code requestStatusesCustomized == true}) :
     *   seuls les états cochés dans {@code allowedRequestStatusNames} autorisent la demande.
     *
     * @return {@code null} si la demande est autorisée, ou le message explicatif si elle est interdite.
     */
    private String getDisallowedStatusReason(MutableIssue issue, ProjectConfig config) {
        String displayStatus = getCurrentStatusDisplayName(issue);

        if (config != null && config.isRequestStatusesCustomized()) {
            Set<String> currentNamesLower = getCurrentStatusAndStepNamesLower(issue);
            List<String> allowed = config.getAllowedRequestStatusNames();
            boolean match = false;
            if (allowed != null) {
                for (String a : allowed) {
                    if (a != null && currentNamesLower.contains(a.trim().toLowerCase())) {
                        match = true;
                        break;
                    }
                }
            }
            if (!match) {
                return "Aucune demande d'infos sup possible à l'état « " + displayStatus + " ».";
            }
            return null;
        }

        // Comportement par défaut : tous les états finaux ne permettent pas de demander d'infos sup
        if (isFinalStatusOrStep(issue)) {
            return "Ticket à l'état final (« " + displayStatus + " ») : aucune demande d'infos sup possible.";
        }
        return null;
    }

    private String getCurrentStatusDisplayName(MutableIssue issue) {
        Status status = issue.getStatus();
        if (status != null) {
            if (status.getNameTranslation() != null && !status.getNameTranslation().trim().isEmpty()) {
                return status.getNameTranslation().trim();
            }
            if (status.getName() != null && !status.getName().trim().isEmpty()) {
                return status.getName().trim();
            }
        }
        return "";
    }

    private Set<String> getCurrentStatusAndStepNamesLower(MutableIssue issue) {
        Set<String> names = new LinkedHashSet<String>();
        Status status = issue.getStatus();
        if (status != null) {
            if (status.getNameTranslation() != null && !status.getNameTranslation().trim().isEmpty()) {
                names.add(status.getNameTranslation().trim().toLowerCase());
            }
            if (status.getName() != null && !status.getName().trim().isEmpty()) {
                names.add(status.getName().trim().toLowerCase());
            }
        }
        try {
            WorkflowManager wfm = ComponentAccessor.getWorkflowManager();
            JiraWorkflow wf = wfm != null ? wfm.getWorkflow(issue) : null;
            if (wf != null && status != null) {
                StepDescriptor step = wf.getLinkedStep(status);
                if (step != null && step.getName() != null && !step.getName().trim().isEmpty()) {
                    names.add(step.getName().trim().toLowerCase());
                }
            }
        } catch (Exception ignored) {
        }
        return names;
    }

    /**
     * Détermine si le ticket est dans un état final (catégorie Jira "done"
     * ou étape terminale du workflow n'ayant aucune transition sortante).
     */
    private boolean isFinalStatusOrStep(MutableIssue issue) {
        Status status = issue.getStatus();
        if (status != null && status.getStatusCategory() != null
                && "done".equalsIgnoreCase(status.getStatusCategory().getKey())) {
            return true;
        }
        try {
            WorkflowManager wfm = ComponentAccessor.getWorkflowManager();
            JiraWorkflow wf = wfm != null ? wfm.getWorkflow(issue) : null;
            if (wf != null && status != null) {
                StepDescriptor step = wf.getLinkedStep(status);
                WorkflowDescriptor wd = wf.getDescriptor();
                if (step != null && wd != null) {
                    boolean hasStepActions = step.getActions() != null && !step.getActions().isEmpty();
                    boolean hasGlobalActions = wd.getGlobalActions() != null && !wd.getGlobalActions().isEmpty();
                    if (!hasStepActions && !hasGlobalActions) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void checkRequestAllowed(MutableIssue issue, ApplicationUser requester) {
        if (requester == null) {
            throw ClarificationException.forbidden("Vous devez être connecté.");
        }
        if (getHistory(issue.getKey()).hasPending()) {
            throw new ClarificationException(ClarificationException.CONFLICT,
                    "Une demande est déjà en attente sur ce ticket.");
        }
        String projectKey = issue.getProjectObject() != null ? issue.getProjectObject().getKey() : null;
        ProjectConfig config = projectKey != null ? projectConfigService.getConfig(projectKey) : null;
        String disallowedReason = getDisallowedStatusReason(issue, config);
        if (disallowedReason != null) {
            throw ClarificationException.forbidden(disallowedReason);
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
