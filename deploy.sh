#!/usr/bin/env bash
#
# deploy.sh - Compile le framework et genere framework.jar
#
# Usage : ./deploy.sh
#

set -e

APP_NAME="framework"

# Repertoire du script (fonctionne meme appele depuis elsewhere)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

SRC_DIR="src/java"
BUILD_DIR="build"
LIB_DIR="lib"

# --- Detection du JDK (Java 17+ requis : pattern matching instanceof) ---------

# Extrait le numero de version majeure depuis une sortie -version
#   "openjdk version \"21.0.2\"" -> 21
#   "java version \"1.8.0_504\""  -> 1
#   "javac 21.0.2"                -> 21
java_major() {
    "$1" -version 2>&1 | head -1 | sed 's/[^0-9]*\([0-9][0-9]*\).*/\1/'
}

JAVAC=""
MIN_JAVA=17

for candidate in \
    "${JAVA_HOME:-/nonexistent}/bin/javac" \
    "$(command -v javac 2>/dev/null || true)" \
    /usr/lib/jvm/java-21-openjdk-amd64/bin/javac \
    /usr/lib/jvm/java-17-openjdk-amd64/bin/javac \
    /usr/lib/jvm/java-21-openjdk/bin/javac \
    /usr/lib/jvm/java-17-openjdk/bin/javac
do
    [ -n "$candidate" ] && [ -x "$candidate" ] || continue

    v="$(java_major "$candidate")"
    if [ -n "$v" ] && [ "$v" -ge "$MIN_JAVA" ]; then
        JAVAC="$candidate"
        break
    fi
done

if [ -z "$JAVAC" ]; then
    echo "ERREUR: Java $MIN_JAVA+ est requis (pattern matching 'instanceof')."
    echo "       Aucun JDK $MIN_JAVA+ trouve. Definissez JAVA_HOME."
    exit 1
fi

JAVA_BIN="$(dirname "$JAVAC")"
echo "JDK utilise : $($JAVAC -version 2>&1 | head -1) [$JAVA_BIN]"

JAR="$JAVA_BIN/jar"
[ -x "$JAR" ] || JAR="jar"

# --- servlet-api.jar (fourni dans lib/) --------------------------------------
SERVLET_API_JAR="$LIB_DIR/servlet-api.jar"

if [ ! -f "$SERVLET_API_JAR" ]; then
    echo "ERREUR: $SERVLET_API_JAR introuvable."
    echo "       Copiez servlet-api.jar depuis Tomcat dans $LIB_DIR/."
    exit 1
fi

echo "Servlet API : $SERVLET_API_JAR"

# --- Nettoyage ---------------------------------------------------------------
echo ""
echo "========================="
echo "Nettoyage..."
echo "========================="
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/classes"

# --- Compilation -------------------------------------------------------------
echo ""
echo "========================="
echo "Compilation..."
echo "========================="

find "$SRC_DIR" -name "*.java" -printf '"%p"\n' > sources.txt

if ! "$JAVAC" \
    -parameters \
    -encoding UTF-8 \
    -cp "$SERVLET_API_JAR" \
    -d "$BUILD_DIR/classes" \
    @sources.txt
then
    echo ""
    echo "ERREUR DE COMPILATION"
    rm -f sources.txt
    exit 1
fi

rm -f sources.txt

# --- Creation du JAR ---------------------------------------------------------
echo ""
echo "========================="
echo "Creation du JAR..."
echo "========================="

mkdir -p "$BUILD_DIR/jar"

$JAR cf "$BUILD_DIR/jar/$APP_NAME.jar" \
    -C "$BUILD_DIR/classes" .

# --- Resume ------------------------------------------------------------------
echo ""
echo "========================="
echo "BUILD TERMINE"
echo "========================="
echo "JAR cree : $BUILD_DIR/jar/$APP_NAME.jar"
echo ""
$JAR tf "$BUILD_DIR/jar/$APP_NAME.jar" | sort
echo ""