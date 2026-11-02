# Jira Attachments Download Plugin (Jira Data Center 10.x)

Plugin officiel pour **Jira Data Center (10.x / Platform 7)** permettant d'exporter et de télécharger en masse les pièces jointes des tickets sous forme d'archives ZIP structurées, avec filtrage multicritère (projet, statut, type de ticket, JQL, extension, taille) et classification hiérarchique.

---

## 🚀 Fonctionnalités Clés

1. **Architecture Cluster-Safe Jira Data Center** :
   - Streaming direct des flux via `AttachmentManager.streamAttachmentContent(...)` sans chargement en mémoire tampon ni saturation du heap (supporte le stockage partagé NFS / AWS S3).
   - Contrôle strict des permissions : vérification de `ProjectPermissions.BROWSE_PROJECTS` pour chaque ticket via `PermissionManager`.

2. **Filtrage Multicritère** :
   - Par **projet** (`project=PRJ`)
   - Par **statut** (`status=Done`, `status="In Progress"`)
   - Par **type de ticket** (`issueType=Bug`, `issueType=Story`)
   - Par **requête JQL personnalisée** (`jql=...`)
   - Par **extension de fichier** (`extension=pdf,png,docx`)
   - Par **taille** (`minSize=1024`, `maxSize=10485760`)
   - Limite de sécurité configurable (`maxIssues=200`, max 1000)

3. **Organisation & Classement Hiérarchique de l'Archive** :
   - `BY_PROJECT_AND_ISSUE` (Défaut) : `[PROJET]/[CLE-TICKET] - [Titre]/[nom_fichier]`
   - `BY_ISSUE` : `[CLE-TICKET]/[nom_fichier]`
   - `BY_STATUS_AND_ISSUE` : `[STATUT]/[CLE-TICKET]/[nom_fichier]`
   - `FLAT_WITH_PREFIX` : `[CLE-TICKET]_[nom_fichier]`
   - **Résolution automatique des collisions** : gestion des doublons au sein d'un même ticket (`fichier.png`, `fichier (1).png`).
   - **Nettoyage des chemins** : assainissement des caractères interdits (`/`, `\`, `:`, `*`, `?`, `"`, `<`, `>`, `|`) et protection contre le *Zip Slip*.

4. **Manifeste d'Audit Intégré** :
   - Génération automatique d'un fichier `export-summary.txt` à la racine de l'archive ZIP détaillant la date, l'utilisateur demandeur, la requête JQL, les filtres appliqués, et la table complète des fichiers exportés.

---

## 📡 Endpoints REST

URL de base : `<JIRA_BASE_URL>/rest/attachments-download/1.0`

### 1. Prévisualisation des métadonnées (sans téléchargement)
```http
GET /rest/attachments-download/1.0/preview?project=PRJ&status=Done&extension=pdf,png
```
**Réponse JSON :**
```json
{
  "totalIssues": 5,
  "totalAttachments": 12,
  "totalSizeBytes": 15482390,
  "formattedTotalSize": "14.8 MB",
  "appliedJql": "project = \"PRJ\" AND status = \"Done\" AND attachments IS NOT EMPTY ORDER BY key ASC",
  "attachments": [
    {
      "attachmentId": 10023,
      "filename": "rapport-incident.pdf",
      "filesize": 2048500,
      "formattedFilesize": "2.0 MB",
      "author": "Jean Dupont",
      "issueKey": "PRJ-101",
      "issueSummary": "Correction bug affichage",
      "issueStatus": "Done",
      "projectKey": "PRJ",
      "archivePath": "PRJ/PRJ-101 - Correction bug affichage/rapport-incident.pdf"
    }
  ]
}
```

### 2. Téléchargement du flux ZIP
```http
GET /rest/attachments-download/1.0/zip?project=PRJ&status=Done&grouping=BY_PROJECT_AND_ISSUE
```
* Retourne un flux binaire `application/zip` avec l'en-tête `Content-Disposition: attachment; filename="attachments-PRJ-20261101-120000.zip"`.

---

## 🖥️ Intégration dans l'Interface Jira

Le plugin déclare 2 boutons dans l'interface utilisateur de Jira :
1. **Menu Actions du ticket** (`jira.issue.tools`) : bouton *"Télécharger les pièces jointes (ZIP)"* qui télécharge directement toutes les pièces jointes du ticket courant.
2. **Outils du projet** (`project_tools`) : lien direct pour l'export des pièces jointes du projet.

---

## 🛠️ Compilation et Packaging

* **Prérequis** : JDK 17, Atlassian Plugin SDK (AMPS 9.11.2+)
* **Commande** :
  ```powershell
  atlas-package
  # ou via Maven
  mvn package -DskipTests
  ```
* **Livrables générés** :
  - `target/jira-attachments-download-1.0.0-SNAPSHOT.jar`
  - `target/jira-attachments-download-1.0.0-SNAPSHOT.obr`
