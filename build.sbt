name := "cats-effect-interop-twitter"
organization := "com.github.quasi-category"
scalaVersion := "2.13.3"
crossScalaVersions := Seq("2.12.11", "2.13.3")

fork in run := true

libraryDependencies ++= {
  Seq(
    "org.typelevel"  %% "cats-effect"                  % "2.3.0",
    "com.twitter"    %% "util-core"                    % "20.10.0",
    "io.monix"       %% "minitest"                     % "2.9.0",
    "com.codecommit" %% "cats-effect-testing-minitest" % "0.4.2"
  )
}

scalafmtOnCompile := true
cancelable in Global := true

testFrameworks += new TestFramework("minitest.runner.Framework")

version ~= (_.replace('+', '-'))
dynver ~= (_.replace('+', '-'))
