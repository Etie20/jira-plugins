package com.mabanque.jira.plugins.clarification.api;

import com.atlassian.jira.user.ApplicationUser;

/**
 * Service métier gérant les demandes de précisions et allers-retours sur les tickets.
 * Toutes les méthodes d'action vérifient les droits de l'utilisateur et lèvent une
 * {@link ClarificationException} en cas de refus ou de saisie invalide.
 */
public interface ClarificationService {

    /**
     * Récupère l'historique complet des demandes d'informations pour un ticket.
     */
    ClarificationHistory getHistory(String issueKey);

    /**
     * Calcule les droits de l'utilisateur sur le module de précisions du ticket.
     */
    ClarificationPermissions getPermissions(String issueKey, ApplicationUser user);

    /**
     * Vérifie qu'une demande peut être envoyée, sans rien enregistrer.
     */
    void validateRequest(String issueKey, String comment, String recipientUsername, ApplicationUser requester);

    /**
     * Enregistre une demande de précision : le ticket est réassigné au destinataire.
     */
    ClarificationEntry requestClarification(String issueKey, String comment, String recipientUsername,
                                            Long attachmentId, String attachmentName, ApplicationUser requester);

    /**
     * Vérifie qu'une réponse peut être envoyée, sans rien enregistrer.
     */
    void validateAnswer(String issueKey, String comment, ApplicationUser responder);

    /**
     * Enregistre la réponse du destinataire : le ticket est réassigné à l'auteur de la demande.
     */
    ClarificationEntry answerClarification(String issueKey, String comment,
                                           Long attachmentId, String attachmentName, ApplicationUser responder);

    /**
     * Annule la demande en cours (réservé à son auteur) : le ticket lui est réassigné.
     */
    ClarificationEntry cancelClarification(String issueKey, ApplicationUser requester);

    /**
     * Vérifie si le ticket est actuellement en attente d'informations.
     */
    boolean hasPendingClarification(String issueKey);
}
