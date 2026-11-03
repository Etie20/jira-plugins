package com.socgen.jira.plugins.clarification.jql;

import com.atlassian.jira.JiraDataType;
import com.atlassian.jira.JiraDataTypes;
import com.atlassian.jira.jql.operand.QueryLiteral;
import com.atlassian.jira.jql.query.QueryCreationContext;
import com.atlassian.jira.plugin.jql.function.AbstractJqlFunction;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.MessageSet;
import com.atlassian.jira.util.MessageSetImpl;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.google.gson.Gson;
import com.socgen.jira.plugins.clarification.api.ClarificationEntry;
import com.socgen.jira.plugins.clarification.api.ClarificationHistory;
import com.atlassian.query.clause.TerminalClause;
import com.atlassian.query.operand.FunctionOperand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Fonction JQL personnalisée : pendingInfoSup()
 *
 * Utilisation : issue in pendingInfoSup()
 * Retourne les clés de tous les tickets ayant une demande d'informations supplémentaires en attente.
 *
 * Les permissions Jira (Browse Projects, Issue Security) sont automatiquement appliquées
 * par le moteur de recherche Jira après l'exécution de cette fonction.
 */
@Named("pendingInfoSupFunction")
public class PendingInfoSupFunction extends AbstractJqlFunction {

    private static final Logger log = LoggerFactory.getLogger(PendingInfoSupFunction.class);
    private static final String PLUGIN_STORAGE_KEY = "com.socgen.jira.clarification";
    private static final String KEY_PREFIX = "clarification.issue.";

    private final PluginSettingsFactory pluginSettingsFactory;
    private final Gson gson = new Gson();

    @Inject
    public PendingInfoSupFunction(@ComponentImport PluginSettingsFactory pluginSettingsFactory) {
        this.pluginSettingsFactory = pluginSettingsFactory;
    }

    @Override
    public MessageSet validate(ApplicationUser searcher, FunctionOperand operand, TerminalClause terminalClause) {
        return new MessageSetImpl();
    }

    @Override
    public List<QueryLiteral> getValues(QueryCreationContext queryCreationContext,
                                        FunctionOperand operand,
                                        TerminalClause terminalClause) {
        List<QueryLiteral> result = new ArrayList<QueryLiteral>();
        try {
            PluginSettings settings = pluginSettingsFactory.createSettingsForKey(PLUGIN_STORAGE_KEY);
            // Parcourir toutes les clés de stockage pour trouver les tickets en attente
            // Note : PluginSettings ne supporte pas l'itération directe des clés.
            // On utilise une approche via un index maintenu séparément.
            Object rawIndex = settings.get("clarification.pending.index");
            if (rawIndex instanceof String && !((String) rawIndex).trim().isEmpty()) {
                String[] keys = ((String) rawIndex).split(",");
                for (String issueKey : keys) {
                    if (issueKey == null || issueKey.trim().isEmpty()) {
                        continue;
                    }
                    String cleanKey = issueKey.trim().toUpperCase();
                    try {
                        Object raw = settings.get(KEY_PREFIX + cleanKey);
                        if (raw instanceof String && !((String) raw).trim().isEmpty()) {
                            ClarificationHistory history = gson.fromJson((String) raw, ClarificationHistory.class);
                            if (history != null && history.hasPending()) {
                                result.add(new QueryLiteral(operand, cleanKey));
                            }
                        }
                    } catch (Exception e) {
                        log.debug("Erreur de lecture pour {} dans la fonction JQL", cleanKey);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Erreur lors de l'exécution de la fonction JQL pendingInfoSup()", e);
        }
        return result;
    }

    @Override
    public int getMinimumNumberOfExpectedArguments() {
        return 0;
    }

    @Override
    public JiraDataType getDataType() {
        return JiraDataTypes.ISSUE;
    }
}
