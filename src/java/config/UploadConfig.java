package config;

import jakarta.servlet.ServletContext;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/**
 * Configuration de l'upload (Sprint 8).
 *
 * <p>Ordre de priorite (du plus fort au plus faible) :
 * <ol>
 *   <li>parametres d'initialisation du {@link ServletContext} :
 *       {@code upload-directory}, {@code upload-max-size},
 *       {@code upload-allowed-extensions} ;</li>
 *   <li>fichier pointe par la propriete systeme
 *       {@code -Dframework.upload.properties=/chemin/upload.properties} ;</li>
 *   <li>ressource classpath {@code /upload.properties} ;</li>
 *   <li>valeurs par defaut ci-dessous.</li>
 * </ol>
 *
 * <p>Le chemin de stockage n'est jamais code en dur dans le code source :
 * il provient de la configuration.
 */
public class UploadConfig {

    public static final String PROPERTIES_RESOURCE = "/upload.properties";
    public static final String PROPERTIES_SYSTEM_PROPERTY = "framework.upload.properties";

    public static final String KEY_DIRECTORY = "upload.directory";
    public static final String KEY_MAX_SIZE = "upload.max-size";
    public static final String KEY_ALLOWED_EXTENSIONS = "upload.allowed-extensions";

    public static final String DEFAULT_DIRECTORY = "uploads";
    public static final long DEFAULT_MAX_SIZE = 5L * 1024 * 1024; // 5 Mo
    public static final String DEFAULT_ALLOWED_EXTENSIONS =
            "jpg,jpeg,png,gif,pdf,txt,doc,docx,xls,xlsx,zip";

    private final Path directory;
    private final long maxSize;
    private final Set<String> allowedExtensions;

    public UploadConfig(Path directory, long maxSize, Set<String> allowedExtensions) {
        if (directory == null) {
            throw new IllegalArgumentException("upload.directory ne peut pas etre null");
        }
        if (maxSize <= 0) {
            throw new IllegalArgumentException("upload.max-size doit etre > 0");
        }
        this.directory = directory.toAbsolutePath().normalize();
        this.maxSize = maxSize;
        this.allowedExtensions = normalizeExtensions(allowedExtensions);
    }

    /** Charge la configuration depuis le contexte web. */
    public static UploadConfig load(ServletContext context) {
        Properties properties = readProperties();

        if (context != null) {
            override(properties, KEY_DIRECTORY, context.getInitParameter("upload-directory"));
            override(properties, KEY_MAX_SIZE, context.getInitParameter("upload-max-size"));
            override(properties, KEY_ALLOWED_EXTENSIONS, context.getInitParameter("upload-allowed-extensions"));
        }

        return fromProperties(properties);
    }

    /** Construit une configuration a partir de proprietes brutes. */
    public static UploadConfig fromProperties(Properties properties) {
        String rawDirectory = properties.getProperty(KEY_DIRECTORY, DEFAULT_DIRECTORY);
        long maxSize = parseMaxSize(properties.getProperty(KEY_MAX_SIZE));
        Set<String> extensions = parseExtensions(
                properties.getProperty(KEY_ALLOWED_EXTENSIONS, DEFAULT_ALLOWED_EXTENSIONS));

        return new UploadConfig(resolveDirectory(rawDirectory), maxSize, extensions);
    }

    /** Configuration par defaut, utile pour les tests. */
    public static UploadConfig defaults() {
        return new UploadConfig(resolveDirectory(DEFAULT_DIRECTORY), DEFAULT_MAX_SIZE,
                parseExtensions(DEFAULT_ALLOWED_EXTENSIONS));
    }

    public Path getDirectory() {
        return directory;
    }

    public long getMaxSize() {
        return maxSize;
    }

    public Set<String> getAllowedExtensions() {
        return allowedExtensions;
    }

    public boolean isAllowedExtension(String extension) {
        return extension != null
                && allowedExtensions.contains(extension.toLowerCase(Locale.ROOT));
    }

    // ------------------------------------------------------------------
    // Chargement
    // ------------------------------------------------------------------

    private static Properties readProperties() {
        Properties properties = new Properties();

        // 1. Ressource embarquee dans le jar du framework (valeurs par defaut).
        try (InputStream in = UploadConfig.class.getResourceAsStream(PROPERTIES_RESOURCE)) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException e) {
            // Une ressource illisible ne doit pas empecher le demarrage :
            // on retombe sur les valeurs par defaut.
        }

        // 2. Surcharge par un fichier externe.
        String overridePath = System.getProperty(PROPERTIES_SYSTEM_PROPERTY);
        if (overridePath != null && !overridePath.isBlank()) {
            try (InputStream in = new FileInputStream(overridePath)) {
                properties.load(in);
            } catch (IOException e) {
                throw new IllegalStateException(
                        "Fichier de configuration d'upload illisible : " + overridePath, e);
            }
        }

        return properties;
    }

    private static void override(Properties properties, String key, String value) {
        if (value != null && !value.isBlank()) {
            properties.setProperty(key, value);
        }
    }

    /**
     * Un chemin relatif est resolu par rapport a {@code catalina.base}
     * (Tomcat) ou, a defaut, au repertoire de travail. Un chemin absolu est
     * utilise tel quel.
     */
    private static Path resolveDirectory(String raw) {
        String value = (raw == null || raw.isBlank()) ? DEFAULT_DIRECTORY : raw.trim();
        Path path = Paths.get(value);
        if (path.isAbsolute()) {
            return path.normalize();
        }

        String base = System.getProperty("catalina.base");
        if (base == null || base.isBlank()) {
            base = System.getProperty("user.dir");
        }
        return Paths.get(base).resolve(path).normalize();
    }

    private static long parseMaxSize(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_MAX_SIZE;
        }
        try {
            long value = Long.parseLong(raw.trim());
            return value > 0 ? value : DEFAULT_MAX_SIZE;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("upload.max-size invalide : " + raw, e);
        }
    }

    private static Set<String> parseExtensions(String csv) {
        Set<String> result = new LinkedHashSet<>();
        if (csv == null) {
            return Collections.unmodifiableSet(result);
        }
        for (String token : csv.split(",")) {
            String extension = token.trim().toLowerCase(Locale.ROOT);
            if (extension.startsWith(".")) {
                extension = extension.substring(1);
            }
            if (!extension.isEmpty()) {
                result.add(extension);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    private static Set<String> normalizeExtensions(Set<String> extensions) {
        Set<String> result = new LinkedHashSet<>();
        if (extensions != null) {
            for (String extension : extensions) {
                if (extension != null && !extension.isBlank()) {
                    result.add(extension.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return Collections.unmodifiableSet(result);
    }

    @Override
    public String toString() {
        return "UploadConfig{directory=" + directory
                + ", maxSize=" + maxSize
                + ", allowedExtensions=" + allowedExtensions + "}";
    }
}