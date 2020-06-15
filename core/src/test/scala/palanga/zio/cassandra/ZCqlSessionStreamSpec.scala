package palanga.zio.cassandra

import java.net.InetSocketAddress

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.{ Row, SimpleStatement }
import palanga.zio.cassandra.module._
import zio.Schedule.spaced
import zio.ZIO
import zio.clock.Clock
import zio.console.{ putStrLn, Console }
import zio.duration._
import zio.test.Assertion._
import zio.test._

import scala.language.postfixOps

object ZCqlSessionStreamSpec extends DefaultRunnableSpec {

  private val host       = "127.0.0.1"
  private val port       = 9042
  private val address    = new InetSocketAddress(host, port)
  private val keyspace   = "zio_cassandra_test"
  private val datacenter = "datacenter1"

  private val cqlSessionLayer =
    putStrLn("Opening cassandra session...")
      .zipRight(
        ZCqlSession.fromCqlSessionAuto(
          CqlSession
            .builder()
            .addContactPoint(address)
            .withKeyspace(keyspace)
            .withLocalDatacenter(datacenter)
            .build
        )
      )
      .toManaged(closeSession(_).fork)
      .tap(_ => putStrLn("Initializing db...").toManaged_)
      .tap(initialize(_).toManaged_)
      .tap(populate(_).toManaged_)
      .tap(_ => putStrLn("Cassandra session is ready").toManaged_)
      .tapError(t => putStrLn("Failed trying to build cql session layer: " + t.getMessage).toManaged_)
      .toLayer

  private def closeSession(session: ZCqlSession.Service) =
    (putStrLn("Closing cassandra session...") *> session.close <* putStrLn("Closed cassandra session"))
      .catchAll(t => putStrLn("Failed trying to close cassandra session:\n" + t.getMessage))

  private val createTable =
    SimpleStatement
      .builder(
        s"""
           |CREATE TABLE IF NOT EXISTS painters_by_region (
           |  region text,
           |  name text,
           |  PRIMARY KEY (region, name)
           |);
           |""".stripMargin
      )
      .build

  private def initialize(session: ZCqlSession.Service) = session.executeSimple(createTable)

  private val insertStatement =
    SimpleStatement.builder("INSERT INTO painters_by_region (region, name) VALUES (?,?);").build

  private val EUROPE        = "Europe"
  private val LATIN_AMERICA = "Latin America"

  private val frida   = Painter(LATIN_AMERICA, "Frida Kahlo")
  private val xul     = Painter(LATIN_AMERICA, "Xul Solar")
  private val tarsila = Painter(LATIN_AMERICA, "Tarsila do Amaral")
  private val benito  = Painter(LATIN_AMERICA, "Benito Quinquela Martín")
  private val zilia   = Painter(LATIN_AMERICA, "Zilia Sánchez Domínguez")
  private val berni   = Painter(LATIN_AMERICA, "Antonio Berni")
  private val berthe  = Painter(EUROPE, "Berthe Morisot")
  private val monet   = Painter(EUROPE, "Claude Monet")
  private val varo    = Painter(EUROPE, "Remedios Varo")
  private val dali    = Painter(EUROPE, "Salvador Dalí")
  private val leBrun  = Painter(EUROPE, "Élisabeth Le Brun")
  private val miro    = Painter(EUROPE, "Joan Miró")

  private val painters = List(frida, xul, tarsila, benito, zilia, berni, berthe, monet, varo, dali, leBrun, miro)

  private val latinPainters    = painters.filter(_.region == LATIN_AMERICA)
  private val europeanPainters = painters.filter(_.region == EUROPE)

  private def populate(session: ZCqlSession.Service) =
    session
      .prepare(insertStatement)
      .flatMap(ps => session.executePreparedPar(painters.map(painter => ps.bind(painter.region, painter.name)): _*))

  private val painterDecoder: Row => Painter = row => Painter(row.getString(0), row.getString(1))

  private val PAGE_SIZE = 3

  private case class Painter(region: String, name: String)

  private val selectByRegionStatement = {
    import ZStatement.SimpleStatementOps
    SimpleStatement
      .builder("SELECT * FROM painters_by_region WHERE region=?;")
      .setPageSize(PAGE_SIZE)
      .build()
      .decode(painterDecoder)
  }

  private def selectByRegion(region: String) = selectByRegionStatement.bind(region)

  private val testSuite =
    suite("ZCqlSession suite")(
      testM("stream") {

        val s         = stream(selectByRegion(LATIN_AMERICA))
        val sPageSize = s.runHead.map(_.fold(0)(_.size))

        val results =
          sPageSize.map(assert(_)(equalTo(PAGE_SIZE))) ::
            s.flattenChunks.runCollect.map(assert(_)(hasSameElements(latinPainters))) :: Nil

        ZIO.collectAllPar(results).map(_.reduce(_ && _))

      },
      testM("stream prepared") {

        prepare(selectByRegionStatement.statement)
          .map(_.bind(LATIN_AMERICA))
          .flatMap(streamPrepared(_).runCollect)
          .map(pages =>
            assert(pages.headOption.fold(0)(_.size))(equalTo(PAGE_SIZE)) &&
              assert(pages.flatten.map(painterDecoder))(hasSameElements(latinPainters))
          )

      },
      testM("stream simple") {

        streamSimple(selectByRegionStatement.statement.setPositionalValues(java.util.List.of(LATIN_AMERICA))).runCollect
          .map(pages =>
            assert(pages.headOption.fold(0)(_.size))(equalTo(PAGE_SIZE)) &&
              assert(pages.flatten.map(painterDecoder))(hasSameElements(latinPainters))
          )

      },
      testM("stream result set") {

        import scala.jdk.CollectionConverters.IterableHasAsScala

        streamResultSet(selectByRegion(EUROPE)).runCollect
          .map(_.count(_.currentPage().asScala.nonEmpty))
          .map(assert(_)(equalTo(europeanPainters.size / PAGE_SIZE)))

      },
      testM("stream result set prepared") {

        import scala.jdk.CollectionConverters.IterableHasAsScala

        prepare(selectByRegionStatement.statement)
          .map(_.bind(EUROPE))
          .flatMap(streamResultSetPrepared(_).runCollect)
          .map(_.count(_.currentPage().asScala.nonEmpty))
          .map(assert(_)(equalTo(europeanPainters.size / PAGE_SIZE)))

      },
      testM("stream result set simple") {

        import scala.jdk.CollectionConverters.IterableHasAsScala

        streamResultSetSimple(
          selectByRegionStatement.statement.setPositionalValues(java.util.List.of(EUROPE))
        ).runCollect
          .map(_.count(_.currentPage().asScala.nonEmpty))
          .map(assert(_)(equalTo(europeanPainters.size / PAGE_SIZE)))

      },
    )

  private val dependencies =
    Console.live ++ Clock.live >>> cqlSessionLayer
      .tapError(_ => putStrLn("Retrying in one second..."))
      .retry(spaced(1 second))

  override def spec = testSuite.provideSomeLayerShared[ZTestEnv](dependencies mapError TestFailure.fail)

}
