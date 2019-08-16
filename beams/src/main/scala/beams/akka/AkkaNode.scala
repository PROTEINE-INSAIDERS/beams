package beams.akka

case class AkkaNode[+Env](ref: NodeActor.Ref) extends AnyVal
