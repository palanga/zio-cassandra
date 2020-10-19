package palanga.zio.cassandra

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql._
import palanga.zio.cassandra.CassandraException._
import palanga.zio.cassandra.ZCqlSession.{ decode, paginate }
import zio.stream.{ Stream, ZStream }
import zio.{ Chunk, IO, Ref, ZIO }

import scala.reflect.ClassTag

object ZCqlSession {

  /**
   * {{{
   *    ZCqlSession.fromCqlSessionAuto(
   *      CqlSession
   *        .builder()
   *        .addContactPoint(address)
   *        .withKeyspace(keyspace)
   *        .withLocalDatacenter(datacenter)
   *        .build
   *    )
   *    .toManaged(_.close().fork)
   *    .toLayer
   * }}}
   *
   * Auto because it will automatically prepare and cache your statements for you the first time they are executed.
   */
  def fromCqlSessionAuto(self: => CqlSession): IO[SessionOpenException, ZCqlSession.Service] =
    Ref
      .make(Map.empty[SimpleStatement, PreparedStatement])
      .flatMap(ZIO effect new AutoPrepareStatementSession(self, _))
      .mapError(SessionOpenException)

  trait Service {
    def close: IO[SessionCloseException, Unit]
    def execute(s: ZStatement[_]): IO[CassandraException, AsyncResultSet]
    def execute(s: BoundStatement): IO[QueryExecutionException, AsyncResultSet]
    def execute(s: SimpleStatement): IO[QueryExecutionException, AsyncResultSet]
    def executeHeadOption[Out](s: ZStatement[Out]): IO[CassandraException, Option[Out]]
    def executeHeadOrFail[Out](s: ZStatement[Out]): IO[CassandraException, Out]
    def executePar(ss: BoundStatement*): IO[QueryExecutionException, List[AsyncResultSet]]
    def executeParSimple(ss: SimpleStatement*): IO[QueryExecutionException, List[AsyncResultSet]]
    def prepare(s: SimpleStatement): IO[PrepareStatementException, PreparedStatement]
    def preparePar(ss: SimpleStatement*): IO[PrepareStatementException, List[PreparedStatement]]
    def stream[Out](s: ZStatement[Out]): Stream[CassandraException, Chunk[Out]]
    def stream(s: BoundStatement): Stream[CassandraException, Chunk[Row]]
    def stream(s: SimpleStatement): Stream[CassandraException, Chunk[Row]]
    def streamResultSet(s: ZStatement[_]): Stream[CassandraException, AsyncResultSet]
    def streamResultSet(s: BoundStatement): Stream[CassandraException, AsyncResultSet]
    def streamResultSet(s: SimpleStatement): Stream[CassandraException, AsyncResultSet]
  }

  private[cassandra] def decode[T](s: ZStatement[T])(row: Row): IO[DecodeException, T] =
    ZIO effect s.decodeUnsafe(row) mapError (DecodeException(s)(_))

  private[cassandra] def paginate(initial: AsyncResultSet): Stream[Throwable, AsyncResultSet] =
    ZStream.paginateM(initial) { current: AsyncResultSet =>
      if (!current.hasMorePages) ZIO succeed (current -> None)
      else ZIO fromCompletionStage current.fetchNextPage() map { next: AsyncResultSet => current -> Some(next) }
    }

}

final class AutoPrepareStatementSession private[cassandra] (
  private val session: CqlSession,
  private val preparedStatements: Ref[Map[SimpleStatement, PreparedStatement]],
) extends ZCqlSession.Service {

  override def close: IO[SessionCloseException, Unit] =
    ZIO effect session.close() mapError SessionCloseException

  override def execute(s: ZStatement[_]): IO[CassandraException, AsyncResultSet] =
    preparedStatements.get.flatMap(_.get(s.statement).fold(prepare(s) flatMap executePrepared(s))(executePrepared(s)))

  override def executeHeadOption[Out](s: ZStatement[Out]): IO[CassandraException, Option[Out]] =
    execute(s)
      .map(result => Option(result.one()))
      .flatMap(maybeRow => ZIO effect maybeRow.map(s.decodeUnsafe) mapError DecodeException(s))

  override def executeHeadOrFail[Out](s: ZStatement[Out]): IO[CassandraException, Out] =
    execute(s).flatMap { rs =>
      val head = rs.one()
      if (head != null) decode(s)(head)
      else ZIO fail EmptyResultSetException(s)
    }

  override def execute(s: BoundStatement): IO[QueryExecutionException, AsyncResultSet] =
    ZIO fromCompletionStage session.executeAsync(s) mapError QueryExecutionException(s.getPreparedStatement.getQuery)

  override def executePar(ss: BoundStatement*): IO[QueryExecutionException, List[AsyncResultSet]] =
    ZIO collectAllPar (ss.map(execute).toList)

  override def execute(s: SimpleStatement): IO[QueryExecutionException, AsyncResultSet] =
    ZIO fromCompletionStage session.executeAsync(s) mapError QueryExecutionException(s.getQuery)

  override def executeParSimple(ss: SimpleStatement*): IO[QueryExecutionException, List[AsyncResultSet]] =
    ZIO collectAllPar (ss.map(execute).toList)

  override def prepare(s: SimpleStatement): IO[PrepareStatementException, PreparedStatement] =
    ZIO fromCompletionStage (session prepareAsync s) mapError PrepareStatementException(s)

  override def preparePar(ss: SimpleStatement*): IO[PrepareStatementException, List[PreparedStatement]] =
    ZIO collectAllPar (ss.map(prepare).toList)

  /**
   * This version of the datastax driver doesn't support reactive streams but the version that does is incompatible
   * with the last version of finch.
   */
  override def stream[Out](s: ZStatement[Out]): Stream[CassandraException, Chunk[Out]] =
    streamResultSet(s).mapM(ChunkOps fromJavaIterable _.currentPage() mapM decode(s) mapError DecodeException(s))

  override def stream(s: BoundStatement): Stream[CassandraException, Chunk[Row]] =
    streamResultSet(s).map(ChunkOps fromJavaIterable _.currentPage())

  override def stream(s: SimpleStatement): Stream[CassandraException, Chunk[Row]] =
    streamResultSet(s).map(ChunkOps fromJavaIterable _.currentPage())

  override def streamResultSet(s: ZStatement[_]): Stream[CassandraException, AsyncResultSet] =
    ZStream
      .fromEffect(execute(s))
      .flatMap(paginate(_) mapError QueryExecutionException(s.statement.getQuery))

  override def streamResultSet(s: BoundStatement): Stream[CassandraException, AsyncResultSet] =
    ZStream
      .fromEffect(execute(s))
      .flatMap(paginate(_) mapError QueryExecutionException(s.getPreparedStatement.getQuery))

  override def streamResultSet(s: SimpleStatement): Stream[CassandraException, AsyncResultSet] =
    ZStream
      .fromEffect(execute(s))
      .flatMap(paginate(_) mapError QueryExecutionException(s.getQuery))

  private def executePrepared(s: ZStatement[_])(ps: PreparedStatement): IO[QueryExecutionException, AsyncResultSet] =
    ZIO fromCompletionStage session.executeAsync(s bindUnsafe ps) mapError QueryExecutionException(s.statement.getQuery)

  private def prepare(s: ZStatement[_]): IO[PrepareStatementException, PreparedStatement] =
    ZIO
      .fromCompletionStage(session prepareAsync s.statement) // TODO logging
      .mapError(PrepareStatementException(s.statement))
      .tap(ps => preparedStatements.update(_ + (s.statement -> ps)))

}

private object ChunkOps {
  import scala.jdk.CollectionConverters.IterableHasAsScala
  // See https://github.com/zio/zio/issues/3822
  def fromJavaIterable[A: ClassTag](iterable: java.lang.Iterable[A]): Chunk[A] =
    Chunk fromArray iterable.asScala.toArray
}
