package palanga.zio

import com.datastax.oss.driver.api.core.cql._
import palanga.zio.cassandra.CassandraException.{
  PrepareStatementException,
  QueryExecutionException,
  SessionCloseException,
}
import zio.stream.ZStream
import zio.{ Chunk, Has, ZIO }

package object cassandra {

  type ZCqlSession = Has[ZCqlSession.Service]

  def close: ZIO[ZCqlSession, SessionCloseException, Unit] =
    ZIO.accessM(_.get.close)

  def execute(s: ZStatement[_]): ZIO[ZCqlSession, CassandraException, AsyncResultSet] =
    ZIO.accessM(_.get.execute(s))

  def executeHeadOption[Out](s: ZStatement[Out]): ZIO[ZCqlSession, CassandraException, Option[Out]] =
    ZIO.accessM(_.get.executeHeadOption(s))

  def executeHeadOrFail[Out](s: ZStatement[Out]): ZIO[ZCqlSession, CassandraException, Out] =
    ZIO.accessM(_.get.executeHeadOrFail(s))

  def executePrepared(s: BoundStatement): ZIO[ZCqlSession, QueryExecutionException, AsyncResultSet] =
    ZIO.accessM(_.get.executePrepared(s))

  def executePreparedPar(ss: BoundStatement*): ZIO[ZCqlSession, QueryExecutionException, List[AsyncResultSet]] =
    ZIO.accessM(_.get.executePreparedPar(ss: _*))

  /**
   * Execute a simple statement without first calculating and caching its prepared statement.
   * Use some of the other alternatives for automatically preparing and caching statements.
   */
  def executeSimple(s: SimpleStatement): ZIO[ZCqlSession, QueryExecutionException, AsyncResultSet] =
    ZIO.accessM(_.get.executeSimple(s))

  /**
   * The same as [[executeSimple]] but in parallel.
   */
  def executeSimplePar(ss: SimpleStatement*): ZIO[ZCqlSession, QueryExecutionException, List[AsyncResultSet]] =
    ZIO.accessM(_.get.executeSimplePar(ss: _*))

  def prepare(s: SimpleStatement): ZIO[ZCqlSession, PrepareStatementException, PreparedStatement] =
    ZIO.accessM(_.get.prepare(s))

  def preparePar(ss: SimpleStatement*): ZIO[ZCqlSession, PrepareStatementException, List[PreparedStatement]] =
    ZIO.accessM(_.get.preparePar(ss: _*))

  /**
   * A stream of chunks, every chunk representing a page.
   */
  def stream[Out](s: ZStatement[Out]): ZStream[ZCqlSession, CassandraException, Chunk[Out]] =
    ZStream.accessStream(_.get.stream(s))

  def streamPrepared(s: BoundStatement): ZStream[ZCqlSession, CassandraException, Chunk[Row]] =
    ZStream.accessStream(_.get.streamPrepared(s))

  def streamSimple(s: SimpleStatement): ZStream[ZCqlSession, CassandraException, Chunk[Row]] =
    ZStream.accessStream(_.get.streamSimple(s))

  def streamResultSet(s: ZStatement[_]): ZStream[ZCqlSession, CassandraException, AsyncResultSet] =
    ZStream.accessStream(_.get.streamResultSet(s))

  def streamResultSetPrepared(s: BoundStatement): ZStream[ZCqlSession, CassandraException, AsyncResultSet] =
    ZStream.accessStream(_.get.streamResultSetPrepared(s))

  def streamResultSetSimple(s: SimpleStatement): ZStream[ZCqlSession, CassandraException, AsyncResultSet] =
    ZStream.accessStream(_.get.streamResultSetSimple(s))

}
