package control;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import annotation.Controller;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import utils.Mapping;
import utils.Utilitaires;

@WebServlet("/")
public class FrontServlet extends HttpServlet{
    protected List<Mapping> urlMappings;

    @Override
    public void init() throws ServletException {
        try {
            String controllerPackage = this.getInitParameter("controllerPackage");

            if (controllerPackage != null && !controllerPackage.isEmpty()) {
                urlMappings = Utilitaires.getUrlMappings(Controller.class, controllerPackage);
            }

        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
        processRequest(request, response);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
        processRequest(request, response);
    }

    private void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html");

        String url = getRequestPath(request);
        Mapping mapping = findMapping(url);
        Mapping sameBaseMapping = findMappingWithSameBase(url);

        PrintWriter out = response.getWriter();
        out.println("<html><body>");
        out.println("<h2>Url recuperee : " + url + "</h2>");

        if (urlMappings == null || urlMappings.isEmpty()) {
            out.println("<p>Aucun lien supporte trouve.</p>");
        } else if (mapping != null) {
            out.println("<p>Lien defini.</p>");
            printMapping(out, mapping);
        } else if (sameBaseMapping != null) {
            out.println("<p>Lien non defini : " + url + "</p>");
            out.println("<p>Controller trouve pour ce prefixe : " + sameBaseMapping.getController() + "</p>");
            out.println("<p>Methode associee : aucune</p>");
        } else {
            out.println("<p>Lien non defini : " + url + "</p>");
            out.println("<h3>Liens supportes</h3>");
            printAllMappings(out);
        }

        out.println("</body></html>");
    }

    private String getRequestPath(HttpServletRequest request) {
        String url = request.getRequestURI();
        String contextPath = request.getContextPath();

        if (contextPath != null && !contextPath.isEmpty() && url.startsWith(contextPath)) {
            url = url.substring(contextPath.length());
        }

        if (url == null || url.isEmpty()) {
            return "/";
        }

        return url;
    }

    private Mapping findMapping(String url) {
        if (urlMappings == null) {
            return null;
        }

        for (Mapping mapping : urlMappings) {
            if (mapping.getUrl().equals(url)) {
                return mapping;
            }
        }

        return null;
    }

    private Mapping findMappingWithSameBase(String url) {
        if (urlMappings == null) {
            return null;
        }

        String urlBase = getBasePath(url);
        for (Mapping mapping : urlMappings) {
            if (getBasePath(mapping.getUrl()).equals(urlBase)) {
                return mapping;
            }
        }

        return null;
    }

    private String getBasePath(String url) {
        String[] parts = url.split("/");

        if (parts.length > 1) {
            return "/" + parts[1];
        }

        return url;
    }

    private void printAllMappings(PrintWriter out) {
        for (Mapping mapping : urlMappings) {
            printMapping(out, mapping);
        }
    }

    private void printMapping(PrintWriter out, Mapping mapping) {
        out.println("<p>" + mapping.getUrl() + " : dans Controller (" + mapping.getController()
                + ") la methode associee est " + mapping.getMethod() + "()</p>");
    }
}
