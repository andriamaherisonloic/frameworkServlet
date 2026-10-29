package context;

import annotation.Autowired;
import annotation.FWController;
import annotation.MyController;
import annotation.RepositoryAnnotation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Contexte global du framework : scan des controleurs, instanciation des beans,
 * injection des dependances et acces au gestionnaire de sessions.
 */
public class ApplicationContext {

    private final Map<Class<?>, Object> beans = new HashMap<>();
    private final Map<String, Class<?>> beanNames = new HashMap<>();
    private final List<Class<?>> beanClasses = new ArrayList<>();
    private final DatabaseConfig databaseConfig;
    private final SessionManager sessionManager;

    public ApplicationContext(String packagesToScan, DatabaseConfig databaseConfig) {
        this(packagesToScan, databaseConfig, Map.of());
    }

    public ApplicationContext(String packagesToScan, DatabaseConfig databaseConfig,
            Map<Class<?>, Object> frameworkBeans) {
        this.databaseConfig = databaseConfig;
        this.sessionManager = new SessionManager(30 * 60);
        try {
            List<Class<?>> discoveredClasses = scanPackages(packagesToScan);
            registerFrameworkBeans(frameworkBeans);
            instantiateBeans(discoveredClasses);
            injectDependencies();
        } catch (Exception exception) {
            throw new RuntimeException("Erreur initialisation ApplicationContext", exception);
        }
    }

    private void registerFrameworkBeans(Map<Class<?>, Object> frameworkBeans) {
        if (frameworkBeans == null) {
            return;
        }
        for (Map.Entry<Class<?>, Object> entry : frameworkBeans.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            beans.put(entry.getKey(), entry.getValue());
            beanNames.put(entry.getKey().getName(), entry.getKey());
        }
    }

    private List<Class<?>> scanPackages(String packagesToScan) throws Exception {
        List<Class<?>> discoveredClasses = new ArrayList<>();
        if (packagesToScan == null || packagesToScan.isBlank()) {
            return discoveredClasses;
        }
        for (String packageName : packagesToScan.split(",")) {
            String trimmedPackageName = packageName.trim();
            if (!trimmedPackageName.isEmpty()) {
                discoveredClasses.addAll(scanPackage(trimmedPackageName));
            }
        }
        return discoveredClasses;
    }

    private List<Class<?>> scanPackage(String packageName) throws Exception {
        List<Class<?>> result = new ArrayList<>();
        String resourcePath = packageName.replace('.', '/');
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL resource = classLoader.getResource(resourcePath);
        if (resource == null) {
            return result;
        }

        File directory = new File(resource.toURI());
        File[] files = directory.listFiles();
        if (files == null) {
            return result;
        }

        for (File file : files) {
            if (!file.getName().endsWith(".class")) {
                continue;
            }
            String className = packageName + "." + file.getName().replace(".class", "");
            Class<?> clazz = Class.forName(className);
            if (isFrameworkComponent(clazz)) {
                result.add(clazz);
            }
        }
        return result;
    }

    private boolean isFrameworkComponent(Class<?> clazz) {
        return clazz.isAnnotationPresent(MyController.class)
                || clazz.isAnnotationPresent(FWController.class)
                || clazz.isAnnotationPresent(RepositoryAnnotation.class);
    }

    private void instantiateBeans(List<Class<?>> classes) throws Exception {
        for (Class<?> clazz : classes) {
            Constructor<?> constructor = clazz.getDeclaredConstructor();
            if (!Modifier.isPublic(constructor.getModifiers())) {
                constructor.setAccessible(true);
            }
            Object instance = constructor.newInstance();
            beans.put(clazz, instance);
            beanNames.put(clazz.getName(), clazz);
            beanClasses.add(clazz);

            if (databaseConfig != null && clazz.isAnnotationPresent(RepositoryAnnotation.class)) {
                injectDatabaseConnection(instance);
            }
        }
    }

    private void injectDatabaseConnection(Object instance) {
        for (Field field : instance.getClass().getDeclaredFields()) {
            if (field.getType() == Connection.class) {
                try {
                    field.setAccessible(true);
                    field.set(instance, databaseConfig.getConnection());
                } catch (Exception exception) {
                    exception.printStackTrace();
                }
            }
        }
    }

    private void injectDependencies() {
        for (Object bean : beans.values()) {
            for (Field field : bean.getClass().getDeclaredFields()) {
                if (!field.isAnnotationPresent(Autowired.class)) {
                    continue;
                }
                Object dependency = findBeanByType(field.getType());
                if (dependency == null) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    field.set(bean, dependency);
                } catch (Exception exception) {
                    exception.printStackTrace();
                }
            }
        }
    }

    private Object findBeanByType(Class<?> type) {
        for (Object bean : beans.values()) {
            if (type.isInstance(bean)) {
                return bean;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public <T> T getBean(Class<T> clazz) {
        Object bean = beans.get(clazz);
        if (bean != null) {
            return (T) bean;
        }
        for (Object instance : beans.values()) {
            if (clazz.isInstance(instance)) {
                return (T) instance;
            }
        }
        return null;
    }

    public Object getBean(String className) {
        Class<?> clazz = beanNames.get(className);
        if (clazz == null) {
            return null;
        }
        return beans.get(clazz);
    }

    public List<Class<?>> getBeanClasses() {
        return beanClasses;
    }

    public Collection<Object> getAllBeans() {
        return beans.values();
    }

    public DatabaseConfig getDatabaseConfig() {
        return databaseConfig;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public Object getSession(HttpServletRequest request) {
        return getSession(request, (HttpServletResponse) null);
    }

    public Object getSession(HttpServletRequest request, HttpServletResponse response) {
        if (sessionManager == null) {
            return null;
        }
        return sessionManager.getOrCreate(request, response, Session.class);
    }

    public Object getSession(HttpServletRequest request, Class<?> sessionType) {
        if (sessionManager == null) {
            return null;
        }
        return sessionManager.getOrCreate(request, null,
                sessionType == null ? Session.class : sessionType);
    }

    public Object createSession(HttpServletRequest request) {
        return getSession(request, (HttpServletResponse) null);
    }

    public Object createSession(HttpServletRequest request, HttpServletResponse response) {
        return getSession(request, response);
    }

    public void removeSession(String sessionId) {
        invalidateSession(sessionId);
    }

    public void invalidateSession(String sessionId) {
        if (sessionManager != null) {
            sessionManager.invalidate(sessionId);
        }
    }
}