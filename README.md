zio-cassandra
=============

A ZIO wrapper around the Datastax Cassandra driver for Java

[![CI][Badge-CI]][Link-CI]
[![Release Artifacts][Badge-SonatypeReleases]][Link-SonatypeReleases]
[![Snapshot Artifacts][Badge-SonatypeSnapshots]][Link-SonatypeSnapshots]

Installation
------------

We publish to maven central for scala 3 and 2.13 so you just have to add this to your `build.sbt` file

```sbt
libraryDependencies += "io.github.palanga" %% "zio-cassandra" % "version"
```

If you want to use snapshots add this resolver:
```sbt
resolvers += "Sonatype OSS Snapshots" at "https://s01.oss.sonatype.org/content/repositories/snapshots"
```

Usage
-----

```scala
package examples

import com.datastax.oss.driver.api.core.CqlSession as DatastaxSession
import com.datastax.oss.driver.api.core.cql.*
import palanga.zio.cassandra.*
import palanga.zio.cassandra.CassandraException.SessionOpenException
import palanga.zio.cassandra.ZStatement.StringOps
import zio.*
import zio.stream.*

import java.net.InetSocketAddress

object SimpleExample {

  /**
   * Our model.
   */
  case class Painter(region: String, name: String)

  /**
   * There are many ways to build statements. Perhaps the simplest one is using `toStatement` String syntax under
   * `palanga.zio.cassandra.ZStatement.StringOps`. Then you can bind a decoder to the statement so it will automatically
   * parse the result.
   */
  val selectFromPaintersByRegion: ZSimpleStatement[Painter] =
    "SELECT * FROM painters_by_region WHERE region=?;"                               // String
      .toStatement                                                                   // ZSimpleStatement[Row]
      .decodeAttempt(row => Painter(row.getString("region"), row.getString("name"))) // ZSimpleStatement[Painter]

  /**
   * Every chunk represents a page. It's easy to flatten them with `.flattenChunks`.
   */
  val streamResult: ZStream[ZCqlSession, CassandraException, Chunk[Painter]] =
    ZCqlSession.stream(selectFromPaintersByRegion.bind("Latin America"))

  val optionResult: ZIO[ZCqlSession, CassandraException, Option[Painter]] =
    ZCqlSession.executeHeadOption(selectFromPaintersByRegion.bind("Europe"))

  val headOrFailResult: ZIO[ZCqlSession, CassandraException, Painter] =
    ZCqlSession.executeHeadOrFail(selectFromPaintersByRegion.bind("West Pacific"))

  val resultSet: ZIO[ZCqlSession, CassandraException, AsyncResultSet] =
    ZCqlSession.untyped.execute(selectFromPaintersByRegion.bind("Latin America"))

  /**
   * The simplest and better way of creating a session is:
   */
  val sessionDefault: ZIO[Scope, SessionOpenException, ZCqlSession] =
    session.auto.openDefault()

  /**
   * or:
   */
  val sessionSimple: ZIO[Scope, SessionOpenException, ZCqlSession] =
    session.auto.open(
      "127.0.0.1",
      9042,
      "painters_keyspace",
    )

  /**
   * In order to get full flexibility on how to build a session we can:
   */
  val sessionFromCqlSession: ZIO[Scope, SessionOpenException, ZCqlSession] =
    session.auto.openFromDatastaxSession(
      DatastaxSession
        .builder()
        .addContactPoint(new InetSocketAddress("127.0.0.1", 9042))
        .withKeyspace("painters_keyspace")
        .withLocalDatacenter("datacenter1")
        .build
    )

  /**
   * To convert to a ZLayer in ZIO 2.x.x we must:
   */
  val layer: ZLayer[Scope, SessionOpenException, ZCqlSession] =
    ZLayer.scoped(sessionSimple)

  /**
   * There are also methods for preparing statements, running plain SimpleStatements or BoundStatements, and for running
   * statements in parallel.
   */

}

```

Testing:
--------

* Run a cassandra container in docker: `docker run --name cassandra-for-zio-cassandra-tests -p 9042:9042 -d cassandra`
* Cross (Scala 3 and 2.13) run tests against it: `sbt +test`



[Link-CI]: https://github.com/palanga/zio-cassandra/actions/workflows/ci.yml "CI"
[Badge-CI]: https://github.com/palanga/zio-cassandra/actions/workflows/ci.yml/badge.svg "CI"


[Link-SonatypeReleases]: https://s01.oss.sonatype.org/content/repositories/releases/io/github/palanga/zio-cassandra_3/ "Sonatype Releases"
[Badge-SonatypeReleases]: https://img.shields.io/nexus/r/https/s01.oss.sonatype.org/io.github.palanga/zio-cassandra_3.svg "Sonatype Releases"

[Link-SonatypeSnapshots]: https://s01.oss.sonatype.org/content/repositories/snapshots/io/github/palanga/zio-cassandra_3/ "Sonatype Snapshots"
[Badge-SonatypeSnapshots]: https://img.shields.io/nexus/s/https/s01.oss.sonatype.org/io.github.palanga/zio-cassandra_3.svg "Sonatype Snapshots"

