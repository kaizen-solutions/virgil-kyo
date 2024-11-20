package io.kaizensolutions.virgil.codecs

import io.kaizensolutions.virgil.codecs.CqlPrimitiveDecoder.ListPrimitiveDecoder
import kyo.Chunk

import scala.jdk.CollectionConverters.*

trait CqlPrimitiveDecoderKyoInstances:
  given [A](using
    element: CqlPrimitiveDecoder[A]
  ): CqlPrimitiveDecoder.WithDriver[Chunk[A], java.util.List[element.DriverType]] =
    ListPrimitiveDecoder[Chunk, A, element.DriverType](
      element,
      (driverList, transformElement) => Chunk.from(driverList.asScala.map(transformElement).toIndexedSeq)
    )
object CqlPrimitiveDecoderKyoInstances extends CqlPrimitiveDecoderKyoInstances
