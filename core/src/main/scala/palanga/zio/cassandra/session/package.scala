package palanga.zio.cassandra

package object session {
  val layer   = ZCqlLayer
  val managed = ZCqlManaged
  val raw     = ZCqlRaw
}
