package context;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Registre centralise des sessions personnalisees du framework.
 */
public class SessionManager {

    private static volatile SessionManager instance = new SessionManager(30 * 60);

    public static SessionManager getInstance() {
        return instance;
    }

    public static void setInstance(SessionManager manager) {
        if (manager != null) {
            instance = manager;
        }
    }

    private final ConcurrentHashMap<String, Map<String, Object>> sessionAttributesById =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastAccessedTimeById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> inactiveTimeoutById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<Class<?>, Object>> sessionInstancesById =
            new ConcurrentHashMap<>();

    private volatile int defaultSessionTimeoutSeconds = 30 * 60;
    private final SecureRandom random = new SecureRandom();

    private final ScheduledExecutorService cleaner;

    public SessionManager(int defaultTimeoutSeconds) {
        if (defaultTimeoutSeconds > 0) {
            this.defaultSessionTimeoutSeconds = defaultTimeoutSeconds;
        }
        this.cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "framework-session-cleaner");
            t.setDaemon(true);
            return t;
        });
        this.cleaner.scheduleAtFixedRate(this::cleanupExpired, 60, 60, TimeUnit.SECONDS);
    }

    public SessionManager() {
        this(30 * 60);
    }

    public int getDefaultMaxInactiveIntervalSeconds() {
        return defaultSessionTimeoutSeconds;
    }

    public void setDefaultMaxInactiveIntervalSeconds(int seconds) {
        if (seconds > 0) {
            defaultSessionTimeoutSeconds = seconds;
        }
    }

    public Object getOrCreate(HttpServletRequest request, HttpServletResponse response,
            Class<?> sessionType) {
        if (sessionType == null) {
            sessionType = Session.class;
        }

        String requestedId = resolveRequestedId(request);
        long now = System.currentTimeMillis();

        String id = requestedId;
        if (id == null || !sessionAttributesById.containsKey(id)) {
            id = generateId();
        } else {
            Long last = lastAccessedTimeById.get(id);
            int timeout = inactiveTimeoutById.getOrDefault(id, defaultSessionTimeoutSeconds);
            if (last != null && (now - last) / 1000L > timeout) {
                doRemoveSession(id);
                id = generateId();
            } else {
                // Verifie l'expiration au niveau de l'instance.
                ConcurrentHashMap<Class<?>, Object> tm = sessionInstancesById.get(id);
                if (tm != null) {
                    for (Object s : tm.values()) {
                        if (s instanceof SessionHandle handle && handle.isExpired()) {
                            doRemoveSession(id);
                            id = generateId();
                            break;
                        }
                    }
                }
            }
        }

        sessionAttributesById.putIfAbsent(id, new ConcurrentHashMap<>());
        lastAccessedTimeById.put(id, now);

        ConcurrentHashMap<Class<?>, Object> typeMap =
                sessionInstancesById.computeIfAbsent(id, k -> new ConcurrentHashMap<>());
        Object session = typeMap.get(sessionType);
        if (session == null) {
            session = newInstance(sessionType, id, sessionAttributesById.get(id), this);
            if (session != null) {
                typeMap.put(sessionType, session);
                inactiveTimeoutById.put(id, extractTimeout(session));
            }
        } else if (session instanceof SessionHandle handle && handle.isExpired()) {
            doRemoveSession(id);
            id = generateId();
            sessionAttributesById.putIfAbsent(id, new ConcurrentHashMap<>());
            lastAccessedTimeById.put(id, now);
            typeMap = sessionInstancesById.computeIfAbsent(id, k -> new ConcurrentHashMap<>());
            session = newInstance(sessionType, id, sessionAttributesById.get(id), this);
            if (session != null) {
                typeMap.put(sessionType, session);
                inactiveTimeoutById.put(id, extractTimeout(session));
            }
        }

        if (session instanceof SessionHandle handle) {
            handle.touch();
        }
        touchSession(id);

        if (response != null && (requestedId == null || !id.equals(requestedId))) {
            setSessionCookie(response, id);
        }

        return session;
    }

    public Object getOrCreate(HttpServletRequest request, HttpServletResponse response) {
        return getOrCreate(request, response, Session.class);
    }

    public Object getOrCreate(HttpServletRequest request) {
        return getOrCreate(request, null, Session.class);
    }

    // --- Alias et surcharges pour compatibilite ---

    public Object getSession(HttpServletRequest request) {
        return getOrCreate(request, null, Session.class);
    }

    public Object getSession(HttpServletRequest request, HttpServletResponse response) {
        return getOrCreate(request, response, Session.class);
    }

    public Object getSession(HttpServletRequest request, Class<?> sessionType) {
        return getOrCreate(request, null,
                sessionType == null ? Session.class : sessionType);
    }

    public Object getSession(String id) {
        return getSessionById(id);
    }

    public Object getSessionById(String id) {
        if (id == null || !sessionAttributesById.containsKey(id)) {
            return null;
        }
        Long last = lastAccessedTimeById.get(id);
        int timeout = inactiveTimeoutById.getOrDefault(id, defaultSessionTimeoutSeconds);
        if (last != null && (System.currentTimeMillis() - last) / 1000L > timeout) {
            doRemoveSession(id);
            return null;
        }
        ConcurrentHashMap<Class<?>, Object> typeMap = sessionInstancesById.get(id);
        if (typeMap == null || typeMap.isEmpty()) {
            return null;
        }
        // Verifie expiration instance.
        for (Object s : typeMap.values()) {
            if (s instanceof SessionHandle handle && handle.isExpired()) {
                doRemoveSession(id);
                return null;
            }
        }
        return typeMap.values().iterator().next();
    }

    public Object createSession(HttpServletRequest request) {
        return getOrCreate(request, null, Session.class);
    }

    public Object createSession(HttpServletRequest request, HttpServletResponse response) {
        return getOrCreate(request, response, Session.class);
    }

    public Object createSession(HttpServletRequest request, HttpServletResponse response, Class<?> sessionType) {
        return getOrCreate(request, response, sessionType == null ? Session.class : sessionType);
    }

    public Object createSession(HttpServletRequest request, Class<?> sessionType) {
        return getOrCreate(request, null, sessionType == null ? Session.class : sessionType);
    }

    public void removeSession(String id) {
        doRemoveSession(id);
    }

    public void removeSessionPublic(String id) {
        doRemoveSession(id);
    }

    public void remove(String id) {
        doRemoveSession(id);
    }

    public void destroy(String id) {
        doRemoveSession(id);
    }

    public void delete(String id) {
        doRemoveSession(id);
    }

    public void invalidate(String id) {
        doRemoveSession(id);
    }

    public void invalidateSession(String id) {
        doRemoveSession(id);
    }

    public void touchSession(String id) {
        if (id != null && sessionAttributesById.containsKey(id)) {
            lastAccessedTimeById.put(id, System.currentTimeMillis());
        }
    }

    public void setLastAccessedTime(String id, long time) {
        if (id != null && sessionAttributesById.containsKey(id)) {
            lastAccessedTimeById.put(id, time);
        }
    }

    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        for (String id : new ArrayList<>(sessionAttributesById.keySet())) {
            boolean expired = false;
            Long last = lastAccessedTimeById.get(id);
            int timeout = inactiveTimeoutById.getOrDefault(id, defaultSessionTimeoutSeconds);
            if (last != null && (now - last) / 1000L > timeout) {
                expired = true;
            }
            ConcurrentHashMap<Class<?>, Object> typeMap = sessionInstancesById.get(id);
            if (typeMap != null) {
                for (Object s : typeMap.values()) {
                    if (s instanceof SessionHandle handle && handle.isExpired()) {
                        expired = true;
                        break;
                    }
                }
            }
            if (expired) {
                doRemoveSession(id);
            }
        }
    }

    public Set<String> getSessionIds() {
        return sessionAttributesById.keySet();
    }

    public int size() {
        return sessionAttributesById.size();
    }

    public void setMaxInactiveInterval(String id, int seconds) {
        if (id != null && seconds > 0) {
            inactiveTimeoutById.put(id, seconds);
        }
    }

    private int extractTimeout(Object session) {
        try {
            Method method = session.getClass().getMethod("getMaxInactiveInterval");
            Object result = method.invoke(session);
            if (result instanceof Integer value && value > 0) {
                return value;
            }
        } catch (Exception ignored) {
        }
        return defaultSessionTimeoutSeconds;
    }

    private void doRemoveSession(String id) {
        if (id == null) {
            return;
        }
        // Invalide les instances.
        ConcurrentHashMap<Class<?>, Object> typeMap = sessionInstancesById.get(id);
        if (typeMap != null) {
            for (Object s : typeMap.values()) {
                if (s instanceof SessionHandle handle && !handle.isInvalidated()) {
                    try {
                        handle.invalidate();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        sessionAttributesById.remove(id);
        lastAccessedTimeById.remove(id);
        sessionInstancesById.remove(id);
        inactiveTimeoutById.remove(id);
    }

    private String resolveRequestedId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        try {
            String id = request.getRequestedSessionId();
            if (isValidId(id) && sessionAttributesById.containsKey(id)) {
                return id;
            }
        } catch (Exception ignored) {
        }

        try {
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if (cookie == null) {
                        continue;
                    }
                    String name = cookie.getName();
                    if (name != null && (name.equalsIgnoreCase("JSESSIONID")
                            || name.equalsIgnoreCase("SESSIONID")
                            || name.equalsIgnoreCase("SESSION")
                            || name.equalsIgnoreCase("FWSESSIONID")
                            || name.toUpperCase().contains("SESSION"))) {
                        String value = cookie.getValue();
                        if (isValidId(value) && sessionAttributesById.containsKey(value)) {
                            return value;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        try {
            Object httpSession = request.getSession(false);
            if (httpSession != null) {
                Method getId = httpSession.getClass().getMethod("getId");
                Object result = getId.invoke(httpSession);
                if (result instanceof String id && isValidId(id) && sessionAttributesById.containsKey(id)) {
                    return id;
                }
            }
        } catch (Exception ignored) {
        }

        for (String attr : new String[]{"sessionId", "session.id", "framework.session.id",
                "frameworkSessionId"}) {
            try {
                Object value = request.getAttribute(attr);
                if (value instanceof String id && isValidId(id) && sessionAttributesById.containsKey(id)) {
                    return id;
                }
            } catch (Exception ignored) {
            }
        }

        for (String param : new String[]{"sessionId", "session.id", "framework.session.id",
                "frameworkSessionId"}) {
            try {
                String value = request.getParameter(param);
                if (isValidId(value) && sessionAttributesById.containsKey(value)) {
                    return value;
                }
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    private boolean isValidId(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        if (id.length() < 16) {
            return false;
        }
        return id.matches("[A-Za-z0-9_\\-]+");
    }

    private String generateId() {
        return UUID.randomUUID().toString().replace("-", "") + Long.toHexString(random.nextLong());
    }

    private Object newInstance(Class<?> sessionType, String id, Map<String, Object> attributes,
            SessionManager manager) {
        Class<?>[][] signatures = new Class<?>[][] {
                new Class<?>[] { String.class, Map.class, SessionManager.class },
                new Class<?>[] { String.class, Map.class, Object.class },
                new Class<?>[] { String.class, java.util.concurrent.ConcurrentHashMap.class },
                new Class<?>[] { String.class, Map.class },
                new Class<?>[] { String.class }
        };
        for (Class<?>[] signature : signatures) {
            try {
                Constructor<?> constructor = sessionType.getDeclaredConstructor(signature);
                constructor.setAccessible(true);
                if (signature.length == 3) {
                    return constructor.newInstance(id, attributes, manager);
                }
                if (signature.length == 2) {
                    return constructor.newInstance(id, attributes);
                }
                return constructor.newInstance(id);
            } catch (NoSuchMethodException ignored) {
            } catch (Exception e) {
            }
        }
        try {
            Constructor<?> constructor = sessionType.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object instance = constructor.newInstance();
            setField(sessionType, instance, "id", id);
            setField(sessionType, instance, "attributes", attributes);
            setField(sessionType, instance, "data", attributes);
            setField(sessionType, instance, "values", attributes);
            setField(sessionType, instance, "manager", manager);
            setField(sessionType, instance, "sessionManager", manager);
            return instance;
        } catch (Exception e) {
            return null;
        }
    }

    private void setField(Class<?> type, Object instance, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = type.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(instance, value);
        } catch (Exception ignored) {
        }
        for (Class<?> current = type.getSuperclass(); current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                java.lang.reflect.Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(instance, value);
                return;
            } catch (Exception ignored) {
            }
        }
    }

    private void setSessionCookie(HttpServletResponse response, String id) {
        try {
            Cookie cookie = new Cookie("JSESSIONID", id);
            cookie.setPath("/");
            cookie.setHttpOnly(true);
            response.addCookie(cookie);
        } catch (Exception ignored) {
        }
        try {
            Cookie cookie = new Cookie("FWSESSIONID", id);
            cookie.setPath("/");
            cookie.setHttpOnly(true);
            response.addCookie(cookie);
        } catch (Exception ignored) {
        }
    }
}