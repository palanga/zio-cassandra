package palanga.zio.cassandra.session

import com.datastax.oss.driver.api.core.cql._
import palanga.zio.cassandra.CassandraException._
import palanga.zio.cassandra.{ CassandraException, ZStatement }
import zio._
import zio.stream.Stream

object ZCqlSession {
  trait Service {
    def close: IO[SessionCloseException, Unit]
    def execute(s: ZStatement[_]): IO[CassandraException, AsyncResultSet]
    def execute(s: BoundStatement): IO[QueryExecutionException, AsyncResultSet]
    def execute(s: SimpleStatement): IO[QueryExecutionException, AsyncResultSet]
    def executeHeadOption[Out](s: ZStatement[Out]): IO[CassandraException, Option[Out]]
    def executeHeadOrFail[Out](s: ZStatement[Out]): IO[CassandraException, Out]
    def executePar(ss: BoundStatement*): IO[QueryExecutionException, List[AsyncResultSet]]
    def executeParSimple(ss: SimpleStatement*): IO[QueryExecutionException, List[AsyncResultSet]]
    def prepare(s: SimpleStatement): IO[PrepareStatementException, PreparedStatement]
    def preparePar(ss: SimpleStatement*): IO[PrepareStatementException, List[PreparedStatement]]
    def stream[Out](s: ZStatement[Out]): Stream[CassandraException, Chunk[Out]]
    def stream(s: BoundStatement): Stream[CassandraException, Chunk[Row]]
    def stream(s: SimpleStatement): Stream[CassandraException, Chunk[Row]]
    def streamResultSet(s: ZStatement[_]): Stream[CassandraException, AsyncResultSet]
    def streamResultSet(s: BoundStatement): Stream[CassandraException, AsyncResultSet]
    def streamResultSet(s: SimpleStatement): Stream[CassandraException, AsyncResultSet]
  }
}
