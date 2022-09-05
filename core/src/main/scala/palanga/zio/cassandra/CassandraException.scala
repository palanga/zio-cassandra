package palanga.zio.cassandra

import com.datastax.oss.driver.api.core.cql.SimpleStatement

sealed trait CassandraException extends Exception
object CassandraException {

  case class DecodeException(s: SimpleStatement)(cause: Throwable)
      extends Exception(s"Failed decoding the result of <<${s.getQuery}>>: ${cause.getMessage}")
      with CassandraException

  case class EmptyResultSetException(s: SimpleStatement)
      extends Exception(s"${s.getQuery} returned empty")
      with CassandraException

  case class PrepareStatementException(s: SimpleStatement)(cause: Throwable)
      extends Exception(s"Failed preparing the statement <<${s.getQuery}>>: ${cause.getMessage}")
      with CassandraException

  case class QueryExecutionException(query: String)(cause: Throwable)
      extends Exception(s"Failed executing <<$query>>: ${cause.getMessage}")
      with CassandraException

  case class SessionCloseException(cause: Throwable)
      extends Exception(s"Failed closing cql session: ${cause.getMessage}")
      with CassandraException

  case class SessionOpenException(cause: Throwable)
      extends Exception(s"Failed opening cql session: ${cause.getMessage}")
      with CassandraException

}
