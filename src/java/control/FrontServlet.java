package control;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import annotation.Controller;
import annotation.UrlMapping;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import utils.Mapping;
import utils.Model;
import utils.ModelAndView;
import utils.UrlMethod;
import utils.Utilitaires;

@WebServlet("/")
public class FrontServlet extends HttpServlet {
    private Map<UrlMethod, Method> urlMappings = new HashMap<>();
    private Map<String, Object> controllerInstances = new HashMap<>();
    protected List<Mapping> mappings;
    private Model lastInjectedModel;

    @Override
    public void init() throws ServletException {
        super.init();
        try {
            String controllerPackage = this.getInitParameter("controllerPackage");
            if (controllerPackage != null && !controllerPackage.isEmpty()) {
                mappings = Utilitaires.getUrlMappings(Controller.class, controllerPackage);
            }
            rebuildRegistry();
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        handleRequest(req, res);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        handleRequest(req, res);
    }

    private void rebuildRegistry() throws ServletException {
        try {
            List<Class<?>> controllers = resolveControllers();
            urlMappings = new HashMap<>();
            controllerInstances = new HashMap<>();

            for (Class<?> controllerClass : controllers) {
                controllerInstances.put(controllerClass.getName(), instantiateController(controllerClass));

                Map<UrlMethod, Method> classMappings = getUrlMappings(controllerClass);
                for (Map.Entry<UrlMethod, Method> entry : classMappings.entrySet()) {
                    if (urlMappings.containsKey(entry.getKey())) {
                        Method existing = urlMappings.get(entry.getKey());
                        throw new ServletException(
                                "UrlMapping dupliqué : " + entry.getKey()
                                + " (déjà déclaré dans " + existing.getDeclaringClass().getName()
                                + "." + existing.getName()
                                + ") en conflit avec "
                                + entry.getValue().getDeclaringClass().getName()
                                + "." + entry.getValue().getName());
                    }
                    urlMappings.put(entry.getKey(), entry.getValue());
                }
            }

            getServletContext().setAttribute("controllers", controllers);
            getServletContext().setAttribute("urlMappings", urlMappings);
        } catch (ServletException e) {
            getServletContext().setAttribute("initError", e.getMessage());
            throw e;
        } catch (Exception e) {
            throw new ServletException("Erreur initialisation registre URL", e);
        }
    }

    private List<Class<?>> resolveControllers() throws Exception {
        Object storedControllers = getServletContext().getAttribute("controllers");
        if (storedControllers instanceof List<?>) {
            List<Class<?>> controllers = new ArrayList<>();
            for (Object item : (List<?>) storedControllers) {
                if (item instanceof Class<?>) {
                    controllers.add((Class<?>) item);
                }
            }
            if (!controllers.isEmpty()) {
                return controllers;
            }
        }

        String packageName = getServletContext().getInitParameter("controllerPackage");
        if (packageName == null || packageName.isBlank()) {
            packageName = "control";
        }
        return scanControllers(packageName);
    }

    private List<Class<?>> scanControllers(String packageName) throws Exception {
        List<Class<?>> controllers = new ArrayList<>();
        String path = packageName.replace('.', '/');
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL resource = classLoader.getResource(path);
        if (resource == null) {
            return controllers;
        }

        File directory = new File(resource.toURI());
        File[] files = directory.listFiles();
        if (files == null) {
            return controllers;
        }

        for (File file : files) {
            if (file.getName().endsWith(".class")) {
                String className = packageName + "." + file.getName().replace(".class", "");
                Class<?> clazz = Class.forName(className);
                if (clazz.isAnnotationPresent(Controller.class)) {
                    controllers.add(clazz);
                }
            }
        }

        return controllers;
    }

    private Map<UrlMethod, Method> getUrlMappings(Class<?> controllerClass) {
        Map<UrlMethod, Method> controllerUrlMappings = new HashMap<>();
        for (Method method : controllerClass.getDeclaredMethods()) {
            UrlMapping mapping = method.getAnnotation(UrlMapping.class);
            if (mapping == null) {
                continue;
            }

            String url = resolveUrl(mapping);
            if (url == null || url.isBlank()) {
                continue;
            }

            UrlMethod key = new UrlMethod(normalizePath(url), resolveHttpMethod(mapping));
            if (controllerUrlMappings.containsKey(key)) {
                Method existing = controllerUrlMappings.get(key);
                throw new RuntimeException(
                        "UrlMapping dupliqué : " + key
                        + " (déjà déclaré dans " + existing.getDeclaringClass().getName()
                        + "." + existing.getName()
                        + ") en conflit avec "
                        + controllerClass.getName() + "." + method.getName());
            }
            controllerUrlMappings.put(key, method);
        }
        return controllerUrlMappings;
    }

    private String resolveUrl(UrlMapping mapping) {
        return mapping.value();
    }

    private String resolveHttpMethod(UrlMapping mapping) {
        if (mapping.method() == null || mapping.method().isBlank()) {
            return "GET";
        }
        return mapping.method().toUpperCase();
    }

    private void handleRequest(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        String error = (String) getServletContext().getAttribute("initError");
        if (error != null) {
            res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, error);
            return;
        }

        String requestPath = normalizePath(getRequestPath(req));
        if ("/".equals(requestPath)) {
            renderControllerIndex(req, res);
            return;
        }

        String httpMethod = req.getMethod();
        Method method = getMethodForUrl(requestPath, httpMethod);
        if (method == null) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND, "Aucune route trouve pour " + requestPath);
            return;
        }

        try {
            Object controller = getControllerInstance(method.getDeclaringClass().getName(), httpMethod, requestPath);
            if (controller == null) {
                res.sendError(HttpServletResponse.SC_NOT_FOUND, "Controleur introuvable pour " + requestPath);
                return;
            }

            Object result = method.invoke(controller, buildArguments(method, req, res));
            if (result instanceof ModelAndView) {
                renderModelAndView(req, res, (ModelAndView) result);
                return;
            }

            if (result instanceof String) {
                Map<String, Object> modelData = (lastInjectedModel != null) ? lastInjectedModel.asMap() : Map.of();
                renderView(req, res, (String) result, modelData);
                return;
            }

            res.sendError(HttpServletResponse.SC_NO_CONTENT);
        } catch (Exception e) {
            throw new ServletException("Erreur invocation de " + method.getName(), e);
        }
    }

    protected Method getMethodForUrl(String path, String httpMethod) {
        if (urlMappings == null) {
            return null;
        }
        return urlMappings.get(new UrlMethod(path, httpMethod));
    }

    protected Map<UrlMethod, Method> getAllMappings() {
        return urlMappings;
    }

    protected Object getControllerInstance(String className, String httpMethod, String path) {
        if (controllerInstances == null) {
            return null;
        }
        Method method = getMethodForUrl(path, httpMethod);
        if (method == null) {
            return null;
        }
        return controllerInstances.get(className);
    }

    private String getRequestPath(HttpServletRequest request) {
        String url = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && url.startsWith(contextPath)) {
            url = url.substring(contextPath.length());
        }
        if (url == null || url.isEmpty()) {
            return "/";
        }
        return url;
    }

    private void renderControllerIndex(HttpServletRequest req, HttpServletResponse res) throws IOException {
        List<String> controllers = new ArrayList<>();
        if (getServletContext().getAttribute("controllers") instanceof List<?>) {
            for (Object item : (List<?>) getServletContext().getAttribute("controllers")) {
                if (item instanceof Class<?>) {
                    controllers.add(((Class<?>) item).getSimpleName());
                }
            }
        }

        controllers = controllers.stream()
                .sorted(String::compareToIgnoreCase)
                .collect(Collectors.toList());

        res.setContentType("text/html; charset=UTF-8");
        PrintWriter writer = res.getWriter();
        writer.println("<!DOCTYPE html>");
        writer.println("<html><head><meta charset=\"UTF-8\"><title>Liste des controllers</title></head><body>");
        writer.println("<h1>Liste des contrôleurs annotés</h1>");
        writer.println("<ul>");
        for (String controller : controllers) {
            writer.println("<li>" + controller + "</li>");
        }
        writer.println("</ul>");

        if (urlMappings != null && !urlMappings.isEmpty()) {
            writer.println("<h2>URLs supportées</h2>");
            writer.println("<ul>");
            for (Map.Entry<UrlMethod, Method> entry : urlMappings.entrySet()) {
                UrlMethod key = entry.getKey();
                Method m = entry.getValue();
                writer.println("<li>" + key + " → " + m.getDeclaringClass().getSimpleName() + "." + m.getName() + "()</li>");
            }
            writer.println("</ul>");
        }

        writer.println("</body></html>");
    }

    private Object instantiateController(Class<?> controllerClass) {
        try {
            Constructor<?> constructor = controllerClass.getDeclaredConstructor();
            if (!Modifier.isPublic(constructor.getModifiers())) {
                constructor.setAccessible(true);
            }
            return constructor.newInstance();
        } catch (Exception e) {
            return null;
        }
    }

    private Object[] buildArguments(Method method, HttpServletRequest req, HttpServletResponse res) {
        lastInjectedModel = null;
        Class<?>[] parameterTypes = method.getParameterTypes();
        Object[] arguments = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];
            if (HttpServletRequest.class.isAssignableFrom(parameterType)) {
                arguments[i] = req;
            } else if (HttpServletResponse.class.isAssignableFrom(parameterType)) {
                arguments[i] = res;
            } else if (Model.class.isAssignableFrom(parameterType)) {
                lastInjectedModel = new Model();
                arguments[i] = lastInjectedModel;
            } else if (Map.class.isAssignableFrom(parameterType)) {
                lastInjectedModel = new Model();
                arguments[i] = lastInjectedModel;
            } else {
                arguments[i] = null;
            }
        }
        return arguments;
    }

    private void renderModelAndView(HttpServletRequest req, HttpServletResponse res, ModelAndView modelAndView)
            throws ServletException, IOException {
        Model model = modelAndView.getModelContainer();
        if (model != null) {
            for (Map.Entry<String, Object> entry : model.asMap().entrySet()) {
                req.setAttribute(entry.getKey(), entry.getValue());
            }
        }
        renderView(req, res, modelAndView.getViewName(), modelAndView.getModel());
    }

    private void renderView(HttpServletRequest req, HttpServletResponse res, String viewName, Map<String, Object> model)
            throws ServletException, IOException {
        for (Map.Entry<String, Object> entry : model.entrySet()) {
            req.setAttribute(entry.getKey(), entry.getValue());
        }

        String prefix = getServletContext().getInitParameter("prefix");
        if (prefix == null) {
            prefix = getServletContext().getInitParameter("viewPrefix");
        }
        if (prefix == null) {
            prefix = "/WEB-INF/views/";
        }

        String suffix = getServletContext().getInitParameter("suffix");
        if (suffix == null) {
            suffix = getServletContext().getInitParameter("viewSuffix");
        }
        if (suffix == null) {
            suffix = ".jsp";
        }

        RequestDispatcher dispatcher = req.getRequestDispatcher(prefix + viewName + suffix);
        dispatcher.forward(req, res);
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private Mapping findMapping(String url) {
        if (mappings == null) {
            return null;
        }
        for (Mapping mapping : mappings) {
            if (mapping.getUrl().equals(url)) {
                return mapping;
            }
        }
        return null;
    }

    private Mapping findMappingWithSameBase(String url) {
        if (mappings == null) {
            return null;
        }
        String urlBase = getBasePath(url);
        for (Mapping mapping : mappings) {
            if (getBasePath(mapping.getUrl()).equals(urlBase)) {
                return mapping;
            }
        }
        return null;
    }

    private String getBasePath(String url) {
        String[] parts = url.split("/");
        if (parts.length > 1) {
            return "/" + parts[1];
        }
        return url;
    }

    private void printAllMappings(PrintWriter out) {
        for (Mapping mapping : mappings) {
            printMapping(out, mapping);
        }
    }

    private void printMapping(PrintWriter out, Mapping mapping) {
        out.println("<p>" + mapping.getUrl() + " : dans Controller (" + mapping.getController()
                + ") la methode associee est " + mapping.getMethod() + "()</p>");
    }
}
