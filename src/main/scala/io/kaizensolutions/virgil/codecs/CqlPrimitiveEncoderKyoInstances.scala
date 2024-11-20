package io.kaizensolutions.virgil.codecs

import io.kaizensolutions.virgil.codecs.CqlPrimitiveEncoder.*
import kyo.Chunk

import scala.jdk.CollectionConverters.*

trait CqlPrimitiveEncoderKyoInstances:
  given [ScalaElem](using
    element: CqlPrimitiveEncoder[ScalaElem]
  ): ListPrimitiveEncoder[Chunk, ScalaElem, element.DriverType] =
    ListPrimitiveEncoder[Chunk, ScalaElem, element.DriverType](
      element,
      (chunk, transform) => chunk.map(transform).asJava
    )

object CqlPrimitiveEncoderKyoInstances extends CqlPrimitiveEncoderKyoInstances
