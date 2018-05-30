name := """ScadAppRentings"""
organization := "co.edu.udea"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.4"

libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test

// Estas son las dependencias necesarias para trabajar con bases de datos MySQL
libraryDependencies += jdbc
libraryDependencies += "mysql" % "mysql-connector-java" % "5.1.41"

// Dependencia para trabajar con JodaTime
libraryDependencies += "com.github.nscala-time" %% "nscala-time" % "2.18.0"

// Dependencia para trabajar con Firebase
libraryDependencies += "com.google.firebase" % "firebase-admin" % "6.1.0"