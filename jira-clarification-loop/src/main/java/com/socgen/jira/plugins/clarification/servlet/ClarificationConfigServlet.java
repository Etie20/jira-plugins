package com.socgen.jira.plugins.clarification.servlet;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.security.GlobalPermissionManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.permission.GlobalPermissionKey;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.workflow.JiraWorkflow;
import com.atlassian.jira.workflow.WorkflowManager;
import com.atlassian.jira.workflow.WorkflowSchemeManager;
import com.atlassian.jira.scheme.Scheme;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.socgen.jira.plugins.clarification.api.ProjectConfig;
import com.socgen.jira.plugins.clarification.impl.ProjectConfigService;
import com.opensymphony.workflow.loader.ActionDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

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
    private final ProjectConfigService projectConfigService;
    private final Gson gson = new Gson();

    @Inject
    public ClarificationConfigServlet(ProjectConfigService projectConfigService) {
        this.projectConfigService = projectConfigService;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
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

        ProjectConfig config = projectConfigService.getConfig(projectKey);

        JsonObject result = new JsonObject();
        result.add("config", gson.toJsonTree(config));

        // Liste des types de tickets du projet
        JsonArray issueTypes = new JsonArray();
        Collection<IssueType> projectIssueTypes = project.getIssueTypes();
        if (projectIssueTypes != null) {
            for (IssueType it : projectIssueTypes) {
                JsonObject obj = new JsonObject();
                obj.addProperty("id", it.getId());
                obj.addProperty("name", it.getName());
                issueTypes.add(obj);
            }
        }
        result.add("issueTypes", issueTypes);

        // Liste des transitions disponibles dans les workflows du projet
        JsonArray transitions = new JsonArray();
        Set<String> seen = new HashSet<String>();
        WorkflowManager wfm = ComponentAccessor.getWorkflowManager();
        try {
            WorkflowSchemeManager wsm = ComponentAccessor.getWorkflowSchemeManager();
            Scheme scheme = wsm.getSchemeObject(project.getId());
            Iterable<JiraWorkflow> workflows = null;
            if (scheme != null) {
                workflows = wfm.getWorkflowsFromScheme(scheme);
            }
            if (workflows != null) {
                for (JiraWorkflow wf : workflows) {
                    Collection<ActionDescriptor> actions = wf.getAllActions();
                    if (actions != null) {
                        for (ActionDescriptor action : actions) {
                            String name = action.getName();
                            if (name != null && !name.trim().isEmpty() && seen.add(name)) {
                                JsonObject obj = new JsonObject();
                                obj.addProperty("id", action.getId());
                                obj.addProperty("name", name);
                                transitions.add(obj);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Impossible de charger les transitions du projet " + projectKey, e);
        }
        result.add("transitions", transitions);

        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write(gson.toJson(result));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
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
            projectConfigService.saveConfig(projectKey, config);

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
        return ComponentAccessor.getProjectManager().getProjectObjByKey(projectKey.trim().toUpperCase());
    }

    private boolean isProjectAdmin(ApplicationUser user, String projectKey) {
        if (user == null || projectKey == null) {
            return false;
        }
        Project project = getProject(projectKey);
        if (project == null) {
            return false;
        }
        // Vérifier si l'utilisateur est admin Jira global
        GlobalPermissionManager gpm = ComponentAccessor.getGlobalPermissionManager();
        if (gpm.hasPermission(GlobalPermissionKey.ADMINISTER, user)) {
            return true;
        }
        // Ou admin du projet
        PermissionManager pm = ComponentAccessor.getPermissionManager();
        return pm.hasPermission(ProjectPermissions.ADMINISTER_PROJECTS, project, user);
    }
}
