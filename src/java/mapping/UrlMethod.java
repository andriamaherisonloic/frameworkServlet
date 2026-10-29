package mapping;

import java.util.Objects;

/**
 * Cle d'association entre une URL et une methode HTTP.
 */
public class UrlMethod {

    private String url;
    private String httpMethod;

    public UrlMethod() {
        this.httpMethod = "GET";
    }

    public UrlMethod(String url, String httpMethod) {
        this.url = url;
        this.httpMethod = normalizeHttpMethod(httpMethod);
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMethod() {
        return httpMethod;
    }

    public void setMethod(String httpMethod) {
        this.httpMethod = normalizeHttpMethod(httpMethod);
    }

    private String normalizeHttpMethod(String method) {
        return method == null ? "GET" : method.toUpperCase();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UrlMethod that)) {
            return false;
        }
        return Objects.equals(url, that.url) && Objects.equals(httpMethod, that.httpMethod);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, httpMethod);
    }

    @Override
    public String toString() {
        return "[" + httpMethod + "] " + url;
    }
}