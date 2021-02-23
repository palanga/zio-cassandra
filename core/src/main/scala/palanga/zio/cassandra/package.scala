package palanga.zio

import palanga.zio.cassandra.session.ZCqlSession
import zio.Has

package object cassandra {
  type ZCqlSession = Has[ZCqlSession.Service]
}
