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
