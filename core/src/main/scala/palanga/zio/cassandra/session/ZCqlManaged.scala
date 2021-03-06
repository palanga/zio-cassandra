package palanga.zio.cassandra.session

import palanga.zio.cassandra.CassandraException
import zio.ZManaged
import zio.clock.Clock
import zio.console.{ putStrLn, Console }

private[cassandra] object ZCqlManaged {

  def default: ZManaged[Console with Clock, CassandraException, ZCqlSession.Service] =
    from(shouldCreateKeyspace = true)

  def from(
    host: String = "127.0.0.1",
    port: Int = 9042,
    keyspace: String = "test",
    datacenter: String = "datacenter1",
    shouldCreateKeyspace: Boolean = false,
  ): ZManaged[Console with Clock, CassandraException, ZCqlSession.Service] =
    ZCqlRaw.from(host, port, keyspace, datacenter, shouldCreateKeyspace).toManaged(closeSession)

  private def closeSession(session: ZCqlSession.Service) =
    (for {
      _ <- putStrLn("Closing cassandra session...")
      _ <- session.close
      _ <- putStrLn("Closed cassandra session")
    } yield ())
      .catchAll(t => putStrLn("Failed trying to close cassandra session:\n" + t.getMessage))

}
