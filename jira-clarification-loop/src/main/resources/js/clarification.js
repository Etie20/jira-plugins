(function ($) {
    "use strict";

    var REQUEST = "request";
    var REPLY = "reply";

    var state = {
        issueKey: null,
        data: null,
        uploads: {},          // fichier déjà téléversé par formulaire (évite les doublons en cas de nouvel essai)
        searchTimer: null,
        searchXhr: null
    };

    // ------------------------------------------------------------------
    // Initialisation
    // ------------------------------------------------------------------

    function init() {
        var $content = $("#clr-content");
        if (!$content.length) {
            return;
        }
        positionPanel();
        if ($content.data("clr-ready")) {
            return;
        }
        $content.data("clr-ready", true);
        state.issueKey = $content.data("issue-key") || (window.JIRA && JIRA.Issue && JIRA.Issue.getIssueKey && JIRA.Issue.getIssueKey()) || $("#key-val").text().trim();

        // AUI Dialog2 doit être attaché au body : on remplace les éventuelles anciennes copies
        moveDialogToBody("#clr-request-dialog");
        moveDialogToBody("#clr-reply-dialog");
        moveDialogToBody("#clr-cancel-dialog");

        load();
    }

    // Garantit que le panneau est toujours en 2e position (juste après "Personnes" et avant "Dates")
    function positionPanel() {
        var $module = $("#clr-content").closest(".module");
        if (!$module.length) {
            $module = $("#demande-infos-sup, [id*='demande-infos-sup']").first();
        }
        var $people = $("#peoplemodule");
        if ($module.length && $people.length && $module.prev()[0] !== $people[0]) {
            $people.after($module);
        }
    }

    function moveDialogToBody(selector) {
        var $fresh = $(selector);
        if ($fresh.length && $fresh.parent()[0] !== document.body) {
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
                showPanelError("Impossible de charger les demandes d'informations supplémentaires.");
            });
    }

    // ------------------------------------------------------------------
    // Rendu du panneau
    // ------------------------------------------------------------------

    function updateBadge(count) {
        var $module = $("#clr-content").closest(".module");
        if (!$module.length) {
            $module = $("#demande-infos-sup, [id*='demande-infos-sup']").first();
        }
        var $header = $module.find(".mod-header");
        if (!$header.length) {
            $header = $module;
        }

        // Trouver le conteneur du titre textuel en privilégiant les balises de titre (h4, h3, h2...)
        var $title = $header.find("h1, h2, h3, h4, h5, h6").first();
        if (!$title.length) {
            $title = $header.find(".toggle-title").not("button, svg").first();
        }
        if (!$title.length) {
            $title = $header.find("button.toggle-title, .toggle-title").first();
        }
        if (!$title.length) {
            $title = $header;
        }

        // Nettoyer d'anciens badges mal positionnés (ex: orphelins dans le bouton chevron)
        $module.find("#clr-count").not($title.find("#clr-count")).remove();

        var $badge = $title.find("#clr-count");
        if (!$badge.length) {
            $badge = $('<span id="clr-count" class="aui-badge" style="margin-left: 6px; vertical-align: middle; display: inline-block;">0</span>');
            var $labelEl = $title.children("a, span").not("#clr-count").first();
            if ($labelEl.length) {
                $labelEl.after($badge);
            } else {
                $title.append($badge);
            }
        }
        $badge.text(count);
    }

    function render() {
        positionPanel();
        var entries = (state.data && state.data.entries) || [];
        var perms = (state.data && state.data.permissions) || {};
        var pending = findPending(entries);

        updateBadge(entries.length);
        $("#clr-status").html(pending ? renderPending(pending, perms) : renderIdle(perms));
        toggleWorkflowTransitions(!!pending, pending);

        var closed = entries.filter(function (e) { return e.status !== "PENDING"; }).reverse();
        $("#clr-history-count").text(closed.length);
        $("#clr-history").toggleClass("clr-hidden", closed.length === 0);
        $("#clr-history-list").html(closed.map(renderHistoryItem).join(""));
    }

    function toggleWorkflowTransitions(locked, pending) {
        var $btns = $("#opsbar-opsbar-transitions a, a.issueaction-workflow-transition, #opsbar-transitions_more");
        if (locked) {
            var reason = "Transition bloquée : une demande d'infos sup est en attente de la réponse de " + ((pending && pending.recipient) || "son destinataire") + ".";
            $btns.addClass("clr-transition-locked").attr({ "aria-disabled": "true", "title": reason });
        } else {
            $btns.removeClass("clr-transition-locked").removeAttr("aria-disabled");
        }
    }

    function renderIdle(perms) {
        if (perms.canRequest) {
            return '<button type="button" id="clr-open-request" class="aui-button">Demande infos sup</button>';
        }
        return '<p class="clr-muted">' + esc(perms.info || "Aucune demande en cours.") + "</p>";
    }

    function renderPending(p, perms) {
        var html =
            '<div class="clr-pending">' +
            '  <div class="clr-pending-title"><span class="aui-lozenge aui-lozenge-current">En attente</span>' +
            '  <span class="aui-lozenge aui-lozenge-moved clr-lock-badge" title="Les transitions du workflow sont bloquées tant que la demande est en attente">Transitions bloquées</span></div>' +
            '  <div class="clr-meta"><strong>' + esc(p.requestedBy) + "</strong> → <strong>" + esc(p.recipient) + "</strong> · " + esc(p.requestedAt) + "</div>" +
            '  <div class="clr-text">' + esc(p.question) + "</div>" +
                 attachmentHtml(p.requestAttachmentId, p.requestAttachmentName) +
            '  <div class="clr-actions">';

        if (perms.canReply) {
            html += '<button type="button" id="clr-open-reply" class="aui-button aui-button-primary">Répondre</button>';
        }
        if (perms.canCancel) {
            html += '<button type="button" id="clr-open-cancel" class="aui-button clr-btn-cancel">Annuler la demande</button>';
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

    function openCancelDialog() {
        var $d = $("#clr-cancel-dialog");
        clearErrors($d);
        $d.data("busy", false);
        $("#clr-confirm-cancel").prop("disabled", false).text("Confirmer l'annulation");
        AJS.dialog2($d).show();
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
            data: JSON.stringify(payload || {}),
            contentType: "application/json; charset=UTF-8",
            dataType: "json",
            headers: { "X-Atlassian-Token": "no-check" }
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
        var $d = $("#clr-cancel-dialog");
        if ($d.data("busy")) {
            return;
        }
        clearErrors($d);
        $d.data("busy", true);
        $("#clr-confirm-cancel").prop("disabled", true).text("Annulation...");

        post("cancel", {})
            .done(function () {
                AJS.dialog2($d).hide();
                flag("Demande annulée.");
                refreshIssue();
            })
            .fail(function (xhr) {
                var r = parseError(xhr);
                showError($d, null, r.error);
            })
            .always(function () {
                $d.data("busy", false);
                $("#clr-confirm-cancel").prop("disabled", false).text("Confirmer l'annulation");
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
        if (err && typeof err.error === "string") {
            return err;                                  // erreur déjà formatée (pièce jointe)
        }
        var json = err && err.responseJSON;
        if (!json && err && err.responseText) {
            try { json = JSON.parse(err.responseText); } catch (ignored) { json = null; }
        }
        if (json && typeof json.error === "string") {
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
        var text = (typeof message === "string" && message) ? message : "Une erreur est survenue. Réessayez.";
        var $fieldError = field ? $d.find('[data-error-for="' + field + '"]') : $();
        if ($fieldError.length) {
            $fieldError.text(text).show();
            $fieldError.closest(".field-group").addClass("clr-invalid");
        } else {
            $d.find(".clr-form-error").removeClass("clr-hidden").find("p").text(text);
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
        var text = (typeof message === "string" && message) ? message : "Une erreur est survenue. Réessayez.";
        $("#clr-panel-error").removeClass("clr-hidden").find("p").text(text);
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
        .on("click.clr", "#clr-open-cancel", function (e) { e.preventDefault(); openCancelDialog(); })
        .on("click.clr", "#clr-confirm-cancel", function (e) { e.preventDefault(); cancelRequest(); })
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

    // Verrouillage des transitions du workflow tant qu'une demande d'infos sup est en attente
    document.addEventListener("click", function (e) {
        var pending = state.data && findPending(state.data.entries || []);
        if (!pending) {
            return;
        }
        var $target = $(e.target).closest("#opsbar-opsbar-transitions a, a.issueaction-workflow-transition, #opsbar-transitions_more");
        if ($target.length) {
            e.preventDefault();
            e.stopPropagation();
            if (typeof e.stopImmediatePropagation === "function") {
                e.stopImmediatePropagation();
            }
            showPanelError("Transitions bloquées : une demande d'infos sup est en attente de la réponse de " + (pending.recipient || "son destinataire") + ".");
        }
    }, true);

    $(init);

    if (window.JIRA && JIRA.bind && JIRA.Events && JIRA.Events.NEW_CONTENT_ADDED) {
        JIRA.bind(JIRA.Events.NEW_CONTENT_ADDED, function () { init(); });
    }

})(AJS.$);
