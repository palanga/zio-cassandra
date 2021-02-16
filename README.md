zio-cassandra
=============

A ZIO wrapper around the Datastax Cassandra driver
--------------------------------------------------

* Scala 2.13.4
* Datastax java driver 4.9.0
* ZIO 1.0.3

Installation
------------

Add this to your `build.sbt` file
```sbt
resolvers += "Artifactory" at "https://palanga.jfrog.io/artifactory/maven/"
libraryDependencies += "dev.palanga" %% "zio-cassandra" % "0.0.1"
```

Usage
-----

```scala
package palanga.examples

import com.datastax.oss.driver.api.core.CqlSession
import palanga.zio.cassandra.ZStatement.StringOps
import palanga.zio.cassandra.{ CassandraException, ZCqlSession, module => zCqlSession }
import zio.clock.Clock
import zio.console.Console
import zio.{ ZIO, ZLayer, ZManaged }

import java.net.InetSocketAddress

object SimpleExample {

  /**
   * Our model.
   */
  case class Painter(region: String, name: String)

  /**
   * There are many ways to build statements. Perhaps the simplest one is using
   * `toStatement` String syntax under `palanga.zio.cassandra.ZStatement.StringOps`.
   * Then you can bind a decoder to the statement so it will automatically parse the
   * result every time it is executed.
   */
  val selectFromPaintersByRegion =
    "SELECT * FROM painters_by_region WHERE region=?;"                        // String
      .toStatement                                                            // ZSimpleStatement[Row]
      .decode(row => Painter(row.getString("region"), row.getString("name"))) // ZSimpleStatement[Painter]

  /**
   * For convenience, there are access methods to the ZCqlSession module under `palanga.zio.cassandra.module`.
   */
  val resultSet = zCqlSession execute selectFromPaintersByRegion.bind("Latin America")
  // resultSet: ZIO[ZCqlSession, CassandraException, AsyncResultSet]

  val streamResult = zCqlSession stream selectFromPaintersByRegion.bind("Latin America")
  // streamResult: ZStream[ZCqlSession, CassandraException, Chunk[Painter]]
  // Every chunk represents a page. It's easy to flatten them with `.flattenChunks`.

  val optionResult = zCqlSession executeHeadOption selectFromPaintersByRegion.bind("Europe")
  // optionResult: ZIO[ZCqlSession, CassandraException, Option[Painter]]

  val headOrFailResult = zCqlSession executeHeadOrFail selectFromPaintersByRegion.bind("West Pacific")
  // headOrFailResult: ZIO[ZCqlSession, CassandraException, Painter]

  /**
   * The simplest and better way of creating a session is:
   */
  val sessionLayer: ZLayer[Console with Clock, CassandraException, ZCqlSession] =
    ZCqlSession.layer
      .from(
        "localhost",
        9042,
        "painters_keyspace",
      )

  /**
   * But it's not the only way to create a session:
   */
  val managedSession: ZManaged[Console with Clock, CassandraException, ZCqlSession.Service] =
    ZCqlSession.managed
      .from(
        "localhost",
        9042,
        "painters_keyspace",
      )

  val rawSession: ZIO[Console with Clock, CassandraException, ZCqlSession.Service] =
    ZCqlSession.raw
      .from(
        "localhost",
        9042,
        "painters_keyspace",
      )

  /**
   * In order to get full flexibility on how to build a session we can:
   */
  val rawSessionFromCqlSession: ZIO[Any, CassandraException.SessionOpenException, ZCqlSession.Service] =
    ZCqlSession.raw
      .fromCqlSession(
        CqlSession
          .builder()
          .addContactPoint(new InetSocketAddress("localhost", 9042))
          .withKeyspace("painters_keyspace")
          .withLocalDatacenter("datacenter1")
          .build
      )

  /**
   * There are also methods for preparing statements, running plain SimpleStatements or BoundStatements,
   * and for running statements in parallel. Everything under `palanga.zio.cassandra.module`.
   */

}

}

```

Testing:
--------

* To run tests: `./sbt` then `test`

Troubleshooting:
----------------

* `brew cask install java11`
* `brew install cassandra` (will also install `cqlsh`)
* Cassandra doesn't work with java 14 (we are using java 11)
* Some jvm options doesn't work so we have to edit `/usr/local/etc/cassandra/jvm.options`
* Launch with `cassandra -f`
* Stop with `ctrl c` or `ps` to get the PID and then `kill <PID>`
* Default host and port is `127.0.0.1:9042`
* `cqlsh` will connect there by default
* `CREATE KEYSPACE IF NOT EXISTS zio_cassandra_test WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 };`
