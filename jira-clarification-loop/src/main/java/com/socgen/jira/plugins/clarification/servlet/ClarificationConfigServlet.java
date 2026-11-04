package com.socgen.jira.plugins.clarification.servlet;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.permission.GlobalPermissionKey;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.security.GlobalPermissionManager;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.user.ApplicationUser;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.socgen.jira.plugins.clarification.api.ProjectConfig;
import com.socgen.jira.plugins.clarification.impl.ClarificationServiceImpl;
import com.socgen.jira.plugins.clarification.impl.ProjectConfigService;
import com.socgen.jira.plugins.clarification.rest.ClarificationRestResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;

/**
 * Servlet REST interne pour la page de configuration « Demande infos sup »
 * dans les paramètres du projet.
 *
 * GET  /plugins/servlet/clarification-config?projectKey=XXX   → configuration + métadonnées
 * POST /plugins/servlet/clarification-config?projectKey=XXX   → sauvegarde
 */
@Named("clarificationConfigServlet")
public class ClarificationConfigServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(ClarificationConfigServlet.class);
    private ProjectConfigService projectConfigService;
    private final Gson gson = new Gson();

    public ClarificationConfigServlet() {
    }

    @Inject
    public ClarificationConfigServlet(ProjectConfigService projectConfigService) {
        this.projectConfigService = projectConfigService;
    }

    private ProjectConfigService getConfigService() {
        if (projectConfigService == null) {
            projectConfigService = ComponentAccessor.getOSGiComponentInstanceOfType(ProjectConfigService.class);
        }
        return projectConfigService;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String projectKey = req.getParameter("projectKey");
        ApplicationUser user = getUser();

        if (!isProjectAdmin(user, projectKey)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Accès réservé aux administrateurs du projet.");
            return;
        }

        Project project = getProject(projectKey);
        if (project == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Projet introuvable.");
            return;
        }

        ProjectConfig config = getConfigService().getConfig(project.getKey());

        JsonObject result = new JsonObject();
        result.add("config", gson.toJsonTree(config));

        CustomField slaField = ClarificationServiceImpl.ensureSlaCustomField();
        result.addProperty("slaFieldName", ClarificationServiceImpl.SLA_FIELD_NAME);
        if (slaField != null) {
            result.addProperty("slaFieldId", slaField.getId());
        }

        JsonArray issueTypes = new JsonArray();
        Collection<IssueType> projectIssueTypes = project.getIssueTypes();
        if (projectIssueTypes != null) {
            for (IssueType it : projectIssueTypes) {
                JsonObject obj = new JsonObject();
                obj.addProperty("id", it.getId());
                obj.addProperty("name", it.getNameTranslation() != null ? it.getNameTranslation() : it.getName());
                issueTypes.add(obj);
            }
        }
        result.add("issueTypes", issueTypes);
        result.add("transitions", ClarificationRestResource.extractProjectTransitions(project));

        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write(gson.toJson(result));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String projectKey = req.getParameter("projectKey");
        ApplicationUser user = getUser();

        if (!isProjectAdmin(user, projectKey)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Accès réservé aux administrateurs du projet.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = req.getReader().readLine()) != null) {
            sb.append(line);
        }

        try {
            ProjectConfig config = gson.fromJson(sb.toString(), ProjectConfig.class);
            getConfigService().saveConfig(projectKey, config);
            if (config.isPauseSla()) {
                ClarificationServiceImpl.ensureSlaCustomField();
            }

            resp.setContentType("application/json;charset=UTF-8");
            resp.getWriter().write("{\"success\":true}");
        } catch (Exception e) {
            log.error("Erreur lors de la sauvegarde de la configuration pour " + projectKey, e);
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Données invalides.");
        }
    }

    private ApplicationUser getUser() {
        return ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();
    }

    private Project getProject(String projectKey) {
        if (projectKey == null || projectKey.trim().isEmpty()) {
            return null;
        }
        Project p = ComponentAccessor.getProjectManager().getProjectObjByKey(projectKey.trim().toUpperCase());
        if (p == null) {
            p = ComponentAccessor.getProjectManager().getProjectObjByKeyIgnoreCase(projectKey.trim());
        }
        return p;
    }

    private boolean isProjectAdmin(ApplicationUser user, String projectKey) {
        if (user == null || projectKey == null) {
            return false;
        }
        Project project = getProject(projectKey);
        if (project == null) {
            return false;
        }
        GlobalPermissionManager gpm = ComponentAccessor.getGlobalPermissionManager();
        if (gpm.hasPermission(GlobalPermissionKey.ADMINISTER, user)) {
            return true;
        }
        PermissionManager pm = ComponentAccessor.getPermissionManager();
        return pm.hasPermission(ProjectPermissions.ADMINISTER_PROJECTS, project, user);
    }
}
