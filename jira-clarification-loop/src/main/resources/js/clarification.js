(function ($) {
    "use strict";

    var REQUEST = "request";
    var REPLY = "reply";

    var state = {
        issueKey: null,
        data: null,
        confirmCancel: false,
        uploads: {},          // fichier déjà téléversé par formulaire (évite les doublons en cas de nouvel essai)
        searchTimer: null,
        searchXhr: null
    };

    // ------------------------------------------------------------------
    // Initialisation
    // ------------------------------------------------------------------

    function init() {
        var $panel = $("#clr-panel");
        if (!$panel.length || $panel.data("clr-ready")) {
            return;
        }
        $panel.data("clr-ready", true);
        state.issueKey = $panel.data("issue-key");
        state.confirmCancel = false;

        // AUI Dialog2 doit être attaché au body : on remplace les éventuelles anciennes copies
        moveDialogToBody("#clr-request-dialog");
        moveDialogToBody("#clr-reply-dialog");

        load();
    }

    function moveDialogToBody(selector) {
        var $fresh = $("#clr-panel").find(selector);
        if ($fresh.length) {
            $("body").children(selector).remove();
            $fresh.appendTo("body");
        }
    }

    function api(path) {
        return AJS.contextPath() + "/rest/clarification/1.0/issue/" + encodeURIComponent(state.issueKey) + (path || "");
    }

    function load() {
        return $.ajax({ url: api(), type: "GET", dataType: "json", cache: false })
            .done(function (data) {
                state.data = data;
                hidePanelError();
                render();
            })
            .fail(function () {
                $("#clr-status").html("");
                showPanelError("Impossible de charger les demandes de précision.");
            });
    }

    // ------------------------------------------------------------------
    // Rendu du panneau
    // ------------------------------------------------------------------

    function render() {
        var entries = (state.data && state.data.entries) || [];
        var perms = (state.data && state.data.permissions) || {};
        var pending = findPending(entries);

        $("#clr-count").text(entries.length);
        $("#clr-status").html(pending ? renderPending(pending, perms) : renderIdle(perms));

        var closed = entries.filter(function (e) { return e.status !== "PENDING"; }).reverse();
        $("#clr-history-count").text(closed.length);
        $("#clr-history").toggleClass("clr-hidden", closed.length === 0);
        $("#clr-history-list").html(closed.map(renderHistoryItem).join(""));
    }

    function renderIdle(perms) {
        if (perms.canRequest) {
            return '<button type="button" id="clr-open-request" class="aui-button">Demander une précision</button>';
        }
        return '<p class="clr-muted">' + esc(perms.info || "Aucune demande en cours.") + "</p>";
    }

    function renderPending(p, perms) {
        var html =
            '<div class="clr-pending">' +
            '  <div class="clr-pending-title"><span class="aui-lozenge aui-lozenge-current">En attente</span></div>' +
            '  <div class="clr-meta"><strong>' + esc(p.requestedBy) + "</strong> → <strong>" + esc(p.recipient) + "</strong> · " + esc(p.requestedAt) + "</div>" +
            '  <div class="clr-text">' + esc(p.question) + "</div>" +
                 attachmentHtml(p.requestAttachmentId, p.requestAttachmentName) +
            '  <div class="clr-actions">';

        if (perms.canReply) {
            html += '<button type="button" id="clr-open-reply" class="aui-button aui-button-primary">Répondre</button>';
        }
        if (perms.canCancel) {
            if (state.confirmCancel) {
                html += '<span class="clr-confirm">Annuler cette demande ? ' +
                        '<button type="button" id="clr-cancel-yes" class="aui-button aui-button-link">Oui</button>' +
                        '<button type="button" id="clr-cancel-no" class="aui-button aui-button-link">Non</button></span>';
            } else {
                html += '<button type="button" id="clr-cancel" class="aui-button aui-button-link">Annuler la demande</button>';
            }
        }
        if (!perms.canReply && !perms.canCancel) {
            html += '<span class="clr-muted">En attente de la réponse de ' + esc(p.recipient) + ".</span>";
        }
        return html + "</div></div>";
    }

    function renderHistoryItem(e) {
        var html =
            '<div class="clr-item">' +
            '  <div class="clr-meta"><strong>' + esc(e.requestedBy) + "</strong> → <strong>" + esc(e.recipient || "-") + "</strong> · " + esc(e.requestedAt) + "</div>" +
            '  <div class="clr-text">' + esc(e.question) + "</div>" +
                 attachmentHtml(e.requestAttachmentId, e.requestAttachmentName);

        if (e.status === "ANSWERED") {
            html +=
                '<div class="clr-answer">' +
                '  <div class="clr-meta"><strong>' + esc(e.answeredBy) + "</strong> · " + esc(e.answeredAt) + "</div>" +
                '  <div class="clr-text">' + esc(e.answer) + "</div>" +
                   attachmentHtml(e.attachmentId, e.attachmentName) +
                "</div>";
        } else if (e.status === "CANCELLED") {
            html += '<div class="clr-meta"><span class="aui-lozenge aui-lozenge-subtle">Annulée</span> ' + esc(e.cancelledAt || "") + "</div>";
        }
        return html + "</div>";
    }

    function attachmentHtml(id, name) {
        if (!name) {
            return "";
        }
        var label = esc(name);
        if (id) {
            var href = AJS.contextPath() + "/secure/attachment/" + encodeURIComponent(id) + "/" + encodeURIComponent(name);
            label = '<a href="' + href + '" target="_blank" rel="noopener">' + label + "</a>";
        }
        return '<div class="clr-attachment"><span class="aui-icon aui-icon-small aui-iconfont-attachment"></span> ' + label + "</div>";
    }

    function findPending(entries) {
        for (var i = 0; i < entries.length; i++) {
            if (entries[i].status === "PENDING") {
                return entries[i];
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Ouverture des formulaires
    // ------------------------------------------------------------------

    function openRequestDialog() {
        var $d = $("#clr-request-dialog");
        var perms = state.data.permissions || {};
        resetForm($d, REQUEST);

        // Pré-sélection du demandeur initial, sauf s'il s'agit de l'utilisateur courant
        if (perms.reporterName && !sameName(perms.reporterName, perms.currentUserName)) {
            selectRecipient(perms.reporterName, perms.reporterDisplayName);
        }

        AJS.dialog2($d).show();
        ($("#clr-recipient-value").val() ? $("#clr-request-comment") : $("#clr-recipient-search")).trigger("focus");
    }

    function openReplyDialog() {
        var $d = $("#clr-reply-dialog");
        var pending = findPending(state.data.entries || []);
        if (!pending) {
            return;
        }
        resetForm($d, REPLY);
        $("#clr-reply-author").text(pending.requestedBy + " · " + pending.requestedAt);
        $("#clr-reply-question").text(pending.question);
        $("#clr-reply-hint").text("Le ticket sera réassigné à " + pending.requestedBy + " après l'envoi.");

        AJS.dialog2($d).show();
        $("#clr-reply-comment").trigger("focus");
    }

    function resetForm($d, kind) {
        clearErrors($d);
        $d.find("textarea, input[type=text], input[type=hidden], input[type=file]").val("");
        $("#clr-recipient-results").addClass("clr-hidden").empty();
        state.uploads[kind] = null;
        setBusy($d, false);
    }

    // ------------------------------------------------------------------
    // Envoi
    // ------------------------------------------------------------------

    function submit(kind) {
        var $d = kind === REQUEST ? $("#clr-request-dialog") : $("#clr-reply-dialog");
        if ($d.data("busy")) {
            return;
        }
        clearErrors($d);

        var payload = { comment: $.trim($d.find("textarea").val()) };
        var valid = true;

        if (kind === REQUEST) {
            payload.recipient = $("#clr-recipient-value").val();
            if (!payload.recipient) {
                showError($d, "recipient", $.trim($("#clr-recipient-search").val())
                    ? "Sélectionnez un utilisateur dans la liste."
                    : "Choisissez un destinataire.");
                valid = false;
            }
        }
        if (!payload.comment) {
            showError($d, "comment", "Le commentaire est obligatoire.");
            valid = false;
        }
        if (!valid) {
            return;
        }

        setBusy($d, true);

        // 1. validation serveur (droits, destinataire...) avant tout téléversement
        post(kind, $.extend({ validateOnly: true }, payload))
            // 2. téléversement de la pièce jointe éventuelle
            .then(function () { return uploadAttachment($d, kind); })
            // 3. enregistrement
            .then(function (attachment) {
                if (attachment) {
                    payload.attachmentId = attachment.id;
                    payload.attachmentName = attachment.filename;
                }
                return post(kind, payload);
            })
            .done(function () {
                AJS.dialog2($d).hide();
                flag(kind === REQUEST ? "Demande envoyée." : "Réponse envoyée.");
                refreshIssue();
            })
            .fail(function (err) {
                showRequestError($d, err);
            })
            .always(function () {
                setBusy($d, false);
            });
    }

    function post(kind, payload) {
        return $.ajax({
            url: api("/" + kind),
            type: "POST",
            data: JSON.stringify(payload),
            contentType: "application/json; charset=UTF-8",
            dataType: "json"
        });
    }

    function uploadAttachment($d, kind) {
        var input = $d.find("input[type=file]")[0];
        var file = input && input.files && input.files[0];
        if (!file) {
            return $.Deferred().resolve(null).promise();
        }
        var cached = state.uploads[kind];
        if (cached && cached.file === file) {
            return $.Deferred().resolve(cached.attachment).promise();
        }

        var formData = new FormData();
        formData.append("file", file);

        return $.ajax({
            url: AJS.contextPath() + "/rest/api/2/issue/" + encodeURIComponent(state.issueKey) + "/attachments",
            type: "POST",
            data: formData,
            processData: false,
            contentType: false,
            headers: { "X-Atlassian-Token": "no-check" }
        }).then(function (res) {
            var a = res && res[0];
            var attachment = { id: a && a.id ? parseInt(a.id, 10) : null, filename: (a && a.filename) || file.name };
            state.uploads[kind] = { file: file, attachment: attachment };
            return attachment;
        }, function (xhr) {
            var message = "Échec de l'envoi de la pièce jointe.";
            if (xhr.status === 403) {
                message = "Vous n'avez pas le droit d'ajouter une pièce jointe sur ce ticket.";
            } else if (xhr.status === 413) {
                message = "Fichier trop volumineux.";
            }
            return $.Deferred().reject({ field: "attachment", error: message }).promise();
        });
    }

    function cancelRequest() {
        $.ajax({ url: api("/cancel"), type: "POST", dataType: "json" })
            .done(function () {
                state.confirmCancel = false;
                flag("Demande annulée.");
                refreshIssue();
            })
            .fail(function (xhr) {
                state.confirmCancel = false;
                render();
                var r = parseError(xhr);
                showPanelError(r.error);
            });
    }

    function refreshIssue() {
        load();
        // Le responsable du ticket a changé : on rafraîchit la vue Jira
        try {
            if (window.JIRA && JIRA.Issue && JIRA.Issue.getIssueId && JIRA.Events && JIRA.Events.REFRESH_ISSUE_PAGE) {
                JIRA.trigger(JIRA.Events.REFRESH_ISSUE_PAGE, [JIRA.Issue.getIssueId()]);
            }
        } catch (ignored) {
            // la mise à jour du panneau suffit
        }
    }

    // ------------------------------------------------------------------
    // Recherche d'utilisateurs
    // ------------------------------------------------------------------

    function searchUsers(query) {
        if (state.searchXhr) {
            state.searchXhr.abort();
        }
        var currentUser = (state.data && state.data.permissions && state.data.permissions.currentUserName) || "";

        state.searchXhr = $.ajax({
            url: AJS.contextPath() + "/rest/api/2/user/picker",
            data: { query: query, maxResults: 10, showAvatar: true },
            dataType: "json"
        }).done(function (data) {
            var users = (data.users || []).filter(function (u) { return !sameName(u.name, currentUser); });
            var $list = $("#clr-recipient-results").empty();
            if (!users.length) {
                $list.append('<li class="clr-empty">Aucun utilisateur trouvé</li>');
            }
            users.forEach(function (u, i) {
                $("<li/>")
                    .addClass("clr-option" + (i === 0 ? " clr-active" : ""))
                    .attr({ "data-name": u.name, "data-display": u.displayName })
                    .append(u.avatarUrl ? $("<img/>").attr({ src: u.avatarUrl, alt: "" }) : null)
                    .append($("<span/>").text(u.displayName))
                    .append($("<span class='clr-username'/>").text(u.name))
                    .appendTo($list);
            });
            $list.removeClass("clr-hidden");
        }).fail(function (xhr) {
            if (xhr.statusText !== "abort") {
                showError($("#clr-request-dialog"), "recipient", "Recherche d'utilisateurs indisponible.");
            }
        });
    }

    function selectRecipient(name, displayName) {
        $("#clr-recipient-value").val(name);
        $("#clr-recipient-search").val(displayName + " (" + name + ")");
        $("#clr-recipient-results").addClass("clr-hidden").empty();
        clearFieldError($("#clr-request-dialog"), "recipient");
    }

    function moveActive(step) {
        var $options = $("#clr-recipient-results .clr-option");
        if (!$options.length) {
            return;
        }
        var index = $options.index($options.filter(".clr-active"));
        index = (index + step + $options.length) % $options.length;
        $options.removeClass("clr-active").eq(index).addClass("clr-active");
    }

    // ------------------------------------------------------------------
    // Erreurs et états
    // ------------------------------------------------------------------

    function parseError(err) {
        if (err && err.error) {
            return err;                                  // erreur déjà formatée (pièce jointe)
        }
        var json = err && err.responseJSON;
        if (!json && err && err.responseText) {
            try { json = JSON.parse(err.responseText); } catch (ignored) { json = null; }
        }
        if (json && json.error) {
            return { field: json.field, error: json.error };
        }
        if (err && err.status === 401) {
            return { error: "Votre session a expiré. Reconnectez-vous." };
        }
        return { error: "Une erreur est survenue. Réessayez." };
    }

    function showRequestError($d, err) {
        var r = parseError(err);
        showError($d, r.field, r.error);
    }

    function showError($d, field, message) {
        var $fieldError = field ? $d.find('[data-error-for="' + field + '"]') : $();
        if ($fieldError.length) {
            $fieldError.text(message).show();
            $fieldError.closest(".field-group").addClass("clr-invalid");
        } else {
            $d.find(".clr-form-error").removeClass("clr-hidden").find("p").text(message);
        }
    }

    function clearFieldError($d, field) {
        var $fieldError = $d.find('[data-error-for="' + field + '"]');
        $fieldError.text("").hide();
        $fieldError.closest(".field-group").removeClass("clr-invalid");
    }

    function clearErrors($d) {
        $d.find(".clr-field-error").text("").hide();
        $d.find(".field-group").removeClass("clr-invalid");
        $d.find(".clr-form-error").addClass("clr-hidden").find("p").text("");
    }

    function showPanelError(message) {
        $("#clr-panel-error").removeClass("clr-hidden").find("p").text(message);
    }

    function hidePanelError() {
        $("#clr-panel-error").addClass("clr-hidden").find("p").text("");
    }

    function setBusy($d, busy) {
        $d.data("busy", busy);
        $d.find(".clr-submit").prop("disabled", busy).text(busy ? "Envoi..." : "Envoyer");
    }

    function flag(message) {
        if (typeof AJS.flag === "function") {
            AJS.flag({ type: "success", body: message, close: "auto" });
        }
    }

    function sameName(a, b) {
        return !!a && !!b && String(a).toLowerCase() === String(b).toLowerCase();
    }

    function esc(text) {
        return text == null ? "" : $("<div/>").text(String(text)).html();
    }

    // ------------------------------------------------------------------
    // Événements (délégués, enregistrés une seule fois)
    // ------------------------------------------------------------------

    $(document)
        .on("click.clr", "#clr-open-request", function (e) { e.preventDefault(); openRequestDialog(); })
        .on("click.clr", "#clr-open-reply", function (e) { e.preventDefault(); openReplyDialog(); })
        .on("click.clr", "#clr-cancel", function () { state.confirmCancel = true; render(); })
        .on("click.clr", "#clr-cancel-no", function () { state.confirmCancel = false; render(); })
        .on("click.clr", "#clr-cancel-yes", function () { cancelRequest(); })
        .on("click.clr", "#clr-history-toggle", function () { $("#clr-history-list").toggleClass("clr-hidden"); })
        .on("click.clr", ".clr-dialog .clr-dialog-close", function () { AJS.dialog2($(this).closest(".clr-dialog")).hide(); })
        .on("click.clr", ".clr-dialog .clr-submit", function () { submit($(this).data("kind")); })
        .on("submit.clr", ".clr-dialog form", function (e) { e.preventDefault(); })
        .on("input.clr", ".clr-dialog textarea", function () { clearFieldError($(this).closest(".clr-dialog"), "comment"); })
        .on("change.clr", ".clr-dialog input[type=file]", function () {
            var $d = $(this).closest(".clr-dialog");
            clearFieldError($d, "attachment");
            state.uploads[$d.is("#clr-request-dialog") ? REQUEST : REPLY] = null;
        })
        .on("input.clr", "#clr-recipient-search", function () {
            var query = $.trim($(this).val());
            $("#clr-recipient-value").val("");
            clearFieldError($("#clr-request-dialog"), "recipient");
            clearTimeout(state.searchTimer);
            if (query.length < 2) {
                $("#clr-recipient-results").addClass("clr-hidden").empty();
                return;
            }
            state.searchTimer = setTimeout(function () { searchUsers(query); }, 250);
        })
        .on("keydown.clr", "#clr-recipient-search", function (e) {
            var open = !$("#clr-recipient-results").hasClass("clr-hidden");
            if (!open) {
                return;
            }
            if (e.key === "ArrowDown") { e.preventDefault(); moveActive(1); }
            else if (e.key === "ArrowUp") { e.preventDefault(); moveActive(-1); }
            else if (e.key === "Enter") {
                e.preventDefault();
                var $active = $("#clr-recipient-results .clr-active");
                if ($active.length) {
                    selectRecipient($active.data("name"), $active.data("display"));
                }
            } else if (e.key === "Escape") {
                e.preventDefault();
                e.stopPropagation();
                $("#clr-recipient-results").addClass("clr-hidden");
            }
        })
        .on("mousedown.clr", "#clr-recipient-results .clr-option", function (e) {
            e.preventDefault();
            selectRecipient($(this).data("name"), $(this).data("display"));
        })
        .on("blur.clr", "#clr-recipient-search", function () {
            setTimeout(function () { $("#clr-recipient-results").addClass("clr-hidden"); }, 150);
        });

    $(init);

    if (window.JIRA && JIRA.bind && JIRA.Events && JIRA.Events.NEW_CONTENT_ADDED) {
        JIRA.bind(JIRA.Events.NEW_CONTENT_ADDED, function () { init(); });
    }

})(AJS.$);
