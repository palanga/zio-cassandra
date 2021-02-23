package palanga.zio.cassandra.session

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.{ PreparedStatement, SimpleStatement }
import palanga.zio.cassandra.CassandraException.SessionOpenException
import palanga.zio.cassandra.CassandraException
import zio.Schedule.{ recurs, spaced }
import zio.clock.Clock
import zio.console.{ putStrLn, Console }
import zio.duration._
import zio.{ Ref, ZIO }

import java.net.InetSocketAddress
import scala.language.postfixOps

private[session] object ZCqlRaw {

  /**
   * WARNING: not managed resource !!!
   */
  def from(
    host: String = "127.0.0.1",
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
