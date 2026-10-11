package control;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import annotation.MyRequestParam;
import annotation.RepositoryAnnotation;
import annotation.UrlMapping;
import annotation.WebApi;
import context.ApplicationContext;
import exception.BindingException;
import mapping.UrlMethod;
import model.Model;
import model.ModelAndView;
import util.JsonSerializer;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class FrontServlet extends HttpServlet {

    private static final int MAX_BINDING_PREFIXES = 4;

    private Map<UrlMethod, Method> urlMappings = new HashMap<>();
    private ApplicationContext applicationContext;

    @Override
    public void init() throws ServletException {
        super.init();
        applicationContext = (ApplicationContext) getServletContext().getAttribute("applicationContext");
        rebuildRegistry();
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
            urlMappings = new HashMap<>();

            for (Class<?> controllerClass : applicationContext.getBeanClasses()) {
                if (controllerClass.isAnnotationPresent(RepositoryAnnotation.class)) {
                    continue;
                }

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

            getServletContext().setAttribute("urlMappings", urlMappings);
        } catch (ServletException e) {
            getServletContext().setAttribute("initError", e.getMessage());
            throw e;
        } catch (Exception e) {
            throw new ServletException("Erreur initialisation registre URL", e);
        }
    }

    private Map<UrlMethod, Method> getUrlMappings(Class<?> controllerClass) {
        Map<UrlMethod, Method> mappings = new HashMap<>();
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
            if (mappings.containsKey(key)) {
                Method existing = mappings.get(key);
                throw new RuntimeException(
                        "UrlMapping dupliqué : " + key
                        + " (déjà déclaré dans " + existing.getDeclaringClass().getName()
                        + "." + existing.getName()
                        + ") en conflit avec "
                        + controllerClass.getName() + "." + method.getName());
            }
            mappings.put(key, method);
        }
        return mappings;
    }

    private String resolveUrl(UrlMapping mapping) {
        if (mapping.path() != null && !mapping.path().isBlank()) {
            return mapping.path();
        }
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

        String requestPath = normalizePath(req.getPathInfo());
        if ("/".equals(requestPath)) {
            renderControllerIndex(res);
            return;
        }

        String httpMethod = req.getMethod();
        Method method = getMethodForUrl(requestPath, httpMethod);
        if (method == null) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND, "Aucune route trouvée pour " + requestPath);
            return;
        }

        try {
            Object controller = getControllerInstance(method.getDeclaringClass().getName());
            if (controller == null) {
                res.sendError(HttpServletResponse.SC_NOT_FOUND, "Contrôleur introuvable pour " + requestPath);
                return;
            }

            Object result = method.invoke(controller, buildArguments(method, req, res));
            if (isWebApi(method)) {
                renderJson(res, result);
                return;
            }

            if (result instanceof ModelAndView) {
                renderModelAndView(req, res, (ModelAndView) result);
                return;
            }

            if (result instanceof String) {
                renderView(req, res, (String) result, Map.of());
                return;
            }

            res.sendError(HttpServletResponse.SC_NO_CONTENT);
        } catch (BindingException e) {
            // Erreur de liaison : la faute est dans les données envoyees par le client.
            getServletContext().log("Data binding : " + e.getMessage(), e);
            res.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            throw new ServletException("Erreur invocation de " + method.getName(), e);
        }
    }

    private boolean isWebApi(Method method) {
        return method.isAnnotationPresent(WebApi.class)
                || method.getDeclaringClass().isAnnotationPresent(WebApi.class);
    }

    private void renderJson(HttpServletResponse res, Object result) throws IOException {
        Object value = result instanceof ModelAndView modelAndView ? modelAndView.getModel() : result;

        res.setContentType("application/json; charset=UTF-8");
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write(JsonSerializer.toJson(value));
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

    protected Object getControllerInstance(String className) {
        if (applicationContext == null) {
            return null;
        }
        return applicationContext.getBean(className);
    }

    private void renderControllerIndex(HttpServletResponse res) throws IOException {
        List<String> controllers = new ArrayList<>();
        for (Class<?> clazz : applicationContext.getBeanClasses()) {
            if (!clazz.isAnnotationPresent(RepositoryAnnotation.class)) {
                controllers.add(clazz.getSimpleName());
            }
        }

        controllers = controllers.stream()
                .sorted(String::compareToIgnoreCase)
                .collect(Collectors.toList());

        res.setContentType("text/html; charset=UTF-8");
        PrintWriter writer = res.getWriter();
        writer.println("<!DOCTYPE html>");
        writer.println("<html><head><meta charset=\"UTF-8\"><title>Contrôleurs annotés</title></head><body>");
        writer.println("<h1>Liste des contrôleurs @MyController</h1>");
        writer.println("<ul>");
        for (String controller : controllers) {
            writer.println("<li>" + controller + "</li>");
        }
        writer.println("</ul>");
        writer.println("</body></html>");
    }

    private Object[] buildArguments(Method method, HttpServletRequest req, HttpServletResponse res)
            throws ServletException {
        Parameter[] parameters = method.getParameters();
        Object[] arguments = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            arguments[i] = resolveArgument(parameters[i], req, res);
        }
        return arguments;
    }

    private Object resolveArgument(Parameter parameter, HttpServletRequest req, HttpServletResponse res)
            throws ServletException {
        MyRequestParam requestParam = parameter.getAnnotation(MyRequestParam.class);
        if (requestParam != null) {
            return resolveRequestParam(parameter, requestParam.value(), req);
        }

        Class<?> parameterType = parameter.getType();
        if (HttpServletRequest.class.isAssignableFrom(parameterType)) {
            return req;
        }
        if (HttpServletResponse.class.isAssignableFrom(parameterType)) {
            return res;
        }
        if (Model.class.isAssignableFrom(parameterType)) {
            return new Model();
        }
        if (Map.class.isAssignableFrom(parameterType)) {
            return new LinkedHashMap<String, Object>();
        }

        // Sprint 7 : type simple non annote -> nom du parametre (flag -parameters),
        // sinon valeur par defaut (null, 0, false, '\0').
        if (isSimpleType(parameterType)) {
            return resolveOptionalValue(parameter, parameter.getName(), req);
        }

        // Sprint 7 bis : objet complexe -> liaison champ par champ.
        return bindObject(parameterType, new LinkedHashSet<>(List.of(parameter.getName())), req, parameter);
    }

    /**
     * Lie un parametre de type simple facultatif : si la requete ne porte pas
     * la valeur, on renvoie la valeur par defaut du type au lieu de lever une
     * exception (evite {@code IllegalArgumentException} a l'invocation).
     */
    private Object resolveOptionalValue(Parameter parameter, String name, HttpServletRequest req) {
        String raw = req.getParameter(name);
        if (raw == null) {
            return defaultValue(parameter.getType());
        }
        return convertParamValue(raw, parameter.getType(), name);
    }

    /**
     * Valeur par defaut : {@code null} pour les references (String, wrappers),
     * valeur neutre du type pour les primitifs.
     */
    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return Boolean.FALSE;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == double.class) {
            return 0d;
        }
        return null;
    }

    private Object resolveRequestParam(Parameter parameter, String name, HttpServletRequest req)
            throws ServletException {
        Class<?> targetType = parameter.getType();

        if (isSimpleType(targetType)) {
            String value = req.getParameter(name);
            if (value == null) {
                throw new BindingException("Paramètre obligatoire manquant : " + name + describe(parameter),
                        name, null);
            }
            return convertParamValue(value, targetType, name);
        }

        return bindObject(targetType, new LinkedHashSet<>(List.of(name)), req, parameter);
    }

    private boolean isSimpleType(Class<?> type) {
        return type == String.class
                || type == CharSequence.class
                || type == Object.class
                || type.isPrimitive()
                || type.isEnum()
                || type == Boolean.class
                || type == Character.class
                || Number.class.isAssignableFrom(type);
    }

    /**
     * Sprint 7 bis : lie les champs d'un objet complexe.
     *
     * <p>Regles :
     * <ul>
     *   <li>parcours la hierarchie : champs declares de la classe puis de ses
     *       superclasses ;</li>
     *   <li>champs statiques, finaux ou synthetiques ignores ;</li>
     *   <li>injection via le setter {@code setXxx} s'il existe et qu'il est
     *       compatible avec le type du champ, sinon acces direct ;</li>
     *   <li>les champs de type non simple (objet imbrique, {@code List}...)
     *       sont ignores et restent a leur valeur par defaut.</li>
     * </ul>
     */
    private Object bindObject(Class<?> targetType, Set<String> prefixes, HttpServletRequest req, Parameter parameter)
            throws ServletException {
        Object instance = instantiate(targetType, primaryPrefix(prefixes), parameter);

        for (Field field : collectFields(targetType)) {
            // Les champs non simples restent a leur valeur par defaut.
            if (!isSimpleType(field.getType())) {
                continue;
            }

            String label = null;
            String value = null;
            for (String candidate : candidates(prefixes, field.getName())) {
                String raw = req.getParameter(candidate);
                if (raw != null) {
                    value = raw;
                    label = candidate;
                    break;
                }
            }

            if (value != null) {
                injectValue(field, instance, convertFieldValue(value, field.getType(), label), label);
            }
        }
        return instance;
    }

    /** Libelle principal, utilise uniquement dans les messages d'erreur. */
    private String primaryPrefix(Set<String> prefixes) {
        return prefixes.isEmpty() ? "objet" : prefixes.iterator().next();
    }

    /**
     * Champs eligibles au binding, du plus specifique au plus general.
     * {@code getDeclaredFields()} seul ne remonte pas la hierarchie.
     */
    private List<Field> collectFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            fields.addAll(Arrays.asList(current.getDeclaredFields()));
        }
        return fields;
    }

    /** Noms de parametres possibles pour un champ : prefixes, puis nom nu. */
    private Set<String> candidates(Set<String> prefixes, String fieldName) {
        Set<String> candidates = new LinkedHashSet<>();
        for (String prefix : prefixes) {
            candidates.add(prefix + "." + fieldName);
        }
        candidates.add(fieldName);

        // Au-dela du quota, on retombe sur le nom nu.
        Set<String> kept = new LinkedHashSet<>();
        for (String candidate : candidates) {
            if (kept.size() >= MAX_BINDING_PREFIXES) {
                break;
            }
            kept.add(candidate);
        }
        kept.add(fieldName);
        return kept;
    }

    /**
     * Cherche un setter {@code setXxx} compatible avec le type du champ.
     * En cas de surcharge, le parametre le plus specifique est retenu.
     */
    private Method findSetter(Class<?> type, Field field) {
        String setterName = "set"
                + Character.toUpperCase(field.getName().charAt(0))
                + field.getName().substring(1);

        Method found = null;
        for (Method candidate : type.getMethods()) {
            if (!candidate.getName().equals(setterName) || candidate.getParameterCount() != 1) {
                continue;
            }
            Class<?> parameterType = candidate.getParameterTypes()[0];
            if (!isCompatible(field.getType(), parameterType)) {
                continue;
            }
            if (found == null || isMoreSpecific(parameterType, found.getParameterTypes()[0])) {
                found = candidate;
            }
        }
        return found;
    }

    /** Le setter peut-il recevoir une valeur du type du champ ? */
    private boolean isCompatible(Class<?> fieldType, Class<?> setterType) {
        if (fieldType.equals(setterType)) {
            return true;
        }
        // primitif -> wrapper (int -> Integer)
        if (isWrapperOf(setterType, fieldType)) {
            return true;
        }
        // un setter acceptant une supertype convient aussi (Integer -> Number/Object)
        return setterType.isAssignableFrom(fieldType);
    }

    private boolean isWrapperOf(Class<?> wrapper, Class<?> primitive) {
        return (primitive == boolean.class && wrapper == Boolean.class)
                || (primitive == char.class && wrapper == Character.class)
                || (primitive == byte.class && wrapper == Byte.class)
                || (primitive == short.class && wrapper == Short.class)
                || (primitive == int.class && wrapper == Integer.class)
                || (primitive == long.class && wrapper == Long.class)
                || (primitive == float.class && wrapper == Float.class)
                || (primitive == double.class && wrapper == Double.class);
    }

    private boolean isMoreSpecific(Class<?> candidate, Class<?> current) {
        return current.isAssignableFrom(candidate) && !candidate.equals(current);
    }

    /** Injecte via le setter s'il existe, sinon par acces direct au champ. */
    private void injectValue(Field field, Object instance, Object value, String label) throws ServletException {
        Method setter = findSetter(instance.getClass(), field);
        if (setter != null) {
            try {
                setter.invoke(instance, value);
                return;
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new BindingException("Paramètre " + label + " : le setter " + setter.getName()
                        + " a échoué", e, label, null);
            }
        }
        setField(field, instance, value, label);
    }

    private void setField(Field field, Object instance, Object value, String label) throws ServletException {
        try {
            field.setAccessible(true);
            field.set(instance, value);
        } catch (IllegalAccessException e) {
            throw new BindingException("Paramètre " + label + " : champ inaccessible " + field.getName(), e, label,
                    null);
        }
    }

    private Object instantiate(Class<?> targetType, String name, Parameter parameter) throws ServletException {
        try {
            Constructor<?> constructor = targetType.getDeclaredConstructor();
            if (!Modifier.isPublic(constructor.getModifiers())) {
                constructor.setAccessible(true);
            }
            return constructor.newInstance();
        } catch (NoSuchMethodException e) {
            throw new BindingException("Paramètre " + name + " : la classe " + targetType.getName()
                    + " doit avoir un constructeur vide pour etre liée" + describe(parameter), e, name, null);
        } catch (Exception e) {
            throw new BindingException("Paramètre " + name + " : impossible d'instancier " + targetType.getName()
                    + describe(parameter), e, name, null);
        }
    }

    private Object convertFieldValue(String value, Class<?> fieldType, String label) throws ServletException {
        if (!isSimpleType(fieldType)) {
            throw new BindingException("Paramètre " + label + " : impossible de convertir \"" + value + "\" en "
                    + fieldType.getName() + ". Pour un objet, utilisez un préfixe (ex. " + label + ".*)", label, value);
        }
        return convertParamValue(value, fieldType, label);
    }

    private String describe(Parameter parameter) {
        return " (paramètre " + parameter.getName()
                + " de " + parameter.getDeclaringExecutable().getDeclaringClass().getName()
                + "." + parameter.getDeclaringExecutable().getName() + ")";
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Object convertParamValue(String value, Class<?> targetType, String name) {
        String raw = value.trim();

        try {
            if (targetType == String.class || targetType == CharSequence.class || targetType == Object.class) {
                return value;
            }
            if (targetType == boolean.class || targetType == Boolean.class) {
                return parseBoolean(raw);
            }
            if (targetType == char.class || targetType == Character.class) {
                if (raw.length() != 1) {
                    throw new BindingException("Paramètre " + name + " : un seul caractère est attendu, reçu \""
                            + value + "\"", name, value);
                }
                return raw.charAt(0);
            }
            if (targetType == int.class || targetType == Integer.class) {
                return (int) checkRange(Long.parseLong(raw), Integer.MIN_VALUE, Integer.MAX_VALUE, name, value);
            }
            if (targetType == long.class || targetType == Long.class) {
                return Long.parseLong(raw);
            }
            if (targetType == short.class || targetType == Short.class) {
                return (short) checkRange(Long.parseLong(raw), Short.MIN_VALUE, Short.MAX_VALUE, name, value);
            }
            if (targetType == byte.class || targetType == Byte.class) {
                return (byte) checkRange(Long.parseLong(raw), Byte.MIN_VALUE, Byte.MAX_VALUE, name, value);
            }
            if (targetType == double.class || targetType == Double.class) {
                return Double.parseDouble(raw);
            }
            if (targetType == float.class || targetType == Float.class) {
                return Float.parseFloat(raw);
            }
            if (targetType == BigInteger.class) {
                return new BigInteger(raw);
            }
            if (targetType == BigDecimal.class) {
                return new BigDecimal(raw);
            }
            if (targetType.isEnum()) {
                return Enum.valueOf((Class<Enum>) targetType, raw.toUpperCase());
            }
        } catch (BindingException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new BindingException("Paramètre " + name + " : impossible de convertir \"" + value
                    + "\" en " + targetType.getSimpleName(), e, name, value);
        }

        throw new BindingException("Paramètre " + name + " : type non supporté " + targetType.getName()
                + ". Utilisez @MyRequestParam uniquement avec un type simple ou une énumération.", name, value);
    }

    private boolean parseBoolean(String raw) {
        if ("true".equalsIgnoreCase(raw) || "on".equalsIgnoreCase(raw) || "yes".equalsIgnoreCase(raw)
                || "1".equals(raw)) {
            return true;
        }
        return false;
    }

    private long checkRange(long parsed, long min, long max, String name, String value) {
        if (parsed < min || parsed > max) {
            throw new BindingException("Paramètre " + name + " : la valeur \"" + value + "\" est hors limites ("
                    + min + " à " + max + ")", name, value);
        }
        return parsed;
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

        String prefix = getServletContext().getInitParameter("viewPrefix");
        String suffix = getServletContext().getInitParameter("viewSuffix");
        if (prefix == null) {
            prefix = "/WEB-INF/views/";
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

}
