package com.mabanque.jira.plugins.compliance.rest;

import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mabanque.jira.plugins.compliance.api.ComplianceRecord;
import com.mabanque.jira.plugins.compliance.api.ComplianceService;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/issue")
@Named
public class ComplianceRestResource {

    private final ComplianceService complianceService;
    private final IssueManager issueManager;
    private final JiraAuthenticationContext authenticationContext;
    private final Gson gson;

    @Inject
    public ComplianceRestResource(ComplianceService complianceService,
                                  @ComponentImport IssueManager issueManager,
                                  @ComponentImport JiraAuthenticationContext authenticationContext) {
        this.complianceService = complianceService;
        this.issueManager = issueManager;
        this.authenticationContext = authenticationContext;
        this.gson = new Gson();
    }

    /**
     * GET /rest/compliance/1.0/issue/{issueKey}
     * Récupère le statut de conformité d'un ticket.
     */
    @GET
    @Path("/{issueKey}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCompliance(@PathParam("issueKey") String issueKey) {
        if (issueKey == null || issueKey.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"Clé de ticket manquante\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }

        Issue issue = issueManager.getIssueObject(issueKey.trim().toUpperCase());
        if (issue == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\":\"Ticket introuvable : " + issueKey + "\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }

        ComplianceRecord record = complianceService.getCompliance(issue.getKey());
        return Response.ok(gson.toJson(record))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    /**
     * POST /rest/compliance/1.0/issue/{issueKey}
     * Enregistre une décision d'audit / conformité.
     * Body attendu : {"status": "COMPLIANT" | "REJECTED" | "PENDING", "comment": "mon commentaire"}
     */
    @POST
    @Path("/{issueKey}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateCompliance(@PathParam("issueKey") String issueKey, String body) {
        if (issueKey == null || issueKey.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"Clé de ticket manquante\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }

        Issue issue = issueManager.getIssueObject(issueKey.trim().toUpperCase());
        if (issue == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\":\"Ticket introuvable : " + issueKey + "\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }

        String status = ComplianceRecord.STATUS_COMPLIANT;
        String comment = "";

        if (body != null && !body.trim().isEmpty()) {
            try {
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                if (json.has("status") && !json.get("status").isJsonNull()) {
                    status = json.get("status").getAsString();
                }
                if (json.has("comment") && !json.get("comment").isJsonNull()) {
                    comment = json.get("comment").getAsString();
                }
            } catch (Exception e) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"error\":\"Format JSON invalide\"}")
                        .type(MediaType.APPLICATION_JSON)
                        .build();
            }
        }

        ApplicationUser currentUser = authenticationContext.getLoggedInUser();
        String reviewer = (currentUser != null)
                ? currentUser.getDisplayName() + " (" + currentUser.getName() + ")"
                : "Utilisateur connecte";

        ComplianceRecord updated = complianceService.setCompliance(issue.getKey(), status, comment, reviewer);

        return Response.ok(gson.toJson(updated))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
