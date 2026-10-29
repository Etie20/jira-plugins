// Plugin PoC Migration - Hello World Popup
// Utilise AUI (Atlassian User Interface) Dialog2 - composant officiel Jira

AJS.toInit(function () {

    // Injecter la structure HTML du dialog dans la page
    var dialogHtml = [
        '<section id="poc-migration-dialog" class="aui-dialog2 aui-dialog2-medium aui-layer" role="dialog" aria-hidden="true">',
        '  <header class="aui-dialog2-header">',
        '    <h2 class="aui-dialog2-header-main">',
        '      <span class="aui-icon aui-icon-small aui-iconfont-success"></span>',
        '      Plugin PoC - Prêt pour la migration',
        '    </h2>',
        '    <a class="aui-dialog2-header-close">',
        '      <span class="aui-icon aui-icon-small aui-iconfont-close-dialog">Fermer</span>',
        '    </a>',
        '  </header>',
        '  <div class="aui-dialog2-content">',
        '    <div class="aui-message aui-message-success">',
        '      <p><strong>✅ Prêt à intégrer les plugins !</strong></p>',
        '    </div>',
        '    <p>Ce plugin a été développé dans le cadre de la scission du groupe bancaire.</p>',
        '    <p>Il démontre notre capacité à :</p>',
        '    <ul>',
        '      <li>Créer et déployer un plugin Jira Data Center</li>',
        '      <li>Injecter des éléments dans l\'interface utilisateur</li>',
        '      <li>Gérer les workflows et plugins lors de la migration</li>',
        '    </ul>',
        '    <hr />',
        '    <p style="color: #707070; font-size: 12px;">Version 1.0.0-SNAPSHOT &nbsp;|&nbsp; Jira Data Center 10.3.5 → 11.3.7</p>',
        '  </div>',
        '  <footer class="aui-dialog2-footer">',
        '    <div class="aui-dialog2-footer-actions">',
        '      <button id="poc-dialog-close-btn" class="aui-button aui-button-primary">Compris !</button>',
        '    </div>',
        '  </footer>',
        '</section>'
    ].join('');

    AJS.$('body').append(dialogHtml);

    // Ouvrir le popup quand on clique sur le bouton dans la nav
    AJS.$(document).on('click', 'a.poc-migration-trigger, #poc-migration-hello-link', function (e) {
        e.preventDefault();
        AJS.dialog2('#poc-migration-dialog').show();
    });

    // Fermer le popup avec le bouton "Compris !" et la croix
    AJS.$(document).on('click', '#poc-dialog-close-btn, #poc-migration-dialog .aui-dialog2-header-close', function (e) {
        e.preventDefault();
        AJS.dialog2('#poc-migration-dialog').hide();
    });

});
