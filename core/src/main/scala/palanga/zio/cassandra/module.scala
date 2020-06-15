package palanga.zio.cassandra

import com.datastax.oss.driver.api.core.cql._
import palanga.zio.cassandra.CassandraException.{
  PrepareStatementException,
  QueryExecutionException,
  SessionCloseException,
}
import zio.stream.ZStream
import zio.{ Chunk, ZIO }

object module {

  def close: ZIO[ZCqlSession, SessionCloseException, Unit] =
    ZIO.accessM(_.get.close)

  def execute(s: ZStatement[_]): ZIO[ZCqlSession, CassandraException, AsyncResultSet] =
    ZIO.accessM(_.get.execute(s))

  def execute(s: BoundStatement): ZIO[ZCqlSession, QueryExecutionException, AsyncResultSet] =
    ZIO.accessM(_.get.execute(s))

  /**
   * Execute a simple statement without first calculating and caching its prepared statement.
   * Use some of the other alternatives for automatically preparing and caching statements.
   */
  def execute(s: SimpleStatement): ZIO[ZCqlSession, QueryExecutionException, AsyncResultSet] =
    ZIO.accessM(_.get.execute(s))

  def executePar(ss: BoundStatement*): ZIO[ZCqlSession, QueryExecutionException, List[AsyncResultSet]] =
    ZIO.accessM(_.get.executePar(ss: _*))

  /**
   * The same as [[execute]] but in parallel.
   */
  def executeParSimple(ss: SimpleStatement*): ZIO[ZCqlSession, QueryExecutionException, List[AsyncResultSet]] =
    ZIO.accessM(_.get.executeParSimple(ss: _*))

  def executeHeadOption[Out](s: ZStatement[Out]): ZIO[ZCqlSession, CassandraException, Option[Out]] =
    ZIO.accessM(_.get.executeHeadOption(s))

  def executeHeadOrFail[Out](s: ZStatement[Out]): ZIO[ZCqlSession, CassandraException, Out] =
    ZIO.accessM(_.get.executeHeadOrFail(s))

  def prepare(s: SimpleStatement): ZIO[ZCqlSession, PrepareStatementException, PreparedStatement] =
    ZIO.accessM(_.get.prepare(s))

  def preparePar(ss: SimpleStatement*): ZIO[ZCqlSession, PrepareStatementException, List[PreparedStatement]] =
    ZIO.accessM(_.get.preparePar(ss: _*))

  /**
   * A stream of chunks, every chunk representing a page.
   */
  def stream[Out](s: ZStatement[Out]): ZStream[ZCqlSession, CassandraException, Chunk[Out]] =
    ZStream.accessStream(_.get.stream(s))

  def stream(s: BoundStatement): ZStream[ZCqlSession, CassandraException, Chunk[Row]] =
    ZStream.accessStream(_.get.stream(s))

  def stream(s: SimpleStatement): ZStream[ZCqlSession, CassandraException, Chunk[Row]] =
    ZStream.accessStream(_.get.stream(s))

  def streamResultSet(s: ZStatement[_]): ZStream[ZCqlSession, CassandraException, AsyncResultSet] =
    ZStream.accessStream(_.get.streamResultSet(s))

  def streamResultSet(s: BoundStatement): ZStream[ZCqlSession, CassandraException, AsyncResultSet] =
    ZStream.accessStream(_.get.streamResultSet(s))

  def streamResultSet(s: SimpleStatement): ZStream[ZCqlSession, CassandraException, AsyncResultSet] =
    ZStream.accessStream(_.get.streamResultSet(s))

}
