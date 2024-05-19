package io.kaizensolutions.virgil

import com.datastax.oss.driver.api.core.*
import com.datastax.oss.driver.api.core.metrics.Metrics as DriverMetrics
import io.kaizensolutions.virgil.configuration.PageState
import io.kaizensolutions.virgil.internal.CQLExecutorKyo
import io.kaizensolutions.virgil.internal.Proofs.*
import kyo.*

trait CQLExecutor:
  def execute[A: Flat](in: CQL[A])(using Tag[A]): Stream[Unit, A, Fibers]

  def executeMutation(in: CQL[MutationResult]): MutationResult < Fibers

  def executePage[A](in: CQL[A], pageState: Option[PageState])(using ev: A =:!= MutationResult): Paged[A] < Fibers

  def metrics: DriverMetrics < Options

object CQLExecutor:
  def apply(builder: => CqlSessionBuilder): CQLExecutor < Resources =
    val acquire: CqlSession < Fibers = Fibers.fromCompletionStage(builder.buildAsync())
    val release: CqlSession => Unit < Fibers = (session: CqlSession) =>
      Fibers.fromCompletionStage(session.closeAsync()).unit

    Resources.acquireRelease(acquire)(release).map(CQLExecutorKyo(_))

  def execute[A: Flat](in: CQL[A])(using Tag[A]): Stream[Unit, A, Envs[CQLExecutor] & Fibers] =
    Streams.initSource[A][Unit, Envs[CQLExecutor] & Fibers]:
      Envs
        .get[CQLExecutor]
        .map(_.execute(in).get)

  def executeMutation(in: CQL[MutationResult]): MutationResult < (Envs[CQLExecutor] & Fibers) =
    Envs
      .get[CQLExecutor]
      .map(_.executeMutation(in))

  def executePage[A](in: CQL[A], pageState: Option[PageState] = None)(using
    A =:!= MutationResult
  ): Paged[A] < (Envs[CQLExecutor] & Fibers) =
    Envs
      .get[CQLExecutor]
      .map(_.executePage(in, pageState))

  val metrics: DriverMetrics < (Envs[CQLExecutor] & Options) =
    Envs
      .get[CQLExecutor]
      .map(_.metrics)
