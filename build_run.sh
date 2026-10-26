#!/bin/bash
# Compile le framework (sprint1.jar) + l'application testmonjar (s5.war),
# deploie dans Tomcat 11 et (re)demarre Tomcat.
# Usage : ./build_run.sh
set -e

# ---------- Configuration ----------
export JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64"   # Tomcat 11 exige Java 17+
export PATH="$JAVA_HOME/bin:$PATH"
export CATALINA_HOME="/home/fandresena-loic/apache-tomcat-11.0.26"

ROOT="$(cd "$(dirname "$0")" && pwd)"
# FW_DIR="$ROOT/framework"
APP_DIR="$ROOT/testmonjar"
APP_NAME="s5"
SERVLET_API="$CATALINA_HOME/lib/servlet-api.jar"
MYSQL_JAR="$FW_DIR/lib/mysql-connector-j-9.5.0.jar"

# ---------- 1. FRAMEWORK ----------
echo "=== 1. Compilation du framework ==="
# cd "$FW_DIR"
rm -rf build/classes
mkdir -p build/classes lib
find src/java -name "*.java" > /tmp/sources_fw.txt
javac -parameters -cp "$SERVLET_API:$MYSQL_JAR" -d build/classes @/tmp/sources_fw.txt
jar cf lib/sprint1.jar -C build/classes .
echo "-> $FW_DIR/lib/sprint1.jar"

# ---------- 2. APPLICATION DE TEST ----------
echo "=== 2. Compilation de l'application de test ==="
cd "$APP_DIR"
rm -rf build
mkdir -p build/WEB-INF/classes build/WEB-INF/lib
find src/main/java -name "*.java" > /tmp/sources_app.txt
javac -parameters -cp "$SERVLET_API:$FW_DIR/lib/sprint1.jar:$MYSQL_JAR" \
      -d build/WEB-INF/classes @/tmp/sources_app.txt

# Fichiers web (web.xml, vues JSP...) + librairies
cp -r src/main/webapp/. build/
cp "$FW_DIR/lib/sprint1.jar" build/WEB-INF/lib/sprint1.jar
cp "$MYSQL_JAR" build/WEB-INF/lib/

# WAR
(cd build && jar cf "$APP_NAME.war" *)
echo "-> $APP_DIR/build/$APP_NAME.war"

# ---------- 3. DEPLOIEMENT + TOMCAT ----------
echo "=== 3. Deploiement et lancement de Tomcat ==="
"$CATALINA_HOME/bin/shutdown.sh" >/dev/null 2>&1 || true
sleep 2
rm -rf "$CATALINA_HOME/webapps/$APP_NAME" "$CATALINA_HOME/webapps/$APP_NAME.war"
cp -f "$APP_DIR/build/$APP_NAME.war" "$CATALINA_HOME/webapps/$APP_NAME.war"
chmod +x "$CATALINA_HOME"/bin/*.sh
"$CATALINA_HOME/bin/startup.sh"

echo ""
echo "Tomcat demarre. Logs : tail -f $CATALINA_HOME/logs/catalina.out"
echo "Tests :"
echo "  http://localhost:8080/$APP_NAME/controller/user"
echo "  http://localhost:8080/$APP_NAME/controller/api/info"
echo "  http://localhost:8080/$APP_NAME/controller/api/users"
echo "  http://localhost:8080/$APP_NAME/controller/api/ping"