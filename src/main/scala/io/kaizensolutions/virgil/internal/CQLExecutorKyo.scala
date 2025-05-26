package io.kaizensolutions.virgil.internal

import com.datastax.oss.driver.api.core.*
import com.datastax.oss.driver.api.core.cql.*
import com.datastax.oss.driver.api.core.metrics.Metrics as DriverMetrics
import io.kaizensolutions.virgil.*
import io.kaizensolutions.virgil.configuration.*
import io.kaizensolutions.virgil.internal.*
import io.kaizensolutions.virgil.internal.Proofs.*
import kyo.*

import scala.jdk.CollectionConverters.*

final private[virgil] class CQLExecutorKyo(private val session: CqlSession) extends CQLExecutor:
  override def execute[A: Tag](in: CQL[A]): Stream[A, Async] =
    in.cqlType match
      case m: CQLType.Mutation =>
        val mutation: MutationResult < Async   = executeMutation(m, in.executionAttributes)
        val s: Unit < (Async & Emit[Chunk[A]]) = mutation.map(m => Emit.value(Chunk(m.asInstanceOf[A])))
        Stream(s)

      case b: CQLType.Batch =>
        val batch: MutationResult < Async      = executeBatch(b, in.executionAttributes)
        val s: Unit < (Async & Emit[Chunk[A]]) = batch.map(m => Emit.value(Chunk(m.asInstanceOf[A])))
        Stream(s)

      case q: CQLType.Query[A] @unchecked =>
        q.pullMode match
          case PullMode.TakeUpto(n) =>
            executeGeneralQuery(q, in.executionAttributes).take(n.toInt)

          case PullMode.All =>
            executeGeneralQuery(q, in.executionAttributes)

  override def executeMutation(in: CQL[MutationResult]): MutationResult < Async = in.cqlType match
    case mutation: CQLType.Mutation =>
      executeMutation(mutation, in.executionAttributes)

    case batch: CQLType.Batch =>
      executeBatch(batch, in.executionAttributes)

    case CQLType.Query(_, _, _) =>
      sys.error("Cannot perform a query using executeMutation")

  override def executePage[A](in: CQL[A], pageState: Option[PageState])(using
    A =:!= MutationResult
  ): Paged[A] < Async = in.cqlType match
    case _: CQLType.Mutation =>
      sys.error("Mutations cannot be used with page queries")

    case CQLType.Batch(_, _) =>
      sys.error("Batch Mutations cannot be used with page queries")

    case q: CQLType.Query[A] @unchecked => fetchSinglePage(q, pageState, in.executionAttributes)

  override def metrics: DriverMetrics < Abort[Absent] =
    Abort.get(Maybe(session.getMetrics().orElse(null)))

  private def executeMutation(m: CQLType.Mutation, config: ExecutionAttributes): MutationResult < Async =
    for
      boundStatement <- buildMutation(m, config)
      result         <- executeAction(boundStatement)
    yield MutationResult.make(result.wasApplied())

  private def executeBatch(m: CQLType.Batch, config: ExecutionAttributes): MutationResult < Async =
    Async
      .collectAll(m.mutations.map(buildMutation(_)))
      .map: statements =>
        val builder = BatchStatement
          .builder(m.batchType.toDriver)
          .addStatements(statements.asJava)
        config.configureBatch(builder).build()
      .map(executeAction)
      .map(r => MutationResult.make(r.wasApplied()))

  private def executeGeneralQuery[Output: Tag](
    input: CQLType.Query[Output],
    config: ExecutionAttributes
  ): Stream[Output, Async] =
    val (queryString, bindMarkers) = CqlStatementRenderer.render(input)
    val reader                     = input.reader
    val statement                  = buildStatement(queryString, bindMarkers, config)
    val out                        = statement.map(s => select(s).map(reader.decode))
    Stream[Output, Async](out.map(_.emit))

  private def select(query: Statement[?]): Stream[Row, Async] =
    def go(rs: AsyncResultSet): Unit < (Emit[Chunk[Row]] & Async) =
      val next: Unit < (Emit[Chunk[Row]] & Async) =
        IO:
          if rs.hasMorePages() then Fiber.fromCompletionStage(rs.fetchNextPage()).map(go)
          else ()

      if rs.remaining() > 0 then
        val chunk: Chunk[Row] = Chunk.from(rs.currentPage().asScala)
        Emit.value(chunk).andThen(next)
      else next

    Stream[Row, Async](Fiber.fromCompletionStage(session.executeAsync(query)).map(go))

  private def fetchSinglePage[A](
    q: CQLType.Query[A],
    pageState: Option[PageState],
    attr: ExecutionAttributes
  ): Paged[A] < Async =
    val (queryString, bindMarkers) = CqlStatementRenderer.render(q)
    for
      boundStatement        <- buildStatement(queryString, bindMarkers, attr)
      reader                 = q.reader
      driverPageState        = pageState.map(_.underlying).orNull
      boundStatementWithPage = boundStatement.setPagingState(driverPageState)
      rp                    <- selectPage(boundStatementWithPage)
      (results, nextPage)    = rp
      chunksToOutput         = results.map(reader.decode)
    yield Paged(chunksToOutput, nextPage)

  private def buildMutation(
    in: CQLType.Mutation,
    attr: ExecutionAttributes = ExecutionAttributes.default
  ): BatchableStatement[?] < Async =
    val (queryString, bindMarkers) = CqlStatementRenderer.render(in)
    if bindMarkers.isEmpty then IO(SimpleStatement.newInstance(queryString))
    else buildStatement(queryString, bindMarkers, attr)

  private def buildStatement(
    queryString: String,
    columns: BindMarkers,
    config: ExecutionAttributes
  ): BoundStatement < Async =
    prepare(queryString).map: ps =>
      val builder = ps.boundStatementBuilder()
      val boundColumns = columns.underlying.foldLeft(builder):
        case (accBuilder, (colName, column)) =>
          column.write.encodeByFieldName(
            structure = accBuilder,
            fieldName = colName.name,
            value = column.value
          )
      // https://docs.datastax.com/en/developer/java-driver/4.13/manual/core/statements/
      val result = config.configure(boundColumns)
      result.build()

  private def selectPage(queryConfiguredWithPageState: Statement[?]): (Chunk[Row], Option[PageState]) < Async =
    executeAction(queryConfiguredWithPageState).map: rs =>
      val currentRows: Chunk[Row] = Chunk.from(rs.currentPage().asScala.toArray)
      if rs.hasMorePages() then
        val pageState = PageState.fromDriver(rs.getExecutionInfo().getSafePagingState())
        currentRows -> Option(pageState)
      else currentRows -> None

  private def prepare(query: String): PreparedStatement < Async =
    Fiber.fromCompletionStage(session.prepareAsync(query))

  private def executeAction(query: Statement[?]): AsyncResultSet < Async =
    Fiber.fromCompletionStage(session.executeAsync(query))
