package com.mabanque.jira.plugins.compliance.listener;

import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.jira.event.issue.IssueEvent;
import com.atlassian.jira.event.type.EventType;
import com.atlassian.jira.issue.Issue;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.mabanque.jira.plugins.compliance.api.ComplianceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Composant Spring d'arrière-plan écoutant les événements Jira (cycle de vie des tickets).
 * Permet de tracer et contrôler les résolutions de tickets sans visa de conformité.
 */
@Named("complianceIssueEventListener")
public class ComplianceIssueEventListener implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ComplianceIssueEventListener.class);

    private final EventPublisher eventPublisher;
    private final ComplianceService complianceService;

    @Inject
    public ComplianceIssueEventListener(@ComponentImport EventPublisher eventPublisher,
                                        ComplianceService complianceService) {
        this.eventPublisher = eventPublisher;
        this.complianceService = complianceService;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // Enregistrement auprès du bus d'événements Atlassian
        eventPublisher.register(this);
        log.info("[COMPLIANCE-PLUGIN] ComplianceIssueEventListener démarré et enregistré.");
    }

    @Override
    public void destroy() throws Exception {
        // Désabonnement propre pour éviter les fuites mémoire lors du rechargement
        eventPublisher.unregister(this);
        log.info("[COMPLIANCE-PLUGIN] ComplianceIssueEventListener désinscrit.");
    }

    @EventListener
    public void onIssueEvent(IssueEvent issueEvent) {
        if (issueEvent == null || issueEvent.getIssue() == null) {
            return;
        }

        Issue issue = issueEvent.getIssue();
        Long eventTypeId = issueEvent.getEventTypeId();

        // Détection de la résolution ou clôture du ticket
        if (EventType.ISSUE_RESOLVED_ID.equals(eventTypeId) || EventType.ISSUE_CLOSED_ID.equals(eventTypeId)) {
            boolean compliant = complianceService.isCompliant(issue.getKey());

            if (!compliant) {
                log.warn("[ALERTE-CONFORMITE] Ticket {} résolu/fermé SANS visa de conformité validé !", issue.getKey());
            } else {
                log.info("[AUDIT-CONFORMITE] Ticket {} résolu avec visa de conformité valide.", issue.getKey());
            }
        } else if (EventType.ISSUE_CREATED_ID.equals(eventTypeId)) {
            log.info("[AUDIT-CONFORMITE] Nouveau ticket créé : {}", issue.getKey());
        }
    }
}
