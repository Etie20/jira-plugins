package com.socgen.jira.plugins.clarification.rest;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.permission.GlobalPermissionKey;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.scheme.Scheme;
import com.atlassian.jira.security.GlobalPermissionManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.workflow.AssignableWorkflowScheme;
import com.atlassian.jira.workflow.JiraWorkflow;
import com.atlassian.jira.workflow.WorkflowManager;
import com.atlassian.jira.workflow.WorkflowSchemeManager;
import com.atlassian.jira.workflow.WorkflowUtil;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.opensymphony.workflow.loader.ActionDescriptor;
import com.opensymphony.workflow.loader.StepDescriptor;
import com.opensymphony.workflow.loader.WorkflowDescriptor;
import com.socgen.jira.plugins.clarification.api.ClarificationEntry;
import com.socgen.jira.plugins.clarification.api.ClarificationException;
import com.socgen.jira.plugins.clarification.api.ClarificationService;
import com.socgen.jira.plugins.clarification.api.ProjectConfig;
import com.socgen.jira.plugins.clarification.impl.ProjectConfigService;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * API REST du module de demande d'informations supplémentaires.
 *
 * GET  /rest/clarification/1.0/issue/{key}              historique + droits de l'utilisateur
 * POST /rest/clarification/1.0/issue/{key}/request      {comment, recipient, attachmentId, attachmentName, validateOnly}
 * POST /rest/clarification/1.0/issue/{key}/reply        {comment, attachmentId, attachmentName, validateOnly}
 * POST /rest/clarification/1.0/issue/{key}/cancel
 * GET  /rest/clarification/1.0/issue/config/{projectKey} configuration du projet + types de tickets + transitions
 * POST /rest/clarification/1.0/issue/config/{projectKey} sauvegarde de la configuration du projet
 *
 * En cas d'erreur : {"error": "...", "field": "comment|recipient|null"}
 */
@Path("/issue")
@Named
public class ClarificationRestResource {

    private static final String JSON_UTF8 = "application/json;charset=UTF-8";

    private final ClarificationService clarificationService;
    private final ProjectConfigService projectConfigService;
    private final JiraAuthenticationContext authenticationContext;
    private final Gson gson = new Gson();

    @Inject
    public ClarificationRestResource(ClarificationService clarificationService,
                                     ProjectConfigService projectConfigService,
                                     @ComponentImport JiraAuthenticationContext authenticationContext) {
        this.clarificationService = clarificationService;
        this.projectConfigService = projectConfigService;
        this.authenticationContext = authenticationContext;
    }

    @GET
    @Path("/{issueKey}")
    @Produces(JSON_UTF8)
    public Response getHistory(@PathParam("issueKey") String issueKey) {
        try {
            ApplicationUser user = authenticationContext.getLoggedInUser();
            JsonObject result = gson.toJsonTree(clarificationService.getHistory(issueKey)).getAsJsonObject();
            result.add("permissions", gson.toJsonTree(clarificationService.getPermissions(issueKey, user)));
            return ok(result);
        } catch (ClarificationException e) {
            return error(e);
        }
    }

    @POST
    @Path("/{issueKey}/request")
    @Consumes("application/json")
    @Produces(JSON_UTF8)
    public Response requestClarification(@PathParam("issueKey") String issueKey, String body) {
        try {
            JsonObject json = parse(body);
            ApplicationUser user = authenticationContext.getLoggedInUser();
            String comment = string(json, "comment");
            String recipient = string(json, "recipient");

            if (bool(json, "validateOnly")) {
                clarificationService.validateRequest(issueKey, comment, recipient, user);
                return ok(new JsonObject());
            }

            ClarificationEntry entry = clarificationService.requestClarification(issueKey, comment, recipient,
                    number(json, "attachmentId"), string(json, "attachmentName"), user);
            return ok(gson.toJsonTree(entry));
        } catch (ClarificationException e) {
            return error(e);
        }
    }

    @POST
    @Path("/{issueKey}/reply")
    @Consumes("application/json")
    @Produces(JSON_UTF8)
    public Response answerClarification(@PathParam("issueKey") String issueKey, String body) {
        try {
            JsonObject json = parse(body);
            ApplicationUser user = authenticationContext.getLoggedInUser();
            String comment = string(json, "comment");

            if (bool(json, "validateOnly")) {
                clarificationService.validateAnswer(issueKey, comment, user);
                return ok(new JsonObject());
            }

            ClarificationEntry entry = clarificationService.answerClarification(issueKey, comment,
                    number(json, "attachmentId"), string(json, "attachmentName"), user);
            return ok(gson.toJsonTree(entry));
        } catch (ClarificationException e) {
            return error(e);
        }
    }

    @POST
    @Path("/{issueKey}/cancel")
    @Consumes("application/json")
    @Produces(JSON_UTF8)
    public Response cancelClarification(@PathParam("issueKey") String issueKey, String body) {
        try {
            ClarificationEntry entry = clarificationService.cancelClarification(issueKey,
                    authenticationContext.getLoggedInUser());
            return ok(gson.toJsonTree(entry));
        } catch (ClarificationException e) {
            return error(e);
        }
    }

    @GET
    @Path("/config/{projectKey}")
    @Produces(JSON_UTF8)
    public Response getProjectConfig(@PathParam("projectKey") String projectKey) {
        ApplicationUser user = authenticationContext.getLoggedInUser();
        Project project = getProject(projectKey);
        if (project == null) {
            return error(new ClarificationException(ClarificationException.NOT_FOUND, "Projet introuvable."));
        }
        if (!isProjectAdmin(user, project)) {
            return error(new ClarificationException(ClarificationException.FORBIDDEN, "Accès réservé aux administrateurs du projet."));
        }

        ProjectConfig config = projectConfigService.getConfig(project.getKey());
        JsonObject result = new JsonObject();
        result.add("config", gson.toJsonTree(config));

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

        result.add("transitions", extractProjectTransitions(project));
        return ok(result);
    }

    @POST
    @Path("/config/{projectKey}")
    @Consumes("application/json")
    @Produces(JSON_UTF8)
    public Response saveProjectConfig(@PathParam("projectKey") String projectKey, String body) {
        ApplicationUser user = authenticationContext.getLoggedInUser();
        Project project = getProject(projectKey);
        if (project == null) {
            return error(new ClarificationException(ClarificationException.NOT_FOUND, "Projet introuvable."));
        }
        if (!isProjectAdmin(user, project)) {
            return error(new ClarificationException(ClarificationException.FORBIDDEN, "Accès réservé aux administrateurs du projet."));
        }

        try {
            ProjectConfig config = gson.fromJson(body == null || body.isEmpty() ? "{}" : body, ProjectConfig.class);
            projectConfigService.saveConfig(project.getKey(), config);
            JsonObject res = new JsonObject();
            res.addProperty("success", true);
            return ok(res);
        } catch (Exception e) {
            return error(new ClarificationException(ClarificationException.BAD_REQUEST, "Données invalides."));
        }
    }

    /**
     * Collecte 100 % des transitions de tous les workflows associés au projet
     * (par type de ticket, par schéma de workflow et par workflow par défaut).
     */
    public static JsonArray extractProjectTransitions(Project project) {
        JsonArray transitions = new JsonArray();
        WorkflowManager wfm = ComponentAccessor.getWorkflowManager();
        WorkflowSchemeManager wsm = ComponentAccessor.getWorkflowSchemeManager();

        Map<String, JiraWorkflow> workflowsByName = new LinkedHashMap<String, JiraWorkflow>();

        // 1. Par chaque type de ticket du projet (méthode la plus fiable)
        Collection<IssueType> projectIssueTypes = project.getIssueTypes();
        if (projectIssueTypes != null) {
            for (IssueType it : projectIssueTypes) {
                try {
                    JiraWorkflow wf = wfm.getWorkflow(project.getId(), it.getId());
                    if (wf != null && wf.getName() != null) {
                        workflowsByName.put(wf.getName(), wf);
                    }
                } catch (Exception ignored) {
                }
            }
        }

        // 2. Via la map du schéma de workflow du projet
        try {
            Map<String, String> wfMap = wsm.getWorkflowMap(project);
            if (wfMap != null) {
                for (String wfName : wfMap.values()) {
                    if (wfName != null && !workflowsByName.containsKey(wfName)) {
                        JiraWorkflow wf = wfm.getWorkflow(wfName);
                        if (wf != null) {
                            workflowsByName.put(wf.getName(), wf);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // 3. Via AssignableWorkflowScheme du projet
        try {
            AssignableWorkflowScheme aws = wsm.getWorkflowSchemeObj(project);
            if (aws != null) {
                String defWf = aws.getConfiguredDefaultWorkflow();
                if (defWf != null && !workflowsByName.containsKey(defWf)) {
                    JiraWorkflow wf = wfm.getWorkflow(defWf);
                    if (wf != null) {
                        workflowsByName.put(wf.getName(), wf);
                    }
                }
                Map<String, String> mappings = aws.getMappings();
                if (mappings != null) {
                    for (String wfName : mappings.values()) {
                        if (wfName != null && !workflowsByName.containsKey(wfName)) {
                            JiraWorkflow wf = wfm.getWorkflow(wfName);
                            if (wf != null) {
                                workflowsByName.put(wf.getName(), wf);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // 4. Via getSchemeFor(project)
        try {
            Scheme scheme = wsm.getSchemeFor(project);
            if (scheme != null) {
                Iterable<JiraWorkflow> schemeWfs = wfm.getWorkflowsFromScheme(scheme);
                if (schemeWfs != null) {
                    for (JiraWorkflow wf : schemeWfs) {
                        if (wf != null && wf.getName() != null) {
                            workflowsByName.put(wf.getName(), wf);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // 5. Fallback sur le workflow par défaut de Jira si vide
        if (workflowsByName.isEmpty()) {
            try {
                JiraWorkflow def = wfm.getDefaultWorkflow();
                if (def != null && def.getName() != null) {
                    workflowsByName.put(def.getName(), def);
                }
            } catch (Exception ignored) {
            }
        }

        // Collecte de toutes les transitions de chaque workflow (globales, communes et par étape)
        Map<String, Set<String>> transitionToWorkflows = new LinkedHashMap<String, Set<String>>();
        Map<String, Integer> transitionToFirstId = new LinkedHashMap<String, Integer>();

        for (JiraWorkflow wf : workflowsByName.values()) {
            String wfLabel = wf.getDisplayName() != null ? wf.getDisplayName() : wf.getName();
            List<ActionDescriptor> actions = collectAllWorkflowActions(wf);
            for (ActionDescriptor action : actions) {
                if (action == null) {
                    continue;
                }
                // Exclure uniquement l'action initiale de création du ticket (id=1 "Create")
                try {
                    if (wf.isInitialAction(action)) {
                        continue;
                    }
                } catch (Exception ignored) {
                    if (action.getId() == 1) {
                        continue;
                    }
                }

                String displayName = null;
                try {
                    displayName = WorkflowUtil.getWorkflowTransitionDisplayName(action);
                } catch (Exception ignored) {
                }
                if (displayName == null || displayName.trim().isEmpty()) {
                    displayName = action.getName();
                }
                if (displayName == null || displayName.trim().isEmpty()) {
                    continue;
                }
                displayName = displayName.trim();

                Set<String> wfs = transitionToWorkflows.get(displayName);
                if (wfs == null) {
                    wfs = new LinkedHashSet<String>();
                    transitionToWorkflows.put(displayName, wfs);
                    transitionToFirstId.put(displayName, action.getId());
                }
                if (wfLabel != null && !wfLabel.trim().isEmpty()) {
                    wfs.add(wfLabel.trim());
                }
            }
        }

        boolean multipleWorkflows = workflowsByName.size() > 1;
        for (Map.Entry<String, Set<String>> entry : transitionToWorkflows.entrySet()) {
            String name = entry.getKey();
            JsonObject obj = new JsonObject();
            obj.addProperty("id", transitionToFirstId.get(name));
            obj.addProperty("name", name);
            if (multipleWorkflows && !entry.getValue().isEmpty()) {
                obj.addProperty("workflows", String.join(", ", entry.getValue()));
            }
            transitions.add(obj);
        }

        return transitions;
    }

    private static List<ActionDescriptor> collectAllWorkflowActions(JiraWorkflow wf) {
        List<ActionDescriptor> list = new ArrayList<ActionDescriptor>();
        try {
            Collection<ActionDescriptor> all = wf.getAllActions();
            if (all != null) {
                list.addAll(all);
            }
        } catch (Exception ignored) {
        }
        try {
            WorkflowDescriptor wd = wf.getDescriptor();
            if (wd != null) {
                if (wd.getGlobalActions() != null) {
                    for (Object o : wd.getGlobalActions()) {
                        if (o instanceof ActionDescriptor) {
                            list.add((ActionDescriptor) o);
                        }
                    }
                }
                if (wd.getCommonActions() != null) {
                    for (Object o : wd.getCommonActions().values()) {
                        if (o instanceof ActionDescriptor) {
                            list.add((ActionDescriptor) o);
                        }
                    }
                }
                if (wd.getSteps() != null) {
                    for (Object s : wd.getSteps()) {
                        if (s instanceof StepDescriptor) {
                            StepDescriptor step = (StepDescriptor) s;
                            if (step.getActions() != null) {
                                for (Object a : step.getActions()) {
                                    if (a instanceof ActionDescriptor) {
                                        list.add((ActionDescriptor) a);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    // ------------------------------------------------------------------

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

    private JsonObject parse(String body) {
        try {
            JsonElement el = JsonParser.parseString(body == null || body.isEmpty() ? "{}" : body);
            if (el.isJsonObject()) {
                return el.getAsJsonObject();
            }
        } catch (Exception ignored) {
            // traité ci-dessous
        }
        throw new ClarificationException(ClarificationException.BAD_REQUEST, "Requête invalide.");
    }

    private static String string(JsonObject json, String name) {
        return json.has(name) && !json.get(name).isJsonNull() ? json.get(name).getAsString() : null;
    }

    private static boolean bool(JsonObject json, String name) {
        return json.has(name) && !json.get(name).isJsonNull() && json.get(name).getAsBoolean();
    }

    private static Long number(JsonObject json, String name) {
        try {
            return json.has(name) && !json.get(name).isJsonNull() ? json.get(name).getAsLong() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Response ok(JsonElement payload) {
        return Response.ok(gson.toJson(payload)).type(JSON_UTF8).build();
    }

    private Response error(ClarificationException e) {
        JsonObject json = new JsonObject();
        json.addProperty("error", e.getMessage());
        json.addProperty("field", e.getField());
        return Response.status(e.getStatus()).entity(gson.toJson(json)).type(JSON_UTF8).build();
    }
}
