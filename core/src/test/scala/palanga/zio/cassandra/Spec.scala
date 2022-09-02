package palanga.zio.cassandra

import zio.*
import zio.test.*

object Spec extends ZIOSpecDefault {

  private val dependencies =
    ZLayer.scoped(
      Live.live(
        session.auto
          .openDefault()
          .tap(session => ZCqlSessionSpec.initialize(session) <&> ZCqlSessionStreamSpec.initialize(session))
      )
    ) mapError TestFailure.fail

  override def spec =
    suite("zio cassandra suite")(
      ZStatementSpec.testSuite,
      ZCqlSessionSpec.testSuite,
      ZCqlSessionStreamSpec.testSuite,
    ).provideCustomLayerShared(dependencies) @@ TestAspect.parallelN(4)

}
