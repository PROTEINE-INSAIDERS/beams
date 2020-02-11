# Beams framework

Beams is a distributed programming framework based on the idea of [code mobility](https://en.wikipedia.org/wiki/Code_mobility).
It utilizes [ZIO](https://github.com/zio/zio) to provide high-level composable abstractions for writing
distributed programs and uses [Akka](https://akka.io/) to run them in distributed environments.

Distributed programming approaches taken in Beams are similar to [Unison's](https://github.com/unisonweb/unison) 
ones. Unlike Unison, Beams does not support strong code mobility. However, in presence of higher-order functions and
closure serialization, strong code mobility [can be implemented](http://www.dcs.gla.ac.uk/~trinder/papers/strongm.pdf)
via continuations.

Beams encapsulates error handling and distributed task cancellation allowing to focus on business logic. 

- No messaging and complex state machines, just plain ZIO-style code.
- No async-programming related complexities, Beams strongly relies on unified ZIO-programming model.
- Resource management are also implemented using ZIO.
- No timeouts, Beams relies on task cancellation instead. 

See [hello-world](examples/hello-world/src/main/scala/Main.scala) for introductory example.

**\[Help wanted\]** Any help in correcting grammatical errors would be greatly appreciated.