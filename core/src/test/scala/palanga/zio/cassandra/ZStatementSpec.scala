package palanga.zio.cassandra

import com.datastax.oss.driver.api.core.cql.{ Row, SimpleStatement }
import palanga.zio.cassandra.ZStatement.{ bindNothing, identityRow }
import zio.test.Assertion.*
import zio.test.*

object ZStatementSpec {

  private val query = "SELECT * FROM painters WHERE name=?"

  val testSuite =
    suite("ZStatement suite")(
      test("from apply") {

        val zstatement = ZStatement(query)
        val statement  = SimpleStatement.builder(query).build

        assert(zstatement.statement)(equalTo(statement)) &&
        assert(zstatement.bindInternal)(equalTo(bindNothing)) &&
        assert(zstatement.decodeInternal)(equalTo(identityRow))

      },
      test("from string ops") {

        import palanga.zio.cassandra.ZStatement.StringOps

        val zstatement = query.toStatement
        val statement  = SimpleStatement.builder(query).build

        assert(zstatement.statement)(equalTo(statement)) &&
        assert(zstatement.bindInternal)(equalTo(bindNothing)) &&
        assert(zstatement.decodeInternal)(equalTo(identityRow))

      },
      test("decode") {

        val decoder: Row => String = _.getString("name")

        // implicitly assert is type of ZSimpleStatement[String] and ZBoundStatement[String]
        val zstatementRow: ZSimpleStatement[Row]       = ZStatement(query)
        val zstatementString: ZSimpleStatement[String] = zstatementRow.decodeAttempt(decoder)
        val zstatementBound: ZBoundStatement[String]   = zstatementRow.bind("Frida Kahlo").decodeAttempt(decoder)

        assert(zstatementString.decodeInternal)(equalTo(decoder)) &&
        assert(zstatementBound.decodeInternal)(equalTo(decoder))

      },
      test("bind preserves decoder") {

        val decoder: Row => String = _.getString("name")

        // implicitly assert is type of ZBoundStatement[String]
        val zstatementRow: ZSimpleStatement[Row]     = ZStatement(query)
        val zstatementBound: ZBoundStatement[String] = zstatementRow.decodeAttempt(decoder).bind("Xul Solar")

        assert(zstatementBound.decodeInternal)(equalTo(decoder))

      },
    )

}
