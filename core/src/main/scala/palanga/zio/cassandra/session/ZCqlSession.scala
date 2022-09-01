package palanga.zio.cassandra.session

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.*
import palanga.zio.cassandra.CassandraException.*
import palanga.zio.cassandra.{ CassandraException, ZCqlSession, ZStatement }
import zio.*
import zio.Console.{ printLine, printLineError }
import zio.Schedule.{ recurs, spaced }
import zio.stream.{ Stream, ZStream }

import java.net.InetSocketAddress
import scala.language.postfixOps

trait ZCqlSession:
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

/**
 * TODO we should use a logger instead of Console.printLine
 */
object ZCqlSession:

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
                        LiveZCqlSession(
                          CqlSession
                            .builder()
                            .addContactPoint(new InetSocketAddress(host, port))
                            .withLocalDatacenter(datacenter)
                            .build,
                          statements,
                        )
                      )
      _          <- printLine(s"Configuring cassandra keyspace $keyspace...")
      _          <- session execute createKeyspace(keyspace) when shouldCreateKeyspace
      _          <- session execute useKeyspace(keyspace)
      _          <- printLine("Cassandra session is ready")
    } yield session)
      .tapError(t => printLine("Failed trying to build cql session: " + t.getMessage))
      .tapError(_ => printLine("Retrying in one second..."))
      .mapError(SessionOpenException.apply)
      .retry(spaced(1 second) && recurs(9))
      .withFinalizer(closeSession(_))

  def openFromCqlSession(underlying: => CqlSession): ZIO[Scope, SessionOpenException, ZCqlSession] =
    (for
      _          <- printLine("Opening cassandra session...")
      statements <- Ref.make(Map.empty[SimpleStatement, PreparedStatement])
      session    <- ZIO.attempt(LiveZCqlSession(underlying, statements))
      _          <- printLine("Cassandra session is ready")
    yield session)
      .tapError(t => printLineError("Failed trying to build cql session: " + t.getMessage))
      .tapError(_ => printLineError("Retrying in one second..."))
      .mapError(SessionOpenException.apply)
      .retry(spaced(1 second) && recurs(9))
      .withFinalizer(closeSession(_))

  @deprecated("Use ZCqlSession trait instead")
  type Service = ZCqlSession

  def close: ZIO[ZCqlSession, SessionCloseException, Unit] =
    ZIO.serviceWithZIO(_.close)

  @deprecated("Use untyped.execute instead")
  def execute(s: ZStatement[_]): ZIO[ZCqlSession, CassandraException, AsyncResultSet] =
    untyped.execute(s)

  @deprecated("Use untyped.execute instead")
  def execute(s: BoundStatement): ZIO[ZCqlSession, QueryExecutionException, AsyncResultSet] =
    untyped.execute(s)

  @deprecated("Use untyped.execute instead")
  def execute(s: SimpleStatement): ZIO[ZCqlSession, QueryExecutionException, AsyncResultSet] =
    untyped.execute(s)

  @deprecated("Use untyped.executePar instead")
  def executePar(ss: BoundStatement*): ZIO[ZCqlSession, QueryExecutionException, List[AsyncResultSet]] =
    untyped.executePar(ss: _*)

  @deprecated("Use untyped.executeParSimple instead")
  def executeParSimple(ss: SimpleStatement*): ZIO[ZCqlSession, QueryExecutionException, List[AsyncResultSet]] =
    untyped.executeParSimple(ss: _*)

  def executeHeadOption[Out](s: ZStatement[Out]): ZIO[ZCqlSession, CassandraException, Option[Out]] =
    ZIO.serviceWithZIO(_.executeHeadOption(s))

  def executeHeadOrFail[Out](s: ZStatement[Out]): ZIO[ZCqlSession, CassandraException, Out] =
    ZIO.serviceWithZIO(_.executeHeadOrFail(s))

  def prepare(s: SimpleStatement): ZIO[ZCqlSession, PrepareStatementException, PreparedStatement] =
    ZIO.serviceWithZIO(_.prepare(s))

  def preparePar(ss: SimpleStatement*): ZIO[ZCqlSession, PrepareStatementException, List[PreparedStatement]] =
    ZIO.serviceWithZIO(_.preparePar(ss: _*))

  /**
   * A stream of chunks, every chunk representing a page.
   */
  def stream[Out](s: ZStatement[Out]): ZStream[ZCqlSession, CassandraException, Chunk[Out]] =
    ZStream.serviceWithStream(_.stream(s))

  @deprecated("Use untyped.stream instead")
  def stream(s: BoundStatement): ZStream[ZCqlSession, CassandraException, Chunk[Row]] =
    untyped.stream(s)

  @deprecated("Use untyped.stream instead")
  def stream(s: SimpleStatement): ZStream[ZCqlSession, CassandraException, Chunk[Row]] =
    untyped.stream(s)

  @deprecated("Use untyped.streamResultSet instead")
  def streamResultSet(s: ZStatement[_]): ZStream[ZCqlSession, CassandraException, AsyncResultSet] =
    untyped.streamResultSet(s)

  @deprecated("Use untyped.streamResultSet instead")
  def streamResultSet(s: BoundStatement): ZStream[ZCqlSession, CassandraException, AsyncResultSet] =
    untyped.streamResultSet(s)

  @deprecated("Use untyped.streamResultSet instead")
  def streamResultSet(s: SimpleStatement): ZStream[ZCqlSession, CassandraException, AsyncResultSet] =
    untyped.streamResultSet(s)

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

  object untyped:

    def execute(s: ZStatement[_]): ZIO[ZCqlSession, CassandraException, AsyncResultSet] =
      ZIO.serviceWithZIO(_.execute(s))

    def execute(s: BoundStatement): ZIO[ZCqlSession, QueryExecutionException, AsyncResultSet] =
      ZIO.serviceWithZIO(_.execute(s))

    /**
     * Execute a simple statement without first calculating and caching its prepared statement. Use some of the other
     * alternatives for automatically preparing and caching statements.
     */
    def execute(s: SimpleStatement): ZIO[ZCqlSession, QueryExecutionException, AsyncResultSet] =
      ZIO.serviceWithZIO(_.execute(s))

    def executePar(ss: BoundStatement*): ZIO[ZCqlSession, QueryExecutionException, List[AsyncResultSet]] =
      ZIO.serviceWithZIO(_.executePar(ss: _*))

    /**
     * The same as `execute` but in parallel.
     */
    def executeParSimple(ss: SimpleStatement*): ZIO[ZCqlSession, QueryExecutionException, List[AsyncResultSet]] =
      ZIO.serviceWithZIO(_.executeParSimple(ss: _*))

    def stream(s: BoundStatement): ZStream[ZCqlSession, CassandraException, Chunk[Row]] =
      ZStream.serviceWithStream(_.stream(s))

    def stream(s: SimpleStatement): ZStream[ZCqlSession, CassandraException, Chunk[Row]] =
      ZStream.serviceWithStream(_.stream(s))

    def streamResultSet(s: ZStatement[_]): ZStream[ZCqlSession, CassandraException, AsyncResultSet] =
      ZStream.serviceWithStream(_.streamResultSet(s))

    def streamResultSet(s: BoundStatement): ZStream[ZCqlSession, CassandraException, AsyncResultSet] =
      ZStream.serviceWithStream(_.streamResultSet(s))

    def streamResultSet(s: SimpleStatement): ZStream[ZCqlSession, CassandraException, AsyncResultSet] =
      ZStream.serviceWithStream(_.streamResultSet(s))
