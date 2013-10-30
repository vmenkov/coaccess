#!/bin/bash
TOMCAT_DIR=/home/arxivsearch/tomcat/apache-tomcat-7.0.12/
export JAVA_HOME=/usr/java/latest

# Increase heap memory limit to 4 GB
export CATALINA_OPTS="-Xms4096m -Xmx4096m"

$TOMCAT_DIR/bin/catalina.sh start

