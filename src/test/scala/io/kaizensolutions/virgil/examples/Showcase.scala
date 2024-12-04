package io.kaizensolutions.virgil.examples

import com.datastax.oss.driver.api.core.CqlSession
import io.kaizensolutions.virgil.*
import io.kaizensolutions.virgil.codecs.*
import io.kaizensolutions.virgil.configuration.ExecutionAttributes
import io.kaizensolutions.virgil.cql.*
import kyo.*

import scala.language.implicitConversions

/**
 * Create the virgil keyspace
 * ```cql
 * CREATE KEYSPACE IF NOT EXISTS virgil WITH REPLICATION = { 'class': 'SimpleStrategy', 'replication_factor': 1 };
 * ```
 *
 * Create the example table
 * ```cql
 * USE virgil;
 * CREATE TABLE IF NOT EXISTS example (id INT PRIMARY KEY, info TEXT);
 * ```
 */
object Showcase extends KyoApp:
  run:
    val program: Unit < (Env[CQLExecutor] & Async) =
      val one   = 1
      val two   = 2
      val Alice = "Alice"
      val Bob   = "Bob"

      val insertAlice = cql"INSERT INTO example (id, info) VALUES ($one, $Alice)".mutation
      val insertBob   = cql"INSERT INTO example (id, info) VALUES ($two, $Bob)".mutation
      val insertValues: Stream[MutationResult, Env[CQLExecutor] & Async] =
        Stream
          .range(1, 1_000)
          .map: i =>
            CQLExecutor.executeMutation:
              cql"INSERT INTO example (id, info) VALUES ($i, ${i.toString()})".mutation

      val query = cql"SELECT id, info FROM example".query[ExampleRow]
      for
        _    <- CQLExecutor.executeMutation(insertAlice)
        _    <- CQLExecutor.executeMutation(insertBob)
        _    <- insertValues.runDiscard
        data <- CQLExecutor.execute(query.take(10)).run
        _    <- IO(println(data))
        _ <- CQLExecutor
               .execute(query)
               .mapChunk: chunk =>
                 IO(println(chunk.size)).map(_ => chunk)
               .runDiscard
        page <- CQLExecutor.executePage(query.withAttributes(ExecutionAttributes.default.withPageSize(4)))
        _    <- IO(println(page))
      yield ()

    for
      executor <- CQLExecutor(CqlSession.builder().withKeyspace("virgil"))
      result   <- Env.run(executor)(program)
    yield result

final case class ExampleRow(id: Int, info: String)
object ExampleRow:
  given CqlRowDecoder.Object[ExampleRow] = CqlRowDecoder.derive[ExampleRow]
