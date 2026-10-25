package exception;

/**
 * Erreur liee a l'upload d'un fichier (Sprint 8).
 *
 * <p>Deux natures d'erreur sont distinguees :
 * <ul>
 *   <li><b>erreur client</b> ({@link #isClientError()} = true) : fichier absent,
 *       trop volumineux, extension refusee, nom dangereux, contenu incoherent
 *       avec l'extension. Le FrontServlet repond alors HTTP 400 ;</li>
 *   <li><b>erreur serveur</b> (false) : dossier inaccessible, disque plein,
 *       ecriture impossible. Le FrontServlet repond HTTP 500.</li>
 * </ul>
 */
public class UploadException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final boolean clientError;

    public UploadException(String message) {
        this(message, null, true);
    }

    public UploadException(String message, boolean clientError) {
        this(message, null, clientError);
    }

    public UploadException(String message, Throwable cause, boolean clientError) {
        super(message, cause);
        this.clientError = clientError;
    }

    /** Erreur d'ecriture / de stockage : responsabilite serveur. */
    public static UploadException storage(String message, Throwable cause) {
        return new UploadException(message, cause, false);
    }

    /** Vrai si la cause est une donnee envoyee par le client. */
    public boolean isClientError() {
        return clientError;
    }
}