package com.mabanque.jira.plugins.compliance.api;

/**
 * Service métier de gestion de la conformité des tickets Jira.
 */
public interface ComplianceService {

    /**
     * Récupère l'enregistrement de conformité d'un ticket.
     */
    ComplianceRecord getCompliance(String issueKey);

    /**
     * Enregistre un visa de conformité ou une décision d'audit pour un ticket.
     */
    ComplianceRecord setCompliance(String issueKey, String status, String comment, String reviewer);

    /**
     * Indique si un ticket dispose d'un visa de conformité validé.
     */
    boolean isCompliant(String issueKey);
}
