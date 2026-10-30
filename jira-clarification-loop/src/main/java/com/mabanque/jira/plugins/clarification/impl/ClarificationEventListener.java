package com.mabanque.jira.plugins.clarification.impl;

import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.jira.event.issue.IssueEvent;
import com.atlassian.jira.event.type.EventDispatchOption;
import com.atlassian.jira.event.type.EventType;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.mabanque.jira.plugins.clarification.api.ClarificationEntry;
import com.mabanque.jira.plugins.clarification.api.ClarificationHistory;
import com.mabanque.jira.plugins.clarification.api.ClarificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Écouteur d'événements Jira assurant l'intégrité des demandes d'informations supplémentaires en attente :
 * empêche le détournement d'assignation pendant qu'une demande d'infos sup est en cours.
 */
@Named("clarificationEventListener")
public class ClarificationEventListener implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ClarificationEventListener.class);

    private final EventPublisher eventPublisher;
    private final ClarificationService clarificationService;
    private final IssueManager issueManager;
    private final UserManager userManager;

    @Inject
    public ClarificationEventListener(@ComponentImport EventPublisher eventPublisher,
                                      ClarificationService clarificationService,
                                      @ComponentImport IssueManager issueManager,
                                      @ComponentImport UserManager userManager) {
        this.eventPublisher = eventPublisher;
        this.clarificationService = clarificationService;
        this.issueManager = issueManager;
        this.userManager = userManager;
    }

    @Override
    public void afterPropertiesSet() {
        eventPublisher.register(this);
        log.info("[CLARIFICATION] ClarificationEventListener enregistré.");
    }

    @Override
    public void destroy() {
        eventPublisher.unregister(this);
        log.info("[CLARIFICATION] ClarificationEventListener désinscrit.");
    }

    @EventListener
    public void onIssueEvent(IssueEvent event) {
        if (event == null || event.getIssue() == null) {
            return;
        }
        Issue issue = event.getIssue();
        ClarificationHistory history = clarificationService.getHistory(issue.getKey());
        ClarificationEntry pending = history.getPendingEntry();
        if (pending == null) {
            return;
        }

        // Si quelqu'un tente de réassigner manuellement le ticket alors qu'une demande d'infos sup est en attente,
        // on rétablit automatiquement le destinataire attendu de la demande.
        if (EventType.ISSUE_ASSIGNED_ID.equals(event.getEventTypeId())) {
            String expectedUsername = pending.getRecipientUsername();
            ApplicationUser currentAssignee = issue.getAssignee();
            if (expectedUsername != null && (currentAssignee == null || !expectedUsername.equalsIgnoreCase(currentAssignee.getName()))) {
                ApplicationUser expectedUser = userManager.getUserByName(expectedUsername);
                MutableIssue mutableIssue = issueManager.getIssueObject(issue.getId());
                if (expectedUser != null && mutableIssue != null) {
                    mutableIssue.setAssignee(expectedUser);
                    issueManager.updateIssue(expectedUser, mutableIssue, EventDispatchOption.DO_NOT_DISPATCH, false);
                    log.info("[CLARIFICATION] Réassignation manuelle bloquée sur {} : maintien du destinataire {}", issue.getKey(), expectedUsername);
                }
            }
        }
    }
}
