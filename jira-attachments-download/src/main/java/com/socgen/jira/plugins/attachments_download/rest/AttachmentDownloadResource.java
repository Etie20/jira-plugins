package com.socgen.jira.plugins.attachments_download.rest;

import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.ConstantsManager;
import com.atlassian.jira.issue.fields.config.manager.IssueTypeSchemeManager;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.issue.status.Status;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.workflow.JiraWorkflow;
import com.atlassian.jira.workflow.WorkflowManager;
import com.atlassian.jira.workflow.WorkflowSchemeManager;
import com.socgen.jira.plugins.attachments_download.api.AttachmentDownloadService;
import com.socgen.jira.plugins.attachments_download.api.AttachmentExportSummary;
import com.socgen.jira.plugins.attachments_download.api.AttachmentFilterCriteria;
import com.socgen.jira.plugins.attachments_download.api.FilterOptions;
import com.socgen.jira.plugins.attachments_download.api.GroupingStrategy;
import com.socgen.jira.plugins.attachments_download.impl.AttachmentDownloadServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * REST Endpoint for querying, previewing and downloading attachments in bulk as a ZIP archive.
 */
@Path("/")
public class AttachmentDownloadResource {

    private static final Logger log = LoggerFactory.getLogger(AttachmentDownloadResource.class);

    private final AttachmentDownloadService downloadService;
    private final JiraAuthenticationContext authenticationContext;
    private final ConstantsManager constantsManager;
    private final PermissionManager permissionManager;
    private final ProjectManager projectManager;
    private final IssueTypeSchemeManager issueTypeSchemeManager;
    private final WorkflowSchemeManager workflowSchemeManager;
    private final WorkflowManager workflowManager;

    /**
     * Default no-arg constructor required by Jersey 2 / HK2 when instantiating REST resource classes.
     * Safely locates Jira host components via ComponentAccessor.
     */
    public AttachmentDownloadResource() {
        this(
                resolveDownloadService(),
                ComponentAccessor.getJiraAuthenticationContext(),
                ComponentAccessor.getConstantsManager(),
                ComponentAccessor.getPermissionManager(),
                ComponentAccessor.getProjectManager(),
                ComponentAccessor.getIssueTypeSchemeManager(),
                ComponentAccessor.getWorkflowSchemeManager(),
                ComponentAccessor.getWorkflowManager()
        );
    }

    @Inject
    public AttachmentDownloadResource(final AttachmentDownloadService downloadService,
                                      final JiraAuthenticationContext authenticationContext,
                                      final ConstantsManager constantsManager,
                                      final PermissionManager permissionManager,
                                      final ProjectManager projectManager,
                                      final IssueTypeSchemeManager issueTypeSchemeManager,
                                      final WorkflowSchemeManager workflowSchemeManager,
                                      final WorkflowManager workflowManager) {
        this.downloadService = downloadService != null ? downloadService : resolveDownloadService();
        this.authenticationContext = authenticationContext != null ? authenticationContext : ComponentAccessor.getJiraAuthenticationContext();
        this.constantsManager = constantsManager != null ? constantsManager : ComponentAccessor.getConstantsManager();
        this.permissionManager = permissionManager != null ? permissionManager : ComponentAccessor.getPermissionManager();
        this.projectManager = projectManager != null ? projectManager : ComponentAccessor.getProjectManager();
        this.issueTypeSchemeManager = issueTypeSchemeManager != null ? issueTypeSchemeManager : ComponentAccessor.getIssueTypeSchemeManager();
        this.workflowSchemeManager = workflowSchemeManager != null ? workflowSchemeManager : ComponentAccessor.getWorkflowSchemeManager();
        this.workflowManager = workflowManager != null ? workflowManager : ComponentAccessor.getWorkflowManager();
    }

    public AttachmentDownloadResource(final AttachmentDownloadService downloadService,
                                      final JiraAuthenticationContext authenticationContext,
                                      final ConstantsManager constantsManager) {
        this(downloadService, authenticationContext, constantsManager, null, null, null, null, null);
    }

    private static AttachmentDownloadService resolveDownloadService() {
        try {
            AttachmentDownloadService s = ComponentAccessor.getComponent(AttachmentDownloadService.class);
            if (s != null) return s;
        } catch (Exception ignored) {
        }
        try {
            AttachmentDownloadService s = ComponentAccessor.getOSGiComponentInstanceOfType(AttachmentDownloadService.class);
            if (s != null) return s;
        } catch (Exception ignored) {
        }
        return new AttachmentDownloadServiceImpl(
                ComponentAccessor.getComponent(SearchService.class),
                ComponentAccessor.getAttachmentManager(),
                ComponentAccessor.getPermissionManager()
        );
    }

    /**
     * Discovers available filter options (accessible projects, statuses, and issue types).
     * If a project key is provided, statuses and issue types are filtered specifically for that project
     * and only accessible to the authorized user.
     */
    @GET
    @Path("/options")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getFilterOptions(@QueryParam("project") String projectKey) {
        ApplicationUser user = authenticationContext.getLoggedInUser();
        if (user == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("{\"error\": \"Authentication required\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }

        try {
            PermissionManager permMgr = this.permissionManager != null ? this.permissionManager : ComponentAccessor.getPermissionManager();
            ProjectManager projMgr = this.projectManager != null ? this.projectManager : ComponentAccessor.getProjectManager();
            IssueTypeSchemeManager itSchemeMgr = this.issueTypeSchemeManager != null ? this.issueTypeSchemeManager : ComponentAccessor.getIssueTypeSchemeManager();
            ConstantsManager constMgr = this.constantsManager != null ? this.constantsManager : ComponentAccessor.getConstantsManager();

            // 1. Collect projects accessible to the authorized user
            List<Project> accessibleProjects = new ArrayList<>();
            if (permMgr != null) {
                Collection<Project> pColl = permMgr.getProjects(ProjectPermissions.BROWSE_PROJECTS, user);
                if (pColl != null && !pColl.isEmpty()) {
                    accessibleProjects.addAll(pColl);
                }
            }
            if (accessibleProjects.isEmpty() && projMgr != null) {
                List<Project> allProjects = projMgr.getProjectObjects();
                if (allProjects != null) {
                    for (Project p : allProjects) {
                        if (permMgr == null || permMgr.hasPermission(ProjectPermissions.BROWSE_PROJECTS, p, user)) {
                            accessibleProjects.add(p);
                        }
                    }
                }
            }

            List<FilterOptions.ProjectOption> projectOptions = new ArrayList<>();
            for (Project p : accessibleProjects) {
                projectOptions.add(new FilterOptions.ProjectOption(p.getId().toString(), p.getKey(), p.getName()));
            }
            projectOptions.sort(Comparator.comparing(FilterOptions.ProjectOption::getKey, String.CASE_INSENSITIVE_ORDER));

            Map<String, FilterOptions.StatusOption> statusMap = new LinkedHashMap<>();
            Map<String, FilterOptions.IssueTypeOption> issueTypeMap = new LinkedHashMap<>();

            // 2. Identify target project if specified
            Project targetProject = null;
            if (StringUtils.isNotBlank(projectKey)) {
                String cleanKey = projectKey.trim();
                for (Project p : accessibleProjects) {
                    if (cleanKey.equalsIgnoreCase(p.getKey()) || cleanKey.equalsIgnoreCase(p.getName())) {
                        targetProject = p;
                        break;
                    }
                }
                if (targetProject == null && projMgr != null) {
                    Project p = projMgr.getProjectObjByKeyIgnoreCase(cleanKey);
                    if (p != null && (permMgr == null || permMgr.hasPermission(ProjectPermissions.BROWSE_PROJECTS, p, user))) {
                        targetProject = p;
                    }
                }
            }

            // 3. Extract project-specific issue types and workflow statuses
            if (targetProject != null) {
                log.info("Populating filter options for target project: {}", targetProject.getKey());
                if (itSchemeMgr != null) {
                    Collection<IssueType> projectTypes = itSchemeMgr.getIssueTypesForProject(targetProject);
                    if (projectTypes != null) {
                        for (IssueType it : projectTypes) {
                            issueTypeMap.put(it.getId(), new FilterOptions.IssueTypeOption(it.getId(), it.getName(), it.isSubTask()));
                        }
                    }
                }
                populateStatusesForProject(targetProject, statusMap);
            } else {
                // If no project specified, aggregate across accessible projects
                for (Project p : accessibleProjects) {
                    if (itSchemeMgr != null) {
                        Collection<IssueType> pTypes = itSchemeMgr.getIssueTypesForProject(p);
                        if (pTypes != null) {
                            for (IssueType it : pTypes) {
                                issueTypeMap.putIfAbsent(it.getId(), new FilterOptions.IssueTypeOption(it.getId(), it.getName(), it.isSubTask()));
                            }
                        }
                    }
                    populateStatusesForProject(p, statusMap);
                }
            }

            // Fallback to global constants if empty
            if (issueTypeMap.isEmpty() && constMgr != null) {
                for (IssueType it : constMgr.getAllIssueTypeObjects()) {
                    issueTypeMap.put(it.getId(), new FilterOptions.IssueTypeOption(it.getId(), it.getName(), it.isSubTask()));
                }
            }
            if (statusMap.isEmpty() && constMgr != null) {
                for (Status st : constMgr.getStatuses()) {
                    String category = (st.getStatusCategory() != null) ? st.getStatusCategory().getName() : null;
                    statusMap.put(st.getId(), new FilterOptions.StatusOption(st.getId(), st.getName(), category));
                }
            }

            List<FilterOptions.StatusOption> statusList = new ArrayList<>(statusMap.values());
            statusList.sort(Comparator.comparing(FilterOptions.StatusOption::getName, String.CASE_INSENSITIVE_ORDER));

            List<FilterOptions.IssueTypeOption> issueTypeList = new ArrayList<>(issueTypeMap.values());
            issueTypeList.sort(Comparator.comparing(FilterOptions.IssueTypeOption::getName, String.CASE_INSENSITIVE_ORDER));

            List<String> strategies = new ArrayList<>();
            for (GroupingStrategy s : GroupingStrategy.values()) {
                strategies.add(s.name());
            }

            FilterOptions options = new FilterOptions(projectOptions, statusList, issueTypeList, strategies);
            return Response.ok(options).build();
        } catch (Exception e) {
            log.error("Failed to load filter options: {}", e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"Failed to load filter options: " + escapeJson(e.getMessage()) + "\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }

    private void populateStatusesForProject(Project project, Map<String, FilterOptions.StatusOption> statusMap) {
        if (project == null) {
            return;
        }
        WorkflowSchemeManager wfSchemeMgr = this.workflowSchemeManager != null ? this.workflowSchemeManager : ComponentAccessor.getWorkflowSchemeManager();
        WorkflowManager wfMgr = this.workflowManager != null ? this.workflowManager : ComponentAccessor.getWorkflowManager();
        if (wfSchemeMgr == null || wfMgr == null) {
            return;
        }

        try {
            Map<String, String> wfMap = wfSchemeMgr.getWorkflowMap(project);
            Set<String> wfNames = new HashSet<>();
            if (wfMap != null) {
                for (String name : wfMap.values()) {
                    if (StringUtils.isNotBlank(name)) {
                        wfNames.add(name.trim());
                    }
                }
            }
            if (wfNames.isEmpty()) {
                JiraWorkflow defWf = wfMgr.getDefaultWorkflow();
                if (defWf != null && defWf.getLinkedStatusObjects() != null) {
                    for (Status st : defWf.getLinkedStatusObjects()) {
                        String category = (st.getStatusCategory() != null) ? st.getStatusCategory().getName() : null;
                        statusMap.putIfAbsent(st.getId(), new FilterOptions.StatusOption(st.getId(), st.getName(), category));
                    }
                }
                return;
            }

            for (String wfName : wfNames) {
                JiraWorkflow wf = wfMgr.getWorkflow(wfName);
                if (wf != null && wf.getLinkedStatusObjects() != null) {
                    for (Status st : wf.getLinkedStatusObjects()) {
                        String category = (st.getStatusCategory() != null) ? st.getStatusCategory().getName() : null;
                        statusMap.putIfAbsent(st.getId(), new FilterOptions.StatusOption(st.getId(), st.getName(), category));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not retrieve workflows for project {}: {}", project.getKey(), e.getMessage());
        }
    }

    /**
     * Previews matching attachments and returns metadata metrics without downloading.
     */
    @GET
    @Path("/preview")
    @Produces(MediaType.APPLICATION_JSON)
    public Response preview(
            @QueryParam("project") String projectKey,
            @QueryParam("status") String status,
            @QueryParam("issueType") String issueType,
            @QueryParam("jql") String jql,
            @QueryParam("extension") String extension,
            @QueryParam("minSize") Long minSize,
            @QueryParam("maxSize") Long maxSize,
            @QueryParam("maxIssues") @DefaultValue("200") int maxIssues,
            @QueryParam("grouping") @DefaultValue("BY_PROJECT_AND_ISSUE") String grouping
    ) {
        ApplicationUser user = authenticationContext.getLoggedInUser();
        if (user == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("{\"error\": \"Authentication required to preview attachments\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }

        try {
            AttachmentFilterCriteria criteria = buildCriteria(projectKey, status, issueType, jql,
                    extension, minSize, maxSize, maxIssues, grouping, true);
            AttachmentExportSummary summary = downloadService.preview(criteria, user);
            return Response.ok(summary).build();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid parameters in preview request: {}", e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"" + escapeJson(e.getMessage()) + "\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        } catch (Exception e) {
            log.error("Failed to generate attachments preview: {}", e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"An error occurred while generating preview: " + escapeJson(e.getMessage()) + "\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }

    /**
     * Generates and streams a ZIP archive containing all matching attachments.
     */
    @GET
    @Path("/zip")
    @Produces("application/zip")
    public Response downloadZip(
            @QueryParam("project") String projectKey,
            @QueryParam("status") String status,
            @QueryParam("issueType") String issueType,
            @QueryParam("jql") String jql,
            @QueryParam("extension") String extension,
            @QueryParam("minSize") Long minSize,
            @QueryParam("maxSize") Long maxSize,
            @QueryParam("maxIssues") @DefaultValue("200") int maxIssues,
            @QueryParam("grouping") @DefaultValue("BY_PROJECT_AND_ISSUE") String grouping,
            @QueryParam("includeManifest") @DefaultValue("true") boolean includeManifest
    ) {
        ApplicationUser user = authenticationContext.getLoggedInUser();
        if (user == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("{\"error\": \"Authentication required to download attachments\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }

        try {
            AttachmentFilterCriteria criteria = buildCriteria(projectKey, status, issueType, jql,
                    extension, minSize, maxSize, maxIssues, grouping, includeManifest);

            String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            String archiveFilename;
            if (projectKey != null && !projectKey.trim().isEmpty()) {
                String cleanKey = projectKey.replaceAll("[^a-zA-Z0-9_-]", "").toUpperCase();
                archiveFilename = "attachments-" + cleanKey + "-" + timestamp + ".zip";
            } else {
                archiveFilename = "attachments-export-" + timestamp + ".zip";
            }

            StreamingOutput stream = output -> downloadService.writeZipToStream(criteria, user, output);

            return Response.ok(stream)
                    .header("Content-Disposition", "attachment; filename=\"" + archiveFilename + "\"")
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .header("Pragma", "no-cache")
                    .header("Expires", "0")
                    .build();

        } catch (IllegalArgumentException e) {
            log.warn("Invalid parameters in zip download request: {}", e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"" + escapeJson(e.getMessage()) + "\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        } catch (Exception e) {
            log.error("Failed to initiate zip download: {}", e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"Failed to initiate zip download: " + escapeJson(e.getMessage()) + "\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }

    private AttachmentFilterCriteria buildCriteria(String projectKey, String status, String issueType,
                                                   String jql, String extension, Long minSize, Long maxSize,
                                                   int maxIssues, String grouping, boolean includeManifest) {
        AttachmentFilterCriteria.Builder builder = AttachmentFilterCriteria.builder()
                .projectKey(projectKey)
                .status(status)
                .issueType(issueType)
                .customJql(jql)
                .minSizeBytes(minSize)
                .maxSizeBytes(maxSize)
                .maxIssues(maxIssues)
                .groupingStrategy(GroupingStrategy.fromString(grouping))
                .includeManifest(includeManifest);

        if (extension != null && !extension.trim().isEmpty()) {
            String[] parts = extension.split(",");
            for (String part : parts) {
                builder.addExtension(part);
            }
        }

        return builder.build();
    }

    private String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", "");
    }
}
