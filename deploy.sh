#!/bin/bash

APP_NAME="framework"

SRC_DIR="src/java"
BUILD_DIR="build"
LIB_DIR="lib"

SERVLET_API_JAR="$LIB_DIR/servlet-api.jar"

echo "Nettoyage..."
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/classes"

echo "Compilation..."

find "$SRC_DIR" -name "*.java" > sources.txt

javac \
    -cp "$SERVLET_API_JAR" \
    -d "$BUILD_DIR/classes" \
    @sources.txt

if [ $? -ne 0 ]; then
    echo "Erreur de compilation"
    rm -f sources.txt
    exit 1
fi

rm -f sources.txt

echo "Creation du JAR..."

mkdir -p "$BUILD_DIR/jar"

jar cf "$BUILD_DIR/jar/$APP_NAME.jar" \
    -C "$BUILD_DIR/classes" .

echo ""
echo "JAR créé : $BUILD_DIR/jar/$APP_NAME.jar"
echo ""