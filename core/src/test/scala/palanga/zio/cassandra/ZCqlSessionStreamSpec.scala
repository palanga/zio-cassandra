package palanga.zio.cassandra

import com.datastax.oss.driver.api.core.cql.{ Row, SimpleStatement }
import palanga.zio.cassandra.module._
import palanga.zio.cassandra.session.ZCqlSession
import zio.ZIO
import zio.clock.Clock
import zio.console.Console
import zio.test.Assertion._
import zio.test._

import scala.language.postfixOps

object ZCqlSessionStreamSpec {

  private val tableName = "painters_by_region"

  private val dropTable = SimpleStatement.builder(s"DROP TABLE IF EXISTS $tableName;").build

  private val createTable =
    SimpleStatement
      .builder(
        s"""
           |CREATE TABLE IF NOT EXISTS $tableName (
           |  region text,
           |  name text,
           |  PRIMARY KEY (region, name)
           |);
           |""".stripMargin
      )
      .build

  def initialize(session: ZCqlSession.Service) =
    session.execute(dropTable) *> session.execute(createTable) *> populate(session)

  private val insertStatement =
    SimpleStatement.builder(s"INSERT INTO $tableName (region, name) VALUES (?,?);").build

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
      .flatMap(ps => session.executePar(painters.map(painter => ps.bind(painter.region, painter.name)): _*))

  private val painterDecoder: Row => Painter = row => Painter(row.getString(0), row.getString(1))

  private val PAGE_SIZE = 3

  private case class Painter(region: String, name: String)

  private val selectByRegionStatement = {
    import ZStatement.SimpleStatementOps
    SimpleStatement
      .builder(s"SELECT * FROM $tableName WHERE region=?;")
      .setPageSize(PAGE_SIZE)
      .build()
      .decode(painterDecoder)
  }

  private def selectByRegion(region: String) = selectByRegionStatement.bind(region)

  val testSuite =
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
          .flatMap(stream(_).runCollect)
          .map(pages =>
            assert(pages.headOption.fold(0)(_.size))(equalTo(PAGE_SIZE)) &&
              assert(pages.flatten.map(painterDecoder))(hasSameElements(latinPainters))
          )

      },
      testM("stream simple") {

        stream(selectByRegionStatement.statement.setPositionalValues(java.util.List.of(LATIN_AMERICA))).runCollect
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
          .flatMap(streamResultSet(_).runCollect)
          .map(_.count(_.currentPage().asScala.nonEmpty))
          .map(assert(_)(equalTo(europeanPainters.size / PAGE_SIZE)))

      },
      testM("stream result set simple") {

        import scala.jdk.CollectionConverters.IterableHasAsScala

        streamResultSet(selectByRegionStatement.statement.setPositionalValues(java.util.List.of(EUROPE))).runCollect
          .map(_.count(_.currentPage().asScala.nonEmpty))
          .map(assert(_)(equalTo(europeanPainters.size / PAGE_SIZE)))

      },
    )

}
