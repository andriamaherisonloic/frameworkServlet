package utils;

import java.util.Objects;

public class UtilMethode {
    private String url;
    private String method;

    public UtilMethode() {
        this.method = "GET";
    }

    public UtilMethode(String url, String method) {
        this.url = url;
        this.method = method != null ? method.toUpperCase() : "GET";
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method != null ? method.toUpperCase() : "GET";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UtilMethode)) return false;
        UtilMethode that = (UtilMethode) o;
        return Objects.equals(url, that.url) && Objects.equals(method, that.method);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, method);
    }

    @Override
    public String toString() {
        return "[" + method + "] " + url;
    }
}
