package utils;

/**
 * Exception levee lors du binding des parametres d'une methode de controller
 * a partir des donnees de la requete HTTP (Sprint 7).
 *
 * Cas d'utilisation :
 *  - valeur recue impossible a convertir vers le type attendu ;
 *  - type de parametre non pris en charge par le binding (objet complexe) ;
 *  - nom de parametre non disponible (code compile sans l'option -parameters).
 */
public class BindingException extends Exception {

    public BindingException(String message) {
        super(message);
    }

    public BindingException(String message, Throwable cause) {
        super(message, cause);
    }
}
