package model;

import context.SessionManager;

import java.util.Map;

/**
 * Alias reutilisable de {@link context.Session}, conserve pour compatibilite
 * avec les applications qui importent le package {@code model}.
 */
public class Session extends context.Session {

    public Session() {
        super();
    }

    public Session(String id) {
        super(id);
    }

    public Session(String id, Map<String, Object> attributes) {
        super(id, attributes);
    }

    public Session(String id, Map<String, Object> attributes, SessionManager manager) {
        super(id, attributes, manager);
    }

    public Session(String id, Map<String, Object> attributes, Object manager) {
        super(id, attributes, manager);
    }
}