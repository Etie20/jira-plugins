package com.mabanque.jira.plugins.clarification.rest;

import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mabanque.jira.plugins.clarification.api.ClarificationEntry;
import com.mabanque.jira.plugins.clarification.api.ClarificationException;
import com.mabanque.jira.plugins.clarification.api.ClarificationService;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;

/**
 * API REST du module de précisions.
 *
 * GET  /rest/clarification/1.0/issue/{key}          historique + droits de l'utilisateur
 * POST /rest/clarification/1.0/issue/{key}/request  {comment, recipient, attachmentId, attachmentName, validateOnly}
 * POST /rest/clarification/1.0/issue/{key}/reply    {comment, attachmentId, attachmentName, validateOnly}
 * POST /rest/clarification/1.0/issue/{key}/cancel
 *
 * En cas d'erreur : {"error": "...", "field": "comment|recipient|null"}
 */
@Path("/issue")
@Named
public class ClarificationRestResource {

    private static final String JSON_UTF8 = "application/json;charset=UTF-8";

    private final ClarificationService clarificationService;
    private final JiraAuthenticationContext authenticationContext;
    private final Gson gson = new Gson();

    @Inject
    public ClarificationRestResource(ClarificationService clarificationService,
                                     @ComponentImport JiraAuthenticationContext authenticationContext) {
        this.clarificationService = clarificationService;
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
    @Produces(JSON_UTF8)
    public Response cancelClarification(@PathParam("issueKey") String issueKey) {
        try {
            ClarificationEntry entry = clarificationService.cancelClarification(issueKey,
                    authenticationContext.getLoggedInUser());
            return ok(gson.toJsonTree(entry));
        } catch (ClarificationException e) {
            return error(e);
        }
    }

    // ------------------------------------------------------------------

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
