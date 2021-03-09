name := "zio-cassandra"

val ZIO_CASSANDRA_VERSION = "0.2.0"

val MAIN_SCALA = "2.13.4"
val ALL_SCALA  = Seq(MAIN_SCALA)

val DATASTAX_JAVA_CASSANDRA_VERSION = "4.10.0"
val ZIO_VERSION                     = "1.0.4-2"

inThisBuild(
  List(
    organization := "dev.palanga",
    homepage := Some(url("https://github.com/palanga/zio-cassandra")),
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    parallelExecution in Test := false,
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
    .settings(skip in publish := true)
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
        compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
      ),
    )
    .settings(
      fork in Test := true,
      fork in run := true,
    )

lazy val examples =
  (project in file("examples"))
    .settings(name := "examples")
    .settings(commonSettings)
    .settings(
      libraryDependencies ++= Seq(compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"))
    )
    .settings(
      fork in Test := true,
      fork in run := true,
      skip in publish := true,
    )
    .dependsOn(core)

val commonSettings = Def.settings(
  scalaVersion := MAIN_SCALA,
  crossScalaVersions := ALL_SCALA,
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-explaintypes",
    "-Yrangepos",
    "-feature",
    "-language:higherKinds",
    "-language:existentials",
    "-unchecked",
    "-Xlint:_,-type-parameter-shadow",
//    "-Xfatal-warnings",
    "-Ywarn-numeric-widen",
    "-Ywarn-unused:patvars,-implicits",
    "-Ywarn-value-discard",
  ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, 12)) =>
      Seq(
        "-Xsource:2.13",
        "-Yno-adapted-args",
        "-Ypartial-unification",
        "-Ywarn-extra-implicit",
        "-Ywarn-inaccessible",
        "-Ywarn-infer-any",
        "-Ywarn-nullary-override",
        "-Ywarn-nullary-unit",
        "-opt-inline-from:<source>",
        "-opt-warnings",
        "-opt:l:inline",
      )
    case _             => Nil
  }),
  scalacOptions in Test --= Seq("-Xfatal-warnings"),
)
