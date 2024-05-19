# Virgil-Kyo

_Virgil is a functional Cassandra client built using Kyo, Magnolia and the Datastax 4.x Java drivers for Scala 3_

This project allows you to use [Virgil, the Cassandra client](https://github.com/kaizen-solutions/virgil) with [Kyo, the next generation toolkit for Scala 3](https://github.com/getkyo/kyo)


## Example

Here's an example of how to represent a workflow that inserts some data into a Cassandra table and then queries it back out:

```scala
import io.kaizensolutions.virgil.*
import io.kaizensolutions.virgil.codecs.*
import io.kaizensolutions.virgil.cql.*
import kyo.*

val program: Unit < (Envs[CQLExecutor] & Fibers) =
  val one = 1
  val two = 2
  val Alice = "Alice"
  val Bob = "Bob"

  val insertAlice = cql"INSERT INTO example (id, info) VALUES ($one, $Alice)".mutation
  val insertBob   = cql"INSERT INTO example (id, info) VALUES ($two, $Bob)".mutation
  val query       = cql"SELECT id, info FROM example".query[ExampleRow]
  for
    _                 <- CQLExecutor.executeMutation(insertAlice)
    _                 <- CQLExecutor.executeMutation(insertBob)
    (aliceAndBob, _)  <- CQLExecutor.execute(query).runSeq
  yield ()
```
