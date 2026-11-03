package com.socgen.jira.plugins.clarification.servlet;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.permission.GlobalPermissionKey;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.security.GlobalPermissionManager;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.templaterenderer.TemplateRenderer;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Servlet qui sert la page HTML de configuration du projet
 * (template Velocity rendu côté serveur).
 *
 * URL : /plugins/servlet/clarification-config-page?projectKey=XXX
 */
@Named("clarificationConfigPageServlet")
public class ClarificationConfigPageServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(ClarificationConfigPageServlet.class);
    private final TemplateRenderer templateRenderer;

    @Inject
    public ClarificationConfigPageServlet(@ComponentImport TemplateRenderer templateRenderer) {
        this.templateRenderer = templateRenderer;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String projectKey = req.getParameter("projectKey");
        ApplicationUser user = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();

        if (!isProjectAdmin(user, projectKey)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Accès réservé aux administrateurs du projet.");
            return;
        }

        resp.setContentType("text/html;charset=UTF-8");
        Map<String, Object> context = new HashMap<String, Object>();
        Project project = ComponentAccessor.getProjectManager().getProjectObjByKey(projectKey.trim().toUpperCase());
        context.put("projectKey", project.getKey());
        context.put("projectId", project.getId());
        context.put("projectName", project.getName());
        templateRenderer.render("templates/project-config.vm", context, resp.getWriter());
    }

    private boolean isProjectAdmin(ApplicationUser user, String projectKey) {
        if (user == null || projectKey == null || projectKey.trim().isEmpty()) {
            return false;
        }
        Project project = ComponentAccessor.getProjectManager().getProjectObjByKey(projectKey.trim().toUpperCase());
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
