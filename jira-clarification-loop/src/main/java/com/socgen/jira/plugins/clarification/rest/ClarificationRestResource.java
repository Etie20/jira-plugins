package com.socgen.jira.plugins.clarification.rest;

import com.atlassian.jira.avatar.Avatar;
import com.atlassian.jira.avatar.AvatarService;
import com.atlassian.jira.bc.user.search.UserSearchIssueContext;
import com.atlassian.jira.bc.user.search.UserSearchParams;
import com.atlassian.jira.bc.user.search.UserSearchService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.comments.Comment;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.issue.status.Status;
import com.atlassian.jira.issue.status.category.StatusCategory;
import com.atlassian.jira.permission.GlobalPermissionKey;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.scheme.Scheme;
import com.atlassian.jira.security.GlobalPermissionManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.security.roles.ProjectRole;
import com.atlassian.jira.security.roles.ProjectRoleActors;
import com.atlassian.jira.security.roles.ProjectRoleManager;
import com.atlassian.jira.security.roles.RoleActor;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
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
import com.socgen.jira.plugins.clarification.api.ClarificationHistory;
import com.socgen.jira.plugins.clarification.api.ClarificationService;
import com.socgen.jira.plugins.clarification.api.ProjectConfig;
import com.socgen.jira.plugins.clarification.impl.ClarificationServiceImpl;
import com.socgen.jira.plugins.clarification.impl.ProjectConfigService;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.net.URI;
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
 * GET  /rest/clarification/1.0/issue/{key}/users?q=...  recherche d'utilisateurs filtrée par sécurité du ticket et intervenants
 * POST /rest/clarification/1.0/issue/{key}/request      {comment, recipient, attachmentId, attachmentName, validateOnly}
 * POST /rest/clarification/1.0/issue/{key}/reply        {comment, attachmentId, attachmentName, validateOnly}
 * POST /rest/clarification/1.0/issue/{key}/cancel
 * GET  /rest/clarification/1.0/issue/config/{projectKey} configuration du projet + types de tickets + états
 * POST /rest/clarification/1.0/issue/config/{projectKey} sauvegarde de la configuration du projet
 *
 * En cas d'erreur : {"error": "...", "field": "comment|recipient|null"}
 */
@Path("/issue")
@Named
public class ClarificationRestResource {

    private static final String JSON_UTF8 = "application/json;charset=UTF-8";
    private static final int MAX_USER_RESULTS = 15;

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

    /**
     * Recherche d'utilisateurs contextuelle au ticket et au projet :
     * 1. Vérifie systématiquement la sécurité du ticket (permission BROWSE_PROJECTS + Security Level du ticket).
     * 2. Propose en priorité les personnes qui interviennent sur le ticket (Rapporteur, Créateur,
     *    Participants aux demandes d'infos sup, Commentateurs, Observateurs).
     * 3. Propose ensuite les membres des rôles du projet et les utilisateurs assignables sur le ticket.
     * 4. Si une saisie est effectuée, élargit aux utilisateurs ayant accès au ticket.
     */
    @GET
    @Path("/{issueKey}/users")
    @Produces(JSON_UTF8)
    public Response searchIssueUsers(@PathParam("issueKey") String issueKey,
                                     @QueryParam("q") String query) {
        ApplicationUser currentUser = authenticationContext.getLoggedInUser();
        if (currentUser == null) {
            return error(ClarificationException.forbidden("Vous devez être connecté."));
        }
        if (issueKey == null || issueKey.trim().isEmpty()) {
            return error(new ClarificationException(ClarificationException.BAD_REQUEST, "Clé de ticket manquante."));
        }

        MutableIssue issue = ComponentAccessor.getIssueManager().getIssueObject(issueKey.trim().toUpperCase());
        if (issue == null) {
            return error(new ClarificationException(ClarificationException.NOT_FOUND, "Ticket introuvable."));
        }

        PermissionManager pm = ComponentAccessor.getPermissionManager();
        if (!pm.hasPermission(ProjectPermissions.BROWSE_PROJECTS, issue, currentUser)) {
            return error(ClarificationException.forbidden("Accès refusé à ce ticket."));
        }

        String q = query == null ? "" : query.trim().toLowerCase();
        Map<String, JsonObject> candidates = new LinkedHashMap<String, JsonObject>();
        UserManager um = ComponentAccessor.getUserManager();
        AvatarService avatarService = ComponentAccessor.getAvatarService();

        // 1. Intervenants directs du ticket
        addCandidate(candidates, issue.getReporter(), "Rapporteur", issue, currentUser, pm, avatarService, q);
        addCandidate(candidates, issue.getCreator(), "Créateur", issue, currentUser, pm, avatarService, q);

        try {
            ClarificationHistory history = clarificationService.getHistory(issue.getKey());
            if (history != null && history.getEntries() != null) {
                for (ClarificationEntry entry : history.getEntries()) {
                    if (entry.getRequestedByUsername() != null) {
                        addCandidate(candidates, um.getUserByName(entry.getRequestedByUsername()), "Intervenant", issue, currentUser, pm, avatarService, q);
                    }
                    if (entry.getRecipientUsername() != null) {
                        addCandidate(candidates, um.getUserByName(entry.getRecipientUsername()), "Intervenant", issue, currentUser, pm, avatarService, q);
                    }
                    if (entry.getAnsweredByUsername() != null) {
                        addCandidate(candidates, um.getUserByName(entry.getAnsweredByUsername()), "Intervenant", issue, currentUser, pm, avatarService, q);
                    }
                }
            }
        } catch (Exception ignored) {
        }

        try {
            List<Comment> comments = ComponentAccessor.getCommentManager().getCommentsForUser(issue, currentUser);
            if (comments != null) {
                for (Comment c : comments) {
                    if (c != null && c.getAuthorApplicationUser() != null) {
                        addCandidate(candidates, c.getAuthorApplicationUser(), "Commentateur", issue, currentUser, pm, avatarService, q);
                    }
                }
            }
        } catch (Exception ignored) {
        }

        try {
            Collection<ApplicationUser> watchers = ComponentAccessor.getWatcherManager().getWatchersUnsorted(issue);
            if (watchers != null) {
                for (ApplicationUser w : watchers) {
                    addCandidate(candidates, w, "Observateur", issue, currentUser, pm, avatarService, q);
                }
            }
        } catch (Exception ignored) {
        }

        // 2. Membres des rôles du projet (acteurs directs et groupes de taille raisonnable)
        Project project = issue.getProjectObject();
        if (project != null && candidates.size() < MAX_USER_RESULTS) {
            try {
                ProjectRoleManager prm = ComponentAccessor.getComponent(ProjectRoleManager.class);
                if (prm != null) {
                    Collection<ProjectRole> roles = prm.getProjectRoles();
                    if (roles != null) {
                        for (ProjectRole role : roles) {
                            if (candidates.size() >= MAX_USER_RESULTS) {
                                break;
                            }
                            ProjectRoleActors actors = prm.getProjectRoleActors(role, project);
                            if (actors != null && actors.getRoleActors() != null) {
                                for (RoleActor actor : actors.getRoleActors()) {
                                    if (actor == null) {
                                        continue;
                                    }
                                    Set<ApplicationUser> actorUsers = actor.getUsers();
                                    if (actorUsers != null && (actorUsers.size() <= 80 || !q.isEmpty())) {
                                        for (ApplicationUser u : actorUsers) {
                                            addCandidate(candidates, u, role.getName(), issue, currentUser, pm, avatarService, q);
                                            if (candidates.size() >= MAX_USER_RESULTS) {
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // 3. Utilisateurs assignables / mentionnables sur le ticket via UserSearchService
        UserSearchService uss = ComponentAccessor.getComponent(UserSearchService.class);
        if (uss != null && candidates.size() < MAX_USER_RESULTS) {
            try {
                UserSearchIssueContext ctx = UserSearchIssueContext.create(issue);
                List<ApplicationUser> assignable = uss.findTopAssignableUsers(q, ctx, MAX_USER_RESULTS);
                if (assignable != null) {
                    for (ApplicationUser u : assignable) {
                        addCandidate(candidates, u, "Projet", issue, currentUser, pm, avatarService, q);
                        if (candidates.size() >= MAX_USER_RESULTS) {
                            break;
                        }
                    }
                }
                if (candidates.size() < MAX_USER_RESULTS) {
                    List<ApplicationUser> mentionable = uss.findTopMentionableUsers(q, ctx, MAX_USER_RESULTS);
                    if (mentionable != null) {
                        for (ApplicationUser u : mentionable) {
                            addCandidate(candidates, u, "Projet", issue, currentUser, pm, avatarService, q);
                            if (candidates.size() >= MAX_USER_RESULTS) {
                                break;
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // 4. Si l'utilisateur tape une recherche, chercher parmi les autres utilisateurs ayant accès au ticket
        if (uss != null && !q.isEmpty() && candidates.size() < MAX_USER_RESULTS) {
            try {
                UserSearchParams params = UserSearchParams.builder(30)
                        .allowEmptyQuery(false)
                        .includeActive(true)
                        .includeInactive(false)
                        .canMatchEmail(true)
                        .build();
                List<ApplicationUser> matched = uss.findUsers(q, params);
                if (matched != null) {
                    for (ApplicationUser u : matched) {
                        addCandidate(candidates, u, "Accès au ticket", issue, currentUser, pm, avatarService, q);
                        if (candidates.size() >= MAX_USER_RESULTS) {
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        JsonArray usersArray = new JsonArray();
        for (JsonObject uObj : candidates.values()) {
            usersArray.add(uObj);
        }
        JsonObject result = new JsonObject();
        result.add("users", usersArray);
        return ok(result);
    }

    private void addCandidate(Map<String, JsonObject> candidates,
                              ApplicationUser candidate,
                              String roleLabel,
                              MutableIssue issue,
                              ApplicationUser currentUser,
                              PermissionManager pm,
                              AvatarService avatarService,
                              String queryLower) {
        if (candidates.size() >= MAX_USER_RESULTS || candidate == null || !candidate.isActive()) {
            return;
        }
        if (currentUser != null && currentUser.getKey().equals(candidate.getKey())) {
            return;
        }
        if (candidates.containsKey(candidate.getKey())) {
            return;
        }
        if (!matchesUserQuery(candidate, queryLower)) {
            return;
        }
        if (!pm.hasPermission(ProjectPermissions.BROWSE_PROJECTS, issue, candidate)) {
            return;
        }

        JsonObject obj = new JsonObject();
        obj.addProperty("name", candidate.getName());
        obj.addProperty("displayName", candidate.getDisplayName() != null ? candidate.getDisplayName() : candidate.getName());
        obj.addProperty("role", roleLabel != null ? roleLabel : "");
        try {
            if (avatarService != null) {
                URI uri = avatarService.getAvatarURL(currentUser, candidate, Avatar.Size.SMALL);
                if (uri != null) {
                    obj.addProperty("avatarUrl", uri.toString());
                }
            }
        } catch (Exception ignored) {
        }
        candidates.put(candidate.getKey(), obj);
    }

    private boolean matchesUserQuery(ApplicationUser user, String q) {
        if (q == null || q.isEmpty()) {
            return true;
        }
        String display = user.getDisplayName() != null ? user.getDisplayName().toLowerCase() : "";
        String name = user.getName() != null ? user.getName().toLowerCase() : "";
        String email = user.getEmailAddress() != null ? user.getEmailAddress().toLowerCase() : "";
        return display.contains(q) || name.contains(q) || email.contains(q);
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
        result.add("statuses", extractProjectStatuses(project));
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
            if (config.isPauseSla()) {
                ClarificationServiceImpl.ensureSlaCustomField();
            }
            JsonObject res = new JsonObject();
            res.addProperty("success", true);
            return ok(res);
        } catch (Exception e) {
            return error(new ClarificationException(ClarificationException.BAD_REQUEST, "Données invalides."));
        }
    }

    /**
     * Collecte tous les workflows associés au projet (par type de ticket,
     * par schéma de workflow et par workflow par défaut).
     */
    public static Map<String, JiraWorkflow> collectProjectWorkflows(Project project) {
        WorkflowManager wfm = ComponentAccessor.getWorkflowManager();
        WorkflowSchemeManager wsm = ComponentAccessor.getWorkflowSchemeManager();
        Map<String, JiraWorkflow> workflowsByName = new LinkedHashMap<String, JiraWorkflow>();

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

        if (workflowsByName.isEmpty()) {
            try {
                JiraWorkflow def = wfm.getDefaultWorkflow();
                if (def != null && def.getName() != null) {
                    workflowsByName.put(def.getName(), def);
                }
            } catch (Exception ignored) {
            }
        }

        return workflowsByName;
    }

    /**
     * Collecte 100 % des États (Statuts / Étapes) de tous les workflows associés au projet,
     * en identifiant automatiquement les états finaux (catégorie Jira "done" ou étape terminale).
     */
    public static JsonArray extractProjectStatuses(Project project) {
        Map<String, JiraWorkflow> workflowsByName = collectProjectWorkflows(project);

        Map<String, String> idByName = new LinkedHashMap<String, String>();
        Map<String, Boolean> finalByName = new LinkedHashMap<String, Boolean>();
        Map<String, String> categoryByName = new LinkedHashMap<String, String>();
        Map<String, Set<String>> workflowsByStatusName = new LinkedHashMap<String, Set<String>>();

        for (JiraWorkflow wf : workflowsByName.values()) {
            String wfLabel = wf.getDisplayName() != null ? wf.getDisplayName() : wf.getName();
            WorkflowDescriptor wd = wf.getDescriptor();
            boolean hasGlobalActions = wd != null && wd.getGlobalActions() != null && !wd.getGlobalActions().isEmpty();

            if (wd != null && wd.getSteps() != null) {
                for (Object sObj : wd.getSteps()) {
                    if (!(sObj instanceof StepDescriptor)) {
                        continue;
                    }
                    StepDescriptor step = (StepDescriptor) sObj;
                    Status status = null;
                    try {
                        status = wf.getLinkedStatusObject(step);
                    } catch (Exception ignored) {
                    }

                    String displayName = null;
                    String statusId = String.valueOf(step.getId());
                    boolean isDoneCategory = false;
                    String categoryKey = "indeterminate";

                    if (status != null) {
                        statusId = status.getId();
                        displayName = status.getNameTranslation() != null && !status.getNameTranslation().trim().isEmpty()
                                ? status.getNameTranslation().trim()
                                : status.getName();
                        StatusCategory cat = status.getStatusCategory();
                        if (cat != null && cat.getKey() != null) {
                            categoryKey = cat.getKey();
                            isDoneCategory = "done".equalsIgnoreCase(cat.getKey());
                        }
                    }
                    if (displayName == null || displayName.trim().isEmpty()) {
                        displayName = step.getName();
                    }
                    if (displayName == null || displayName.trim().isEmpty()) {
                        continue;
                    }
                    displayName = displayName.trim();

                    boolean hasStepActions = step.getActions() != null && !step.getActions().isEmpty();
                    boolean isTerminalStep = !hasStepActions && !hasGlobalActions;
                    boolean isFinal = isDoneCategory || isTerminalStep;

                    if (!idByName.containsKey(displayName)) {
                        idByName.put(displayName, statusId);
                        finalByName.put(displayName, Boolean.valueOf(isFinal));
                        categoryByName.put(displayName, categoryKey);
                        workflowsByStatusName.put(displayName, new LinkedHashSet<String>());
                    } else if (isFinal) {
                        finalByName.put(displayName, Boolean.TRUE);
                    }

                    if (wfLabel != null && !wfLabel.trim().isEmpty()) {
                        workflowsByStatusName.get(displayName).add(wfLabel.trim());
                    }
                }
            }
        }

        boolean multipleWorkflows = workflowsByName.size() > 1;
        JsonArray nonFinalArr = new JsonArray();
        JsonArray finalArr = new JsonArray();

        for (String name : idByName.keySet()) {
            boolean isFinal = Boolean.TRUE.equals(finalByName.get(name));
            JsonObject obj = new JsonObject();
            obj.addProperty("id", idByName.get(name));
            obj.addProperty("name", name);
            obj.addProperty("isFinal", isFinal);
            obj.addProperty("category", categoryByName.get(name));
            Set<String> wfs = workflowsByStatusName.get(name);
            if (multipleWorkflows && wfs != null && !wfs.isEmpty()) {
                obj.addProperty("workflows", String.join(", ", wfs));
            }
            if (isFinal) {
                finalArr.add(obj);
            } else {
                nonFinalArr.add(obj);
            }
        }

        // Afficher d'abord les états actifs/intermédiaires, puis les états finaux à la fin
        JsonArray statuses = new JsonArray();
        for (JsonElement el : nonFinalArr) {
            statuses.add(el);
        }
        for (JsonElement el : finalArr) {
            statuses.add(el);
        }
        return statuses;
    }

    /**
     * Collecte 100 % des transitions de tous les workflows associés au projet.
     */
    public static JsonArray extractProjectTransitions(Project project) {
        JsonArray transitions = new JsonArray();
        Map<String, JiraWorkflow> workflowsByName = collectProjectWorkflows(project);

        Map<String, Set<String>> transitionToWorkflows = new LinkedHashMap<String, Set<String>>();
        Map<String, Integer> transitionToFirstId = new LinkedHashMap<String, Integer>();

        for (JiraWorkflow wf : workflowsByName.values()) {
            String wfLabel = wf.getDisplayName() != null ? wf.getDisplayName() : wf.getName();
            List<ActionDescriptor> actions = collectAllWorkflowActions(wf);
            for (ActionDescriptor action : actions) {
                if (action == null) {
                    continue;
                }
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
