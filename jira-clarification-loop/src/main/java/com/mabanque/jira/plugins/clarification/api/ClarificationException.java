package com.mabanque.jira.plugins.clarification.api;

/**
 * Erreur métier levée par le service de précisions.
 * Porte un code HTTP et, si l'erreur concerne un champ précis du formulaire, le nom de ce champ
 * afin que l'interface puisse afficher le message directement sous le champ concerné.
 */
public class ClarificationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public static final int BAD_REQUEST = 400;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    public static final int CONFLICT = 409;

    private final int status;
    private final String field;

    public ClarificationException(int status, String message) {
        this(status, null, message);
    }

    public ClarificationException(int status, String field, String message) {
        super(message);
        this.status = status;
        this.field = field;
    }

    public static ClarificationException field(String field, String message) {
        return new ClarificationException(BAD_REQUEST, field, message);
    }

    public static ClarificationException forbidden(String message) {
        return new ClarificationException(FORBIDDEN, message);
    }

    public int getStatus() {
        return status;
    }

    public String getField() {
        return field;
    }
}
