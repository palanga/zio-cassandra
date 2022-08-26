package palanga.zio.cassandra

import com.datastax.oss.driver.api.core.cql.{ Row, SimpleStatement }
import palanga.zio.cassandra.CassandraException.EmptyResultSetException
import palanga.zio.cassandra.module._
import palanga.zio.cassandra.session.ZCqlSession
import zio.ZIO
import zio.test.Assertion._
import zio.test._

object ZCqlSessionSpec {

  private val tableName = "painters_by_country"

  private val dropTable = SimpleStatement.builder(s"DROP TABLE IF EXISTS $tableName;").build

  private val createTable =
    SimpleStatement
      .builder(
        s"""
           |CREATE TABLE IF NOT EXISTS $tableName (
           |  country text,
           |  name text,
           |  PRIMARY KEY (country, name)
           |);
           |""".stripMargin
      )
      .build

  def initialize(session: ZCqlSession.Service) = session.execute(dropTable) *> session.execute(createTable)

  private val painterDecoder: Row => Painter = row => Painter(row.getString(0), row.getString(1))

  private val insertStatement = ZStatement(s"INSERT INTO $tableName (country, name) VALUES (?,?);")

  private val selectByCountryAndNameStatement =
    ZStatement(s"SELECT * FROM $tableName WHERE country=? AND name=?;").decode(painterDecoder)

  private val ARGENTINA = "Argentina"
  private val BRAZIL    = "Brazil"
  private val FRANCE    = "France"
  private val MEXICO    = "Mexico"
  private val SPAIN     = "Spain"

  private case class Painter(country: String, name: String)

  private def insert(painter: Painter) = insertStatement.bind(painter.country, painter.name)

  private def selectByCountryAndName(country: String, name: String) =
    selectByCountryAndNameStatement.bind(country, name)

  val testSuite =
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
        val remedios                               = Painter(SPAIN, "Remedios Varo")
        val remediosValues: java.util.List[AnyRef] = java.util.List.of(remedios.country, remedios.name)
        execute(insertStatement.statement.setPositionalValues(remediosValues))
          .zipRight(execute(selectByCountryAndNameStatement.statement.setPositionalValues(remediosValues)))
          .flatMap(rs => ZIO effect painterDecoder(rs.one()))
          .map(assert(_)(equalTo(remedios)))
      },
      testM("execute simple par") {

        val dali                                 = Painter(SPAIN, "Salvador Dalí")
        val leBrun                               = Painter(FRANCE, "Élisabeth Le Brun")
        val daliValues: java.util.List[AnyRef]   = java.util.List.of(dali.country, dali.name)
        val leBrunValues: java.util.List[AnyRef] = java.util.List.of(leBrun.country, leBrun.name)

        executeParSimple(
          insertStatement.statement.setPositionalValues(daliValues),
          insertStatement.statement.setPositionalValues(leBrunValues),
        ).zipRight(
          executeParSimple(
            selectByCountryAndNameStatement.statement.setPositionalValues(daliValues),
            selectByCountryAndNameStatement.statement.setPositionalValues(leBrunValues),
          )
        ).flatMap(rss => ZIO effect rss.map(_.one()).map(painterDecoder))
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
        executeHeadOrFail(selectByCountryAndName(ARGENTINA, "nik chorro")).run map
          (assert(_)(fails(isSubtype[EmptyResultSetException](anything))))
      },
    )

}
