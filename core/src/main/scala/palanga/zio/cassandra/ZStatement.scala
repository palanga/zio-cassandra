package palanga.zio.cassandra

import com.datastax.oss.driver.api.core.cql.*

object ZStatement {

  def apply(query: String): ZSimpleStatement[Row] =
    new ZSimpleStatement[Row](SimpleStatement.builder(query).build(), bindNothing, identityRow)

  def fromString(query: String): ZSimpleStatement[Row] = apply(query)

  def fromDatastaxStatement(statement: SimpleStatement): ZSimpleStatement[Row] =
    ZSimpleStatement[Row](statement, bindNothing, identityRow)

  implicit class StringOps(private val self: String) extends AnyVal {
    def toStatement: ZSimpleStatement[Row] = apply(self)
  }

  implicit class SimpleStatementOps(private val self: SimpleStatement) extends AnyVal {
    def bind(params: Any*): ZBoundStatement[Row]    = new ZBoundStatement[Row](self, _.bind(params: _*), identity)
    def decode[T](f: Row => T): ZSimpleStatement[T] = new ZSimpleStatement[T](self, _.bind(), f)
  }

  private[cassandra] val identityRow: Row => Row                          = identity
  private[cassandra] val bindNothing: PreparedStatement => BoundStatement = _.bind()

}

trait ZStatement[Out] {
  private[cassandra] val statement: SimpleStatement
  private[cassandra] val bindUnsafe: PreparedStatement => BoundStatement
  private[cassandra] val decodeUnsafe: Row => Out
}

final class ZSimpleStatement[Out](
  private[cassandra] val statement: SimpleStatement,
  private[cassandra] val bindUnsafe: PreparedStatement => BoundStatement,
  private[cassandra] val decodeUnsafe: Row => Out,
) extends ZStatement[Out] {
  def bind(params: Any*): ZBoundStatement[Out]    = new ZBoundStatement[Out](statement, _.bind(params: _*), decodeUnsafe)
  def decode[T](f: Row => T): ZSimpleStatement[T] = new ZSimpleStatement[T](statement, bindUnsafe, f)
}

final class ZBoundStatement[Out](
  private[cassandra] val statement: SimpleStatement,
  private[cassandra] val bindUnsafe: PreparedStatement => BoundStatement,
  private[cassandra] val decodeUnsafe: Row => Out,
) extends ZStatement[Out] {
  def decode[T](f: Row => T): ZBoundStatement[T] = new ZBoundStatement[T](statement, bindUnsafe, f)
}
