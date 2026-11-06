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

    /**
     * Indique si l'administrateur du projet a personnalisé la liste des états
     * autorisant la création d'une demande d'infos sup.
     * Tant que {@code false} (par défaut), tous les états non finaux sont autorisés
     * et tous les états finaux (catégorie Done / Terminé) sont interdits.
     */
    private boolean requestStatusesCustomized;
    private List<String> allowedRequestStatusNames;

    /**
     * États du ticket dans lesquels une demande d'infos sup en attente NE fige PAS le ticket
     * (ne bloque pas le changement d'état / les transitions).
     * Vide par défaut (= le ticket est figé quel que soit son état lorsqu'une demande est en attente).
     */
    private List<String> unblockedStatusNames;

    private List<String> blockedTransitionNames;
    private List<String> disallowedRequestTransitionNames;

    public ProjectConfig() {
        this.enabled = false;
        this.pauseSla = Boolean.TRUE;
        this.enabledIssueTypeIds = new ArrayList<String>();
        this.requestStatusesCustomized = false;
        this.allowedRequestStatusNames = new ArrayList<String>();
        this.unblockedStatusNames = new ArrayList<String>();
        this.blockedTransitionNames = new ArrayList<String>();
        this.disallowedRequestTransitionNames = new ArrayList<String>();
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
     * Actif par défaut.
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

    public boolean isRequestStatusesCustomized() {
        return requestStatusesCustomized;
    }

    public void setRequestStatusesCustomized(boolean requestStatusesCustomized) {
        this.requestStatusesCustomized = requestStatusesCustomized;
    }

    public List<String> getAllowedRequestStatusNames() {
        if (allowedRequestStatusNames == null) {
            allowedRequestStatusNames = new ArrayList<String>();
        }
        return allowedRequestStatusNames;
    }

    public void setAllowedRequestStatusNames(List<String> allowedRequestStatusNames) {
        this.allowedRequestStatusNames = allowedRequestStatusNames != null
                ? allowedRequestStatusNames : new ArrayList<String>();
    }

    public List<String> getUnblockedStatusNames() {
        if (unblockedStatusNames == null) {
            unblockedStatusNames = new ArrayList<String>();
        }
        return unblockedStatusNames;
    }

    public void setUnblockedStatusNames(List<String> unblockedStatusNames) {
        this.unblockedStatusNames = unblockedStatusNames != null
                ? unblockedStatusNames : new ArrayList<String>();
    }

    public List<String> getBlockedTransitionNames() {
        return blockedTransitionNames;
    }

    public void setBlockedTransitionNames(List<String> blockedTransitionNames) {
        this.blockedTransitionNames = blockedTransitionNames != null ? blockedTransitionNames : new ArrayList<String>();
    }

    public List<String> getDisallowedRequestTransitionNames() {
        if (disallowedRequestTransitionNames == null) {
            disallowedRequestTransitionNames = new ArrayList<String>();
        }
        return disallowedRequestTransitionNames;
    }

    public void setDisallowedRequestTransitionNames(List<String> disallowedRequestTransitionNames) {
        this.disallowedRequestTransitionNames = disallowedRequestTransitionNames != null
                ? disallowedRequestTransitionNames : new ArrayList<String>();
    }

    /**
     * Vérifie si un type de ticket donné est éligible au module.
     * Si la liste est vide, tous les types sont autorisés (rétrocompatibilité).
     */
    public boolean isIssueTypeAllowed(String issueTypeId) {
        return enabledIssueTypeIds == null || enabledIssueTypeIds.isEmpty()
                || enabledIssueTypeIds.contains(issueTypeId);
    }

    public boolean isTransitionBlocked(String transitionName) {
        return blockedTransitionNames == null || blockedTransitionNames.isEmpty()
                || blockedTransitionNames.contains(transitionName);
    }
}
