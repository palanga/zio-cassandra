package palanga.zio.cassandra

import com.datastax.oss.driver.api.core.cql.{ Row, SimpleStatement }
import palanga.zio.cassandra.CassandraException.EmptyResultSetException
import zio.ZIO
import zio.test.*
import zio.test.Assertion.*

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

  def initialize(session: ZCqlSession) = session.execute(dropTable) *> session.execute(createTable)

  private val painterDecoder: Row => Painter = row => Painter(row.getString(0), row.getString(1))

  private val insertStatement = ZStatement(s"INSERT INTO $tableName (country, name) VALUES (?,?);")

  private val selectByCountryAndNameStatement =
    ZStatement(s"SELECT * FROM $tableName WHERE country=? AND name=?;").decodeAttempt(painterDecoder)

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
      test("execute") {
        val frida = Painter(MEXICO, "Frida Kahlo")
        ZCqlSession.untyped
          .execute(insert(frida))
          .zipRight(ZCqlSession.untyped.execute(selectByCountryAndName(frida.country, frida.name)))
          .flatMap(rs => ZIO `attempt` painterDecoder(rs.one()))
          .map(assert(_)(equalTo(frida)))
      },
      test("execute head option") {
        val xul = Painter(ARGENTINA, "Xul Solar")
        ZCqlSession.untyped.execute(insert(xul)) *>
          ZCqlSession.executeHeadOption(selectByCountryAndName(xul.country, xul.name)) `map`
          (assert(_)(isSome(equalTo(xul))))
      },
      test("execute head or fail succeed case") {
        val tarsila = Painter(BRAZIL, "Tarsila do Amaral")
        ZCqlSession.untyped.execute(insert(tarsila)) *>
          ZCqlSession.executeHeadOrFail(selectByCountryAndName(tarsila.country, tarsila.name)) `map`
          (assert(_)(equalTo(tarsila)))
      },
      test("execute prepared") {
        val benito = Painter(ARGENTINA, "Benito Quinquela Martín")
        ZCqlSession
          .prepare(insertStatement.statement)
          .map(_.bind(benito.country, benito.name))
          .flatMap(ZCqlSession.untyped.execute)
          .zipRight(ZCqlSession.prepare(selectByCountryAndNameStatement.statement))
          .map(_.bind(benito.country, benito.name))
          .flatMap(ZCqlSession.untyped.execute)
          .flatMap(rs => ZIO `attempt` painterDecoder(rs.one()))
          .map(assert(_)(equalTo(benito)))
      },
      test("execute prepared par") {
        val berthe = Painter(FRANCE, "Berthe Morisot")
        val monet  = Painter(FRANCE, "Claude Monet")
        for {
          prepared    <- ZCqlSession.preparePar(insertStatement.statement, selectByCountryAndNameStatement.statement)
          insert       = prepared.head
          select       = prepared.tail.head
          _           <- ZCqlSession.untyped
                           .executePar(insert.bind(berthe.country, berthe.name), insert.bind(monet.country, monet.name))
          rss         <- ZCqlSession.untyped
                           .executePar(select.bind(berthe.country, berthe.name), select.bind(monet.country, monet.name))
          results     <- ZIO `attempt` rss.map(_.one()).map(painterDecoder)
          bertheResult = results.head
          monetResult  = results.tail.head
        } yield assert(bertheResult)(equalTo(berthe)) && assert(monetResult)(equalTo(monet))
      },
      test("execute simple") {
        val remedios                               = Painter(SPAIN, "Remedios Varo")
        val remediosValues: java.util.List[AnyRef] = java.util.List.of(remedios.country, remedios.name)
        ZCqlSession.untyped
          .execute(insertStatement.statement.setPositionalValues(remediosValues))
          .zipRight(
            ZCqlSession.untyped.execute(selectByCountryAndNameStatement.statement.setPositionalValues(remediosValues))
          )
          .flatMap(rs => ZIO `attempt` painterDecoder(rs.one()))
          .map(assert(_)(equalTo(remedios)))
      },
      test("execute simple par") {

        val dali                                 = Painter(SPAIN, "Salvador Dalí")
        val leBrun                               = Painter(FRANCE, "Élisabeth Le Brun")
        val daliValues: java.util.List[AnyRef]   = java.util.List.of(dali.country, dali.name)
        val leBrunValues: java.util.List[AnyRef] = java.util.List.of(leBrun.country, leBrun.name)

        ZCqlSession.untyped
          .executeParSimple(
            insertStatement.statement.setPositionalValues(daliValues),
            insertStatement.statement.setPositionalValues(leBrunValues),
          )
          .zipRight(
            ZCqlSession.untyped.executeParSimple(
              selectByCountryAndNameStatement.statement.setPositionalValues(daliValues),
              selectByCountryAndNameStatement.statement.setPositionalValues(leBrunValues),
            )
          )
          .flatMap(rss => ZIO `attempt` rss.map(_.one()).map(painterDecoder))
          .map { case d :: l :: Nil => assert(d)(equalTo(dali)) && assert(l)(equalTo(leBrun)) }

      },
      test("prepare") {
        ZCqlSession.prepare(insertStatement.statement) `map` (_.getQuery) `map` (assert(_)(
          equalTo(insertStatement.statement.getQuery)
        ))
      },
      test("prepare par") {
        ZCqlSession.preparePar(insertStatement.statement, selectByCountryAndNameStatement.statement).map {
          case insert :: select :: Nil =>
            assert(insert.getQuery)(equalTo(insertStatement.statement.getQuery)) &&
            assert(select.getQuery)(equalTo(selectByCountryAndNameStatement.statement.getQuery))
        }
      },
      test("execute head or fail failed case") {
        ZCqlSession.executeHeadOrFail(selectByCountryAndName(ARGENTINA, "nik chorro")).exit `map`
          (assert(_)(fails(isSubtype[EmptyResultSetException](anything))))
      },
    )

}
