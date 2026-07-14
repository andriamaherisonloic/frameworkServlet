package control;

import java.util.HashMap;

import utils.Mapping;
import utils.UtilMethode;
import utils.Utilitaires;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

@WebListener
public class ApplicationListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {

        try {

            ServletContext context = sce.getServletContext();

            String pack = context.getInitParameter("controller");
            if (pack == null || pack.isBlank()) {
                pack = "controller";
            }

            HashMap<UtilMethode, Mapping> urlMapping = new HashMap<>();

            Utilitaires.getUrlAndMethod(pack, urlMapping);

            context.setAttribute("viewPrefix",
                    context.getInitParameter("viewPrefix"));

            context.setAttribute("viewSuffix",
                    context.getInitParameter("viewSuffix"));

            context.setAttribute("urlMapping", urlMapping);

            System.out.println("Framework initialise");

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {

    }

}
