package utils;

public class Mapping {
    private String url;
    private String controller;
    private String method;

    public Mapping(String url, String controller, String method) {
        this.url = url;
        this.controller = controller;
        this.method = method;
    }

    public String getUrl() {
        return url;
    }

    public String getController() {
        return controller;
    }

    public String getMethod() {
        return method;
    }
}
