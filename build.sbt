name := "zio-cassandra"

val ZIO_CASSANDRA_VERSION = "0.5.0"

val MAIN_SCALA = "3.1.3"
val ALL_SCALA  = Seq(MAIN_SCALA)

val DATASTAX_JAVA_CASSANDRA_VERSION = "4.14.1"

val ZIO_VERSION = "2.0.1"

inThisBuild(
  List(
    organization := "dev.palanga",
    homepage := Some(url("https://github.com/palanga/zio-cassandra")),
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    Test / parallelExecution := false,
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/palanga/zio-cassandra/"),
        "scm:git:git@github.com:palanga/zio-cassandra.git",
      )
    ),
    developers := List(
      Developer(
        "palanga",
        "Andrés González",
        "a.gonzalez.terres@gmail.com",
        url("https://github.com/palanga"),
      )
    ),
    publishTo := Some("Artifactory Realm" at "https://palanga.jfrog.io/artifactory/maven/"),
    credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
  )
)

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")

lazy val root =
  (project in file("."))
    .settings(publish / skip := true)
    .aggregate(
      core,
      examples,
    )

lazy val core =
  (project in file("core"))
    .settings(name := "zio-cassandra")
    .settings(commonSettings)
    .settings(version := ZIO_CASSANDRA_VERSION)
    .settings(
      testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework")),
      libraryDependencies ++= Seq(
        "com.datastax.oss" % "java-driver-core" % DATASTAX_JAVA_CASSANDRA_VERSION,
        "dev.zio"         %% "zio"              % ZIO_VERSION,
        "dev.zio"         %% "zio-streams"      % ZIO_VERSION,
        "dev.zio"         %% "zio-test"         % ZIO_VERSION % "test",
        "dev.zio"         %% "zio-test-sbt"     % ZIO_VERSION % "test",
      ),
    )
    .settings(
      Test / fork := true,
      run / fork := true,
    )

lazy val examples =
  (project in file("examples"))
    .settings(name := "examples")
    .settings(commonSettings)
    .settings(
      Test / fork := true,
      run / fork := true,
      publish / skip := true,
    )
    .dependsOn(core)

val commonSettings = Def.settings(
  scalaVersion := MAIN_SCALA,
  crossScalaVersions := ALL_SCALA,
  versionScheme := Some("strict"),
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-explaintypes",
    "-feature",
    "-language:higherKinds",
    "-language:existentials",
    "-unchecked",
  ),
)
