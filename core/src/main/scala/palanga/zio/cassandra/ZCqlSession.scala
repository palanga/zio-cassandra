package palanga.zio.cassandra

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql._
import palanga.zio.cassandra.CassandraException._
import zio.Schedule.{ recurs, spaced }
import zio._
import zio.clock.Clock
import zio.console.{ putStrLn, Console }
import zio.duration._
import zio.stream.Stream

import java.net.InetSocketAddress
import scala.language.postfixOps

object ZCqlSession {

  val layer   = ZCqlLayer
  val managed = ZCqlManaged
  val raw     = ZCqlRaw

  object ZCqlRaw {

    /**
     * WARNING: not managed resource !!!
     */
    def from(
      host: String = "localhost",
      port: Int = 9042,
      keyspace: String = "test",
      datacenter: String = "datacenter1",
      shouldCreateKeyspace: Boolean = false,
    ): ZIO[Console with Clock, CassandraException, ZCqlSession.Service] =
      (for {
        _       <- putStrLn("Opening cassandra session...")
        session <- fromCqlSession(
                     CqlSession
                       .builder()
                       .addContactPoint(new InetSocketAddress(host, port))
                       .withLocalDatacenter(datacenter)
                       .build
                   )
        _       <- putStrLn(s"Configuring cassandra keyspace $keyspace...")
        _       <- session execute createKeyspace(keyspace) when shouldCreateKeyspace
        _       <- session execute useKeyspace(keyspace)
        _       <- putStrLn("Cassandra session is ready")
      } yield session)
        .tapError(t => putStrLn("Failed trying to build cql session: " + t.getMessage))
        .tapError(_ => putStrLn("Retrying in one second..."))
        .retry(spaced(1 second) && recurs(9))

    /**
     * WARNING: not managed resource !!!
     *
     * Keep in mind that calling `build` on [[CqlSession]] has side-effects
     */
    def fromCqlSession(self: => CqlSession): ZIO[Any, SessionOpenException, ZCqlSession.Service] =
      Ref
        .make(Map.empty[SimpleStatement, PreparedStatement])
        .flatMap(ZIO effect new LiveZCqlSession(self, _))
        .mapError(SessionOpenException)

  }

  object ZCqlLayer {

    def default: ZLayer[Console with Clock, CassandraException, ZCqlSession] = from(shouldCreateKeyspace = true)

    def from(
      host: String = "0.0.0.0",
      port: Int = 9042,
      keyspace: String = "test",
      datacenter: String = "datacenter1",
      shouldCreateKeyspace: Boolean = false,
    ): ZLayer[Console with Clock, CassandraException, ZCqlSession] =
      ZCqlManaged.from(host, port, keyspace, datacenter, shouldCreateKeyspace).toLayer

  }

  object ZCqlManaged {

    def default: ZManaged[Console with Clock, CassandraException, ZCqlSession.Service] =
      from(shouldCreateKeyspace = true)

    def from(
      host: String = "0.0.0.0",
      port: Int = 9042,
      keyspace: String = "test",
      datacenter: String = "datacenter1",
      shouldCreateKeyspace: Boolean = false,
    ): ZManaged[Console with Clock, CassandraException, ZCqlSession.Service] =
      ZCqlRaw.from(host, port, keyspace, datacenter, shouldCreateKeyspace).toManaged(closeSession)

  }

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

  private def closeSession(session: ZCqlSession.Service) =
    (for {
      _ <- putStrLn("Closing cassandra session...")
      _ <- session.close
      _ <- putStrLn("Closed cassandra session")
    } yield ())
      .catchAll(t => putStrLn("Failed trying to close cassandra session:\n" + t.getMessage))

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

}
