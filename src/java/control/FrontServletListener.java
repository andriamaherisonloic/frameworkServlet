package control;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import annotation.Controller;
import utils.Repository;
import utils.Utilitaires;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebListener
public class FrontServletListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try {
            ServletContext context = sce.getServletContext();

            String packageName = context.getInitParameter("controllerPackage");
            if (packageName == null || packageName.isBlank()) {
                packageName = "controller";
            }

            List<Class<?>> controllers = Utilitaires.getClassesByPackageAndAnnotation(
                    Controller.class, packageName, java.lang.annotation.ElementType.TYPE);
            context.setAttribute("controllers", controllers);

            String dbDriver = context.getInitParameter("dbDriver");
            String dbUrl = context.getInitParameter("dbUrl");
            String dbUser = context.getInitParameter("dbUser");
            String dbPassword = context.getInitParameter("dbPassword");

            Map<String, Object> repositoryInstances = scanAndInitRepositories(
                    packageName, dbDriver, dbUrl, dbUser, dbPassword);
            context.setAttribute("repositories", repositoryInstances);

            System.out.println("Framework initialise - " + controllers.size()
                    + " controllers, " + repositoryInstances.size() + " repositories");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> scanAndInitRepositories(String packageName, String dbDriver,
            String dbUrl, String dbUser, String dbPassword) throws Exception {
        Map<String, Object> instances = new HashMap<>();
        String path = packageName.replace('.', '/');
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL resource = classLoader.getResource(path);

        if (resource == null) {
            return instances;
        }

        File directory = new File(resource.toURI());
        File[] files = directory.listFiles();
        if (files == null) {
            return instances;
        }

        for (File file : files) {
            if (file.getName().endsWith(".class")) {
                String className = packageName + "." + file.getName().replace(".class", "");
                Class<?> clazz = Class.forName(className);

                if (Repository.class.isAssignableFrom(clazz)
                        && !Modifier.isAbstract(clazz.getModifiers())) {
                    Constructor<?> constructor = clazz.getDeclaredConstructor();
                    if (!Modifier.isPublic(constructor.getModifiers())) {
                        constructor.setAccessible(true);
                    }
                    Repository repo = (Repository) constructor.newInstance();
                    repo.setDbDriver(dbDriver);
                    repo.setDbUrl(dbUrl);
                    repo.setDbUser(dbUser);
                    repo.setDbPassword(dbPassword);
                    instances.put(clazz.getName(), repo);
                }
            }
        }

        return instances;
    }
}
