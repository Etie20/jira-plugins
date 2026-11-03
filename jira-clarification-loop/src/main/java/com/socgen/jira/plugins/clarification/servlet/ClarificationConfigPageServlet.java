package com.socgen.jira.plugins.clarification.servlet;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.permission.GlobalPermissionKey;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.security.GlobalPermissionManager;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.user.ApplicationUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * Servlet qui sert la page HTML de configuration du projet
 * dans « Paramètres du projet -> Demande infos sup ».
 *
 * URL : /plugins/servlet/clarification-config-page?projectKey=XXX
 */
@Named("clarificationConfigPageServlet")
public class ClarificationConfigPageServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(ClarificationConfigPageServlet.class);
    private static final String REQUEST_CACHE_PROJECT_KEY =
            "com.atlassian.jira.projectconfig.util.ServletRequestProjectConfigRequestCache:project";

    public ClarificationConfigPageServlet() {
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String projectKey = req.getParameter("projectKey");
        ApplicationUser user = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();
        Project project = getProject(projectKey);

        if (project == null || !isProjectAdmin(user, project)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Accès réservé aux administrateurs du projet.");
            return;
        }

        // Attributs requis par le décorateur SiteMesh "admin" (HeaderContextProvider / ServletRequestProjectConfigRequestCache)
        req.setAttribute(REQUEST_CACHE_PROJECT_KEY, project);
        req.setAttribute("project", project);
        req.setAttribute("projectKey", project.getKey());

        try {
            ComponentAccessor.getWebResourceManager().requireResourcesForContext("jira.admin.conf");
            ComponentAccessor.getWebResourceManager().requireResource("com.atlassian.jira.jira-project-config-plugin:project-config-global");
        } catch (Exception e) {
            log.debug("Ressources project-config non chargées", e);
        }

        resp.setContentType("text/html;charset=UTF-8");
        String template = loadTemplate();
        String html = template
                .replace("$projectKey", escapeHtml(project.getKey()))
                .replace("$projectId", String.valueOf(project.getId()))
                .replace("$projectName", escapeHtml(project.getName()));
        resp.getWriter().write(html);
    }

    private String loadTemplate() throws IOException {
        InputStream in = getClass().getClassLoader().getResourceAsStream("templates/project-config.vm");
        if (in == null) {
            throw new IOException("Template templates/project-config.vm introuvable.");
        }
        try (Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
            return scanner.hasNext() ? scanner.next() : "";
        }
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

    private boolean isProjectAdmin(ApplicationUser user, Project project) {
        if (user == null || project == null) {
            return false;
        }
        GlobalPermissionManager gpm = ComponentAccessor.getGlobalPermissionManager();
        if (gpm.hasPermission(GlobalPermissionKey.ADMINISTER, user)) {
            return true;
        }
        PermissionManager pm = ComponentAccessor.getPermissionManager();
        return pm.hasPermission(ProjectPermissions.ADMINISTER_PROJECTS, project, user);
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
