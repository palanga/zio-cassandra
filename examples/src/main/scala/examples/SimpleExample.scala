package examples

import com.datastax.oss.driver.api.core.CqlSession
import palanga.zio.cassandra.ZStatement.StringOps
import palanga.zio.cassandra.session.ZCqlSession
import palanga.zio.cassandra.{CassandraException, ZCqlSession}
import zio.{Scope, ZIO, ZLayer}

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
  val selectFromPaintersByRegion =
    "SELECT * FROM painters_by_region WHERE region=?;"                        // String
      .toStatement                                                            // ZSimpleStatement[Row]
      .decode(row => Painter(row.getString("region"), row.getString("name"))) // ZSimpleStatement[Painter]

  /**
   * For convenience, there are access methods to the ZCqlSession module under `palanga.zio.cassandra.module`.
   */
  val resultSet = ZCqlSession.untyped.execute(selectFromPaintersByRegion.bind("Latin America"))
  // resultSet: ZIO[ZCqlSession, CassandraException, AsyncResultSet]

  val streamResult = ZCqlSession.stream(selectFromPaintersByRegion.bind("Latin America"))
  // streamResult: ZStream[ZCqlSession, CassandraException, Chunk[Painter]]
  // Every chunk represents a page. It's easy to flatten them with `.flattenChunks`.

  val optionResult = ZCqlSession.executeHeadOption(selectFromPaintersByRegion.bind("Europe"))
  // optionResult: ZIO[ZCqlSession, CassandraException, Option[Painter]]

  val headOrFailResult = ZCqlSession.executeHeadOrFail(selectFromPaintersByRegion.bind("West Pacific"))
  // headOrFailResult: ZIO[ZCqlSession, CassandraException, Painter]

  /**
   * The simplest and better way of creating a session is:
   */
  val sessionDefault: ZIO[Scope, CassandraException.SessionOpenException, ZCqlSession] =
    palanga.zio.cassandra.session.ZCqlSession.openDefault()

  /**
   * or:
   */
  val session: ZIO[Scope, CassandraException.SessionOpenException, ZCqlSession] =
    palanga.zio.cassandra.session.ZCqlSession.open(
      "127.0.0.1",
      9042,
      "painters_keyspace",
    )

  /**
   * In order to get full flexibility on how to build a session we can:
   */
  val sessionFromCqlSession: ZIO[Scope, CassandraException.SessionOpenException, ZCqlSession] =
    palanga.zio.cassandra.session.ZCqlSession.openFromCqlSession(
      CqlSession
        .builder()
        .addContactPoint(new InetSocketAddress("127.0.0.1", 9042))
        .withKeyspace("painters_keyspace")
        .withLocalDatacenter("datacenter1")
        .build
    )

  /**
   * To convert to a ZLayer in ZIO 2.x.x we must:
   */
  val layer: ZLayer[Scope, CassandraException.SessionOpenException, ZCqlSession] =
    ZLayer.scoped(session)

  /**
   * There are also methods for preparing statements, running plain SimpleStatements or BoundStatements, and for running
   * statements in parallel.
   */

}
