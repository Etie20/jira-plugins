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
        initSearchFilter();

        var $content = $("#clr-content");
        if (!$content.length) {
            return;
        }
        positionPanel();
        ensurePanelOpen();
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

    // ------------------------------------------------------------------
    // Filtre rapide sans JQL dans la recherche de tickets (/issues/)
    // ------------------------------------------------------------------

    function getCurrentSearchJql() {
        var search = window.location.search || "";
        var m = search.match(/[?&]jql=([^&]*)/i);
        if (m && m[1] !== undefined) {
            try {
                return decodeURIComponent(m[1].replace(/\+/g, " "));
            } catch (ignored) {
                return m[1];
            }
        }
        var $adv = $("#advanced-search");
        if ($adv.length && $adv.is(":visible")) {
            return $.trim($adv.val() || "");
        }
        return "";
    }

    function isPendingFilterActive() {
        var urlJql = getCurrentSearchJql();
        if (/pendingInfoSup\s*\(\s*\)/i.test(urlJql)) {
            return true;
        }
        var $adv = $("#advanced-search");
        if ($adv.length && $adv.is(":visible") && /pendingInfoSup\s*\(\s*\)/i.test($adv.val() || "")) {
            return true;
        }
        return false;
    }

    function buildToggledJql(currentlyActive) {
        var jql = getCurrentSearchJql();
        var $adv = $("#advanced-search");
        if (!jql && $adv.length && $adv.is(":visible")) {
            jql = $.trim($adv.val() || "");
        }
        if (!currentlyActive) {
            if (!jql) {
                return "issue in pendingInfoSup()";
            }
            var orderMatch = jql.match(/\bORDER\s+BY\b/i);
            if (orderMatch) {
                var idx = orderMatch.index;
                var wherePart = $.trim(jql.slice(0, idx));
                var orderPart = $.trim(jql.slice(idx));
                if (!wherePart) {
                    return "issue in pendingInfoSup() " + orderPart;
                }
                return "(" + wherePart + ") AND issue in pendingInfoSup() " + orderPart;
            }
            return "(" + jql + ") AND issue in pendingInfoSup()";
        } else {
            var cleaned = jql
                .replace(/\bAND\s+issue\s+in\s+pendingInfoSup\s*\(\s*\)/ig, "")
                .replace(/\bissue\s+in\s+pendingInfoSup\s*\(\s*\)\s+AND\b/ig, "")
                .replace(/\bissue\s+in\s+pendingInfoSup\s*\(\s*\)/ig, "");
            cleaned = $.trim(cleaned);
            if (/^\(\s*(.*)\s*\)$/.test(cleaned)) {
                cleaned = cleaned.replace(/^\(\s*/, "").replace(/\s*\)$/, "");
            }
            return $.trim(cleaned);
        }
    }

    function initSearchFilter() {
        var active = isPendingFilterActive();

        // 1. Bouton critère sobre dans la barre de recherche (.search-criteria .criteria-list ou .search-options-container)
        var $criteriaList = $(".search-criteria .criteria-list").first();
        if ($criteriaList.length) {
            var $existing = $("#clr-search-filter-item");
            if (!$existing.length) {
                $existing = $('<li id="clr-search-filter-item"><button type="button" id="clr-search-filter-btn" class="aui-button aui-button-subtle"></button></li>');
                var $more = $criteriaList.find(".criteria-actions");
                if ($more.length) {
                    $more.after($existing);
                } else {
                    $criteriaList.append($existing);
                }
            }
            updateSearchButtonState($("#clr-search-filter-btn"), active);
        } else {
            var $optionsBox = $(".search-options-container").first();
            if ($optionsBox.length) {
                var $btn = $("#clr-search-filter-btn");
                if (!$btn.length) {
                    $btn = $('<button type="button" id="clr-search-filter-btn" class="aui-button aui-button-subtle" style="margin-left:8px;"></button>');
                    $optionsBox.append($btn);
                }
                updateSearchButtonState($btn, active);
            }
        }

        // 2. Lien rapide dans le panneau latéral gauche « Filtres » (.system-filters)
        var $sysFilters = $(".filter-panel-system-container ul.system-filters");
        if ($sysFilters.length) {
            var $item = $("#clr-system-filter-item");
            if (!$item.length) {
                var href = AJS.contextPath() + "/issues/?jql=" + encodeURIComponent("issue in pendingInfoSup()");
                $item = $(
                    '<li id="clr-system-filter-item">' +
                    '<a class="clr-system-filter-link" href="' + href + '" title="Afficher les tickets ayant une demande d\'informations supplémentaires en attente">Demandes d\'infos sup</a>' +
                    '</li>'
                );
                $sysFilters.append($item);
            }
            var targetActiveClass = active ? "yes" : "no";
            if ($item.attr("data-clr-active") !== targetActiveClass) {
                $item.attr("data-clr-active", targetActiveClass);
                $item.toggleClass("clr-active-filter", active);
            }
        }
    }

    function updateSearchButtonState($btn, active) {
        if (!$btn.length) {
            return;
        }
        var targetState = active ? "on" : "off";
        // Ne jamais recréer le DOM interne si l'état n'a pas changé (évite d'annuler le clic en cours)
        if ($btn.attr("data-clr-state") === targetState) {
            return;
        }
        $btn.attr("data-clr-state", targetState);
        $btn.attr("aria-pressed", active ? "true" : "false");
        $btn.toggleClass("clr-search-active", active);
        if (active) {
            $btn.attr("title", "Filtre actif : Demandes d'infos sup en attente (cliquer pour désactiver)");
            $btn.html('<span class="clr-btn-label">Demande infos sup : </span><span class="clr-btn-val">En attente</span>');
        } else {
            $btn.attr("title", "Filtrer les tickets ayant une demande d'informations supplémentaires en attente");
            $btn.html('<span class="clr-btn-label">Demande infos sup : </span><span class="clr-btn-val">Tout</span>');
        }
    }

    // Assure que le panneau est ouvert (déplié) par défaut et supprime tout badge résiduel
    function ensurePanelOpen() {
        var $module = $("#clr-content").closest(".module");
        if (!$module.length) {
            $module = $("#demande-infos-sup, [id*='demande-infos-sup']").first();
        }
        if ($module.length) {
            $module.find("#clr-count").remove();
            if ($module.hasClass("collapsed")) {
                $module.removeClass("collapsed");
            }
            $module.find("button.toggle-title, .toggle-title").attr("aria-expanded", "true");
            $module.find(".mod-content").show();
        }
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

    function render() {
        positionPanel();
        ensurePanelOpen();
        var entries = (state.data && state.data.entries) || [];
        var perms = (state.data && state.data.permissions) || {};
        var pending = findPending(entries);

        $("#clr-status").html(pending ? renderPending(pending, perms) : renderIdle(perms));
        var isFrozen = !!pending && (perms.transitionsLocked !== false);
        toggleWorkflowTransitions(isFrozen, pending);

        var closed = entries.filter(function (e) { return e.status !== "PENDING"; }).reverse();
        $("#clr-history-count").text(closed.length);
        $("#clr-history").toggleClass("clr-hidden", closed.length === 0);
        $("#clr-history-list").html(closed.map(renderHistoryItem).join(""));
    }

    function toggleWorkflowTransitions(locked, pending) {
        var $allBtns = $("#opsbar-opsbar-transitions a, a.issueaction-workflow-transition, #opsbar-transitions_more");
        if (!locked) {
            $allBtns.removeClass("clr-transition-locked").removeAttr("aria-disabled").removeAttr("title");
            return;
        }

        var reason = "État figé : une demande d'infos sup est en attente de la réponse de " + ((pending && pending.recipient) || "son destinataire") + ".";
        $allBtns.addClass("clr-transition-locked").attr({ "aria-disabled": "true", "title": reason });
    }

    function renderIdle(perms) {
        if (perms.canRequest) {
            return '<button type="button" id="clr-open-request" class="aui-button">Demande infos sup</button>';
        }
        return '<p class="clr-muted">' + esc(perms.info || "Aucune demande en cours.") + "</p>";
    }

    function renderPending(p, perms) {
        var isFrozen = perms.transitionsLocked !== false;
        var lockBadge = isFrozen
            ? '  <span class="aui-lozenge aui-lozenge-moved clr-lock-badge" title="L\'état du ticket est figé tant que la demande est en attente">État figé</span>'
            : '';
        var html =
            '<div class="clr-pending">' +
            '  <div class="clr-pending-title"><span class="aui-lozenge aui-lozenge-current">En attente</span>' +
            lockBadge + '</div>' +
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
        $("#clr-reply-author").text(pending.requestedBy || "-");
        $("#clr-reply-date").text(pending.requestedAt || "");
        $("#clr-reply-question").text(pending.question || "");

        var attHtml = attachmentHtml(pending.requestAttachmentId, pending.requestAttachmentName);
        var $attBox = $("#clr-reply-attachment");
        if (attHtml) {
            $attBox.html(attHtml).removeClass("clr-hidden");
        } else {
            $attBox.empty().addClass("clr-hidden");
        }

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
    // Recherche d'utilisateurs basée sur les intervenants et la sécurité du ticket
    // ------------------------------------------------------------------

    function searchUsers(query) {
        if (state.searchXhr) {
            state.searchXhr.abort();
        }
        var currentUser = (state.data && state.data.permissions && state.data.permissions.currentUserName) || "";

        state.searchXhr = $.ajax({
            url: api("/users"),
            data: { q: query || "" },
            dataType: "json",
            cache: false
        }).done(function (data) {
            var users = (data.users || []).filter(function (u) { return !sameName(u.name, currentUser); });
            var $list = $("#clr-recipient-results").empty();
            if (!users.length) {
                $list.append('<li class="clr-empty">Aucun intervenant trouvé pour ce ticket</li>');
            }
            users.forEach(function (u, i) {
                var $li = $("<li/>")
                    .addClass("clr-option" + (i === 0 ? " clr-active" : ""))
                    .attr({ "data-name": u.name, "data-display": u.displayName });

                if (u.avatarUrl) {
                    $li.append($("<img/>").attr({ src: u.avatarUrl, alt: "" }));
                }

                var $main = $("<div class='clr-user-main'/>")
                    .append($("<span class='clr-user-display'/>").text(u.displayName))
                    .append($("<span class='clr-username'/>").text("(" + u.name + ")"));
                $li.append($main);

                if (u.role) {
                    $li.append($("<span class='clr-user-role'/>").text(u.role));
                }

                $li.appendTo($list);
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
        .on("click.clr", "#clr-search-filter-btn", function (e) {
            e.preventDefault();
            e.stopPropagation();
            var $btn = $(this);
            if ($btn.data("clr-navigating")) {
                return;
            }
            $btn.data("clr-navigating", true);
            var active = isPendingFilterActive();
            var nextJql = buildToggledJql(active);
            updateSearchButtonState($btn, !active);
            window.location.href = AJS.contextPath() + "/issues/?jql=" + encodeURIComponent(nextJql);
        })
        .on("click.clr", "#opsbar-transitions_more", function () {
            var pending = state.data && findPending(state.data.entries || []);
            if (pending) {
                setTimeout(function () { toggleWorkflowTransitions(true, pending); }, 50);
            }
        })
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
        .on("focus.clr click.clr", "#clr-recipient-search", function () {
            // Au clic/focus dans le champ, proposer immédiatement les intervenants du ticket
            if ($("#clr-recipient-results").hasClass("clr-hidden")) {
                var hasSelected = !!$("#clr-recipient-value").val();
                var query = hasSelected ? "" : $.trim($(this).val());
                searchUsers(query);
            }
        })
        .on("input.clr", "#clr-recipient-search", function () {
            var query = $.trim($(this).val());
            $("#clr-recipient-value").val("");
            clearFieldError($("#clr-request-dialog"), "recipient");
            clearTimeout(state.searchTimer);
            state.searchTimer = setTimeout(function () { searchUsers(query); }, 180);
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
        if (!$target.length) {
            return;
        }
        // Vérifier si cette transition spécifique est bloquée
        if (!$target.hasClass("clr-transition-locked")) {
            return;
        }
        e.preventDefault();
        e.stopPropagation();
        if (typeof e.stopImmediatePropagation === "function") {
            e.stopImmediatePropagation();
        }
        showPanelError("Transition bloquée : une demande d'infos sup est en attente de la réponse de " + (pending.recipient || "son destinataire") + ".");
    }, true);

    $(function () {
        init();
        // Dans l'Issue Navigator, la barre de critères et les filtres système sont rendus en asynchrone
        if (window.MutationObserver) {
            var navTimer = null;
            var observer = new MutationObserver(function () {
                if (!$("#clr-search-filter-btn").length || !$("#clr-system-filter-item").length) {
                    clearTimeout(navTimer);
                    navTimer = setTimeout(initSearchFilter, 100);
                }
            });
            if (document.body) {
                observer.observe(document.body, { childList: true, subtree: true });
            }
        }
    });

    if (window.JIRA && JIRA.Events && JIRA.Events.NEW_CONTENT_ADDED && JIRA.bind) {
        JIRA.bind(JIRA.Events.NEW_CONTENT_ADDED, init);
    }
})(AJS.$);
