# frameworkServlet

spring mvc et web api 
        -EmptController 
        annoter @Controller

        lister
        annoter @GetMapping

        Model

        asa
        micreer annotation ,prendre donnee
        anarana,aiza mipetraka,attribue possible
        micreer class annotena



sprint2:
        @Controller 
        class EmpController 
                @Url Mapping 

        /Emp/list

        Identifier 
        
Implementation:

        @Controller
        public class EmpController {
                @UrlMapping("/emp/list")
                public void list() {
                }
        }

Resultat attendu:

        /emp/list : dans Controller (EmpController) la methode associee est list()

Fonctionnement:

        - Le FrontServlet scanne le package defini par le parametre init controllerPackage.
        - Il garde tous les liens des methodes annotees @UrlMapping.
        - Si l'url tapee existe, il affiche le controller et la methode associee.
        - Si l'url tapee n'existe pas, il affiche les liens supportes.
        - Si le prefixe existe deja, par exemple /emp/list supporte mais /emp/new tape,
          il affiche seulement /emp/new comme lien non defini avec le controller trouve pour /emp.
        
