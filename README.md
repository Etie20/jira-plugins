# Jira Plugins - Data Center

Ce dépôt regroupe l'ensemble des plugins Jira Data Center (version 10.3.5+, Java 17) développés par **Etienne FOYANG** au sein du **Data Process et Innovation (DPI)**, accompagnés de la documentation technique et de la présentation de référence.

---

## 📦 Contenu du dépôt

### 1. [`jira-clarification-loop`](./jira-clarification-loop/)
Plugin complet de **boucle de demande d'informations supplémentaires et réassignation automatique** :
* Panneau latéral droit (`web-panel`) transverse à tous les tickets sans modification des workflows.
* Recherche globale d'utilisateurs Jira via autocomplétion (`/rest/api/2/user/picker`).
* Dépôt direct de pièces jointes (upload multipart).
* Contrôle strict des habilitations (seul l'assigné demande, seul le destinataire répond, seul l'auteur annule).
* Persistance légère et sécurisée via `PluginSettings` (SAL) et commentaires horodatés.

### 2. [`jira-compliance-tracker`](./jira-compliance-tracker/)
Plugin de **traçabilité et conformité bancaire** :
* Suivi des exigences réglementaires et conformité sur les tickets.
* Stockage et gestion d'audit.

### 3. [`poc-migration`](./poc-migration/)
**Proof of Concept initial (« Hello World Banque »)** :
* Exemple de démarrage avec le SDK Atlassian.
* Injection de bouton dans la barre de navigation et modale AUI Dialog2.

---

## 📄 Documentation & Présentations

* **[Documentation Technique et Fonctionnelle (Markdown)](./Documentation_Plugin_Jira_Clarification_Loop.md)**
* **[Documentation Technique et Fonctionnelle (PDF)](./Documentation_Plugin_Jira_Clarification_Loop.pdf)**
* **[Présentation - Développer un plugin Jira Data Center (PDF)](./Presentation_Developpement_Plugin_Jira_DataCenter.pdf)**
* **[Présentation - Développer un plugin Jira Data Center (PowerPoint)](./Presentation_Developpement_Plugin_Jira_DataCenter.pptx)**

---

## 🛠️ Prérequis et Compilation

* **Java :** JDK 17
* **Atlassian Plugin SDK :** 9.11.2+

Pour compiler et générer le `.jar` d'un plugin (ex. `jira-clarification-loop`) :
```bash
cd jira-clarification-loop
atlas-package -DskipTests
```
Le fichier `.jar` livrable est produit dans le dossier `target/`.
