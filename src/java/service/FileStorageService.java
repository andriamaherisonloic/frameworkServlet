package service;

import config.UploadConfig;
import exception.UploadException;
import model.MaFile;
import util.FileSignature;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * Service responsable de l'enregistrement physique des fichiers (Sprint 8).
 *
 * <p>Regles de securite appliquees :
 * <ul>
 *   <li>nom d'origine nettoye et protection contre le path traversal ;</li>
 *   <li>le nom stocke est genere (UUID), jamais derive du nom client ;</li>
 *   <li>extension verifiee contre la liste blanche ;</li>
 *   <li>taille maximale verifiee ;</li>
 *   <li>signature binaire verifiee (pas de confiance au MIME client) ;</li>
 *   <li>le chemin final est normalise et doit rester sous le dossier configure.</li>
 * </ul>
 */
public class FileStorageService {

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM");

    /** Nombre d'essais en cas de collision de nom genere (tres improbable). */
    private static final int MAX_NAME_ATTEMPTS = 5;

    private final UploadConfig config;

    public FileStorageService(UploadConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("UploadConfig ne peut pas etre null");
        }
        this.config = config;
    }

    public UploadConfig getConfig() {
        return config;
    }

    /** Dossier racine configure (non cree par cet accesseur). */
    public Path getDirectory() {
        return config.getDirectory();
    }

    /**
     * Cree le dossier racine de stockage s'il n'existe pas.
     *
     * <p>Cette methode peut etre appelee au demarrage pour valider la
     * configuration. Elle est aussi appelee automatiquement par
     * {@link #save(MaFile)}.</p>
     *
     * @return le dossier racine absolu et normalise
     * @throws UploadException (erreur serveur) si le dossier ne peut pas etre cree
     */
    public Path ensureDirectory() {
        Path base = config.getDirectory().toAbsolutePath().normalize();
        try {
            Files.createDirectories(base);
            if (!Files.isDirectory(base)) {
                throw new IOException("Le chemin configure n'est pas un dossier : " + base);
            }
            return base;
        } catch (IOException e) {
            throw UploadException.storage(
                    "Impossible de creer le dossier de stockage : " + base, e);
        }
    }

    /**
     * Valide puis enregistre le fichier.
     *
     * <p>Le nom stocke est genere (UUID) : le nom envoye par le client n'est
     * jamais utilise comme nom physique, ce qui evite l'ecrasement et les
     * collisions. L'ecriture utilise {@code CREATE_NEW}.</p>
     *
     * @return le chemin absolu du fichier ecrit
     * @throws UploadException si le fichier est invalide (erreur client) ou si
     *         l'ecriture echoue (erreur serveur)
     */
    public Path save(MaFile file) {
        validate(file);

        String extension = extensionOf(sanitizeFileName(file.getFileName()));
        Path base = ensureDirectory();

        Path targetDirectory = base.resolve(LocalDate.now().format(DATE_PATH)).normalize();
        assertInside(base, targetDirectory);

        try {
            Files.createDirectories(targetDirectory);
        } catch (IOException e) {
            throw UploadException.storage(
                    "Impossible de creer le dossier de stockage : " + targetDirectory, e);
        }

        for (int attempt = 0; attempt < MAX_NAME_ATTEMPTS; attempt++) {
            Path target = newTarget(targetDirectory, extension, base);
            try {
                Files.write(target, file.getData(),
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                return target;
            } catch (FileAlreadyExistsException e) {
                // Collision de UUID : on regenere un nom au tour suivant.
            } catch (IOException e) {
                throw UploadException.storage("Impossible d'enregistrer le fichier", e);
            }
        }

        throw UploadException.storage(
                "Impossible de generer un nom de fichier unique dans " + targetDirectory, null);
    }

    private Path newTarget(Path directory, String extension, Path base) {
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path target = directory.resolve(storedName).normalize();
        assertInside(base, target);
        return target;
    }

    /** Valide le fichier sans l'ecrire. Utile pour les tests et la verification. */
    public void validate(MaFile file) {
        if (file == null) {
            throw new UploadException("Fichier absent");
        }
        if (file.isEmpty()) {
            throw new UploadException("Fichier vide ou absent");
        }

        String safeName = sanitizeFileName(file.getFileName());
        if (safeName == null) {
            throw new UploadException("Nom de fichier invalide ou dangereux");
        }

        if (file.getData().length > config.getMaxSize()) {
            throw new UploadException("Fichier trop volumineux : " + file.getData().length
                    + " octets (maximum " + config.getMaxSize() + ")");
        }

        String extension = extensionOf(safeName);
        if (extension.isEmpty()) {
            throw new UploadException("Le fichier doit avoir une extension");
        }
        if (!config.isAllowedExtension(extension)) {
            throw new UploadException("Extension non autorisee : ." + extension);
        }
        if (!FileSignature.matches(extension, file.getData())) {
            throw new UploadException("Le contenu du fichier ne correspond pas a son extension ."
                    + extension);
        }
    }

    /**
     * Nettoie le nom d'origine.
     *
     * @return le nom de base sur, ou {@code null} si le nom est dangereux.
     */
    public static String sanitizeFileName(String rawName) {
        if (rawName == null) {
            return null;
        }
        String name = rawName.trim();
        if (name.isEmpty() || name.indexOf('\0') >= 0) {
            return null;
        }

        // Certains navigateurs (anciens) envoient le chemin complet :
        // on ne garde que le nom de base, mais on refuse tout segment "..".
        String normalized = name.replace('\\', '/');
        for (String segment : normalized.split("/")) {
            if ("..".equals(segment)) {
                return null;
            }
        }
        int slash = normalized.lastIndexOf('/');
        String baseName = slash >= 0 ? normalized.substring(slash + 1) : normalized;

        if (baseName.isEmpty() || ".".equals(baseName) || "..".equals(baseName)) {
            return null;
        }

        StringBuilder cleaned = new StringBuilder(baseName.length());
        for (int i = 0; i < baseName.length(); i++) {
            char c = baseName.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                return null;
            }
            // Caracteres interdits sous Windows : remplaces par "_".
            if ("<>:\"|?*".indexOf(c) >= 0) {
                cleaned.append('_');
            } else {
                cleaned.append(c);
            }
        }

        String result = cleaned.toString().trim();
        if (result.isEmpty() || ".".equals(result) || "..".equals(result)) {
            return null;
        }
        return result.length() > 255 ? result.substring(result.length() - 255) : result;
    }

    /** Extension en minuscules sans point, ou chaine vide. */
    public static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static void assertInside(Path base, Path candidate) {
        if (!candidate.startsWith(base)) {
            throw new UploadException("Chemin de stockage invalide (path traversal)");
        }
    }
}