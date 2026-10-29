# Documentation Technique et Fonctionnelle : Plugin `jira-clarification-loop`

**Auteur :** Etienne FOYANG - Département Projet et Innovation (DPI)  
**Cible :** Jira Data Center 10.3.5 (Java 17 / Atlassian Plugin SDK 9.11.2)  
**Clé du plugin :** `com.mabanque.jira.plugins.jira-clarification-loop`  
**Version :** `1.0.0-SNAPSHOT`

---

## 1. Contexte et expression du besoin

### 1.1 Le besoin métier
Dans le traitement quotidien des tickets Jira au sein de la banque, l'intervenant à qui un ticket est assigné a fréquemment besoin d'obtenir une précision, une validation ponctuelle ou une pièce justificative auprès d'un autre collaborateur avant de pouvoir poursuivre son instruction.

Le fonctionnement attendu est une **boucle de clarification aller-retour** :
1. **Demande :** L'assigné actuel (Utilisateur A) sollicite un collaborateur (Utilisateur B) en précisant son commentaire et en joignant éventuellement un document. Le ticket est alors **automatiquement assigné à B**.
2. **Réponse :** L'Utilisateur B fournit sa réponse (et joint éventuellement le document demandé). Dès l'envoi de sa réponse, le ticket est **automatiquement réassigné à l'Utilisateur A** pour qu'il reprenne son traitement.
3. **Annulation :** Tant que B n'a pas répondu, l'Utilisateur A peut annuler sa demande et récupérer immédiatement le ticket.

### 1.2 Limites de l'approche historique (scripts SIL / Power Scripts)
L'implémentation initiale par scripts SIL présentait plusieurs contraintes d'exploitation et de maintenance :
- Obligation de créer et maintenir **4 champs personnalisés techniques** (`previousAssignee`, `clarificationAuthor`, `clarificationQuestion`, `clarificationAnswer`).
- Nécessité de modifier **chaque workflow** pour y ajouter deux transitions dédiées, deux écrans de transition et des scripts de validation / post-fonctions.
- Forte adhérence aux projets : toute évolution devait être répercutée manuellement sur les workflows concernés.

### 1.3 Apports du plugin Java natif (`jira-clarification-loop`)
Le plugin `jira-clarification-loop` remplace cette mécanique par un composant transverse :
- **Zéro modification de workflow ni champ personnalisé :** un panneau latéral droit (`Demandes de précision`) s'affiche nativement sur la vue de tous les tickets Jira.
- **Stockage dédié et autonome :** les échanges sont persistés au format JSON via l'API `PluginSettingsFactory` (SAL) de Jira, tout en doublant chaque action d'un commentaire horodaté natif sur le ticket.
- **Recherche globale d'utilisateurs :** sélection du destinataire via l'API native `/rest/api/2/user/picker` avec autocomplétion.
- **Pièces jointes intégrées :** dépôt direct de fichiers lors de la demande ou de la réponse via l'API native `/rest/api/2/issue/{issueKey}/attachments`.
- **Contrôle strict des habilitations :** vérifications appliquées à la fois dans l'interface et côté serveur REST.
- **Ergonomie soignée :** libellés simplifiés (`Destinataire`, `Commentaire`, `Pièce jointe`) et affichage des erreurs directement sous les champs du formulaire (aucune alerte navigateur `alert()`).

---

## 2. Règles de gestion et matrice des habilitations

Le plugin calcule dynamiquement les droits de l'utilisateur connecté à chaque chargement du ticket via la classe `ClarificationPermissions` et les vérifie systématiquement côté serveur dans `ClarificationServiceImpl`.

| Action | Acteur autorisé | Conditions préalables et contrôles métier | Effets produits |
| :--- | :--- | :--- | :--- |
| **Demander une précision** | **Uniquement l'assigné actuel** du ticket | - Utilisateur connecté<br>- Ticket non clôturé (sans résolution)<br>- Aucune demande déjà en statut `PENDING`<br>- Destinataire existant, actif, différent de l'auteur et disposant de la permission `BROWSE_PROJECTS` sur le ticket<br>- Commentaire non vide | - Création d'une entrée `PENDING`<br>- Ajout d'un commentaire tracé sur le ticket (avec lien vers la pièce jointe éventuelle)<br>- **Assignation du ticket au destinataire** |
| **Répondre à la demande** | **Uniquement le destinataire** de la demande en attente | - Utilisateur connecté<br>- Demande en statut `PENDING`<br>- Utilisateur connecté = `recipientKey` de la demande<br>- Commentaire de réponse non vide | - Passage de l'entrée au statut `ANSWERED`<br>- Ajout d'un commentaire de réponse sur le ticket<br>- **Réassignation automatique du ticket à l'auteur initial (`authorKey`)** |
| **Annuler la demande** | **Uniquement l'auteur** de la demande en attente | - Utilisateur connecté<br>- Demande en statut `PENDING`<br>- Utilisateur connecté = `authorKey` de la demande | - Passage de l'entrée au statut `CANCELLED`<br>- Ajout d'un commentaire d'annulation sur le ticket<br>- **Réassignation du ticket à l'auteur initial** |
| **Consulter l'historique** | **Tout utilisateur** ayant le droit de voir le ticket | - Permission `BROWSE_PROJECTS` sur le ticket | - Lecture de tous les échanges passés et en cours<br>- Affichage d'un bandeau informatif expliquant pourquoi aucune action n'est disponible si l'utilisateur n'est pas l'acteur attendu |

---

## 3. Architecture et structure du projet

```text
C:\Dev\JiraPlugins\jira-clarification-loop\
├── pom.xml                                      # Configuration Maven, Jira 10.3.5, Spring Scanner, OSGi Gson
└── src/
    └── main/
        ├── java/com/mabanque/jira/plugins/clarification/
        │   ├── api/
        │   │   ├── ClarificationEntry.java          # Modèle de données d'un échange (PENDING / ANSWERED / CANCELLED)
        │   │   ├── ClarificationException.java      # Exception métier typée (message, champ ciblé, statut HTTP)
        │   │   ├── ClarificationPermissions.java    # DTO des droits calculés pour l'utilisateur courant
        │   │   └── ClarificationService.java        # Interface du service métier
        │   ├── impl/
        │   │   └── ClarificationServiceImpl.java    # Implémentation métier, permissions, persistance, assignation
        │   └── rest/
        │       └── ClarificationRestResource.java   # Endpoints JAX-RS (/rest/clarification/1.0/issue/{key})
        └── resources/
            ├── atlassian-plugin.xml                 # Descripteur des modules (web-resource, web-panel, rest)
            ├── css/
            │   └── clarification.css                # Styles du panneau, de l'autocomplétion et des erreurs en ligne
            ├── i18n/
            │   └── jira-clarification-loop.properties
            ├── js/
            │   └── clarification.js                 # Logique UI, modales AUI Dialog2, recherche user, upload fichier
            └── templates/
                └── clarification-panel.vm           # Template Velocity du panneau latéral et des modales AUI
```

---

## 4. Construction pas à pas (Step-by-Step)

### Étape 1 : Initialisation et configuration Maven / OSGi (`pom.xml`)

Le projet a été généré via `atlas-create-jira-plugin` puis configuré dans `pom.xml` pour cibler **Jira Data Center 10.3.5** et **Java 17** :

- **Propriétés principales :**
  ```xml
  <properties>
      <jira.version>10.3.5</jira.version>
      <amps.version>8.1.2</amps.version>
      <atlassian.spring.scanner.version>2.2.4</atlassian.spring.scanner.version>
      <maven.compiler.source>17</maven.compiler.source>
      <maven.compiler.target>17</maven.compiler.target>
  </properties>
  ```
- **Dépendances Spring (`provided`) :** ajout de `org.springframework:spring-beans` et `org.springframework:spring-context` pour la compilation des annotations d'injection.
- **Gestion de l'isolation OSGi DMZ de Jira 10 pour `Gson` :**
  Depuis Jira 10, le package `com.google.gson` est considéré comme un package interne de Jira et ne peut plus être importé via OSGi par un plugin tiers. Pour éviter l'erreur `Package com.google.gson is internal and is not available for export to plugin`, `gson` est embarqué directement en mode *inline* dans le `.jar` du plugin :
  ```xml
  <banningExcludes>
      <exclude>com.google.code.gson:gson</exclude>
  </banningExcludes>
  <instructions>
      <Atlassian-Plugin-Key>${atlassian.plugin.key}</Atlassian-Plugin-Key>
      <Export-Package>com.mabanque.jira.plugins.clarification.api</Export-Package>
      <Import-Package>
          !com.google.gson.*,
          org.springframework.osgi.*;resolution:="optional",
          org.eclipse.gemini.blueprint.*;resolution:="optional",
          *
      </Import-Package>
      <Embed-Dependency>gson;scope=compile|runtime;inline=true</Embed-Dependency>
      <Spring-Context>*</Spring-Context>
  </instructions>
  ```

---

### Étape 2 : Déclaration des modules (`atlassian-plugin.xml`)

Le fichier `src/main/resources/atlassian-plugin.xml` déclare trois modules :

1. **`<web-resource key="clarification-resources-v3">` :**
   Charge `clarification.js` et `clarification.css` sur le contexte `jira.view.issue` (vue d'un ticket), avec les dépendances AUI (`com.atlassian.auiplugin:ajs`, `com.atlassian.auiplugin:aui-dialog2`, `com.atlassian.auiplugin:aui-lozenge`).
2. **`<web-panel key="clarification-panel" location="atl.jira.view.issue.right.context">` :**
   Affiche le panneau **Demandes de précision** dans la colonne latérale droite de chaque ticket à partir du template Velocity `templates/clarification-panel.vm`.
3. **`<rest key="clarification-rest" path="/clarification" version="1.0">` :**
   Expose les services REST du plugin sous `/rest/clarification/1.0/...`.

---

### Étape 3 : Couche métier et persistance (`api/` et `impl/`)

#### 3.1 Modèles et contrat (`api/`)
- **`ClarificationEntry` :** représente un échange avec :
  - `id` (UUID), `issueKey`, `status` (`PENDING`, `ANSWERED`, `CANCELLED`),
  - `authorKey`, `authorName`, `recipientKey`, `recipientName`, `previousAssigneeKey`,
  - `question`, `questionDate`, `answer`, `answerDate`.
- **`ClarificationPermissions` :** indique à l'interface ce que l'utilisateur connecté a le droit de faire :
  - `canRequest`, `canReply`, `canCancel`, `hasPending`, `reasonMessage`, `currentUsername`, `assigneeUsername`, `assigneeDisplayName`.
- **`ClarificationException` :** exception métier portant un code HTTP (`400`, `401`, `403`, `404`) et le nom du champ en erreur (`recipient`, `question`, `answer`, `general`) afin que l'interface affiche l'erreur exactement au bon endroit dans le formulaire.

#### 3.2 Implémentation (`ClarificationServiceImpl.java`)
Annotée avec `@ExportAsService({ClarificationService.class})` et `@Named("clarificationService")`, la classe reçoit par injection (`@Inject` + `@ComponentImport`) :
- `PluginSettingsFactory` : stockage clé-valeur persistant de la liste des échanges par ticket (`com.mabanque.jira.clarification.v1:issue.{ISSUE_KEY}`).
- `IssueManager` : récupération du `MutableIssue`, mise à jour de l'assigné (`issue.setAssignee(targetUser)`) et sauvegarde sans déclencher de transition de workflow (`issueManager.updateIssue(..., EventDispatchOption.ISSUE_ASSIGNED, false)`).
- `CommentManager` : publication automatique d'un commentaire formaté Wiki Jira lors de chaque demande, réponse ou annulation.
- `UserManager` : recherche des utilisateurs par `username` ou `userKey` et vérification que le compte est actif (`user.isActive()`).
- `PermissionManager` : vérification que le destinataire possède la permission `ProjectPermissions.BROWSE_PROJECTS` sur le ticket avant de lui assigner la demande.

---

### Étape 4 : Exposition de l'API REST (`ClarificationRestResource.java`)

La classe `ClarificationRestResource` expose quatre endpoints JSON sous `/rest/clarification/1.0/issue` :

| Méthode | URL | Description | Corps de requête (JSON) |
| :--- | :--- | :--- | :--- |
| `GET` | `/rest/clarification/1.0/issue/{issueKey}` | Retourne l'historique des échanges (`entries`) et les droits de l'utilisateur courant (`permissions`). | - |
| `POST` | `/rest/clarification/1.0/issue/{issueKey}/request` | Crée une demande de précision (ou valide uniquement le formulaire si `validateOnly: true`). | `{ "recipientUsername": "...", "question": "...", "validateOnly": false }` |
| `POST` | `/rest/clarification/1.0/issue/{issueKey}/reply` | Enregistre la réponse du destinataire et réassigne le ticket à l'auteur initial. | `{ "answer": "...", "validateOnly": false }` |
| `POST` | `/rest/clarification/1.0/issue/{issueKey}/cancel` | Annule la demande en cours (par son auteur) et réassigne le ticket à l'auteur. | `{}` |

En cas d'erreur métier (`ClarificationException`), l'API renvoie une réponse JSON structurée :
```json
{
  "error": "L'utilisateur sélectionné n'a pas accès à ce ticket / projet.",
  "field": "recipient"
}
```

---

### Étape 5 : Interface utilisateur (`clarification-panel.vm`, `clarification.js`, `clarification.css`)

1. **Template Velocity (`clarification-panel.vm`) :**
   - Génère le conteneur du panneau `#clarification-panel-root` ainsi que les deux fenêtres modales AUI Dialog2 (`#clr-request-dialog` et `#clr-reply-dialog`).
   - Les formulaires utilisent des libellés simples et directs : **Destinataire**, **Commentaire**, **Pièce jointe**.
   - Chaque modale contient une bannière d'erreur générale et des emplacements d'erreur sous chaque champ (`#clr-req-recipient-error`, `#clr-req-question-error`, `#clr-req-file-error`, `#clr-rep-answer-error`, `#clr-rep-file-error`).

2. **Logique JavaScript (`clarification.js`) :**
   - **Gestion du cache DOM AUI Dialog2 (`moveDialogToBody`) :** AUI Dialog2 déplace les modales à la racine de `<body>`. Lors des navigations AJAX entre tickets, le script remplace systématiquement toute ancienne instance présente dans `<body>` par le nouveau DOM Velocity.
   - **Recherche globale d'utilisateurs :** le champ `#clr-recipient-search` interroge `/rest/api/2/user/picker?query=...&maxResults=10` avec un *debounce* de 250 ms, affiche une liste déroulante avec avatar, nom complet et identifiant, et enregistre l'identifiant sélectionné.
   - **Validation en 2 temps avec pièce jointe :**
     Lorsque l'utilisateur joint un fichier, le script appelle d'abord l'endpoint REST avec `validateOnly: true`. Si les règles métier sont respectées, le fichier est envoyé en `multipart/form-data` vers `/rest/api/2/issue/{issueKey}/attachments` (avec l'en-tête `X-Atlassian-Token: no-check`), puis la référence Jira (`[Document joint : ^fichier.ext]`) est ajoutée au commentaire avant l'enregistrement final.
   - **Affichage des erreurs en ligne :** aucune utilisation de `alert()` navigateur ; toutes les erreurs sont affichées en rouge dans la modale sur le champ concerné.

---

## 5. Commandes de build, d'exécution et de déploiement

### 5.1 Compiler et générer le livrable `.jar`
```powershell
Set-Location "C:\Dev\JiraPlugins\jira-clarification-loop"
& "C:\PROGRA~1\atlassian-plugin-sdk-9.11.2\bin\atlas-package.bat" -DskipTests
```
Le fichier généré se trouve dans :
`C:\Dev\JiraPlugins\jira-clarification-loop\target\jira-clarification-loop-1.0.0-SNAPSHOT.jar`

### 5.2 Lancer l'instance Jira locale de développement
```powershell
Set-Location "C:\Dev\JiraPlugins\jira-clarification-loop"
& "C:\PROGRA~1\atlassian-plugin-sdk-9.11.2\bin\atlas-run.bat"
```
- **URL locale :** `http://localhost:2990/jira`
- **Identifiants par défaut :** `admin` / `admin`
- **Rechargement à chaud (QuickReload) :** lorsque `atlas-run` tourne, relancer `atlas-package.bat -DskipTests` dans un second terminal met à jour automatiquement le plugin dans Jira en quelques secondes sans redémarrer le serveur.

### 5.3 Déployer sur Jira Data Center (Recette / Production)
1. Se connecter à Jira Data Center avec un compte administrateur.
2. Ouvrir **Administration (icône engrenage) > Gérer les applications** (*Manage apps*).
3. Cliquer sur **Importer une application** (*Upload app*) et sélectionner `jira-clarification-loop-1.0.0-SNAPSHOT.jar`.
4. Le plugin est automatiquement déployé et activé à chaud sur tous les nœuds du cluster Data Center.
