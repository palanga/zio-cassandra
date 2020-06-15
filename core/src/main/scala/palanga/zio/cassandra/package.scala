package palanga.zio

import zio.Has

package object cassandra {
  type ZCqlSession = Has[ZCqlSession.Service]
}
