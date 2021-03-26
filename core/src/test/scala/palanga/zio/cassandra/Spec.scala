package palanga.zio.cassandra

import zio.clock.Clock
import zio.console.Console
import zio.test.{ DefaultRunnableSpec, TestAspect, TestFailure }

object Spec extends DefaultRunnableSpec {

  private val dependencies =
    Console.live ++ Clock.live >>> session.layer.default.tap { sessionLayer =>
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
