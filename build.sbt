name := "cats-effect-interop-twitter"
organization := "com.github.yaroot"
scalaVersion := "2.13.2"
crossScalaVersions := Seq("2.12.11", "2.13.2")

fork in run := true

libraryDependencies ++= {
  Seq(
    "org.typelevel"  %% "cats-effect"                  % "2.1.3",
    "com.twitter"    %% "util-core"                    % "20.4.1",
    "io.monix"       %% "minitest"                     % "2.8.2",
    "com.codecommit" %% "cats-effect-testing-minitest" % "0.4.0"
  )
}

scalafmtOnCompile := true
cancelable in Global := true

wartremoverErrors in (Compile, compile) ++= Warts.all

testFrameworks += new TestFramework("minitest.runner.Framework")

version ~= (_.replace('+', '-'))
dynver ~= (_.replace('+', '-'))
