package com.socgen.jira.plugins.clarification.impl;

import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.google.gson.Gson;
import com.socgen.jira.plugins.clarification.api.ProjectConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Service de gestion de la configuration « Demande infos sup » par projet.
 * Stocke et lit la configuration dans PluginSettings (JSON).
 */
@Named("projectConfigService")
public class ProjectConfigService {

    private static final Logger log = LoggerFactory.getLogger(ProjectConfigService.class);
    private static final String PLUGIN_STORAGE_KEY = "com.socgen.jira.clarification";
    private static final String CONFIG_PREFIX = "project.config.";

    private final PluginSettingsFactory pluginSettingsFactory;
    private final Gson gson;

    @Inject
    public ProjectConfigService(@ComponentImport PluginSettingsFactory pluginSettingsFactory) {
        this.pluginSettingsFactory = pluginSettingsFactory;
        this.gson = new Gson();
    }

    private PluginSettings getSettings() {
        return pluginSettingsFactory.createSettingsForKey(PLUGIN_STORAGE_KEY);
    }

    /**
     * Récupère la configuration pour un projet donné.
     * Si aucune configuration n'a encore été enregistrée, le module est ACTIVÉ par défaut
     * pour tous les types de tickets et toutes les transitions (comportement v1 conservé).
     */
    public ProjectConfig getConfig(String projectKey) {
        if (projectKey == null || projectKey.trim().isEmpty()) {
            return defaultConfig();
        }
        try {
            Object raw = getSettings().get(CONFIG_PREFIX + projectKey.trim().toUpperCase());
            if (raw instanceof String && !((String) raw).trim().isEmpty()) {
                ProjectConfig config = gson.fromJson((String) raw, ProjectConfig.class);
                if (config != null) {
                    config.setPauseSla(config.isPauseSla());
                    return config;
                }
            }
        } catch (Exception e) {
            log.error("Erreur lors de la lecture de la configuration pour le projet " + projectKey, e);
        }
        return defaultConfig();
    }

    /** Configuration par défaut : module actif, SLA mis en pause, tous types de tickets, toutes transitions bloquées. */
    private static ProjectConfig defaultConfig() {
        ProjectConfig config = new ProjectConfig();
        config.setEnabled(true);
        config.setPauseSla(true);
        return config;
    }

    /**
     * Sauvegarde la configuration pour un projet donné.
     */
    public void saveConfig(String projectKey, ProjectConfig config) {
        if (projectKey == null || config == null) {
            return;
        }
        config.setPauseSla(config.isPauseSla());
        String json = gson.toJson(config);
        getSettings().put(CONFIG_PREFIX + projectKey.trim().toUpperCase(), json);
        log.info("[DEMANDE INFOS SUP] Configuration sauvegardée pour le projet {}", projectKey);
    }

    /**
     * Vérifie si le module est activé pour un projet et un type de ticket donnés.
     */
    public boolean isEnabledFor(String projectKey, String issueTypeId) {
        ProjectConfig config = getConfig(projectKey);
        return config.isEnabled() && config.isIssueTypeAllowed(issueTypeId);
    }
}
