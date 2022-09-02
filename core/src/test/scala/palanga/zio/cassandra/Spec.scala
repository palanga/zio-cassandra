package palanga.zio.cassandra

import zio.test.*
import zio.*

object Spec extends ZIOSpecDefault {

  private val dependencies =
    ZLayer.scoped(Live.live(LiveZCqlSession.openDefault())).tap { sessionLayer =>
      val session = sessionLayer.get
      ZCqlSessionSpec.initialize(session) <&> ZCqlSessionStreamSpec.initialize(session)
    }

  override def spec =
    suite("zio cassandra suite")(
      ZStatementSpec.testSuite,
      ZCqlSessionSpec.testSuite,
      ZCqlSessionStreamSpec.testSuite,
    ).provideCustomLayerShared(dependencies mapError TestFailure.fail) @@ TestAspect.parallelN(4)

}
