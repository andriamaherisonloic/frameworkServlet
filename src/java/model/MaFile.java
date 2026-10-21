package model;

/**
 * Fichier recu depuis un formulaire {@code multipart/form-data} (Sprint 8).
 *
 * <p>Le framework instancie automatiquement cet objet et l'injecte dans la
 * methode d'action lorsque le type du parametre attendu est {@code MaFile}.
 *
 * <p>Convention : {@link #getType()} contient le type MIME declare par le
 * client. Cette valeur n'est <b>pas</b> fiable ; la validation reelle est
 * faite par le service de stockage (extension + signature binaire).
 */
public class MaFile {

    /** Nom d'origine du fichier, tel qu'envoye par le navigateur. */
    private String fileName;

    /** Type MIME declare par le client (peut etre null). */
    private String type;

    /** Contenu binaire du fichier. */
    private byte[] data;

    public MaFile() {
    }

    public MaFile(String fileName, String type, byte[] data) {
        this.fileName = fileName;
        this.type = type;
        this.data = data;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    /** Taille du fichier en octets (0 si le contenu est absent). */
    public long getSize() {
        return data == null ? 0 : data.length;
    }

    /**
     * Vrai si le fichier ne contient aucun octet.
     *
     * <p>Un fichier sans nom mais avec du contenu n'est pas considere comme
     * vide : cette methode ne regarde que le contenu binaire.</p>
     */
    public boolean isEmpty() {
        return data == null || data.length == 0;
    }

    /** Vrai si le fichier contient au moins un octet. */
    public boolean hasContent() {
        return !isEmpty();
    }

    /** Extension en minuscules, sans point, ou chaine vide. */
    public String getExtension() {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public String toString() {
        return "MaFile{fileName='" + fileName + "', type='" + type + "', size=" + getSize() + "}";
    }
}