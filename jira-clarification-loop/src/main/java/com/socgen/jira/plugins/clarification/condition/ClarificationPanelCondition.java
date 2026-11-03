package com.socgen.jira.plugins.clarification.condition;

import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.plugin.webfragment.conditions.AbstractWebCondition;
import com.atlassian.jira.plugin.webfragment.model.JiraHelper;
import com.atlassian.jira.user.ApplicationUser;
import com.socgen.jira.plugins.clarification.impl.ProjectConfigService;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Condition qui masque le panneau « Demande infos sup » si le module
 * n'est pas activé pour le projet / type de ticket courant.
 */
@Named("clarificationPanelCondition")
public class ClarificationPanelCondition extends AbstractWebCondition {

    private final ProjectConfigService projectConfigService;

    @Inject
    public ClarificationPanelCondition(ProjectConfigService projectConfigService) {
        this.projectConfigService = projectConfigService;
    }

    @Override
    public boolean shouldDisplay(ApplicationUser user, JiraHelper jiraHelper) {
        Issue issue = (Issue) jiraHelper.getContextParams().get("issue");
        if (issue == null) {
            return false;
        }
        String projectKey = issue.getProjectObject() != null ? issue.getProjectObject().getKey() : null;
        String issueTypeId = issue.getIssueType() != null ? issue.getIssueType().getId() : null;
        if (projectKey == null) {
            return false;
        }
        return projectConfigService.isEnabledFor(projectKey, issueTypeId);
    }
}
