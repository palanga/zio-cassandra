package palanga.zio.cassandra.session

import palanga.zio.cassandra.{ CassandraException, ZCqlSession }
import zio.ZLayer
import zio.clock.Clock
import zio.console.Console

object ZCqlLayer {

  def default: ZLayer[Console with Clock, CassandraException, ZCqlSession] = from(shouldCreateKeyspace = true)

  def from(
    host: String = "127.0.0.1",
    port: Int = 9042,
    keyspace: String = "test",
    datacenter: String = "datacenter1",
    shouldCreateKeyspace: Boolean = false,
  ): ZLayer[Console with Clock, CassandraException, ZCqlSession] =
    ZCqlManaged.from(host, port, keyspace, datacenter, shouldCreateKeyspace).toLayer

}
