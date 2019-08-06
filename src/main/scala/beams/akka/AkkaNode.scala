package beams.akka

final case class AkkaNode[+Env](
                                 ref: NodeActor.Ref
                               )