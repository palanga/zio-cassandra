name := "zio-cassandra"

val MAIN_SCALA                      = "3.1.3"
val ALL_SCALA                       = Seq(MAIN_SCALA)
val DATASTAX_JAVA_CASSANDRA_VERSION = "4.14.1"
val ZIO_VERSION                     = "2.0.2"

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")

inThisBuild(
  List(
    organization           := "io.github.palanga",
    homepage               := Some(url("https://github.com/palanga/zio-cassandra")),
    licenses               := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers             := List(
      Developer(
        "palanga",
        "Andrés González",
        "a.gonzalez.terres@gmail.com",
        url("https://github.com/palanga/"),
      )
    ),
    sonatypeCredentialHost := "s01.oss.sonatype.org",
    sonatypeRepository     := "https://s01.oss.sonatype.org/service/local",
  )
)

lazy val root =
  (project in file("."))
    .settings(
      publish / skip := true
    )
    .aggregate(
      core,
      examples,
    )

lazy val core =
  (project in file("core"))
    .settings(
      name           := "zio-cassandra",
      description    := "A ZIO wrapper around the Datastax Cassandra driver for Java",
      Test / fork    := true,
      run / fork     := true,
      testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework")),
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
      name           := "examples",
      publish / skip := true,
      Test / fork    := true,
      run / fork     := true,
      commonSettings,
    )
    .dependsOn(core)

val commonSettings = Def.settings(
  scalaVersion       := MAIN_SCALA,
  crossScalaVersions := ALL_SCALA,
  versionScheme      := Some("strict"),
  scalacOptions ++= Seq(
    "-rewrite",
    "-source:future-migration",
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
