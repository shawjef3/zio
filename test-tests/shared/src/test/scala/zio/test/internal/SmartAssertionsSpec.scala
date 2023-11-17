package zio.test.internal

import zio.test._

object SmartAssertionsSpec extends ZIOBaseSpec {
  class A
  class B extends A

  def spec = suite("containsSeq")(
    test("with embedded subtype") {
      val arrow = !(TestArrow.succeed[Seq[Seq[A]]](Seq[Seq[A]]()) >>> SmartAssertions.containsSeq[Seq[B]](Seq[B]()))
        .withCode("!Seq[Seq[A]]().contains(Seq[B]())")
      val testResult = TestResult(arrow)
      testResult
    }
  )

}
