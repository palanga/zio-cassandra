package palanga.zio.cassandra

import com.datastax.oss.driver.api.core.cql.AsyncResultSet
import zio.*
import zio.stream.*

private[cassandra] object util:
  def paginate(initial: AsyncResultSet): Stream[Throwable, AsyncResultSet] =
    ZStream.paginateZIO(initial) { current =>
      if (!current.hasMorePages) ZIO.succeed(current -> None)
      else ZIO.fromCompletionStage(current.fetchNextPage()) map { next => current -> Some(next) }
    }
