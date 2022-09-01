package palanga.zio.cassandra.session

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql._
import palanga.zio.cassandra.CassandraException._
import palanga.zio.cassandra.util.{ decode, paginate }
import palanga.zio.cassandra.{ util, CassandraException, ZStatement }
import zio.stream.{ Stream, ZStream }
import zio.{ Chunk, IO, Ref, ZIO }

final class LiveZCqlSession private[cassandra] (
  private val session: CqlSession,
  private val preparedStatements: Ref[Map[SimpleStatement, PreparedStatement]],
) extends ZCqlSession {

  override def close: IO[SessionCloseException, Unit] =
    ZIO attempt session.close() mapError SessionCloseException.apply

  override def execute(s: ZStatement[_]): IO[CassandraException, AsyncResultSet] =
    preparedStatements.get.flatMap(_.get(s.statement).fold(prepare(s) flatMap executePrepared(s))(executePrepared(s)))

  override def executeHeadOption[Out](s: ZStatement[Out]): IO[CassandraException, Option[Out]] =
    execute(s)
      .map(result => Option(result.one()))
      .flatMap(maybeRow => ZIO attempt maybeRow.map(s.decodeUnsafe) mapError DecodeException(s))

  override def executeHeadOrFail[Out](s: ZStatement[Out]): IO[CassandraException, Out] =
    execute(s).flatMap { rs =>
      val head = rs.one()
      if (head != null) decode(s)(head)
      else ZIO fail EmptyResultSetException(s)
    }

  override def execute(s: BoundStatement): IO[QueryExecutionException, AsyncResultSet] =
    ZIO fromCompletionStage session.executeAsync(s) mapError QueryExecutionException(s.getPreparedStatement.getQuery)

  override def executePar(ss: BoundStatement*): IO[QueryExecutionException, List[AsyncResultSet]] =
    ZIO collectAllPar ss.map(execute).toList

  override def execute(s: SimpleStatement): IO[QueryExecutionException, AsyncResultSet] =
    ZIO fromCompletionStage session.executeAsync(s) mapError QueryExecutionException(s.getQuery)

  override def executeParSimple(ss: SimpleStatement*): IO[QueryExecutionException, List[AsyncResultSet]] =
    ZIO collectAllPar ss.map(execute).toList

  override def prepare(s: SimpleStatement): IO[PrepareStatementException, PreparedStatement] =
    ZIO fromCompletionStage (session prepareAsync s) mapError PrepareStatementException(s)

  override def preparePar(ss: SimpleStatement*): IO[PrepareStatementException, List[PreparedStatement]] =
    ZIO collectAllPar ss.map(prepare).toList

  override def stream[Out](s: ZStatement[Out]): Stream[CassandraException, Chunk[Out]] =
    streamResultSet(s).mapZIO(Chunk fromJavaIterable _.currentPage() mapZIO decode(s) mapError DecodeException(s))

  override def stream(s: BoundStatement): Stream[CassandraException, Chunk[Row]] =
    streamResultSet(s).map(Chunk fromJavaIterable _.currentPage())

  override def stream(s: SimpleStatement): Stream[CassandraException, Chunk[Row]] =
    streamResultSet(s).map(Chunk fromJavaIterable _.currentPage())

  override def streamResultSet(s: ZStatement[_]): Stream[CassandraException, AsyncResultSet] =
    ZStream
      .fromZIO(execute(s))
      .flatMap(paginate(_) mapError QueryExecutionException(s.statement.getQuery))

  override def streamResultSet(s: BoundStatement): Stream[CassandraException, AsyncResultSet] =
    ZStream
      .fromZIO(execute(s))
      .flatMap(paginate(_) mapError QueryExecutionException(s.getPreparedStatement.getQuery))

  override def streamResultSet(s: SimpleStatement): Stream[CassandraException, AsyncResultSet] =
    ZStream
      .fromZIO(execute(s))
      .flatMap(paginate(_) mapError QueryExecutionException(s.getQuery))

  private def executePrepared(s: ZStatement[_])(ps: PreparedStatement): IO[QueryExecutionException, AsyncResultSet] =
    ZIO fromCompletionStage session.executeAsync(s bindUnsafe ps) mapError QueryExecutionException(s.statement.getQuery)

  private def prepare(s: ZStatement[_]): IO[PrepareStatementException, PreparedStatement] =
    ZIO
      .fromCompletionStage(session prepareAsync s.statement) // TODO logging
      .mapError(PrepareStatementException(s.statement))
      .tap(ps => preparedStatements.update(_ + (s.statement -> ps)))

}
