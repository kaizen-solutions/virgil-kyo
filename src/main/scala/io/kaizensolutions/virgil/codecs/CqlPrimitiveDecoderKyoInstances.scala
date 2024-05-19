package io.kaizensolutions.virgil.codecs

import io.kaizensolutions.virgil.codecs.CqlPrimitiveDecoder.ListPrimitiveDecoder
import kyo.{Chunk, Chunks}

import scala.jdk.CollectionConverters.*

trait CqlPrimitiveDecoderKyoInstances:
  given [A](using
    element: CqlPrimitiveDecoder[A]
  ): CqlPrimitiveDecoder.WithDriver[Chunk[A], java.util.List[element.DriverType]] =
    ListPrimitiveDecoder[Chunk, A, element.DriverType](
      element,
      (driverList, transformElement) => Chunks.initSeq(driverList.asScala.map(transformElement).toIndexedSeq)
    )
object CqlPrimitiveDecoderKyoInstances extends CqlPrimitiveDecoderKyoInstances
