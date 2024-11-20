package io.kaizensolutions.virgil.examples

import com.datastax.oss.driver.api.core.CqlSession
import io.kaizensolutions.virgil.*
import io.kaizensolutions.virgil.codecs.*
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
      val query       = cql"SELECT id, info FROM example".query[ExampleRow]
      for
        _        <- CQLExecutor.executeMutation(insertAlice)
        _        <- CQLExecutor.executeMutation(insertBob)
        data      <- CQLExecutor.execute(query).run
        _        <- IO(println(data))
        page     <- CQLExecutor.executePage(query)
        _        <- IO(println(page.data))
      yield ()

    for
      executor <- CQLExecutor(CqlSession.builder().withKeyspace("virgil"))
      result   <- Env.run(executor)(program)
    yield result

final case class ExampleRow(id: Int, info: String)
object ExampleRow:
  given CqlRowDecoder.Object[ExampleRow] = CqlRowDecoder.derive[ExampleRow]
