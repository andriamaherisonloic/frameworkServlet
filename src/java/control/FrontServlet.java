package control;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.io.PrintWriter;
import java.util.List;

import annotation.Controller;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import utils.Utilitaires;

@WebServlet("/")
public class FrontServlet extends HttpServlet{
    protected List<String> listeController;
    @Override
    public void init() throws ServletException {
        try {
            if (!this.getInitParameter("controllerPackage").isEmpty()) {
                String ControllerPackage = this.getInitParameter("controllerPackage");
                listeController = Utilitaires.getClassByPackageAndAnnotation(Controller.class, ControllerPackage,
                        ElementType.TYPE);
            }

        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
            response.setContentType("text/html");

            String url = request.getRequestURI();

            PrintWriter out = response.getWriter();
            out.println("<h1>Url recuperer : " + url + "</h1>");
            out.println("<h2>Controllers trouves :</h2>");
            out.println("<ul>");
            if (listeController != null && !listeController.isEmpty()) {
                for (String ctrl : listeController) {
                    out.println("<li>" + ctrl + "</li>");
                }
            } else {
                out.println("<li>Aucun @Controller trouve</li>");
            }
            out.println("</ul>");
        
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
            response.setContentType("text/html");

            String url = request.getRequestURI();

            PrintWriter out = response.getWriter();
            out.println("<h1>Url recuperer : " + url + "</h1>");
            out.println("<h2>Controllers trouves :</h2>");
            out.println("<ul>");
            if (listeController != null && !listeController.isEmpty()) {
                for (String ctrl : listeController) {
                    out.println("<li>" + ctrl + "</li>");
                }
            } else {
                out.println("<li>Aucun @Controller trouve</li>");
            }
            out.println("</ul>");
        
    }
}

