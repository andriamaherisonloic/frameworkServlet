package util;

import java.util.Locale;

/**
 * Verification de la signature binaire ("magic bytes") d'un fichier.
 *
 * <p>Sprint 8 - securite : on ne fait pas confiance au nom ni au type MIME
 * fournis par le client. Le contenu doit correspondre a l'extension annoncee.
 *
 * <p>Les extensions non reconnues sont considerees comme non verifiables et
 * donc refusees par {@code FileStorageService}.
 */
public final class FileSignature {

    private FileSignature() {
    }

    /**
     * @return vrai si le contenu binaire est compatible avec l'extension.
     */
    public static boolean matches(String extension, byte[] data) {
        if (extension == null || data == null || data.length == 0) {
            return false;
        }
        String ext = extension.toLowerCase(Locale.ROOT);

        switch (ext) {
            case "jpg":
            case "jpeg":
                return startsWith(data, 0xFF, 0xD8, 0xFF);

            case "png":
                return startsWith(data, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);

            case "gif":
                return startsWithAscii(data, "GIF87a") || startsWithAscii(data, "GIF89a");

            case "pdf":
                return startsWithAscii(data, "%PDF");

            case "zip":
            case "docx":
            case "xlsx":
            case "pptx":
            case "jar":
                // Archive ZIP : entete local (03 04) ou fin de repertoire (05 06).
                return startsWith(data, 0x50, 0x4B, 0x03, 0x04)
                        || startsWith(data, 0x50, 0x4B, 0x05, 0x06);

            case "doc":
            case "xls":
            case "ppt":
                // Ancien format Office (OLE2 / compound file).
                return startsWith(data, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1);

            case "txt":
            case "csv":
            case "md":
            case "log":
            case "json":
            case "xml":
            case "html":
            case "css":
            case "js":
                return isProbablyText(data);

            default:
                // Extension inconnue : impossible de verifier -> refuse.
                return false;
        }
    }

    private static boolean startsWith(byte[] data, int... expected) {
        if (data.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((data[i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWithAscii(byte[] data, String expected) {
        if (data.length < expected.length()) {
            return false;
        }
        for (int i = 0; i < expected.length(); i++) {
            if ((data[i] & 0xFF) != expected.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Heuristique pour les fichiers texte : pas d'octet nul et pas de
     * caracteres de controle bizarres dans les premiers 512 octets.
     */
    private static boolean isProbablyText(byte[] data) {
        int limit = Math.min(data.length, 512);
        for (int i = 0; i < limit; i++) {
            int value = data[i] & 0xFF;
            if (value == 0) {
                return false;
            }
            // Autorise tabulation, LF, FF, CR ; refuse les autres controles.
            if (value < 0x09 || (value > 0x0D && value < 0x20)) {
                return false;
            }
        }
        return true;
    }
}