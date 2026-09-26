package zio.internal

import zio.ZIOBaseSpec
import zio.test._

/**
 * `RunLoopFlags.USER_CODE_VIA_HELPERS` selects which form of
 * `FiberRuntime.runLoopInner` this JVM runs (zio/zio#11251). This spec pins the
 * selection rule, and, when CI asks for a specific form through the
 * `ZIO_RUNLOOP_USER_CODE_VIA_HELPERS` environment variable, checks that the
 * build really forwarded it to this test JVM, so a broken forwarding cannot
 * turn the "other form" CI job into a silent repeat of the default one.
 */
object RunLoopFlagsSpec extends ZIOBaseSpec {

  private val property    = sys.props.get(RunLoopFlags.USER_CODE_VIA_HELPERS_PROPERTY).map(_.trim)
  private val requested   = sys.env.get("ZIO_RUNLOOP_USER_CODE_VIA_HELPERS").map(_.trim)
  private val arch        = sys.props.getOrElse("os.arch", "")
  private val archDefault = arch == "aarch64" || arch == "arm64"

  def spec = suite("RunLoopFlagsSpec")(
    test("the switch follows the system property when it is set, else the architecture default") {
      val expected = property.map(_.toLowerCase) match {
        case Some("true")  => true
        case Some("false") => false
        case _             => archDefault
      }
      assertTrue(RunLoopFlags.USER_CODE_VIA_HELPERS == expected)
    },
    test("a form requested through ZIO_RUNLOOP_USER_CODE_VIA_HELPERS reaches the test JVM") {
      requested match {
        case Some(value) => assertTrue(property.contains(value), RunLoopFlags.USER_CODE_VIA_HELPERS == value.toBoolean)
        case None        => assertCompletes
      }
    }
  )
}
