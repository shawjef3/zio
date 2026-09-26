package zio.internal

import zio.ZIOBaseSpec
import zio.test._

import java.io.DataInputStream

/**
 * `FiberRuntime.unwindSuccessInner` and `FiberRuntime.evalSyncInner` invoke
 * application lambdas on behalf of `runLoopInner`, and `runLoopInner` itself
 * must not be inlined into `runLoop`. All three must stay above C2's
 * `FreqInlineSize` (325 bytes of bytecode) so C2 never inlines them, and with
 * them the application's lambdas, into the loops (zio/zio#11251). This spec
 * reads the class file and fails if a refactor drops any of them below the
 * threshold.
 */
object FiberRuntimeHelperSizeSpec extends ZIOBaseSpec {

  private val FreqInlineSize = 325

  /** Bytecode length of each method in the class file, by method name. */
  private def codeLengths(cls: Class[_]): Map[String, Int] = {
    val in = new DataInputStream(cls.getResourceAsStream(cls.getSimpleName + ".class"))
    try {
      in.readInt(); in.readUnsignedShort(); in.readUnsignedShort() // magic, minor, major
      val cpCount = in.readUnsignedShort()
      val utf8    = new Array[String](cpCount)
      var i       = 1
      while (i < cpCount) {
        in.readUnsignedByte() match {
          case 1                          => utf8(i) = in.readUTF()
          case 3 | 4                      => in.skipBytes(4)
          case 5 | 6                      => in.skipBytes(8); i += 1
          case 7 | 8 | 16 | 19 | 20       => in.skipBytes(2)
          case 9 | 10 | 11 | 12 | 17 | 18 => in.skipBytes(4)
          case 15                         => in.skipBytes(3)
          case tag                        => throw new IllegalStateException(s"unknown constant pool tag $tag")
        }
        i += 1
      }
      in.skipBytes(6)                          // access, this, super
      in.skipBytes(2 * in.readUnsignedShort()) // interfaces
      val fields = in.readUnsignedShort()
      (0 until fields).foreach { _ =>
        in.skipBytes(6)
        (0 until in.readUnsignedShort()).foreach { _ => in.skipBytes(2); in.skipBytes(in.readInt()) }
      }
      val methods = in.readUnsignedShort()
      (0 until methods).flatMap { _ =>
        in.skipBytes(2)
        val name = utf8(in.readUnsignedShort())
        in.skipBytes(2)
        (0 until in.readUnsignedShort()).flatMap { _ =>
          val attr = utf8(in.readUnsignedShort())
          val len  = in.readInt()
          if (attr == "Code") {
            in.skipBytes(4) // max_stack, max_locals
            val codeLength = in.readInt()
            in.skipBytes(len - 8)
            Some(name -> codeLength)
          } else {
            in.skipBytes(len)
            None
          }
        }
      }.toMap
    } finally in.close()
  }

  def spec = suite("FiberRuntimeHelperSizeSpec")(
    test("run loop helpers are too large for C2 to inline into the loops") {
      val sizes = codeLengths(classOf[FiberRuntime[_, _]])
      // Match on suffix in case the compiler expands a private name.
      def size(name: String): Int = sizes.collectFirst { case (n, s) if n.endsWith(name) => s }.getOrElse(0)
      assertTrue(
        size("unwindSuccessInner") > FreqInlineSize,
        size("evalSyncInner") > FreqInlineSize,
        size("runLoopInner") > FreqInlineSize
      )
    }
  )
}
