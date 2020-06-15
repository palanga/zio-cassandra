package palanga.examples

import java.net.InetSocketAddress

import com.datastax.oss.driver.api.core.CqlSession
import palanga.zio.cassandra.ZStatement.StringOps
import palanga.zio.cassandra.{ ZCqlSession, module => zCqlSession }

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
   * We can build a ZCqlSession layer from a CqlSession. If you are reading this I guess you are
   * familiar with ZIO's ZLayer.
   *
   * It will automatically prepare and cache your statements the first time they are run,
   * hence the `Auto` in `fromCqlSessionAuto`.
   */
  val sessionLayer =
    ZCqlSession
      .fromCqlSessionAuto(
        CqlSession
          .builder()
          .addContactPoint(new InetSocketAddress("127.0.0.1", 9042))
          .withKeyspace("zio_cassandra_test")
          .withLocalDatacenter("datacenter1")
          .build
      )
      .toManaged(_.close.fork)
      .toLayer

  /**
   * There are also methods for preparing statements, running plain SimpleStatements or BoundStatements,
   * and for running statements in parallel. Everything under `palanga.zio.cassandra.module`.
   */

}
