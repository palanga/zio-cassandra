package palanga.zio.cassandra

import com.datastax.oss.driver.api.core.cql.{ Row, SimpleStatement }
import palanga.zio.cassandra.ZStatement.{ bindNothing, identityRow }
import zio.test.Assertion._
import zio.test._

object ZStatementSpec extends DefaultRunnableSpec {

  private val query = "SELECT * FROM painters WHERE name=?"

  private val testSuite =
    suite("ZStatement suite")(
      test("from apply") {

        val zstatement = ZStatement(query)
        val statement  = SimpleStatement.builder(query).build

        assert(zstatement.statement)(equalTo(statement)) &&
        assert(zstatement.bindUnsafe)(equalTo(bindNothing)) &&
        assert(zstatement.decodeUnsafe)(equalTo(identityRow))

      },
      test("from string ops") {

        import palanga.zio.cassandra.ZStatement.StringOps

        val zstatement = query.toStatement
        val statement  = SimpleStatement.builder(query).build

        assert(zstatement.statement)(equalTo(statement)) &&
        assert(zstatement.bindUnsafe)(equalTo(bindNothing)) &&
        assert(zstatement.decodeUnsafe)(equalTo(identityRow))

      },
      test("decode") {

        val decoder: Row => String = _.getString("name")

        // implicitly assert is type of ZSimpleStatement[String] and ZBoundStatement[String]
        val zstatementRow: ZSimpleStatement[Row]       = ZStatement(query)
        val zstatementString: ZSimpleStatement[String] = zstatementRow.decode(decoder)
        val zstatementBound: ZBoundStatement[String]   = zstatementRow.bind("Frida Kahlo").decode(decoder)

        assert(zstatementString.decodeUnsafe)(equalTo(decoder)) &&
        assert(zstatementBound.decodeUnsafe)(equalTo(decoder))

      },
      test("bind preserves decoder") {

        val decoder: Row => String = _.getString("name")

        // implicitly assert is type of ZBoundStatement[String]
        val zstatementRow: ZSimpleStatement[Row]     = ZStatement(query)
        val zstatementBound: ZBoundStatement[String] = zstatementRow.decode(decoder).bind("Xul Solar")

        assert(zstatementBound.decodeUnsafe)(equalTo(decoder))

      },
    )

  override def spec = testSuite

}
