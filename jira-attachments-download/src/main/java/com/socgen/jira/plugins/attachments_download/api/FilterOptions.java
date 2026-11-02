package com.socgen.jira.plugins.attachments_download.api;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Available filter options (accessible projects, statuses, issue types and grouping modes in Jira).
 */
@XmlRootElement(name = "filterOptions")
@XmlAccessorType(XmlAccessType.FIELD)
public class FilterOptions implements Serializable {

    private static final long serialVersionUID = 1L;

    @XmlElement(name = "projects")
    private List<ProjectOption> projects = new ArrayList<>();

    @XmlElement(name = "statuses")
    private List<StatusOption> statuses = new ArrayList<>();

    @XmlElement(name = "issueTypes")
    private List<IssueTypeOption> issueTypes = new ArrayList<>();

    @XmlElement(name = "groupingStrategies")
    private List<String> groupingStrategies = new ArrayList<>();

    public FilterOptions() {
    }

    public FilterOptions(List<ProjectOption> projects,
                         List<StatusOption> statuses,
                         List<IssueTypeOption> issueTypes,
                         List<String> groupingStrategies) {
        this.projects = projects != null ? projects : new ArrayList<>();
        this.statuses = statuses != null ? statuses : new ArrayList<>();
        this.issueTypes = issueTypes != null ? issueTypes : new ArrayList<>();
        this.groupingStrategies = groupingStrategies != null ? groupingStrategies : new ArrayList<>();
    }

    public List<ProjectOption> getProjects() {
        return projects;
    }

    public void setProjects(List<ProjectOption> projects) {
        this.projects = projects;
    }

    public List<StatusOption> getStatuses() {
        return statuses;
    }

    public void setStatuses(List<StatusOption> statuses) {
        this.statuses = statuses;
    }

    public List<IssueTypeOption> getIssueTypes() {
        return issueTypes;
    }

    public void setIssueTypes(List<IssueTypeOption> issueTypes) {
        this.issueTypes = issueTypes;
    }

    public List<String> getGroupingStrategies() {
        return groupingStrategies;
    }

    public void setGroupingStrategies(List<String> groupingStrategies) {
        this.groupingStrategies = groupingStrategies;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ProjectOption implements Serializable {
        private static final long serialVersionUID = 1L;

        @XmlElement
        private String id;

        @XmlElement
        private String key;

        @XmlElement
        private String name;

        public ProjectOption() {
        }

        public ProjectOption(String id, String key, String name) {
            this.id = id;
            this.key = key;
            this.name = name;
        }

        public String getId() {
            return id;
        }

        public String getKey() {
            return key;
        }

        public String getName() {
            return name;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class StatusOption implements Serializable {
        private static final long serialVersionUID = 1L;

        @XmlElement
        private String id;

        @XmlElement
        private String name;

        @XmlElement
        private String category;

        public StatusOption() {
        }

        public StatusOption(String id, String name, String category) {
            this.id = id;
            this.name = name;
            this.category = category;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getCategory() {
            return category;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class IssueTypeOption implements Serializable {
        private static final long serialVersionUID = 1L;

        @XmlElement
        private String id;

        @XmlElement
        private String name;

        @XmlElement
        private boolean subTask;

        public IssueTypeOption() {
        }

        public IssueTypeOption(String id, String name, boolean subTask) {
            this.id = id;
            this.name = name;
            this.subTask = subTask;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public boolean isSubTask() {
            return subTask;
        }
    }
}
