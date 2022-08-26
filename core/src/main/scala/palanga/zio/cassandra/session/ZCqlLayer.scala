package palanga.zio.cassandra.session

object ZCqlLayer {

  def default = from(shouldCreateKeyspace = true)

  def from(
    host: String = "127.0.0.1",
    port: Int = 9042,
    keyspace: String = "test",
    datacenter: String = "datacenter1",
    shouldCreateKeyspace: Boolean = false,
  ) =
    ZCqlManaged.from(host, port, keyspace, datacenter, shouldCreateKeyspace).toLayer

}
