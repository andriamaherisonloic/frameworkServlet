package control;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import annotation.Controller;
import utils.Utilitaires;

import java.util.ArrayList;
import java.util.List;

@WebListener
public class FrontServletListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try {
            ServletContext context = sce.getServletContext();
            String packageName = context.getInitParameter("controllerPackage");
            if (packageName == null || packageName.isBlank()) {
                packageName = "control";
            }

            List<Class<?>> controllers = new ArrayList<>();
            controllers.addAll(Utilitaires.getClassesByPackageAndAnnotation(Controller.class, packageName, java.lang.annotation.ElementType.TYPE));


            context.setAttribute("controllers", controllers);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
