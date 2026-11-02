package com.socgen.jira.plugins.attachments_download.impl;

import com.atlassian.sal.api.ApplicationProperties;
import com.socgen.jira.plugins.attachments_download.api.AttachmentsDownloadPluginComponent;

public class AttachmentsDownloadPluginComponentImpl implements AttachmentsDownloadPluginComponent {

    private final ApplicationProperties applicationProperties;

    public AttachmentsDownloadPluginComponentImpl(final ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
    }

    @Override
    public String getName() {
        if (applicationProperties != null) {
            return "attachmentsDownloadComponent:" + applicationProperties.getDisplayName();
        }
        return "attachmentsDownloadComponent";
    }
}
