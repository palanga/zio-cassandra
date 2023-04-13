package palanga.zio.cassandra

import com.datastax.oss.driver.api.core.cql.{ Row, SimpleStatement }
import palanga.zio.cassandra.CassandraException.DecodeException
import palanga.zio.cassandra.ZStatement.{ bindNothing, identityRow }
import zio.test.*

object ZStatementSpec {

  private val query = "SELECT * FROM painters WHERE name=?"

  val testSuite =
    suite("ZStatement suite")(
      test("from apply") {

        val zstatement = ZStatement(query)
        val statement  = SimpleStatement.builder(query).build

        assertTrue(
          zstatement.statement == statement
            && zstatement.bindInternal == bindNothing
            && zstatement.decodeInternal == identityRow
        )

      },
      test("from string ops") {

        import palanga.zio.cassandra.ZStatement.StringOps

        val zstatement = query.toStatement
        val statement  = SimpleStatement.builder(query).build

        assertTrue(
          zstatement.statement == statement
            && zstatement.bindInternal == bindNothing
            && zstatement.decodeInternal == identityRow
        )

      },
      test("decode") {

        import scala.util.Try

        val zStatement                                      = ZStatement(query)
        val decoder: Row => Either[DecodeException, String] =
          row => Try(row.getString("name")).toEither.left.map(DecodeException(zStatement.statement)(_))

        val zstatementWithDecoder: ZSimpleStatement[String] = zStatement.decode(decoder)
        val zstatementBound: ZBoundStatement[String]        = zStatement.bind("Frida Kahlo").decode(decoder)

        assertTrue(zstatementWithDecoder.decodeInternal == decoder && zstatementBound.decodeInternal == decoder)

      },
      test("bind preserves decoder") {

        val decoder: Row => String = _.getString("name")

        val zstatementWithDecoder: ZSimpleStatement[String] = ZStatement(query).decodeAttempt(decoder)
        val zstatementBound: ZBoundStatement[String]        = zstatementWithDecoder.bind("Xul Solar")

        assertTrue(zstatementWithDecoder.decodeInternal == zstatementBound.decodeInternal)

      },
    )

}
