package session;

import context.SessionHandle;
import context.SessionManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Session implements SessionHandle {

    private String id;
    private Map<String, Object> attributes;
    private SessionManager manager;

    private volatile long lastAccessedTime = System.currentTimeMillis();
    private volatile boolean invalidated = false;
    private volatile int maxInactiveIntervalSeconds = 30 * 60;

    public Session() {
        this(java.util.UUID.randomUUID().toString().replace("-", "")
                + Long.toHexString(new java.security.SecureRandom().nextLong()),
                new ConcurrentHashMap<>(), null);
    }

    public Session(String id) {
        this(id, new ConcurrentHashMap<>(), null);
    }

    public Session(String id, Map<String, Object> attributes) {
        this(id, attributes, null);
    }

    public Session(String id, Map<String, Object> attributes, SessionManager manager) {
        this.id = (id == null || id.isBlank())
                ? java.util.UUID.randomUUID().toString().replace("-", "")
                : id;
        this.attributes = (attributes == null) ? new ConcurrentHashMap<>() : attributes;
        this.manager = manager;
        if (manager != null) {
            this.maxInactiveIntervalSeconds = manager.getDefaultMaxInactiveIntervalSeconds();
        }
        this.lastAccessedTime = System.currentTimeMillis();
    }

    public Session(String id, Map<String, Object> attributes, Object manager) {
        this(id, attributes, (manager instanceof SessionManager) ? (SessionManager) manager : null);
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        if (id != null && !id.isBlank()) {
            this.id = id;
        }
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        if (attributes != null) {
            this.attributes = attributes;
        }
    }

    public Map<String, Object> getData() {
        return attributes;
    }

    public void setData(Map<String, Object> data) {
        setAttributes(data);
    }

    public void setAttribute(String key, Object value) {
        ensureValid();
        if (key == null) {
            return;
        }
        attributes.put(key, value);
        touch();
    }

    public Object getAttribute(String key) {
        ensureValid();
        touch();
        if (key == null) {
            return null;
        }
        return attributes.get(key);
    }

    public void removeAttribute(String key) {
        ensureValid();
        if (key == null) {
            return;
        }
        attributes.remove(key);
        touch();
    }

    public boolean containsAttribute(String key) {
        return attributes.containsKey(key);
    }

    @Override
    public void invalidate() {
        invalidated = true;
        attributes.clear();
        if (manager != null) {
            manager.invalidate(id);
        }
    }

    @Override
    public boolean isInvalidated() {
        return invalidated;
    }

    @Override
    public void touch() {
        lastAccessedTime = System.currentTimeMillis();
        if (manager != null) {
            manager.touchSession(id);
        }
    }

    @Override
    public boolean isExpired() {
        if (invalidated) {
            return true;
        }
        long idle = (System.currentTimeMillis() - lastAccessedTime) / 1000L;
        return idle > maxInactiveIntervalSeconds;
    }

    public long getLastAccessedTime() {
        return lastAccessedTime;
    }

    public void setLastAccessedTime(long time) {
        lastAccessedTime = time;
        if (manager != null) {
            manager.setLastAccessedTime(id, time);
        }
    }

    public void clear() {
        ensureValid();
        attributes.clear();
        touch();
    }

    public int size() {
        return attributes.size();
    }

    public int getMaxInactiveInterval() {
        return maxInactiveIntervalSeconds;
    }

    public void setMaxInactiveInterval(int seconds) {
        if (seconds > 0) {
            maxInactiveIntervalSeconds = seconds;
            if (manager != null) {
                manager.setMaxInactiveInterval(id, seconds);
            }
        }
    }

    private void ensureValid() {
        if (invalidated) {
            throw new IllegalStateException("Session invalidee");
        }
    }

    @Override
    public String toString() {
        return "Session{id=" + id + ", attributes=" + attributes + "}";
    }
}