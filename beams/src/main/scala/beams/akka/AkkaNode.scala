package beams.akka

case class AkkaNode[+Env](ref: NodeActor.Ref[Env]) extends AnyVal
