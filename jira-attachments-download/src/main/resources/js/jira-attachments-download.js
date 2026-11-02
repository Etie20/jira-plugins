/**
 * Jira Attachments Download - Interactive UI Dialog
 * Connects directly with /rest/attachments-download/1.0/options, /preview, and /zip
 * Provides dynamic search-as-you-type multi-selection for projects, statuses, issue types, and file formats.
 */
(function ($) {
    "use strict";

    var BASE_REST_URL = AJS.contextPath() + "/rest/attachments-download/1.0";

    // Proposed common attachment formats
    var PRESET_EXTENSIONS = [
        { ext: "pdf", label: ".pdf", category: "Document" },
        { ext: "docx", label: ".docx", category: "Word" },
        { ext: "doc", label: ".doc", category: "Word" },
        { ext: "xlsx", label: ".xlsx", category: "Excel" },
        { ext: "xls", label: ".xls", category: "Excel" },
        { ext: "pptx", label: ".pptx", category: "PowerPoint" },
        { ext: "csv", label: ".csv", category: "Données" },
        { ext: "txt", label: ".txt", category: "Texte" },
        { ext: "png", label: ".png", category: "Image" },
        { ext: "jpg", label: ".jpg", category: "Image" },
        { ext: "jpeg", label: ".jpeg", category: "Image" },
        { ext: "gif", label: ".gif", category: "Image" },
        { ext: "zip", label: ".zip", category: "Archive" },
        { ext: "json", label: ".json", category: "Données" },
        { ext: "xml", label: ".xml", category: "Données" },
        { ext: "log", label: ".log", category: "Journal" },
        { ext: "msg", label: ".msg", category: "Email" }
    ];

    // Dynamic State
    var availableProjects = [];
    var availableStatuses = [];
    var availableIssueTypes = [];

    var selectedStatuses = [];   // Array of status objects: { id, name, category }
    var selectedTypes = [];      // Array of issue type objects: { id, name, subTask }
    var selectedExtensions = []; // Array of lowercase extension strings: ["pdf", "xlsx", ...]
    var customExtensions = [];   // Additional custom extensions added by the user during session

    var currentIssueKey = "";
    var currentProjectKey = "";
    var isAdvancedOpen = false;
    var isLoadingOptions = false;
    var isTypingProject = false;
    var projectDebounceTimer = null;

    $(document).ready(function () {
        initTriggers();
    });

    function initTriggers() {
        $(document).on("click", "#attachments-download-trigger, #attachments-download-project-trigger, .attachments-download-trigger-btn", function (e) {
            e.preventDefault();
            openExportDialog($(this));
        });
    }

    function openExportDialog($trigger) {
        var $dialog = $("#attachments-download-dialog");
        if ($dialog.length === 0) {
            createDialogHtml();
            $dialog = $("#attachments-download-dialog");
            bindDialogEvents();
        }

        // Reset state
        selectedStatuses = [];
        selectedTypes = [];
        selectedExtensions = [];
        customExtensions = [];
        isTypingProject = false;
        $("#ad-extensions").val("");
        $(".ad-dropdown").hide();
        $("#ad-preview-result").hide().empty();

        // Auto-detect current context from clicked trigger element and page
        var detected = prefillContext($trigger);

        // Start in simple mode (collapsed advanced fields)
        setAdvancedMode(false);

        // Render formats UI
        renderExtensionsUI();

        // Load dynamic options for the current project context
        loadFilterOptions(detected.projectKey);

        AJS.dialog2("#attachments-download-dialog").show();
    }

    function createDialogHtml() {
        var html = [
            '<section id="attachments-download-dialog" class="aui-dialog2 aui-dialog2-medium aui-layer" role="dialog" aria-hidden="true">',
            '  <header class="aui-dialog2-header">',
            '    <h2 class="aui-dialog2-header-main"><span class="aui-icon aui-icon-small aui-iconfont-download"></span> Téléchargement des Pièces Jointes</h2>',
            '    <button class="aui-dialog2-header-close" aria-label="Fermer"><span class="aui-icon aui-icon-small aui-iconfont-close-dialog"></span></button>',
            '  </header>',
            '  <div class="aui-dialog2-content">',
            '    <form class="aui" id="attachments-download-form" onsubmit="return false;">',

            // Ticket context banner (shown when opened on a specific ticket)
            '      <div class="aui-message aui-message-info" id="ad-ticket-banner" style="display:none;">',
            '        <p>Téléchargement des pièces jointes du ticket <strong id="ad-ticket-key-label"></strong>.</p>',
            '      </div>',

            // Primary fields for ticket download: Interactive Extensions & Archive Organization
            '      <div class="field-group">',
            '        <label for="ad-extensions">Formats de pièces jointes :</label>',
            '        <div class="ad-search-box" id="ad-extension-box">',
            '          <div id="ad-selected-extensions" class="ad-tags-container">',
            '            <span class="ad-empty-hint">Tous les formats sont inclus (aucun filtre)</span>',
            '          </div>',
            '          <div class="ad-preset-formats-label">Formats proposés (cliquez pour sélectionner / désélectionner) :</div>',
            '          <div id="ad-preset-extensions" class="ad-format-pills"></div>',
            '          <div class="ad-input-wrapper ad-ext-input-row">',
            '            <input type="text" class="text" id="ad-extensions" name="extensions" autocomplete="off" placeholder="Autre format spécifique ? Tapez-le ici (ex: yaml, 7z, sql) puis Entrée..." />',
            '            <button type="button" class="aui-button ad-btn-add-ext" id="ad-btn-add-ext">Ajouter</button>',
            '          </div>',
            '          <div id="ad-extension-dropdown" class="ad-dropdown"></div>',
            '          <div class="ad-quick-actions" id="ad-extension-clear" style="display:none;">',
            '            <a href="#" id="ad-link-clear-extensions">Tout désélectionner (inclure tous les formats)</a>',
            '          </div>',
            '        </div>',
            '        <div class="description">Cliquez sur les formats proposés pour les activer/désactiver, ou saisissez un format spécifique non listé.</div>',
            '      </div>',

            '      <div class="field-group">',
            '        <label for="ad-grouping">Organisation de l\'archive :</label>',
            '        <select class="select" id="ad-grouping" name="grouping">',
            '          <option value="BY_PROJECT_AND_ISSUE" selected>[Projet] / [Clé - Titre] / [Fichier] (Recommandé)</option>',
            '          <option value="BY_ISSUE">[Clé Ticket] / [Fichier]</option>',
            '          <option value="BY_STATUS_AND_ISSUE">[Statut] / [Clé Ticket] / [Fichier]</option>',
            '          <option value="FLAT_WITH_PREFIX">[Clé]_[NomFichier] (Racine)</option>',
            '        </select>',
            '      </div>',

            // Toggle for Advanced Download
            '      <div class="ad-advanced-toggle-wrapper">',
            '        <a href="#" id="ad-toggle-advanced" class="ad-advanced-toggle" role="button" aria-expanded="false">',
            '          <span class="ad-toggle-icon" id="ad-toggle-icon">&#9656;</span>',
            '          <span id="ad-toggle-text">Téléchargement avancé</span>',
            '          <span class="ad-toggle-subtitle">(sélectionner le projet, les types de tickets, statuts et JQL)</span>',
            '        </a>',
            '      </div>',

            // Collapsible Advanced Fields Section
            '      <div id="ad-advanced-section" class="ad-advanced-section" style="display:none;">',
            '        <div class="aui-message aui-message-info" id="ad-info-banner">',
            '          <p>Les critères de statut et types de tickets s\'adaptent dynamiquement aux workflows et schémas du projet sélectionné.</p>',
            '        </div>',

            // Project Field with Autocomplete
            '        <div class="field-group">',
            '          <label for="ad-project">Projet :</label>',
            '          <div class="ad-search-box">',
            '            <div class="ad-input-wrapper">',
            '              <input type="text" class="text" id="ad-project" name="project" autocomplete="off" placeholder="Tapez la clé ou le nom du projet (ex: PRJ)..." />',
            '              <span id="ad-project-spinner" class="ad-spinner" style="display:none;"><span class="aui-icon aui-icon-wait"></span></span>',
            '            </div>',
            '            <div id="ad-project-dropdown" class="ad-dropdown"></div>',
            '          </div>',
            '          <div class="description">Tapez pour chercher un projet. Laissez vide pour inclure tous les projets accessibles.</div>',
            '        </div>',

            // Status Multi-Select
            '        <div class="field-group">',
            '          <label>Statuts :</label>',
            '          <div class="ad-search-box">',
            '            <div id="ad-selected-statuses" class="ad-tags-container">',
            '              <span class="ad-empty-hint">Tous les statuts du projet sont inclus (aucun filtre)</span>',
            '            </div>',
            '            <div class="ad-input-wrapper">',
            '              <input type="text" class="text" id="ad-status-search" autocomplete="off" placeholder="Rechercher un statut au fil de l\'eau (ex: Ouvert, En cours, Résolu)..." />',
            '              <span id="ad-status-spinner" class="ad-spinner" style="display:none;"><span class="aui-icon aui-icon-wait"></span></span>',
            '            </div>',
            '            <div id="ad-status-dropdown" class="ad-dropdown"></div>',
            '            <div class="ad-quick-actions" id="ad-status-clear" style="display:none;">',
            '              <a href="#" id="ad-link-clear-statuses">Effacer la sélection des statuts</a>',
            '            </div>',
            '          </div>',
            '          <div class="description">Tapez des lettres pour filtrer les statuts du projet, puis cliquez pour les ajouter.</div>',
            '        </div>',

            // Issue Type Multi-Select
            '        <div class="field-group">',
            '          <label>Types de tickets :</label>',
            '          <div class="ad-search-box">',
            '            <div id="ad-selected-types" class="ad-tags-container">',
            '              <span class="ad-empty-hint">Tous les types de tickets du projet sont inclus (aucun filtre)</span>',
            '            </div>',
            '            <div class="ad-input-wrapper">',
            '              <input type="text" class="text" id="ad-type-search" autocomplete="off" placeholder="Rechercher un type (ex: Bug, Story, Tâche, Demande)..." />',
            '              <span id="ad-type-spinner" class="ad-spinner" style="display:none;"><span class="aui-icon aui-icon-wait"></span></span>',
            '            </div>',
            '            <div id="ad-type-dropdown" class="ad-dropdown"></div>',
            '            <div class="ad-quick-actions" id="ad-type-clear" style="display:none;">',
            '              <a href="#" id="ad-link-clear-types">Effacer la sélection des types</a>',
            '            </div>',
            '          </div>',
            '          <div class="description">Recherche dynamique parmi les types configurés sur le projet (standard ou sous-tâche).</div>',
            '        </div>',

            // Custom JQL
            '        <div class="field-group">',
            '          <label for="ad-custom-jql">JQL additionnel (optionnel) :</label>',
            '          <input type="text" class="text long-field" id="ad-custom-jql" name="jql" placeholder="Ex: created &gt;= -30d AND priority = High" />',
            '        </div>',
            '      </div>',

            // Preview result container
            '      <div id="ad-preview-result" style="display:none; margin-top:15px;"></div>',
            '    </form>',
            '  </div>',
            '  <footer class="aui-dialog2-footer">',
            '    <div class="aui-dialog2-footer-actions">',
            '      <button class="aui-button aui-button-subtle" id="ad-btn-preview"><span class="aui-icon aui-icon-small aui-iconfont-search"></span> Prévisualiser</button>',
            '      <button class="aui-button aui-button-primary" id="ad-btn-download"><span class="aui-icon aui-icon-small aui-iconfont-download"></span> Télécharger le ZIP</button>',
            '      <button class="aui-button" id="ad-btn-close">Fermer</button>',
            '    </div>',
            '  </footer>',
            '</section>'
        ].join('\n');

        $('body').append(html);
    }

    function setAdvancedMode(open) {
        isAdvancedOpen = !!open;
        var $section = $("#ad-advanced-section");
        var $toggle = $("#ad-toggle-advanced");
        var $icon = $("#ad-toggle-icon");

        $(".ad-dropdown").hide();
        $("#ad-preview-result").hide().empty();

        if (isAdvancedOpen) {
            $section.slideDown(180);
            $toggle.attr("aria-expanded", "true").addClass("ad-expanded");
            $icon.html("&#9662;");
            $("#ad-ticket-banner").hide();
        } else {
            $section.slideUp(150);
            $toggle.attr("aria-expanded", "false").removeClass("ad-expanded");
            $icon.html("&#9656;");
            if (currentIssueKey) {
                $("#ad-ticket-key-label").text(currentIssueKey);
                $("#ad-ticket-banner").show();
            } else {
                $("#ad-ticket-banner").hide();
            }
        }
    }

    function prefillContext($trigger) {
        var currentProject = "";
        var currentIssue = "";

        // 1. Try to extract from the clicked trigger element href or data attributes
        if ($trigger && $trigger.length) {
            var href = $trigger.attr("href") || "";
            var issueMatch = href.match(/(?:key%20%3D%20|key\s*=\s*|key%3D|key=)([A-Za-z0-9_]+-[0-9]+)/i);
            if (issueMatch && issueMatch[1]) {
                currentIssue = issueMatch[1];
            }

            var projMatch = href.match(/(?:project=|project%3D)([A-Za-z0-9_]+)/i);
            if (projMatch && projMatch[1]) {
                currentProject = projMatch[1];
            }

            if (!currentIssue) {
                currentIssue = $trigger.data("issue-key") || $trigger.closest("[data-issue-key]").data("issue-key") || "";
            }
        }

        // 2. Fallback to Jira DOM elements (standard issue view)
        if (!currentIssue) {
            var $keyVal = $("#key-val, #issuekey-val");
            if ($keyVal.length) {
                var text = $keyVal.text().trim();
                if (/^[A-Za-z0-9_]+-[0-9]+$/.test(text)) {
                    currentIssue = text;
                }
            }
        }

        // 3. Fallback to URL path (/browse/KEY-123 or /projects/KEY)
        if (!currentIssue) {
            var urlIssueMatch = window.location.pathname.match(/\/browse\/([A-Za-z0-9_]+-[0-9]+)/i);
            if (urlIssueMatch && urlIssueMatch[1]) {
                currentIssue = urlIssueMatch[1];
            }
        }
        if (!currentProject) {
            var urlProjMatch = window.location.pathname.match(/\/projects\/([A-Za-z0-9_]+)/i);
            if (urlProjMatch && urlProjMatch[1]) {
                currentProject = urlProjMatch[1];
            }
        }

        // 4. Fallback to AJS / JIRA meta APIs
        if (!currentIssue && typeof AJS !== "undefined" && AJS.Meta && AJS.Meta.get) {
            currentIssue = AJS.Meta.get("issue-key") || "";
        }
        if (!currentIssue) {
            var $issueMeta = $('meta[name="ajs-issue-key"]');
            if ($issueMeta.length) {
                currentIssue = $issueMeta.attr("content") || "";
            }
        }
        if (!currentProject && typeof AJS !== "undefined" && AJS.Meta && AJS.Meta.get) {
            currentProject = AJS.Meta.get("project-key") || "";
        }

        // 5. Derive project from issue key if project not yet found
        if (!currentProject && currentIssue) {
            var parts = currentIssue.split("-");
            if (parts.length >= 2) {
                currentProject = parts[0];
            }
        }

        currentIssueKey = currentIssue || "";
        currentProjectKey = currentProject || "";

        // Set form fields
        $("#ad-project").val(currentProjectKey);
        $("#ad-custom-jql").val("");

        if (currentIssueKey) {
            $("#ad-ticket-key-label").text(currentIssueKey);
            $("#ad-ticket-banner").show();
        } else {
            $("#ad-ticket-banner").hide();
        }

        renderSelectedStatuses();
        renderSelectedTypes();

        return { projectKey: currentProjectKey, issueKey: currentIssueKey };
    }

    function loadFilterOptions(projectKey) {
        isLoadingOptions = true;
        $("#ad-status-spinner, #ad-type-spinner, #ad-project-spinner").show();

        var url = BASE_REST_URL + "/options";
        if (projectKey) {
            url += "?project=" + encodeURIComponent(projectKey);
        }

        $.ajax({
            url: url,
            type: "GET",
            dataType: "json",
            cache: false,
            success: function (data) {
                isLoadingOptions = false;
                $("#ad-status-spinner, #ad-type-spinner, #ad-project-spinner").hide();

                if (data.projects) {
                    availableProjects = data.projects;
                }
                availableStatuses = data.statuses || [];
                availableIssueTypes = data.issueTypes || [];

                // Filter out any selected items that no longer exist in this project scheme
                selectedStatuses = selectedStatuses.filter(function (sel) {
                    return availableStatuses.some(function (avail) {
                        return String(avail.id) === String(sel.id) || avail.name.toLowerCase() === sel.name.toLowerCase();
                    });
                });

                selectedTypes = selectedTypes.filter(function (sel) {
                    return availableIssueTypes.some(function (avail) {
                        return String(avail.id) === String(sel.id) || avail.name.toLowerCase() === sel.name.toLowerCase();
                    });
                });

                renderSelectedStatuses();
                renderSelectedTypes();

                // Only refresh dropdown if user is actively typing in that field and dropdown is open
                if (isTypingProject && $("#ad-project").is(":focus") && $("#ad-project-dropdown").is(":visible")) {
                    showProjectDropdown($("#ad-project").val().trim());
                }
                if ($("#ad-status-search").is(":focus") && $("#ad-status-dropdown").is(":visible")) {
                    showStatusDropdown($("#ad-status-search").val().trim());
                }
                if ($("#ad-type-search").is(":focus") && $("#ad-type-dropdown").is(":visible")) {
                    showTypeDropdown($("#ad-type-search").val().trim());
                }
            },
            error: function (xhr) {
                isLoadingOptions = false;
                $("#ad-status-spinner, #ad-type-spinner, #ad-project-spinner").hide();
                console.error("Erreur lors du chargement des options de filtrage:", xhr);
            }
        });
    }

    function selectProject(key) {
        isTypingProject = false;
        clearTimeout(projectDebounceTimer);
        $("#ad-project-dropdown").hide();
        $("#ad-project").val(key).blur();
        // Clear previous project status/type selections
        selectedStatuses = [];
        selectedTypes = [];
        renderSelectedStatuses();
        renderSelectedTypes();
        $("#ad-preview-result").hide().empty();
        loadFilterOptions(key);
    }

    function closeOutsideDropdowns(e) {
        var $target = $(e.target);
        if ($target.closest(".ad-no-results").length) {
            $(".ad-dropdown").hide();
            return;
        }
        $(".ad-search-box").each(function () {
            var $box = $(this);
            if (!$target.closest($box.find(".ad-input-wrapper, .ad-dropdown")).length) {
                $box.find(".ad-dropdown").hide();
            }
        });
    }

    // ==========================================
    // File Formats / Extensions Selection
    // ==========================================
    function normalizeExt(raw) {
        if (!raw) return "";
        return String(raw).trim().replace(/^\.+/, "").toLowerCase().replace(/[^a-z0-9_-]/g, "");
    }

    function isExtensionSelected(ext) {
        var clean = normalizeExt(ext);
        return clean && selectedExtensions.indexOf(clean) !== -1;
    }

    function getAllKnownExtensions() {
        var list = PRESET_EXTENSIONS.slice();
        customExtensions.forEach(function (cExt) {
            if (!list.some(function (p) { return p.ext === cExt; })) {
                list.push({ ext: cExt, label: "." + cExt, category: "Personnalisé" });
            }
        });
        return list;
    }

    function toggleExtension(rawExt) {
        var ext = normalizeExt(rawExt);
        if (!ext) return;

        var idx = selectedExtensions.indexOf(ext);
        if (idx !== -1) {
            selectedExtensions.splice(idx, 1);
        } else {
            selectedExtensions.push(ext);
            if (!PRESET_EXTENSIONS.some(function (p) { return p.ext === ext; }) && customExtensions.indexOf(ext) === -1) {
                customExtensions.push(ext);
            }
        }
        renderExtensionsUI();
    }

    function addCustomExtensionsFromInput(rawInput, shouldCloseDropdown) {
        if (!rawInput) return;
        var parts = String(rawInput).split(/[,;\s]+/);
        var addedAny = false;

        parts.forEach(function (part) {
            var ext = normalizeExt(part);
            if (ext) {
                if (!PRESET_EXTENSIONS.some(function (p) { return p.ext === ext; }) && customExtensions.indexOf(ext) === -1) {
                    customExtensions.push(ext);
                }
                if (selectedExtensions.indexOf(ext) === -1) {
                    selectedExtensions.push(ext);
                }
                addedAny = true;
            }
        });

        $("#ad-extensions").val("");
        if (addedAny) {
            renderExtensionsUI();
        }

        if (shouldCloseDropdown) {
            $("#ad-extension-dropdown").hide();
            $("#ad-extensions").blur();
        } else {
            $("#ad-extension-dropdown").hide();
        }
    }

    function showExtensionDropdown(query) {
        $("#ad-project-dropdown, #ad-status-dropdown, #ad-type-dropdown").hide();
        var $dropdown = $("#ad-extension-dropdown").empty();
        var q = normalizeExt(query);

        if (!q) {
            $dropdown.hide();
            return;
        }

        var allKnown = getAllKnownExtensions();
        var matches = allKnown.filter(function (item) {
            return item.ext.indexOf(q) !== -1;
        });

        var exactExists = allKnown.some(function (item) {
            return item.ext === q;
        });

        // Option to add custom format if not an exact match or not yet in presets
        if (!exactExists) {
            var customHtml = [
                '<div class="ad-dropdown-item ad-ext-dropdown-item" data-ext="' + escapeHtml(q) + '">',
                '  <div class="ad-dropdown-item-content">',
                '    <span class="ad-tag-dot ad-dot-inprogress"></span>',
                '    <span>Ajouter le format spécifique <strong>.' + escapeHtml(q) + '</strong></span>',
                '  </div>',
                '  <span class="ad-item-category ad-category-inprogress">Nouveau format</span>',
                '</div>'
            ].join('');
            $dropdown.append(customHtml);
        }

        matches.forEach(function (item) {
            var selected = isExtensionSelected(item.ext);
            var actionLabel = selected ? "Sélectionné (cliquer pour retirer)" : item.category;
            var catClass = selected ? "ad-category-done" : "";
            var dotClass = selected ? "ad-dot-done" : "ad-dot-todo";
            var itemHtml = [
                '<div class="ad-dropdown-item ad-ext-dropdown-item" data-ext="' + escapeHtml(item.ext) + '">',
                '  <div class="ad-dropdown-item-content">',
                '    <span class="ad-tag-dot ' + dotClass + '"></span>',
                '    <span>.' + highlightMatch(item.ext, q) + '</span>',
                '  </div>',
                '  <span class="ad-item-category ' + catClass + '">' + escapeHtml(actionLabel) + '</span>',
                '</div>'
            ].join('');
            $dropdown.append(itemHtml);
        });

        $dropdown.show();
    }

    function renderExtensionsUI() {
        // 1. Render selected tags container
        var $tagsContainer = $("#ad-selected-extensions").empty();
        if (selectedExtensions.length === 0) {
            $tagsContainer.html('<span class="ad-empty-hint">Tous les formats sont inclus (aucun filtre)</span>');
            $("#ad-extension-clear").hide();
        } else {
            $("#ad-extension-clear").show();
            selectedExtensions.forEach(function (ext) {
                var tagHtml = [
                    '<span class="ad-tag ad-ext-tag" data-ext="' + escapeHtml(ext) + '" title="Cliquer pour désélectionner .' + escapeHtml(ext) + '">',
                    '  <span class="ad-tag-dot ad-dot-done"></span>',
                    '  <span class="ad-tag-name">.' + escapeHtml(ext) + '</span>',
                    '  <span class="ad-tag-remove ad-remove-ext" data-ext="' + escapeHtml(ext) + '" title="Retirer ce format">&times;</span>',
                    '</span>'
                ].join('');
                $tagsContainer.append(tagHtml);
            });
        }

        // 2. Render interactive preset + custom format pills
        var $pillsContainer = $("#ad-preset-extensions").empty();
        var allFormats = getAllKnownExtensions();
        allFormats.forEach(function (item) {
            var active = isExtensionSelected(item.ext);
            var activeClass = active ? " selected" : "";
            var iconHtml = active ? '<span class="ad-pill-check">&#10003;</span>' : '<span class="ad-pill-plus">+</span>';
            var pillHtml = [
                '<button type="button" class="ad-format-pill' + activeClass + '" data-ext="' + escapeHtml(item.ext) + '" aria-pressed="' + (active ? "true" : "false") + '" title="' + (active ? 'Désélectionner ' : 'Sélectionner ') + escapeHtml(item.label) + ' (' + escapeHtml(item.category) + ')">',
                '  ' + iconHtml,
                '  <span>' + escapeHtml(item.label) + '</span>',
                '</button>'
            ].join('');
            $pillsContainer.append(pillHtml);
        });
    }

    // ==========================================
    // Event Handlers and Interactions
    // ==========================================
    function bindDialogEvents() {
        // Toggle Advanced Download section
        $(document).on("click", "#ad-toggle-advanced", function (e) {
            e.preventDefault();
            setAdvancedMode(!isAdvancedOpen);
        });

        // Click on proposed format pill (toggle select / deselect)
        $(document).on("click", ".ad-format-pill", function (e) {
            e.preventDefault();
            var ext = $(this).data("ext");
            toggleExtension(ext);
        });

        // Click on selected extension tag or its remove button to deselect
        $(document).on("click", ".ad-remove-ext, .ad-ext-tag", function (e) {
            e.preventDefault();
            e.stopPropagation();
            var ext = $(this).data("ext");
            var idx = selectedExtensions.indexOf(normalizeExt(ext));
            if (idx !== -1) {
                selectedExtensions.splice(idx, 1);
                renderExtensionsUI();
            }
        });

        // Clear all selected extensions
        $(document).on("click", "#ad-link-clear-extensions", function (e) {
            e.preventDefault();
            selectedExtensions = [];
            renderExtensionsUI();
        });

        // Extension input typing
        $(document).on("input", "#ad-extensions", function () {
            var val = $(this).val();
            // If user typed a comma or semicolon, immediately add the token before it
            if (val.indexOf(",") !== -1 || val.indexOf(";") !== -1) {
                addCustomExtensionsFromInput(val, false);
                return;
            }
            showExtensionDropdown(val);
        });

        $(document).on("focus", "#ad-extensions", function () {
            $("#ad-project-dropdown, #ad-status-dropdown, #ad-type-dropdown").hide();
            var val = $(this).val().trim();
            if (val) {
                showExtensionDropdown(val);
            }
        });

        $(document).on("keydown", "#ad-extensions", function (e) {
            if (e.which === 13) { // Enter key
                e.preventDefault();
                var val = $(this).val().trim();
                if (val) {
                    addCustomExtensionsFromInput(val, true);
                }
            }
        });

        // Click "Ajouter" button for custom format
        $(document).on("click", "#ad-btn-add-ext", function (e) {
            e.preventDefault();
            var val = $("#ad-extensions").val().trim();
            if (val) {
                addCustomExtensionsFromInput(val, true);
            } else {
                $("#ad-extensions").focus();
            }
        });

        // Click on extension dropdown item
        $(document).on("mousedown", ".ad-ext-dropdown-item", function (e) {
            e.preventDefault();
            e.stopPropagation();
            var ext = $(this).data("ext");
            var totalVisible = $("#ad-extension-dropdown .ad-ext-dropdown-item").length;
            var isLastItem = $(this).is(":last-child") || totalVisible <= 1;

            toggleExtension(ext);
            $("#ad-extensions").val("");

            if (isLastItem) {
                $("#ad-extension-dropdown").hide();
                $("#ad-extensions").blur();
            } else {
                $("#ad-extension-dropdown").hide();
            }
        });

        // Project input typing (debounced live search & reload)
        $(document).on("input", "#ad-project", function () {
            isTypingProject = true;
            var query = $(this).val().trim();
            showProjectDropdown(query);

            clearTimeout(projectDebounceTimer);
            projectDebounceTimer = setTimeout(function () {
                loadFilterOptions(query);
            }, 400);
        });

        $(document).on("focus", "#ad-project", function () {
            $("#ad-status-dropdown, #ad-type-dropdown, #ad-extension-dropdown").hide();
            showProjectDropdown($(this).val().trim());
        });

        $(document).on("keydown", "#ad-project", function (e) {
            if (e.which === 13) { // Enter key
                e.preventDefault();
                var $firstItem = $("#ad-project-dropdown .ad-project-item").first();
                if ($firstItem.length && $("#ad-project-dropdown").is(":visible")) {
                    selectProject($firstItem.data("key"));
                } else {
                    selectProject($(this).val().trim());
                }
            }
        });

        // Click / Mousedown on project dropdown item
        $(document).on("mousedown", ".ad-project-item", function (e) {
            e.preventDefault();
            e.stopPropagation();
            var key = $(this).data("key");
            selectProject(key);
        });

        // Status search input typing
        $(document).on("input", "#ad-status-search", function () {
            var query = $(this).val();
            showStatusDropdown(query);
        });

        $(document).on("focus", "#ad-status-search", function () {
            $("#ad-project-dropdown, #ad-type-dropdown, #ad-extension-dropdown").hide();
            showStatusDropdown($(this).val());
        });

        $(document).on("keydown", "#ad-status-search", function (e) {
            if (e.which === 13) { // Enter key
                e.preventDefault();
                var $items = $("#ad-status-dropdown .ad-status-item");
                var $first = $items.first();
                if ($first.length && $("#ad-status-dropdown").is(":visible")) {
                    addStatusById($first.data("id"), $items.length <= 1);
                }
            }
        });

        // Click / Mousedown on status dropdown item
        $(document).on("mousedown", ".ad-status-item", function (e) {
            e.preventDefault();
            e.stopPropagation();
            var id = $(this).data("id");
            var totalVisible = $("#ad-status-dropdown .ad-status-item").length;
            var isLastItem = $(this).is(":last-child") || totalVisible <= 1;
            addStatusById(id, isLastItem);
        });

        // Remove status tag
        $(document).on("click", ".ad-remove-status", function (e) {
            e.stopPropagation();
            var id = $(this).data("id");
            selectedStatuses = selectedStatuses.filter(function (s) { return String(s.id) !== String(id); });
            renderSelectedStatuses();
        });

        // Clear all statuses
        $(document).on("click", "#ad-link-clear-statuses", function (e) {
            e.preventDefault();
            selectedStatuses = [];
            renderSelectedStatuses();
        });

        // Issue type search input typing
        $(document).on("input", "#ad-type-search", function () {
            var query = $(this).val();
            showTypeDropdown(query);
        });

        $(document).on("focus", "#ad-type-search", function () {
            $("#ad-project-dropdown, #ad-status-dropdown, #ad-extension-dropdown").hide();
            showTypeDropdown($(this).val());
        });

        $(document).on("keydown", "#ad-type-search", function (e) {
            if (e.which === 13) { // Enter key
                e.preventDefault();
                var $items = $("#ad-type-dropdown .ad-type-item");
                var $first = $items.first();
                if ($first.length && $("#ad-type-dropdown").is(":visible")) {
                    addTypeById($first.data("id"), $items.length <= 1);
                }
            }
        });

        // Click / Mousedown on issue type dropdown item
        $(document).on("mousedown", ".ad-type-item", function (e) {
            e.preventDefault();
            e.stopPropagation();
            var id = $(this).data("id");
            var totalVisible = $("#ad-type-dropdown .ad-type-item").length;
            var isLastItem = $(this).is(":last-child") || totalVisible <= 1;
            addTypeById(id, isLastItem);
        });

        // Remove issue type tag
        $(document).on("click", ".ad-remove-type", function (e) {
            e.stopPropagation();
            var id = $(this).data("id");
            selectedTypes = selectedTypes.filter(function (t) { return String(t.id) !== String(id); });
            renderSelectedTypes();
        });

        // Clear all types
        $(document).on("click", "#ad-link-clear-types", function (e) {
            e.preventDefault();
            selectedTypes = [];
            renderSelectedTypes();
        });

        // Close dropdowns when clicking any space in the popup or outside the active input/dropdown
        $(document).on("mousedown click", "#attachments-download-dialog", function (e) {
            closeOutsideDropdowns(e);
        });

        $(document).on("mousedown click", function (e) {
            closeOutsideDropdowns(e);
        });

        // Close open dropdowns on Escape key
        $(document).on("keydown", "#attachments-download-dialog", function (e) {
            if (e.which === 27 && $(".ad-dropdown:visible").length) {
                e.stopPropagation();
                $(".ad-dropdown").hide();
                $("#ad-project, #ad-status-search, #ad-type-search, #ad-extensions").blur();
            }
        });

        // Close dialog
        $(document).on("click", "#ad-btn-close, .aui-dialog2-header-close", function () {
            $(".ad-dropdown").hide();
            AJS.dialog2("#attachments-download-dialog").hide();
        });

        // Preview button
        $(document).on("click", "#ad-btn-preview", function () {
            $(".ad-dropdown").hide();
            executePreview();
        });

        // Download button
        $(document).on("click", "#ad-btn-download", function () {
            $(".ad-dropdown").hide();
            executeDownload();
        });
    }

    // ==========================================
    // Project Autocompletion Dropdown
    // ==========================================
    function showProjectDropdown(query) {
        $("#ad-status-dropdown, #ad-type-dropdown, #ad-extension-dropdown").hide();
        var $dropdown = $("#ad-project-dropdown").empty();

        if (isLoadingOptions && (!availableProjects || availableProjects.length === 0)) {
            $dropdown.html('<div class="ad-no-results"><span class="aui-icon aui-icon-wait"></span> Chargement des projets accessibles...</div>').show();
            return;
        }

        if (!availableProjects || availableProjects.length === 0) {
            $dropdown.html('<div class="ad-no-results">Aucun projet accessible trouvé</div>').show();
            return;
        }

        var q = (query || "").toLowerCase().trim();
        var matches = availableProjects.filter(function (p) {
            return !q || p.key.toLowerCase().indexOf(q) !== -1 || p.name.toLowerCase().indexOf(q) !== -1;
        });

        if (matches.length === 0) {
            $dropdown.html('<div class="ad-no-results">Aucun projet accessible correspondant à "' + escapeHtml(query) + '"</div>').show();
            return;
        }

        matches.slice(0, 20).forEach(function (p) {
            var itemHtml = [
                '<div class="ad-dropdown-item ad-project-item" data-key="' + escapeHtml(p.key) + '">',
                '  <div class="ad-dropdown-item-content">',
                '    <strong class="ad-project-key">' + highlightMatch(p.key, q) + '</strong>',
                '    <span class="ad-project-name"> - ' + highlightMatch(p.name, q) + '</span>',
                '  </div>',
                '  <span class="ad-item-category">Projet</span>',
                '</div>'
            ].join('');
            $dropdown.append(itemHtml);
        });

        $dropdown.show();
    }

    // ==========================================
    // Status Search Dropdown & Tags
    // ==========================================
    function isStatusSelected(status) {
        return selectedStatuses.some(function (s) { return String(s.id) === String(status.id); });
    }

    function addStatusById(id, shouldClose) {
        var statusObj = availableStatuses.find(function (s) { return String(s.id) === String(id); });
        if (statusObj && !isStatusSelected(statusObj)) {
            selectedStatuses.push(statusObj);
            renderSelectedStatuses();
        }
        $("#ad-status-search").val("");
        if (shouldClose || isAllStatusesSelected()) {
            $("#ad-status-dropdown").hide();
            $("#ad-status-search").blur();
        } else {
            $("#ad-status-search").focus();
            showStatusDropdown("");
        }
    }

    function showStatusDropdown(query) {
        $("#ad-project-dropdown, #ad-type-dropdown, #ad-extension-dropdown").hide();
        var $dropdown = $("#ad-status-dropdown").empty();
        var q = (query || "").toLowerCase().trim();

        var matches = availableStatuses.filter(function (s) {
            return !isStatusSelected(s) && (!q || s.name.toLowerCase().indexOf(q) !== -1);
        });

        if (matches.length === 0) {
            var msg = availableStatuses.length === 0
                ? (isLoadingOptions ? "Chargement des statuts du projet..." : "Aucun statut trouvé pour ce projet")
                : (isAllStatusesSelected() ? "Tous les statuts disponibles sont déjà sélectionnés" : "Aucun statut correspondant");
            $dropdown.html('<div class="ad-no-results">' + msg + '</div>').show();
            return;
        }

        matches.forEach(function (st) {
            var catClass = getCategoryClass(st.category);
            var dotClass = getCategoryDotClass(st.category);
            var itemHtml = [
                '<div class="ad-dropdown-item ad-status-item" data-id="' + escapeHtml(st.id) + '">',
                '  <div class="ad-dropdown-item-content">',
                '    <span class="ad-tag-dot ' + dotClass + '"></span>',
                '    <span>' + highlightMatch(st.name, q) + '</span>',
                '  </div>',
                '  <span class="ad-item-category ' + catClass + '">' + escapeHtml(st.category || "Statut") + '</span>',
                '</div>'
            ].join('');
            $dropdown.append(itemHtml);
        });

        $dropdown.show();
    }

    function isAllStatusesSelected() {
        return availableStatuses.length > 0 && selectedStatuses.length >= availableStatuses.length;
    }

    function renderSelectedStatuses() {
        var $container = $("#ad-selected-statuses").empty();
        if (selectedStatuses.length === 0) {
            $container.html('<span class="ad-empty-hint">Tous les statuts du projet sont inclus (aucun filtre)</span>');
            $("#ad-status-clear").hide();
            return;
        }

        $("#ad-status-clear").show();
        selectedStatuses.forEach(function (st) {
            var dotClass = getCategoryDotClass(st.category);
            var tagHtml = [
                '<span class="ad-tag" data-id="' + escapeHtml(st.id) + '">',
                '  <span class="ad-tag-dot ' + dotClass + '"></span>',
                '  <span class="ad-tag-name">' + escapeHtml(st.name) + '</span>',
                '  <span class="ad-tag-remove ad-remove-status" data-id="' + escapeHtml(st.id) + '" title="Retirer ce statut">&times;</span>',
                '</span>'
            ].join('');
            $container.append(tagHtml);
        });
    }

    // ==========================================
    // Issue Type Search Dropdown & Tags
    // ==========================================
    function isTypeSelected(type) {
        return selectedTypes.some(function (t) { return String(t.id) === String(type.id); });
    }

    function addTypeById(id, shouldClose) {
        var typeObj = availableIssueTypes.find(function (t) { return String(t.id) === String(id); });
        if (typeObj && !isTypeSelected(typeObj)) {
            selectedTypes.push(typeObj);
            renderSelectedTypes();
        }
        $("#ad-type-search").val("");
        if (shouldClose || isAllTypesSelected()) {
            $("#ad-type-dropdown").hide();
            $("#ad-type-search").blur();
        } else {
            $("#ad-type-search").focus();
            showTypeDropdown("");
        }
    }

    function showTypeDropdown(query) {
        $("#ad-project-dropdown, #ad-status-dropdown, #ad-extension-dropdown").hide();
        var $dropdown = $("#ad-type-dropdown").empty();
        var q = (query || "").toLowerCase().trim();

        var matches = availableIssueTypes.filter(function (t) {
            return !isTypeSelected(t) && (!q || t.name.toLowerCase().indexOf(q) !== -1);
        });

        if (matches.length === 0) {
            var msg = availableIssueTypes.length === 0
                ? (isLoadingOptions ? "Chargement des types de tickets..." : "Aucun type de ticket configuré pour ce projet")
                : (isAllTypesSelected() ? "Tous les types disponibles sont déjà sélectionnés" : "Aucun type de ticket correspondant");
            $dropdown.html('<div class="ad-no-results">' + msg + '</div>').show();
            return;
        }

        matches.forEach(function (it) {
            var dotClass = it.subTask ? "ad-dot-subtask" : "ad-dot-standard";
            var badgeText = it.subTask ? "Sous-tâche" : "Standard";
            var itemHtml = [
                '<div class="ad-dropdown-item ad-type-item" data-id="' + escapeHtml(it.id) + '">',
                '  <div class="ad-dropdown-item-content">',
                '    <span class="ad-tag-dot ' + dotClass + '"></span>',
                '    <span>' + highlightMatch(it.name, q) + '</span>',
                '  </div>',
                '  <span class="ad-item-category">' + badgeText + '</span>',
                '</div>'
            ].join('');
            $dropdown.append(itemHtml);
        });

        $dropdown.show();
    }

    function isAllTypesSelected() {
        return availableIssueTypes.length > 0 && selectedTypes.length >= availableIssueTypes.length;
    }

    function renderSelectedTypes() {
        var $container = $("#ad-selected-types").empty();
        if (selectedTypes.length === 0) {
            $container.html('<span class="ad-empty-hint">Tous les types de tickets du projet sont inclus (aucun filtre)</span>');
            $("#ad-type-clear").hide();
            return;
        }

        $("#ad-type-clear").show();
        selectedTypes.forEach(function (it) {
            var dotClass = it.subTask ? "ad-dot-subtask" : "ad-dot-standard";
            var subtaskBadge = it.subTask ? '<span class="ad-tag-badge">Sous-tâche</span>' : '';
            var tagHtml = [
                '<span class="ad-tag" data-id="' + escapeHtml(it.id) + '">',
                '  <span class="ad-tag-dot ' + dotClass + '"></span>',
                '  <span class="ad-tag-name">' + escapeHtml(it.name) + '</span>',
                subtaskBadge,
                '  <span class="ad-tag-remove ad-remove-type" data-id="' + escapeHtml(it.id) + '" title="Retirer ce type">&times;</span>',
                '</span>'
            ].join('');
            $container.append(tagHtml);
        });
    }

    // ==========================================
    // Form Criteria & Actions
    // ==========================================
    function collectFormCriteria() {
        // Also capture any custom format typed in the input field even if the user didn't press Enter
        var pendingExtInput = $("#ad-extensions").val().trim();
        if (pendingExtInput) {
            addCustomExtensionsFromInput(pendingExtInput, true);
        }

        var extensions = selectedExtensions.join(",");
        var grouping = $("#ad-grouping").val();

        // When in simple mode on a specific ticket, target that ticket directly
        if (!isAdvancedOpen && currentIssueKey) {
            return {
                project: currentProjectKey,
                status: "",
                issueType: "",
                extension: extensions,
                grouping: grouping,
                jql: 'key = "' + currentIssueKey + '"'
            };
        }

        var project = $("#ad-project").val().trim();
        var customJql = $("#ad-custom-jql").val().trim();

        var statusNames = selectedStatuses.map(function (s) { return s.name; });
        var typeNames = selectedTypes.map(function (t) { return t.name; });

        return {
            project: project,
            status: statusNames.join(","),
            issueType: typeNames.join(","),
            extension: extensions,
            grouping: grouping,
            jql: customJql
        };
    }

    function buildQueryString(criteria) {
        var params = [];
        if (criteria.project) params.push("project=" + encodeURIComponent(criteria.project));
        if (criteria.status) params.push("status=" + encodeURIComponent(criteria.status));
        if (criteria.issueType) params.push("issueType=" + encodeURIComponent(criteria.issueType));
        if (criteria.extension) params.push("extension=" + encodeURIComponent(criteria.extension));
        if (criteria.grouping) params.push("grouping=" + encodeURIComponent(criteria.grouping));
        if (criteria.jql) params.push("jql=" + encodeURIComponent(criteria.jql));
        return params.join("&");
    }

    function executePreview() {
        var criteria = collectFormCriteria();
        var qs = buildQueryString(criteria);
        var $resultBox = $("#ad-preview-result");

        $resultBox.show().html('<div class="aui-message aui-message-wait"><p><span class="aui-icon aui-icon-wait"></span> Analyse des tickets et pièces jointes en cours...</p></div>');

        $.ajax({
            url: BASE_REST_URL + "/preview?" + qs,
            type: "GET",
            dataType: "json",
            cache: false,
            success: function (data) {
                var totalIssues = data.totalIssues || 0;
                var totalFiles = data.totalAttachments || 0;
                var totalSize = data.formattedTotalSize || "0 B";

                if (totalFiles === 0) {
                    $resultBox.html('<div class="aui-message aui-message-warning"><p>Aucune pièce jointe ne correspond aux critères sélectionnés.</p></div>');
                    return;
                }

                var html = [
                    '<div class="aui-message aui-message-success">',
                    '  <p class="title"><strong>Résultat de la prévisualisation :</strong></p>',
                    '  <p><strong>' + totalIssues + '</strong> ticket(s) • <strong>' + totalFiles + '</strong> pièce(s) jointe(s) • Volume total estimé : <strong>' + totalSize + '</strong></p>',
                    '</div>'
                ].join('\n');

                $resultBox.html(html);
            },
            error: function (xhr) {
                var errorMsg = "Erreur lors de la prévisualisation";
                try {
                    var res = JSON.parse(xhr.responseText);
                    if (res.error) errorMsg = res.error;
                } catch (e) {
                }
                $resultBox.html('<div class="aui-message aui-message-error"><p>' + escapeHtml(errorMsg) + '</p></div>');
            }
        });
    }

    function executeDownload() {
        var criteria = collectFormCriteria();
        var qs = buildQueryString(criteria);
        var downloadUrl = BASE_REST_URL + "/zip?" + qs;

        // Trigger browser download directly
        window.location.href = downloadUrl;
    }

    // ==========================================
    // Helpers
    // ==========================================
    function getCategoryClass(category) {
        if (category === "Done") return "ad-category-done";
        if (category === "In Progress") return "ad-category-inprogress";
        return "ad-category-todo";
    }

    function getCategoryDotClass(category) {
        if (category === "Done") return "ad-dot-done";
        if (category === "In Progress") return "ad-dot-inprogress";
        return "ad-dot-todo";
    }

    function highlightMatch(text, query) {
        if (!text) return "";
        if (!query) return escapeHtml(text);
        var idx = text.toLowerCase().indexOf(query.toLowerCase());
        if (idx === -1) return escapeHtml(text);

        var before = text.substring(0, idx);
        var match = text.substring(idx, idx + query.length);
        var after = text.substring(idx + query.length);

        return escapeHtml(before) + '<span class="ad-dropdown-highlight">' + escapeHtml(match) + '</span>' + escapeHtml(after);
    }

    function escapeHtml(text) {
        if (!text) return "";
        return $('<div>').text(text).html();
    }

})(AJS.$ || jQuery);
