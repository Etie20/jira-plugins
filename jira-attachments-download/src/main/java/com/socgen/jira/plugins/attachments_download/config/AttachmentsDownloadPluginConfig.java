package com.socgen.jira.plugins.attachments_download.config;

import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.config.ConstantsManager;
import com.atlassian.jira.issue.AttachmentManager;
import com.atlassian.jira.issue.fields.config.manager.IssueTypeSchemeManager;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.workflow.WorkflowManager;
import com.atlassian.jira.workflow.WorkflowSchemeManager;
import com.atlassian.plugins.osgi.javaconfig.ExportOptions;
import com.atlassian.plugins.osgi.javaconfig.configs.beans.ModuleFactoryBean;
import com.atlassian.plugins.osgi.javaconfig.configs.beans.PluginAccessorBean;
import com.atlassian.sal.api.ApplicationProperties;
import com.socgen.jira.plugins.attachments_download.api.AttachmentDownloadService;
import com.socgen.jira.plugins.attachments_download.api.AttachmentsDownloadPluginComponent;
import com.socgen.jira.plugins.attachments_download.impl.AttachmentDownloadServiceImpl;
import com.socgen.jira.plugins.attachments_download.impl.AttachmentsDownloadPluginComponentImpl;
import com.socgen.jira.plugins.attachments_download.rest.AttachmentDownloadResource;
import org.osgi.framework.ServiceRegistration;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static com.atlassian.plugins.osgi.javaconfig.OsgiServices.exportOsgiService;
import static com.atlassian.plugins.osgi.javaconfig.OsgiServices.importOsgiService;

/**
 * Spring Java Configuration for Jira Attachments Download Plugin.
 * Manages imports from Jira OSGi host container and exports plugin services.
 */
@Configuration
@Import({
        ModuleFactoryBean.class,
        PluginAccessorBean.class
})
public class AttachmentsDownloadPluginConfig {

    // Imports from Jira OSGi host container
    @Bean
    public ApplicationProperties applicationProperties() {
        return importOsgiService(ApplicationProperties.class);
    }

    @Bean
    public SearchService searchService() {
        return importOsgiService(SearchService.class);
    }

    @Bean
    public AttachmentManager attachmentManager() {
        return importOsgiService(AttachmentManager.class);
    }

    @Bean
    public PermissionManager permissionManager() {
        return importOsgiService(PermissionManager.class);
    }

    @Bean
    public JiraAuthenticationContext jiraAuthenticationContext() {
        return importOsgiService(JiraAuthenticationContext.class);
    }

    @Bean
    public ConstantsManager constantsManager() {
        return importOsgiService(ConstantsManager.class);
    }

    @Bean
    public ProjectManager projectManager() {
        return importOsgiService(ProjectManager.class);
    }

    @Bean
    public IssueTypeSchemeManager issueTypeSchemeManager() {
        return importOsgiService(IssueTypeSchemeManager.class);
    }

    @Bean
    public WorkflowSchemeManager workflowSchemeManager() {
        return importOsgiService(WorkflowSchemeManager.class);
    }

    @Bean
    public WorkflowManager workflowManager() {
        return importOsgiService(WorkflowManager.class);
    }

    // Business components
    @Bean
    public AttachmentsDownloadPluginComponent attachmentsDownloadPluginComponent(ApplicationProperties applicationProperties) {
        return new AttachmentsDownloadPluginComponentImpl(applicationProperties);
    }

    @Bean
    public AttachmentDownloadService attachmentDownloadService(SearchService searchService,
                                                               AttachmentManager attachmentManager,
                                                               PermissionManager permissionManager) {
        return new AttachmentDownloadServiceImpl(searchService, attachmentManager, permissionManager);
    }

    // REST resource
    @Bean
    public AttachmentDownloadResource attachmentDownloadResource(AttachmentDownloadService attachmentDownloadService,
                                                                 JiraAuthenticationContext jiraAuthenticationContext,
                                                                 ConstantsManager constantsManager,
                                                                 PermissionManager permissionManager,
                                                                 ProjectManager projectManager,
                                                                 IssueTypeSchemeManager issueTypeSchemeManager,
                                                                 WorkflowSchemeManager workflowSchemeManager,
                                                                 WorkflowManager workflowManager) {
        return new AttachmentDownloadResource(attachmentDownloadService,
                jiraAuthenticationContext,
                constantsManager,
                permissionManager,
                projectManager,
                issueTypeSchemeManager,
                workflowSchemeManager,
                workflowManager);
    }

    // OSGi exported services
    @Bean
    public FactoryBean<ServiceRegistration> registerPluginComponentService(final AttachmentsDownloadPluginComponent pluginComponent) {
        return exportOsgiService(pluginComponent, ExportOptions.as(AttachmentsDownloadPluginComponent.class));
    }

    @Bean
    public FactoryBean<ServiceRegistration> registerAttachmentDownloadService(final AttachmentDownloadService attachmentDownloadService) {
        return exportOsgiService(attachmentDownloadService, ExportOptions.as(AttachmentDownloadService.class));
    }
}
