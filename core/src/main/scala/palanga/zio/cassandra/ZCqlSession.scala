package palanga.zio.cassandra

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.*
import palanga.zio.cassandra.CassandraException.*
import palanga.zio.cassandra.{ CassandraException, ZStatement }
import zio.*
import zio.Console.{ printLine, printLineError }
import zio.Schedule.{ recurs, spaced }
import zio.stream.{ Stream, ZStream }

import java.net.InetSocketAddress
import scala.language.postfixOps

trait ZCqlSession:
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

/**
 * TODO we should use a logger instead of Console.printLine
 */
object ZCqlSession:

  def close: ZIO[ZCqlSession, SessionCloseException, Unit] =
    ZIO.serviceWithZIO(_.close)

  def executeHeadOption[Out](s: ZStatement[Out]): ZIO[ZCqlSession, CassandraException, Option[Out]] =
    ZIO.serviceWithZIO(_.executeHeadOption(s))

  def executeHeadOrFail[Out](s: ZStatement[Out]): ZIO[ZCqlSession, CassandraException, Out] =
    ZIO.serviceWithZIO(_.executeHeadOrFail(s))

  def prepare(s: SimpleStatement): ZIO[ZCqlSession, PrepareStatementException, PreparedStatement] =
    ZIO.serviceWithZIO(_.prepare(s))

  def preparePar(ss: SimpleStatement*): ZIO[ZCqlSession, PrepareStatementException, List[PreparedStatement]] =
    ZIO.serviceWithZIO(_.preparePar(ss: _*))

  /**
   * A stream of chunks, every chunk representing a page.
   */
  def stream[Out](s: ZStatement[Out]): ZStream[ZCqlSession, CassandraException, Chunk[Out]] =
    ZStream.serviceWithStream(_.stream(s))

  object untyped:

    def execute(s: ZStatement[_]): ZIO[ZCqlSession, CassandraException, AsyncResultSet] =
      ZIO.serviceWithZIO(_.execute(s))

    def execute(s: BoundStatement): ZIO[ZCqlSession, QueryExecutionException, AsyncResultSet] =
      ZIO.serviceWithZIO(_.execute(s))

    /**
     * Execute a simple statement without first calculating and caching its prepared statement. Use some of the other
     * alternatives for automatically preparing and caching statements.
     */
    def execute(s: SimpleStatement): ZIO[ZCqlSession, QueryExecutionException, AsyncResultSet] =
      ZIO.serviceWithZIO(_.execute(s))

    def executePar(ss: BoundStatement*): ZIO[ZCqlSession, QueryExecutionException, List[AsyncResultSet]] =
      ZIO.serviceWithZIO(_.executePar(ss: _*))

    /**
     * The same as `execute` but in parallel.
     */
    def executeParSimple(ss: SimpleStatement*): ZIO[ZCqlSession, QueryExecutionException, List[AsyncResultSet]] =
      ZIO.serviceWithZIO(_.executeParSimple(ss: _*))

    def stream(s: BoundStatement): ZStream[ZCqlSession, CassandraException, Chunk[Row]] =
      ZStream.serviceWithStream(_.stream(s))

    def stream(s: SimpleStatement): ZStream[ZCqlSession, CassandraException, Chunk[Row]] =
      ZStream.serviceWithStream(_.stream(s))

    def streamResultSet(s: ZStatement[_]): ZStream[ZCqlSession, CassandraException, AsyncResultSet] =
      ZStream.serviceWithStream(_.streamResultSet(s))

    def streamResultSet(s: BoundStatement): ZStream[ZCqlSession, CassandraException, AsyncResultSet] =
      ZStream.serviceWithStream(_.streamResultSet(s))

    def streamResultSet(s: SimpleStatement): ZStream[ZCqlSession, CassandraException, AsyncResultSet] =
      ZStream.serviceWithStream(_.streamResultSet(s))
