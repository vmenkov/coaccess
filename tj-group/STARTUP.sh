#!/bin/bash
TOMCAT_DIR=/Users/JackFan/Documents/apache-tomcat-7.0.50
export JAVA_HOME=/usr

# Increase heap memory limit to 4 GB
export CATALINA_OPTS="-Xms4096m -Xmx4096m"

$TOMCAT_DIR/bin/catalina.sh start

