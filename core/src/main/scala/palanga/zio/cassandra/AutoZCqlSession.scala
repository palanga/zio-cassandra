package palanga.zio.cassandra

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.*
import palanga.zio.cassandra.CassandraException.*
import palanga.zio.cassandra.util.paginate
import zio.*
import zio.Console.{ printLine, printLineError }
import zio.Schedule.{ recurs, spaced }
import zio.stream.{ Stream, ZStream }

import java.net.InetSocketAddress
import scala.language.postfixOps

/**
 * A Cassandra session that need minimal configuration. The first time a statement is executed by this session it is
 * automatically prepared and cached for further use.
 */
object AutoZCqlSession {

  // TODO set query timeout
  def openDefault(): ZIO[Scope, SessionOpenException, ZCqlSession] = open()

  def open(
    host: String = "127.0.0.1",
    port: Int = 9042,
    keyspace: String = "test",
    datacenter: String = "datacenter1",
    shouldCreateKeyspace: Boolean = true,
  ): ZIO[Scope, SessionOpenException, ZCqlSession] =
    (for {
      _          <- printLine("Opening cassandra session...")
      statements <- Ref.make(Map.empty[SimpleStatement, PreparedStatement])
      session    <- ZIO
                      .attempt(
                        new AutoZCqlSession(
                          CqlSession
                            .builder()
                            .addContactPoint(new InetSocketAddress(host, port))
                            .withLocalDatacenter(datacenter)
                            .build,
                          statements,
                        )
                      )
      _          <- printLine(s"Configuring cassandra keyspace $keyspace...")
      _          <- session.execute(createKeyspace(keyspace)).when(shouldCreateKeyspace)
      _          <- session.execute(useKeyspace(keyspace))
      _          <- printLine("Cassandra session is ready")
    } yield session)
      .tapError(t => printLine("Failed trying to build cql session: " + t.getMessage))
      .tapError(_ => printLine("Retrying in one second..."))
      .mapError(SessionOpenException.apply)
      .retry(spaced(1 second) && recurs(9))
      .withFinalizer(closeSession(_))

  def openFromDatastaxSession(underlying: => CqlSession): ZIO[Scope, SessionOpenException, ZCqlSession] =
    (for {
      _          <- printLine("Opening cassandra session...")
      statements <- Ref.make(Map.empty[SimpleStatement, PreparedStatement])
      session    <- ZIO.attempt(new AutoZCqlSession(underlying, statements))
      _          <- printLine("Cassandra session is ready")
    } yield session)
      .tapError(t => printLineError("Failed trying to build cql session: " + t.getMessage))
      .tapError(_ => printLineError("Retrying in one second..."))
      .mapError(SessionOpenException.apply)
      .retry(spaced(1 second) && recurs(9))
      .withFinalizer(closeSession(_))

  private def closeSession(session: ZCqlSession) =
    (for {
      _ <- printLine("Closing cassandra session...")
      _ <- session.close
      _ <- printLine("Closed cassandra session")
    } yield ())
      .catchAll(t => printLineError("Failed trying to close cassandra session:\n" + t.getMessage).ignore)

  private def createKeyspace(keyspace: String) =
    SimpleStatement
      .builder(
        s"""CREATE KEYSPACE IF NOT EXISTS $keyspace WITH REPLICATION = {
           |  'class': 'SimpleStrategy',
           |  'replication_factor': 1
           |};
           |""".stripMargin
      )
      .build

  private def useKeyspace(keyspace: String) = SimpleStatement.builder(s"USE $keyspace;").build

}

/**
 * @see
 *   [[AutoZCqlSession]]
 */
final class AutoZCqlSession private[cassandra] (
  private val session: CqlSession,
  private val preparedStatements: Ref[Map[SimpleStatement, PreparedStatement]],
) extends ZCqlSession {

  override def close: IO[SessionCloseException, Unit] =
    ZIO.attempt(session.close()).mapError(SessionCloseException.apply)

  override def execute(s: ZStatement[?]): IO[CassandraException, AsyncResultSet] =
    preparedStatements.get.flatMap(_.get(s.statement).fold(prepare(s).flatMap(executePrepared(s)))(executePrepared(s)))

  override def executeHeadOption[Out](s: ZStatement[Out]): IO[CassandraException, Option[Out]] = {
    val zero: Either[DecodeException, Option[Out]] = Right(None)
    execute(s)
      .map(result => Option(result.one()))
      .flatMap(maybeRow => ZIO.fromEither(maybeRow.fold(zero)(r => s.decodeInternal(r).map(Some(_)))))
  }

  override def executeHeadOrFail[Out](s: ZStatement[Out]): IO[CassandraException, Out] =
    execute(s).flatMap { rs =>
      val head = rs.one()
      if (head != null) ZIO.fromEither(s.decodeInternal(head))
      else ZIO.fail(EmptyResultSetException(s.statement))
    }

  override def execute(s: BoundStatement): IO[QueryExecutionException, AsyncResultSet] =
    ZIO.fromCompletionStage(session.executeAsync(s)).mapError(QueryExecutionException(s.getPreparedStatement.getQuery))

  override def executePar(ss: BoundStatement*): IO[QueryExecutionException, List[AsyncResultSet]] =
    ZIO.collectAllPar(ss.map(execute).toList)

  override def execute(s: SimpleStatement): IO[QueryExecutionException, AsyncResultSet] =
    ZIO.fromCompletionStage(session.executeAsync(s)).mapError(QueryExecutionException(s.getQuery))

  override def executeParSimple(ss: SimpleStatement*): IO[QueryExecutionException, List[AsyncResultSet]] =
    ZIO.collectAllPar(ss.map(execute).toList)

  override def prepare(s: SimpleStatement): IO[PrepareStatementException, PreparedStatement] =
    ZIO.fromCompletionStage(session.prepareAsync(s)).mapError(PrepareStatementException(s))

  override def preparePar(ss: SimpleStatement*): IO[PrepareStatementException, List[PreparedStatement]] =
    ZIO.collectAllPar(ss.map(prepare).toList)

  override def stream[Out](s: ZStatement[Out]): Stream[CassandraException, Chunk[Out]] =
    streamResultSet(s)
      .map(resultSet => Chunk.fromJavaIterable(resultSet.currentPage()))
      .mapZIO(page => ZIO.validate(page)(row => ZIO.fromEither(s.decodeInternal(row))).mapError(_.head))

  override def stream(s: BoundStatement): Stream[CassandraException, Chunk[Row]] =
    streamResultSet(s).map(resultSet => Chunk.fromJavaIterable(resultSet.currentPage()))

  override def stream(s: SimpleStatement): Stream[CassandraException, Chunk[Row]] =
    streamResultSet(s).map(resultSet => Chunk.fromJavaIterable(resultSet.currentPage()))

  override def streamResultSet(s: ZStatement[?]): Stream[CassandraException, AsyncResultSet] =
    ZStream
      .fromZIO(execute(s))
      .flatMap(paginate(_).mapError(QueryExecutionException(s.statement.getQuery)))

  override def streamResultSet(s: BoundStatement): Stream[CassandraException, AsyncResultSet] =
    ZStream
      .fromZIO(execute(s))
      .flatMap(paginate(_).mapError(QueryExecutionException(s.getPreparedStatement.getQuery)))

  override def streamResultSet(s: SimpleStatement): Stream[CassandraException, AsyncResultSet] =
    ZStream
      .fromZIO(execute(s))
      .flatMap(paginate(_).mapError(QueryExecutionException(s.getQuery)))

  private def executePrepared(s: ZStatement[?])(ps: PreparedStatement): IO[QueryExecutionException, AsyncResultSet] =
    ZIO
      .fromCompletionStage(session.executeAsync(s.bindInternal(ps)))
      .mapError(QueryExecutionException(s.statement.getQuery))

  private def prepare(s: ZStatement[?]): IO[PrepareStatementException, PreparedStatement] =
    ZIO
      .fromCompletionStage(session.prepareAsync(s.statement)) // TODO logging
      .mapError(PrepareStatementException(s.statement))
      .tap(ps => preparedStatements.update(_ + (s.statement -> ps)))

}
