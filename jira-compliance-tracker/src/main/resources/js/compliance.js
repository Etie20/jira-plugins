(function ($) {
    "use strict";

    function initCompliance() {
        var $panel = $("#compliance-panel-root");
        if (!$panel.length) {
            return;
        }

        var issueKey = $panel.data("issue-key");
        if (!issueKey && typeof JIRA !== "undefined" && JIRA.Issue) {
            issueKey = JIRA.Issue.getIssueKey();
        }

        if (!issueKey) {
            return;
        }

        loadStatus(issueKey);
        bindEvents(issueKey);
    }

    function loadStatus(issueKey) {
        var url = AJS.contextPath() + "/rest/compliance/1.0/issue/" + encodeURIComponent(issueKey);

        $.ajax({
            url: url,
            type: "GET",
            dataType: "json",
            contentType: "application/json"
        }).done(function (data) {
            renderStatus(data);
        }).fail(function () {
            var $badge = $("#compliance-badge");
            $badge.attr("class", "aui-lozenge aui-lozenge-error").text("ERREUR REST");
        });
    }

    function renderStatus(record) {
        var $badge = $("#compliance-badge");
        var $details = $("#compliance-details");

        if (!record || record.status === "PENDING") {
            $badge.attr("class", "aui-lozenge aui-lozenge-subtle").text("EN ATTENTE DE VISA");
            $details.hide();
        } else if (record.status === "COMPLIANT") {
            $badge.attr("class", "aui-lozenge aui-lozenge-success").text("CONFORME (VISA ACCORDÉ)");
            $("#compliance-reviewer").text(record.reviewer || "-");
            $("#compliance-date").text(record.reviewedAt || "-");
            $("#compliance-comment").text(record.comment || "Aucun commentaire");
            $details.show();
        } else if (record.status === "REJECTED") {
            $badge.attr("class", "aui-lozenge aui-lozenge-error").text("NON CONFORME (REFUSÉ)");
            $("#compliance-reviewer").text(record.reviewer || "-");
            $("#compliance-date").text(record.reviewedAt || "-");
            $("#compliance-comment").text(record.comment || "Aucun motif précisé");
            $details.show();
        }
    }

    function postComplianceDecision(issueKey, status, comment) {
        var url = AJS.contextPath() + "/rest/compliance/1.0/issue/" + encodeURIComponent(issueKey);
        var payload = JSON.stringify({
            status: status,
            comment: comment
        });

        $("#compliance-badge").attr("class", "aui-lozenge aui-lozenge-subtle").text("ENREGISTREMENT...");

        $.ajax({
            url: url,
            type: "POST",
            data: payload,
            dataType: "json",
            contentType: "application/json"
        }).done(function (data) {
            renderStatus(data);

            if (typeof AJS.flag === "function") {
                if (status === "COMPLIANT") {
                    AJS.flag({
                        type: "success",
                        title: "Visa de conformité validé",
                        body: "Le visa d'audit a été enregistré pour le ticket " + issueKey,
                        close: "auto"
                    });
                } else {
                    AJS.flag({
                        type: "warning",
                        title: "Visa refusé",
                        body: "Le ticket " + issueKey + " a été marqué comme non conforme.",
                        close: "auto"
                    });
                }
            }
        }).fail(function (xhr) {
            alert("Erreur lors de l'enregistrement : " + xhr.responseText);
            loadStatus(issueKey);
        });
    }

    function bindEvents(issueKey) {
        // Évite le double enregistrement d'événements
        $(document).off("click", "#btn-validate-compliance").on("click", "#btn-validate-compliance", function (e) {
            e.preventDefault();
            var comment = prompt("Commentaire de validation DPI (optionnel) :", "Contrôles de sécurité et conformité validés");
            if (comment === null) {
                return; // Annulé par l'utilisateur
            }
            postComplianceDecision(issueKey, "COMPLIANT", comment);
        });

        $(document).off("click", "#btn-reject-compliance").on("click", "#btn-reject-compliance", function (e) {
            e.preventDefault();
            var comment = prompt("Motif du rejet / non-conformité :", "Non-respect des règles de livraison DPI");
            if (comment === null) {
                return; // Annulé
            }
            postComplianceDecision(issueKey, "REJECTED", comment);
        });
    }

    // Initialisation au chargement de la page
    $(document).ready(function () {
        initCompliance();
    });

    // Support des rechargements AJAX natifs de Jira (dialogues, boards)
    if (typeof JIRA !== "undefined" && JIRA.bind && JIRA.Events && JIRA.Events.NEW_CONTENT_ADDED) {
        JIRA.bind(JIRA.Events.NEW_CONTENT_ADDED, function (e, context, reason) {
            initCompliance();
        });
    }

})(AJS.$);
