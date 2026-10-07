package utils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Sprint 7 : binding des parametres simples des methodes de controller.
 *
 * Pour chaque parametre de type simple (String, int, double, ...), la valeur
 * correspondante est recuperee dans la requete via request.getParameter(nomDuParametre),
 * puis convertie vers le type declare dans la signature de la methode.
 *
 * Regles :
 *  - par defaut, chaque parametre est initialise a null
 *    (valeur par defaut 0/false pour les types primitifs qui ne peuvent pas etre null) ;
 *  - si le parametre est present dans la requete, sa valeur remplace la valeur par defaut ;
 *  - les objets complexes (ex : Employe, Personne) ne sont PAS encore bindes dans
 *    cette version : une BindingException est levee.
 */
public class ParamBinder {

    private static final Set<Class<?>> SIMPLE_TYPES = new HashSet<>();

    static {
        SIMPLE_TYPES.add(String.class);

        SIMPLE_TYPES.add(Integer.class);
        SIMPLE_TYPES.add(Long.class);
        SIMPLE_TYPES.add(Double.class);
        SIMPLE_TYPES.add(Float.class);
        SIMPLE_TYPES.add(Short.class);
        SIMPLE_TYPES.add(Byte.class);
        SIMPLE_TYPES.add(Boolean.class);
        SIMPLE_TYPES.add(Character.class);

        SIMPLE_TYPES.add(BigDecimal.class);
        SIMPLE_TYPES.add(BigInteger.class);

        SIMPLE_TYPES.add(LocalDate.class);
        SIMPLE_TYPES.add(LocalDateTime.class);
        SIMPLE_TYPES.add(java.util.Date.class);
        SIMPLE_TYPES.add(java.sql.Date.class);
    }

    private ParamBinder() {
    }

    /**
     * Indique si le type est un type simple pris en charge par le binding
     * (String, primitifs et leurs wrappers, nombres, booleens, dates simples, enums).
     */
    public static boolean isSimpleType(Class<?> type) {
        return type.isPrimitive() || type.isEnum() || SIMPLE_TYPES.contains(type);
    }

    /**
     * Recupere la valeur du parametre dans la requete et la convertit vers
     * le type declare dans la signature de la methode.
     */
    public static Object bindParameter(Method method, Parameter parameter, Class<?> type,
            HttpServletRequest req) throws BindingException {
        String name = resolveParameterName(method, parameter);
        String rawValue = req.getParameter(name);
        return convert(rawValue, type, name, method);
    }

    /**
     * Nom du parametre utilise pour request.getParameter(...).
     * Necessite la compilation du projet web avec l'option javac -parameters.
     */
    private static String resolveParameterName(Method method, Parameter parameter) throws BindingException {
        if (!parameter.isNamePresent()) {
            throw new BindingException(
                    "Nom du parametre indisponible pour " + description(method, parameter) + ". "
                    + "Recompilez le projet web avec l'option javac -parameters pour activer le binding.");
        }
        return parameter.getName();
    }

    /**
     * Convertit une valeur brute (String) vers le type attendu.
     * Une valeur absente (null) ou vide donne null, sauf pour les primitifs
     * qui recoivent leur valeur par defaut (0, false, ...).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object convert(String rawValue, Class<?> targetType, String paramName, Method method)
            throws BindingException {

        if (targetType == String.class) {
            return rawValue;
        }

        if (rawValue == null || rawValue.trim().isEmpty()) {
            return defaultValue(targetType);
        }

        String value = rawValue.trim();

        try {
            if (targetType == int.class || targetType == Integer.class) {
                return Integer.parseInt(value);
            }
            if (targetType == long.class || targetType == Long.class) {
                return Long.parseLong(value);
            }
            if (targetType == double.class || targetType == Double.class) {
                return Double.parseDouble(value);
            }
            if (targetType == float.class || targetType == Float.class) {
                return Float.parseFloat(value);
            }
            if (targetType == short.class || targetType == Short.class) {
                return Short.parseShort(value);
            }
            if (targetType == byte.class || targetType == Byte.class) {
                return Byte.parseByte(value);
            }
            if (targetType == boolean.class || targetType == Boolean.class) {
                return parseBoolean(value);
            }
            if (targetType == char.class || targetType == Character.class) {
                return value.charAt(0);
            }
            if (targetType == BigDecimal.class) {
                return new BigDecimal(value);
            }
            if (targetType == BigInteger.class) {
                return new BigInteger(value);
            }
            if (targetType == LocalDate.class) {
                return LocalDate.parse(value);
            }
            if (targetType == LocalDateTime.class) {
                return LocalDateTime.parse(value);
            }
            if (targetType == java.sql.Date.class) {
                return java.sql.Date.valueOf(value);
            }
            if (targetType == java.util.Date.class) {
                return java.sql.Date.valueOf(value);
            }
            if (targetType.isEnum()) {
                return Enum.valueOf((Class<? extends Enum>) targetType.asSubclass(Enum.class), value);
            }
        } catch (Exception e) {
            throw new BindingException(
                    "Impossible de convertir la valeur \"" + rawValue + "\" du parametre \"" + paramName
                    + "\" vers le type " + targetType.getSimpleName()
                    + " (" + description(method, null) + ")", e);
        }

        throw new BindingException("Type simple non gere : " + targetType.getName()
                + " (" + description(method, null) + ")");
    }

    /**
     * Valeur par defaut d'un parametre absent de la requete :
     * null pour les objets, 0/false/'\0' pour les primitifs.
     */
    public static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
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

    /**
     * Accepte les valeurs classiques des formulaires HTML
     * (cases a cocher, booleens) : true/1/on/yes.
     */
    private static boolean parseBoolean(String value) {
        return "true".equalsIgnoreCase(value)
                || "1".equals(value)
                || "on".equalsIgnoreCase(value)
                || "yes".equalsIgnoreCase(value);
    }

    private static String description(Method method, Parameter parameter) {
        StringBuilder sb = new StringBuilder();
        sb.append(method.getDeclaringClass().getSimpleName()).append('.').append(method.getName()).append("()");
        if (parameter != null) {
            sb.append(", parametre ").append(parameter);
        }
        return sb.toString();
    }
}
