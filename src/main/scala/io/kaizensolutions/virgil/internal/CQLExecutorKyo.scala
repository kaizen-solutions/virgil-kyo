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
  override def execute[A: Flat](in: CQL[A])(using Tag[A]): Stream[Unit, A, Fibers] =
    in.cqlType match
      case m: CQLType.Mutation =>
        val mutation: A < Fibers            = executeMutation(m, in.executionAttributes).asInstanceOf[A < Fibers]
        val s: Unit < (Fibers & Streams[A]) = mutation.map(m => Chunks.init(m)).map(Streams.emitChunk)
        Streams.initSource(s)

      case b: CQLType.Batch =>
        val batch: A < Fibers               = executeBatch(b, in.executionAttributes).asInstanceOf[A < Fibers]
        val s: Unit < (Fibers & Streams[A]) = batch.map(m => Chunks.init(m)).map(Streams.emitChunk)
        Streams.initSource(s)

      case q @ CQLType.Query(_, _, pullMode) =>
        pullMode match
          case PullMode.TakeUpto(n) =>
            executeGeneralQuery(q.asInstanceOf[CQLType.Query[A]], in.executionAttributes).take(n.toInt)

          case PullMode.All =>
            executeGeneralQuery(q.asInstanceOf[CQLType.Query[A]], in.executionAttributes)

  override def executeMutation(in: CQL[MutationResult]): MutationResult < Fibers = in.cqlType match
    case mutation: CQLType.Mutation =>
      executeMutation(mutation, in.executionAttributes)

    case batch: CQLType.Batch =>
      executeBatch(batch, in.executionAttributes)

    case CQLType.Query(_, _, _) =>
      sys.error("Cannot perform a query using executeMutation")

  override def executePage[A](in: CQL[A], pageState: Option[PageState])(using
    ev: A =:!= MutationResult
  ): Paged[A] < Fibers = in.cqlType match
    case _: CQLType.Mutation =>
      sys.error("Mutations cannot be used with page queries")

    case CQLType.Batch(_, _) =>
      sys.error("Batch Mutations cannot be used with page queries")

    case q @ CQLType.Query(_, _, _) =>
      fetchSinglePage(q, pageState, in.executionAttributes).asInstanceOf[Paged[A] < Fibers]

  override def metrics: DriverMetrics < Options =
    val optMetrics = session.getMetrics()
    if optMetrics.isPresent then Options(optMetrics.get())
    else Options.empty

  private def executeMutation(m: CQLType.Mutation, config: ExecutionAttributes): MutationResult < Fibers =
    for
      boundStatement <- buildMutation(m, config)
      result         <- executeAction(boundStatement)
    yield MutationResult.make(result.wasApplied())

  private def executeBatch(m: CQLType.Batch, config: ExecutionAttributes): MutationResult < Fibers =
    Fibers
      .parallel(m.mutations.map(buildMutation(_)))
      .map: statements =>
        val builder = BatchStatement
          .builder(m.batchType.toDriver)
          .addStatements(statements.asJava)
        config.configureBatch(builder).build()
      .map(executeAction)
      .map(r => MutationResult.make(r.wasApplied()))

  private def executeGeneralQuery[Output: Flat](
    input: CQLType.Query[Output],
    config: ExecutionAttributes
  )(using Tag[Output]): Stream[Unit, Output, Fibers] =
    val (queryString, bindMarkers) = CqlStatementRenderer.render(input)
    val reader                     = input.reader
    val statement                  = buildStatement(queryString, bindMarkers, config)
    val out                        = statement.map(s => select(s).transform(reader.decode))
    Streams.initSource[Output][Unit, Fibers](out.map(_.get))

  private def select(query: Statement[?]): Stream[Unit, Row, Fibers] =
    def go(rs: AsyncResultSet): Unit < (Streams[Row] & Fibers) =
      val next =
        if rs.hasMorePages() then Fibers.fromCompletionStage(rs.fetchNextPage()).map(go)
        else ()

      if rs.remaining() > 0 then
        val chunk = Chunks.initSeq(rs.currentPage().asScala.toSeq)
        Streams.emitChunk(chunk).map(_ => next)
      else next

    Streams.initSource[Row][Unit, Fibers](Fibers.fromCompletionStage(session.executeAsync(query)).map(go))

  private def fetchSinglePage[A](
    q: CQLType.Query[A],
    pageState: Option[PageState],
    attr: ExecutionAttributes
  ): Paged[A] < Fibers =
    val (queryString, bindMarkers) = CqlStatementRenderer.render(q)
    for
      boundStatement        <- buildStatement(queryString, bindMarkers, attr)
      reader                 = q.reader
      driverPageState        = pageState.map(_.underlying).orNull
      boundStatementWithPage = boundStatement.setPagingState(driverPageState)
      rp                    <- selectPage(boundStatementWithPage)
      (results, nextPage)    = rp
      chunksToOutput         = results.map(reader.decode)
    yield Paged(chunksToOutput.pure, nextPage)

  private def buildMutation(
    in: CQLType.Mutation,
    attr: ExecutionAttributes = ExecutionAttributes.default
  ): BatchableStatement[?] < Fibers =
    val (queryString, bindMarkers) = CqlStatementRenderer.render(in)
    if bindMarkers.isEmpty then IOs(SimpleStatement.newInstance(queryString))
    else buildStatement(queryString, bindMarkers, attr)

  private def buildStatement(
    queryString: String,
    columns: BindMarkers,
    config: ExecutionAttributes
  ): BoundStatement < Fibers =
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

  private def selectPage(queryConfiguredWithPageState: Statement[?]): (Chunk[Row], Option[PageState]) < Fibers =
    executeAction(queryConfiguredWithPageState).map: rs =>
      val currentRows: Chunk[Row] = Chunks.initSeq(rs.currentPage().asScala.toSeq)
      if rs.hasMorePages() then
        val pageState = PageState.fromDriver(rs.getExecutionInfo().getSafePagingState())
        currentRows -> Option(pageState)
      else currentRows -> None

  private def prepare(query: String): PreparedStatement < Fibers =
    Fibers.fromCompletionStage(session.prepareAsync(query))

  private def executeAction(query: Statement[?]): AsyncResultSet < Fibers =
    Fibers.fromCompletionStage(session.executeAsync(query))
