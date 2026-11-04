package com.socgen.jira.plugins.clarification.api;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration du module « Demande infos sup » pour un projet donné.
 * Stockée en JSON via PluginSettings, clé par projectKey.
 */
public class ProjectConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean enabled;
    private Boolean pauseSla;
    private List<String> enabledIssueTypeIds;
    private List<String> blockedTransitionNames;

    public ProjectConfig() {
        this.enabled = false;
        this.pauseSla = Boolean.TRUE;
        this.enabledIssueTypeIds = new ArrayList<String>();
        this.blockedTransitionNames = new ArrayList<String>();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Indique si le champ personnalisé « Attente Infos Sup » (pour Time to SLA)
     * doit être synchronisé automatiquement lors d'une demande d'infos sup.
     * Actif par défaut (y compris pour les projets déjà configurés en v2.0.2/v2.0.3).
     */
    public boolean isPauseSla() {
        return pauseSla == null || pauseSla.booleanValue();
    }

    public void setPauseSla(boolean pauseSla) {
        this.pauseSla = Boolean.valueOf(pauseSla);
    }

    public List<String> getEnabledIssueTypeIds() {
        return enabledIssueTypeIds;
    }

    public void setEnabledIssueTypeIds(List<String> enabledIssueTypeIds) {
        this.enabledIssueTypeIds = enabledIssueTypeIds != null ? enabledIssueTypeIds : new ArrayList<String>();
    }

    public List<String> getBlockedTransitionNames() {
        return blockedTransitionNames;
    }

    public void setBlockedTransitionNames(List<String> blockedTransitionNames) {
        this.blockedTransitionNames = blockedTransitionNames != null ? blockedTransitionNames : new ArrayList<String>();
    }

    /**
     * Vérifie si un type de ticket donné est éligible au module.
     * Si la liste est vide, tous les types sont autorisés (rétrocompatibilité).
     */
    public boolean isIssueTypeAllowed(String issueTypeId) {
        return enabledIssueTypeIds == null || enabledIssueTypeIds.isEmpty()
                || enabledIssueTypeIds.contains(issueTypeId);
    }

    /**
     * Vérifie si une transition est dans la liste des transitions à bloquer.
     * Si la liste est vide, toutes les transitions sont bloquées (comportement v1).
     */
    public boolean isTransitionBlocked(String transitionName) {
        return blockedTransitionNames == null || blockedTransitionNames.isEmpty()
                || blockedTransitionNames.contains(transitionName);
    }
}
