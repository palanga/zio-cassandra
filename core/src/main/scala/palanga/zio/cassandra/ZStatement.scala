package palanga.zio.cassandra

import com.datastax.oss.driver.api.core.cql.*
import palanga.zio.cassandra.CassandraException.DecodeException

import scala.util.Try

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

    def bind(params: Any*): ZBoundStatement[Row] =
      new ZBoundStatement[Row](self, _.bind(params*), identityRow)

    def decode[T](f: Row => Either[DecodeException, T]): ZSimpleStatement[T] =
      new ZSimpleStatement[T](self, _.bind(), f)

    def decodeAttempt[T](f: Row => T): ZSimpleStatement[T] =
      decode(row => Try(f(row)).toEither.left.map(DecodeException(self)(_)))

  }

  private[cassandra] val identityRow: Row => Either[DecodeException, Row] = r => Right(r)
  private[cassandra] val bindNothing: PreparedStatement => BoundStatement = _.bind()

}

trait ZStatement[Out] {
  private[cassandra] val statement: SimpleStatement
  private[cassandra] val bindInternal: PreparedStatement => BoundStatement
  private[cassandra] val decodeInternal: Row => Either[DecodeException, Out]
}

final class ZSimpleStatement[Out](
  private[cassandra] val statement: SimpleStatement,
  private[cassandra] val bindInternal: PreparedStatement => BoundStatement,
  private[cassandra] val decodeInternal: Row => Either[DecodeException, Out],
) extends ZStatement[Out] {

  def bind(params: Any*): ZBoundStatement[Out] =
    new ZBoundStatement[Out](statement, _.bind(params*), decodeInternal)

  def decode[T](f: Row => Either[DecodeException, T]): ZSimpleStatement[T] =
    new ZSimpleStatement[T](statement, bindInternal, f)

  def decodeAttempt[T](f: Row => T): ZSimpleStatement[T] =
    decode(row => Try(f(row)).toEither.left.map(DecodeException(statement)(_)))

}

final class ZBoundStatement[Out](
  private[cassandra] val statement: SimpleStatement,
  private[cassandra] val bindInternal: PreparedStatement => BoundStatement,
  private[cassandra] val decodeInternal: Row => Either[DecodeException, Out],
) extends ZStatement[Out] {

  def decode[T](f: Row => Either[DecodeException, T]): ZBoundStatement[T] =
    new ZBoundStatement[T](statement, bindInternal, f)

  /**
   * Add a decoder to this statement. Any exception thrown by the provided function will be catch.
   */
  def decodeAttempt[T](f: Row => T): ZBoundStatement[T] =
    decode(row => Try(f(row)).toEither.left.map(DecodeException(statement)(_)))

}
