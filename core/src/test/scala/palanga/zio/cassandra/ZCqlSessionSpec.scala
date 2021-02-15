package palanga.zio.cassandra

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.{ Row, SimpleStatement }
import palanga.zio.cassandra.CassandraException.EmptyResultSetException
import palanga.zio.cassandra.module._
import zio.Schedule.spaced
import zio.ZIO
import zio.clock.Clock
import zio.console.{ putStrLn, Console }
import zio.duration._
import zio.test.Assertion._
import zio.test._

import java.net.InetSocketAddress
import java.util
import scala.language.postfixOps

object ZCqlSessionSpec extends DefaultRunnableSpec {

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
            .withLocalDatacenter(datacenter)
            .build
        )
      )
      .toManaged(closeSession(_).fork)
      .tap(_ => putStrLn("Initializing db...").toManaged_)
      .tap(initialize(_).toManaged_)
      .tap(_ => putStrLn("Cassandra session is ready").toManaged_)
      .tapError(t => putStrLn("Failed trying to build cql session layer: " + t.getMessage).toManaged_)
      .toLayer

  private def closeSession(session: ZCqlSession.Service) =
    (putStrLn("Closing cassandra session...") *> session.close <* putStrLn("Closed cassandra session"))
      .catchAll(t => putStrLn("Failed trying to close cassandra session:\n" + t.getMessage))

  private val createKeyspace =
    SimpleStatement
      .builder(
        s"""CREATE KEYSPACE IF NOT EXISTS $keyspace WITH REPLICATION = {
           |  'class': 'SimpleStrategy',
           |  'replication_factor': 1
           |};
           |""".stripMargin
      )
      .build

  private val useKeyspace = SimpleStatement.builder(s"USE $keyspace;").build

  private val dropTable = SimpleStatement.builder("DROP TABLE IF EXISTS painters_by_country;").build

  private val createTable =
    SimpleStatement
      .builder(
        s"""
           |CREATE TABLE IF NOT EXISTS painters_by_country (
           |  country text,
           |  name text,
           |  PRIMARY KEY (country, name)
           |);
           |""".stripMargin
      )
      .build

  private def initialize(session: ZCqlSession.Service) =
    session.execute(createKeyspace) *>
      session.execute(useKeyspace) *>
      session.execute(dropTable) *>
      session.execute(createTable)

  private val painterDecoder: Row => Painter = row => Painter(row.getString(0), row.getString(1))

  private val insertStatement = ZStatement("INSERT INTO painters_by_country (country, name) VALUES (?,?);")

  private val selectByCountryAndNameStatement =
    ZStatement("SELECT * FROM painters_by_country WHERE country=? AND name=?;").decode(painterDecoder)

  private val ARGENTINA = "Argentina"
  private val BRAZIL    = "Brazil"
  private val FRANCE    = "France"
  private val MEXICO    = "Mexico"
  private val SPAIN     = "Spain"

  private case class Painter(country: String, name: String)

  private def insert(painter: Painter) = insertStatement.bind(painter.country, painter.name)

  private def selectByCountryAndName(country: String, name: String) =
    selectByCountryAndNameStatement.bind(country, name)

  private val testSuite =
    suite("ZCqlSession suite")(
      testM("execute") {
        val frida = Painter(MEXICO, "Frida Kahlo")
        execute(insert(frida))
          .zipRight(execute(selectByCountryAndName(frida.country, frida.name)))
          .flatMap(rs => ZIO effect painterDecoder(rs.one()))
          .map(assert(_)(equalTo(frida)))
      },
      testM("execute head option") {
        val xul = Painter(ARGENTINA, "Xul Solar")
        execute(insert(xul)) *>
          executeHeadOption(selectByCountryAndName(xul.country, xul.name)) map
          (assert(_)(isSome(equalTo(xul))))
      },
      testM("execute head or fail succeed case") {
        val tarsila = Painter(BRAZIL, "Tarsila do Amaral")
        execute(insert(tarsila)) *>
          executeHeadOrFail(selectByCountryAndName(tarsila.country, tarsila.name)) map
          (assert(_)(equalTo(tarsila)))
      },
      testM("execute prepared") {
        val benito = Painter(ARGENTINA, "Benito Quinquela Martín")
        prepare(insertStatement.statement)
          .map(_.bind(benito.country, benito.name))
          .flatMap(execute)
          .zipRight(prepare(selectByCountryAndNameStatement.statement))
          .map(_.bind(benito.country, benito.name))
          .flatMap(execute)
          .flatMap(rs => ZIO effect painterDecoder(rs.one()))
          .map(assert(_)(equalTo(benito)))
      },
      testM("execute prepared par") {
        val berthe = Painter(FRANCE, "Berthe Morisot")
        val monet  = Painter(FRANCE, "Claude Monet")
        for {
          insert :: select :: Nil <- preparePar(insertStatement.statement, selectByCountryAndNameStatement.statement)
          _                       <- executePar(insert.bind(berthe.country, berthe.name), insert.bind(monet.country, monet.name))
          rss                     <- executePar(select.bind(berthe.country, berthe.name), select.bind(monet.country, monet.name))
          b :: m :: Nil           <- ZIO effect rss.map(_.one()).map(painterDecoder)
        } yield assert(b)(equalTo(berthe)) && assert(m)(equalTo(monet))
      },
      testM("execute simple") {
        val remedios                          = Painter(SPAIN, "Remedios Varo")
        val remediosValues: util.List[AnyRef] = java.util.List.of(remedios.country, remedios.name)
        execute(insertStatement.statement.setPositionalValues(remediosValues))
          .zipRight(execute(selectByCountryAndNameStatement.statement.setPositionalValues(remediosValues)))
          .flatMap(rs => ZIO effect painterDecoder(rs.one()))
          .map(assert(_)(equalTo(remedios)))
      },
      testM("execute simple par") {

        val dali                            = Painter(SPAIN, "Salvador Dalí")
        val leBrun                          = Painter(FRANCE, "Élisabeth Le Brun")
        val daliValues: util.List[AnyRef]   = java.util.List.of(dali.country, dali.name)
        val leBrunValues: util.List[AnyRef] = java.util.List.of(leBrun.country, leBrun.name)

        executeParSimple(
          insertStatement.statement.setPositionalValues(daliValues),
          insertStatement.statement.setPositionalValues(leBrunValues),
        ).zipRight(
            executeParSimple(
              selectByCountryAndNameStatement.statement.setPositionalValues(daliValues),
              selectByCountryAndNameStatement.statement.setPositionalValues(leBrunValues),
            )
          )
          .flatMap(rss => ZIO effect rss.map(_.one()).map(painterDecoder))
          .map { case d :: l :: Nil => assert(d)(equalTo(dali)) && assert(l)(equalTo(leBrun)) }

      },
      testM("prepare") {
        prepare(insertStatement.statement) map (_.getQuery) map (assert(_)(equalTo(insertStatement.statement.getQuery)))
      },
      testM("prepare par") {
        preparePar(insertStatement.statement, selectByCountryAndNameStatement.statement).map {
          case insert :: select :: Nil =>
            assert(insert.getQuery)(equalTo(insertStatement.statement.getQuery)) &&
              assert(select.getQuery)(equalTo(selectByCountryAndNameStatement.statement.getQuery))
        }
      },
      testM("execute head or fail failed case") {
        executeHeadOrFail(selectByCountryAndName(ARGENTINA, "nik")).run map
          (assert(_)(fails(isSubtype[EmptyResultSetException](anything))))
      },
    )

  val dependencies =
    Console.live ++ Clock.live >>> cqlSessionLayer
      .tapError(_ => putStrLn("Retrying in one second..."))
      .retry(spaced(1 second))

  override def spec = testSuite.provideSomeLayerShared[ZTestEnv](dependencies mapError TestFailure.fail)

}
