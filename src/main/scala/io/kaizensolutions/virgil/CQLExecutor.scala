package io.kaizensolutions.virgil

import com.datastax.oss.driver.api.core.*
import com.datastax.oss.driver.api.core.metrics.Metrics as DriverMetrics
import io.kaizensolutions.virgil.configuration.PageState
import io.kaizensolutions.virgil.internal.CQLExecutorKyo
import io.kaizensolutions.virgil.internal.Proofs.*
import kyo.*

trait CQLExecutor:
  def execute[A: Tag](in: CQL[A]): Stream[A, Async]

  def executeMutation(in: CQL[MutationResult]): MutationResult < Async

  def executePage[A](in: CQL[A], pageState: Option[PageState])(using ev: A =:!= MutationResult): Paged[A] < Async

  def metrics: DriverMetrics < Abort[Absent]

object CQLExecutor:
  def apply(builder: => CqlSessionBuilder): CQLExecutor < (Resource & Async) =
    val acquire: CqlSession < Async = Fiber.fromCompletionStage(builder.buildAsync())
    val release: CqlSession => Unit < Async = (session: CqlSession) =>
      Fiber.fromCompletionStage(session.closeAsync()).unit
    Resource.acquireRelease(acquire)(release).map(CQLExecutorKyo(_))

  val layer: Layer[CQLExecutor, Resource & Async & Env[CqlSessionBuilder]] = Layer {
    Env.use[CqlSessionBuilder](CQLExecutor(_))
  }

  def execute[A: Tag](in: CQL[A]): Stream[A, Env[CQLExecutor] & Async] =
    Stream[A, Env[CQLExecutor] & Async]:
      Env.use[CQLExecutor](_.execute(in).emit)

  def executeMutation(in: CQL[MutationResult]): MutationResult < (Env[CQLExecutor] & Async) =
    Env.use[CQLExecutor](_.executeMutation(in))

  def executePage[A](in: CQL[A], pageState: Option[PageState] = None)(using
    A =:!= MutationResult
  ): Paged[A] < (Env[CQLExecutor] & Async) = Env.use[CQLExecutor](_.executePage(in, pageState))

  val metrics: DriverMetrics < (Env[CQLExecutor] & Abort[Absent]) = Env.use[CQLExecutor](_.metrics)
