package service;

import config.UploadConfig;
import exception.UploadException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import model.MaFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Extraction des fichiers d'une requete {@code multipart/form-data} (Sprint 8).
 *
 * <p>Cette classe ne fait que lire et valider la taille ; l'ecriture est
 * deleguee a {@link FileStorageService}.
 */
public class MultipartResolver {

    private final UploadConfig config;

    public MultipartResolver(UploadConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("UploadConfig ne peut pas etre null");
        }
        this.config = config;
    }

    /** Vrai si la requete est de type multipart/form-data. */
    public boolean isMultipart(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null
                && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/");
    }

    /**
     * Retourne le premier fichier portant ce nom.
     *
     * @return le fichier, ou {@code null} s'il est absent
     */
    public MaFile resolve(HttpServletRequest request, String name) {
        List<MaFile> files = resolveAll(request, name);
        return files.isEmpty() ? null : files.get(0);
    }

    /**
     * Retourne tous les fichiers portant ce nom (formulaire multi-fichiers).
     * Les champs texte portant le meme nom sont ignores.
     */
    public List<MaFile> resolveAll(HttpServletRequest request, String name) {
        if (!isMultipart(request)) {
            return Collections.emptyList();
        }

        List<MaFile> files = new ArrayList<>();
        try {
            for (Part part : request.getParts()) {
                if (!name.equals(part.getName())) {
                    continue;
                }
                String submitted = part.getSubmittedFileName();
                if (submitted == null || submitted.isBlank()) {
                    continue; // champ texte classique, pas un fichier
                }
                files.add(toMaFile(part));
            }
        } catch (IOException e) {
            throw UploadException.storage("Lecture du fichier impossible", e);
        } catch (ServletException e) {
            throw new UploadException("Requete multipart invalide", e, true);
        } catch (IllegalStateException e) {
            // Le conteneur leve cette exception quand maxRequestSize est depasse.
            throw new UploadException("Requete multipart invalide ou trop volumineuse", e, true);
        }
        return files;
    }

    private MaFile toMaFile(Part part) throws IOException {
        long declaredSize = part.getSize();
        if (declaredSize > config.getMaxSize()) {
            throw new UploadException("Fichier trop volumineux : " + declaredSize
                    + " octets (maximum " + config.getMaxSize() + ")");
        }

        byte[] data = readLimited(part, config.getMaxSize());
        if (data.length == 0) {
            throw new UploadException("Fichier vide : " + part.getSubmittedFileName());
        }
        return new MaFile(part.getSubmittedFileName(), part.getContentType(), data);
    }

    /** Lecture bornee : ne fait pas confiance a {@code Part.getSize()}. */
    private byte[] readLimited(Part part, long maxSize) throws IOException {
        try (InputStream in = part.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > maxSize) {
                    throw new UploadException("Fichier trop volumineux (maximum "
                            + maxSize + " octets)");
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }
}