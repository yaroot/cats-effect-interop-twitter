name := "cats-effect-interop-twitter"
organization := "com.github.yaroot"
scalaVersion := "2.12.11"
crossScalaVersions := Seq("2.12.11", "2.13.1")

fork in run := true

libraryDependencies ++= {
  Seq(
    "org.typelevel" %% "cats-effect" % "2.1.3",
    "com.twitter"   %% "util-core"   % "20.4.0",
    "org.specs2"    %% "specs2-core" % "4.9.2" % "test",
  )
}

scalafmtOnCompile := true
cancelable in Global := true

wartremoverErrors in (Compile, compile) ++= Warts.all
