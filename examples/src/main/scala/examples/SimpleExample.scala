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
    "SELECT * FROM painters_by_region WHERE region=?;"                        // String
      .toStatement                                                            // ZSimpleStatement[Row]
      .decode(row => Painter(row.getString("region"), row.getString("name"))) // ZSimpleStatement[Painter]

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
