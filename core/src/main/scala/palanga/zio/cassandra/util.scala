package palanga.zio.cassandra

import com.datastax.oss.driver.api.core.cql.{ AsyncResultSet, Row }
import palanga.zio.cassandra.CassandraException.DecodeException
import zio.stream.{ Stream, ZStream }
import zio.{ IO, ZIO }

private[cassandra] object util {

  def decode[T](s: ZStatement[T])(row: Row): IO[DecodeException, T] =
    ZIO attempt s.decodeUnsafe(row) mapError (DecodeException(s)(_))

  def paginate(initial: AsyncResultSet): Stream[Throwable, AsyncResultSet] =
    ZStream.paginateZIO(initial) { current =>
      if (!current.hasMorePages) ZIO succeed (current -> None)
      else ZIO fromCompletionStage current.fetchNextPage() map { next => current -> Some(next) }
    }

}
