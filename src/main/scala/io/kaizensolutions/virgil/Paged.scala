package io.kaizensolutions.virgil

import io.kaizensolutions.virgil.configuration.PageState

final case class Paged[A](data: Seq[A], pageState: Option[PageState])
