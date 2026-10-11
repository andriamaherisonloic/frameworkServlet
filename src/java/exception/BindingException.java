package exception;

/**
 * Levee lorsqu'une valeur de la requete HTTP ne peut pas etre convertie
 * en argument attendu par la methode du controleur.
 *
 * <p>Cas couverts (Sprint 7) :
 * <ul>
 *   <li>parametre obligatoire absent : un parametre annote
 *       {@code @MyRequestParam("x")} qui n'a pas ete envoye ;</li>
 *   <li>valeur invalide : la chaine envoyee n'est pas convertible
 *       (ex. {@code "abc"} vers un {@code int}) ;</li>
 *   <li>objet complexe non instanciable : aucun constructeur sans
 *       argument (Sprint 7 bis).</li>
 * </ul>
 *
 * <p>Herite de {@link RuntimeException} : les methodes des controleurs
 * n'ont pas a declarer {@code throws BindingException}.
 */
public class BindingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Nom du parametre de la methode concerne, ou null si inconnu. */
    private final String parameterName;

    /** Valeur brute recue de la requete, ou null si le parametre est absent. */
    private final String rawValue;

    public BindingException(String message) {
        this(message, null, null, null);
    }

    public BindingException(String message, String parameterName, String rawValue) {
        this(message, null, parameterName, rawValue);
    }

    public BindingException(String message, Throwable cause) {
        this(message, cause, null, null);
    }

    public BindingException(String message, Throwable cause, String parameterName, String rawValue) {
        super(message, cause);
        this.parameterName = parameterName;
        this.rawValue = rawValue;
    }

    /** Nom du parametre ayant echoue, ou null si inconnu. */
    public String getParameterName() {
        return parameterName;
    }

    /** Valeur brute envoyeee par le client, ou null si le parametre est absent. */
    public String getRawValue() {
        return rawValue;
    }
}