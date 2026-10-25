package listener;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import config.UploadConfig;
import context.ApplicationContext;
import context.DatabaseConfig;
import exception.UploadException;
import service.FileStorageService;

import java.util.Map;

@WebListener
public class FrontServletListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try {
            ServletContext context = sce.getServletContext();

            String packageName = context.getInitParameter("controllers-package");
            if (packageName == null || packageName.isBlank()) {
                packageName = "Controller";
            }

            String dbUrl = context.getInitParameter("db-url");
            String dbUser = context.getInitParameter("db-user");
            String dbPassword = context.getInitParameter("db-password");

            DatabaseConfig databaseConfig = null;
            if (dbUrl != null && !dbUrl.isBlank()) {
                databaseConfig = new DatabaseConfig(dbUrl, dbUser, dbPassword);
            }

            // Sprint 8 : configuration et service d'upload. Le chemin de
            // stockage provient du fichier upload.properties (jamais en dur).
            UploadConfig uploadConfig = UploadConfig.load(context);
            FileStorageService fileStorageService = new FileStorageService(uploadConfig);

            try {
                fileStorageService.ensureDirectory();
            } catch (UploadException e) {
                // On ne bloque pas le demarrage : les autres fonctionnalites
                // doivent rester disponibles. L'upload echouera proprement.
                context.log(e.getMessage(), e);
            }

            ApplicationContext applicationContext = new ApplicationContext(
                    packageName,
                    databaseConfig,
                    Map.of(
                            UploadConfig.class, uploadConfig,
                            FileStorageService.class, fileStorageService));

            context.setAttribute("applicationContext", applicationContext);
            context.setAttribute("uploadConfig", uploadConfig);
            context.setAttribute("fileStorageService", fileStorageService);
        } catch (Exception e) {
            throw new RuntimeException("Erreur initialisation ApplicationContext", e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        ServletContext context = sce.getServletContext();
        Object obj = context.getAttribute("applicationContext");
        if (obj instanceof ApplicationContext) {
            ApplicationContext appCtx = (ApplicationContext) obj;
            if (appCtx.getDatabaseConfig() != null) {
                appCtx.getDatabaseConfig().close();
            }
        }
    }
}
