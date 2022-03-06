package io.iohk.armadillo.example

object Test {
  def main(args: Array[String]): Unit = {
    val str = """{"a": 1, "b": "string"}"""
    io.circe.parser.parse(str) match {
      case Left(value)  => ???
      case Right(value) => println(value.noSpaces)
    }
  }
}
//Biggest question? Allow to use multiple codec and be safer for the cost of additional parsing?
