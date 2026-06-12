package control;
import java.io.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

// @webservlet("/")
public class FrontControllerServlet extends HttpServlet{
    public void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
            response.setContentType("text/html");

            String url = request.getRequestURI();

            PrintWriter out = response.getWriter();
            out.println("Url recuperer : " + url);

        
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
            response.setContentType("text/html");

            String url = request.getRequestURI();

            PrintWriter out = response.getWriter();
            out.println("Url recuperer : " + url);

    }
}

