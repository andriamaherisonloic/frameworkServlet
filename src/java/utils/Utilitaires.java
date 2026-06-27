package utils;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.net.URL;

import annotation.UrlMapping;

public class Utilitaires {

    public static List<String> getClassByPackageAndAnnotation(Class<? extends Annotation> annotation,
            String packageName, ElementType type)
            throws Exception {
        try {
            if (typeElementValide(annotation, type)) {
                List<String> listeClass = new ArrayList<>();
                List<Class<?>> classInPackage = getClassByPackage(packageName);
                List<Class<?>> classByAnnotation = filterByAnnotation(classInPackage, annotation);

                for (Class<?> class1 : classByAnnotation) {
                    listeClass.add(class1.getSimpleName());
                }

                return listeClass;
            }


            
        } catch (Exception e) {
            throw e;
        }

        return null;
    }

    public static List<Class<?>> getClassesByPackageAndAnnotation(Class<? extends Annotation> annotation,
            String packageName, ElementType type)
            throws Exception {
        if (typeElementValide(annotation, type)) {
            List<Class<?>> classInPackage = getClassByPackage(packageName);
            return filterByAnnotation(classInPackage, annotation);
        }

        return new ArrayList<>();
    }

    public static List<Mapping> getUrlMappings(Class<? extends Annotation> controllerAnnotation,
            String packageName) throws Exception {
        List<Mapping> mappings = new ArrayList<>();
        List<Class<?>> controllers = getClassesByPackageAndAnnotation(controllerAnnotation, packageName,
                ElementType.TYPE);

        for (Class<?> controller : controllers) {
            Method[] methods = controller.getDeclaredMethods();

            for (Method method : methods) {
                if (method.isAnnotationPresent(UrlMapping.class)) {
                    UrlMapping urlMapping = method.getAnnotation(UrlMapping.class);
                    mappings.add(new Mapping(urlMapping.value(), controller.getSimpleName(), method.getName()));
                }
            }
        }

        return mappings;
    }

    public static List<Class<?>> getClassByPackage(String packageName) throws Exception {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        String path = packageName.replace('.', '/');
        URL resource = classLoader.getResource(path);

        if (resource == null) {
            throw new IllegalArgumentException("Aucun répertoire trouvé pour le package " + packageName);
        }

        File directory = new File(resource.getFile());
        List<Class<?>> classes = new ArrayList<>();

        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.getName().endsWith(".class")) {
                        String className = packageName + '.' + file.getName().substring(0, file.getName().length() - 6);
                        classes.add(Class.forName(className));
                    }
                }
            }
        }

        return classes;
    }

    public static List<Class<?>> filterByAnnotation(List<Class<?>> listeClass, Class<? extends Annotation> annotation) {
        List<Class<?>> classDispo = new ArrayList<>();
        for (Class<?> classes : listeClass) {
            if (classes.isAnnotationPresent(annotation)) {
                classDispo.add(classes);
            }
        }
        return classDispo;
    }

    public static boolean typeElementValide(Class<? extends Annotation> annotation, ElementType type) {
        Target target = annotation.getAnnotation(Target.class);
        ElementType[] elementTypes = target.value();

        for (ElementType elementType : elementTypes) {
            if (type.equals(elementType)) {
                return true;
            }
        }

        return false;
    }
}
