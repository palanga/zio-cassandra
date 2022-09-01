import xerial.sbt.Sonatype._

name := "zio-cassandra"

//val ZIO_CASSANDRA_VERSION = "0.6.0"

val MAIN_SCALA = "3.1.3"
val ALL_SCALA  = Seq(MAIN_SCALA)

val DATASTAX_JAVA_CASSANDRA_VERSION = "4.14.1"

val ZIO_VERSION = "2.0.1"

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")

lazy val root =
  (project in file("."))
    .settings(
      sonatypeBundleRelease / skip := true
    )
    .aggregate(
      core,
      examples,
    )

lazy val core =
  (project in file("core"))
    .settings(
      name                   := "zio-cassandra",
      organization           := "io.github.palanga",
//      version                := ZIO_CASSANDRA_VERSION,
      publishMavenStyle      := true,
      licenses               := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
      description            := "A ZIO wrapper around the Datastax Cassandra driver for Java",
      sonatypeProjectHosting := Some(GitHubHosting("palanga", "zio-cassandra", "a.gonzalez.terres@gmail.com")),
      sonatypeCredentialHost := "s01.oss.sonatype.org",
      sonatypeRepository     := "https://s01.oss.sonatype.org/service/local",
      publishTo              := sonatypePublishToBundle.value,
      Test / fork            := true,
      run / fork             := true,
      testFrameworks         := Seq(new TestFramework("zio.test.sbt.ZTestFramework")),
      libraryDependencies ++= Seq(
        "com.datastax.oss" % "java-driver-core" % DATASTAX_JAVA_CASSANDRA_VERSION,
        "dev.zio"         %% "zio"              % ZIO_VERSION,
        "dev.zio"         %% "zio-streams"      % ZIO_VERSION,
        "dev.zio"         %% "zio-test"         % ZIO_VERSION % "test",
        "dev.zio"         %% "zio-test-sbt"     % ZIO_VERSION % "test",
      ),
      commonSettings,
    )

lazy val examples =
  (project in file("examples"))
    .settings(
      name                         := "examples",
      sonatypeBundleRelease / skip := true,
      Test / fork                  := true,
      run / fork                   := true,
      commonSettings,
    )
    .dependsOn(core)

val commonSettings = Def.settings(
  scalaVersion       := MAIN_SCALA,
  crossScalaVersions := ALL_SCALA,
  versionScheme      := Some("strict"),
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
