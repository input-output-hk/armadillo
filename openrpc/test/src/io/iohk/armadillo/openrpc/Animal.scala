package io.iohk.armadillo.openrpc

sealed trait Animal

object Animal {
  final case class Amphibian(name: String) extends Animal

  final case class Bird(name: String, canFly: Boolean) extends Animal

  final case class Fish(name: String) extends Animal

  final case class Invertebrate(name: String, numberOfLegs: Int) extends Animal

  final case class Mammal(name: String) extends Animal

  final case class Reptile(name: String) extends Animal
}
