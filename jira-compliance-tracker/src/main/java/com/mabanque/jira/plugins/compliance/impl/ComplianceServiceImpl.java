package com.mabanque.jira.plugins.compliance.impl;

import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.google.gson.Gson;
import com.mabanque.jira.plugins.compliance.api.ComplianceRecord;
import com.mabanque.jira.plugins.compliance.api.ComplianceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.text.SimpleDateFormat;
import java.util.Date;

@ExportAsService({ComplianceService.class})
@Named("complianceService")
public class ComplianceServiceImpl implements ComplianceService {

    private static final Logger log = LoggerFactory.getLogger(ComplianceServiceImpl.class);
    private static final String PLUGIN_STORAGE_KEY = "com.mabanque.jira.compliance";
    private static final String KEY_PREFIX = "issue.";

    private final PluginSettingsFactory pluginSettingsFactory;
    private final Gson gson;

    @Inject
    public ComplianceServiceImpl(@ComponentImport PluginSettingsFactory pluginSettingsFactory) {
        this.pluginSettingsFactory = pluginSettingsFactory;
        this.gson = new Gson();
    }

    private PluginSettings getSettings() {
        return pluginSettingsFactory.createSettingsForKey(PLUGIN_STORAGE_KEY);
    }

    @Override
    public ComplianceRecord getCompliance(String issueKey) {
        if (issueKey == null || issueKey.trim().isEmpty()) {
            return new ComplianceRecord();
        }

        try {
            Object raw = getSettings().get(KEY_PREFIX + issueKey.trim().toUpperCase());
            if (raw instanceof String) {
                ComplianceRecord record = gson.fromJson((String) raw, ComplianceRecord.class);
                if (record != null) {
                    return record;
                }
            }
        } catch (Exception e) {
            log.error("Erreur lors de la lecture de la conformite pour le ticket " + issueKey, e);
        }

        ComplianceRecord fallback = new ComplianceRecord();
        fallback.setIssueKey(issueKey.trim().toUpperCase());
        fallback.setStatus(ComplianceRecord.STATUS_PENDING);
        return fallback;
    }

    @Override
    public ComplianceRecord setCompliance(String issueKey, String status, String comment, String reviewer) {
        String cleanKey = issueKey.trim().toUpperCase();
        String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        ComplianceRecord record = new ComplianceRecord(cleanKey, status, reviewer, dateStr, comment);

        try {
            String json = gson.toJson(record);
            getSettings().put(KEY_PREFIX + cleanKey, json);
            log.info("[COMPLIANCE-AUDIT] Ticket {} mis a jour : statut={}, auditeur={}", cleanKey, status, reviewer);
        } catch (Exception e) {
            log.error("Erreur lors de l'enregistrement de la conformite pour le ticket " + cleanKey, e);
        }

        return record;
    }

    @Override
    public boolean isCompliant(String issueKey) {
        ComplianceRecord record = getCompliance(issueKey);
        return ComplianceRecord.STATUS_COMPLIANT.equalsIgnoreCase(record.getStatus());
    }
}
