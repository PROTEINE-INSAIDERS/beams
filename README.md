# Beams framework

Beams is a distributed programming framework based on the idea of [code mobility](https://en.wikipedia.org/wiki/Code_mobility).
It utilizes [ZIO](https://github.com/zio/zio) to provide high-level composable abstractions for writing
distributed programs and uses [Akka](https://akka.io/) to run them in distributed environments.

Approaches taken in Beams for distributed programming are similar to [Unison's](https://github.com/unisonweb/unison) 
ones. Unlike Unison, Beams does not support strong code mobility. However, in presence of higher order functions and
closure serialization, strong code mobility [can be implemented](http://www.dcs.gla.ac.uk/~trinder/papers/strongm.pdf)
via continuations. 

See [hello-world](examples/hello-world/src/main/scala/Main.scala) application for introductory example.
