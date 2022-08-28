package palanga.zio.cassandra

import com.datastax.oss.driver.api.core.cql.{ AsyncResultSet, Row }
import palanga.zio.cassandra.CassandraException.DecodeException
import zio.stream.{ Stream, ZStream }
import zio.{ Chunk, IO, ZIO }

import scala.reflect.ClassTag

private[cassandra] object util {

  def decode[T](s: ZStatement[T])(row: Row): IO[DecodeException, T] =
    ZIO effect s.decodeUnsafe(row) mapError (DecodeException(s)(_))

  def paginate(initial: AsyncResultSet): Stream[Throwable, AsyncResultSet] =
    ZStream.paginateM(initial) { current =>
      if (!current.hasMorePages) ZIO succeed (current -> None)
      else ZIO fromCompletionStage current.fetchNextPage() map { next => current -> Some(next) }
    }

  object Chunk {
    import scala.jdk.CollectionConverters.IterableHasAsScala
    // See https://github.com/zio/zio/issues/3822
    def fromJavaIterable[A: ClassTag](iterable: java.lang.Iterable[A]): Chunk[A] =
      zio.Chunk fromArray iterable.asScala.toArray
  }

}
