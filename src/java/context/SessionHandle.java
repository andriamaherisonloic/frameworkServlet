package context;

/**
 * Contrat minimal expose par une session personnalisee du framework.
 *
 * <p>Permet au {@link SessionManager} de piloter le cycle de vie sans
 * dependre d'une classe concrete particuliere (et donc sans couplage a un
 * package precis).</p>
 */
public interface SessionHandle {

    String getId();

    boolean isInvalidated();

    void touch();

    boolean isExpired();

    void invalidate();
}