package io.iohk.armadillo

class MethodName(private val value: String) extends AnyVal {
  def asString: String = value
}

trait MethodNameInterpolator {
  implicit class MethodNameContext(sc: StringContext) {
    def m(args: Any*): MethodName = MethodNameInterpolator.interpolate(sc, args)
  }
}
object MethodNameInterpolator {
  def interpolate(sc: StringContext, args: Any*): MethodName = {
    val strings = sc.parts.iterator
    val expressions = args.iterator
    val buf = new StringBuilder(strings.next())
    while (strings.hasNext) {
      buf.append(expressions.next())
      buf.append(strings.next())
    }
    val str = buf.toString()
    if (str.startsWith("rpc.")) {
      throw new IllegalArgumentException("'rpc.' prefix is reserved for rpc-internal methods and extensions")
    } else {
      new MethodName(str)
    }
  }
}
