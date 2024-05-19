package io.kaizensolutions.virgil

import io.kaizensolutions.virgil.configuration.PageState
import kyo.Chunk

final case class Paged[A](data: Chunk[A], pageState: Option[PageState])
