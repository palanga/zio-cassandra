package palanga.zio

import palanga.zio.cassandra.session.ZCqlSession

package object cassandra {
  type ZCqlSession = ZCqlSession.Service
}
