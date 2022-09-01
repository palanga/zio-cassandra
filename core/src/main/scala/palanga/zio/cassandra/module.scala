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

  @deprecated("Use palanga.zio.cassandra.session.ZCqlSession object instead")
  def close: ZIO[ZCqlSession, SessionCloseException, Unit] =
    ZIO.environmentWithZIO(_.get.close)

  @deprecated("Use palanga.zio.cassandra.session.ZCqlSession object instead")
  def execute(s: ZStatement[_]): ZIO[ZCqlSession, CassandraException, AsyncResultSet] =
    ZIO.environmentWithZIO(_.get.execute(s))

  @deprecated("Use palanga.zio.cassandra.session.ZCqlSession object instead")
  def execute(s: BoundStatement): ZIO[ZCqlSession, QueryExecutionException, AsyncResultSet] =
    ZIO.environmentWithZIO(_.get.execute(s))

  @deprecated("Use palanga.zio.cassandra.session.ZCqlSession object instead")
  def execute(s: SimpleStatement): ZIO[ZCqlSession, QueryExecutionException, AsyncResultSet] =
    ZIO.environmentWithZIO(_.get.execute(s))

  @deprecated("Use palanga.zio.cassandra.session.ZCqlSession object instead")
  def executePar(ss: BoundStatement*): ZIO[ZCqlSession, QueryExecutionException, List[AsyncResultSet]] =
    ZIO.environmentWithZIO(_.get.executePar(ss: _*))

  @deprecated("Use palanga.zio.cassandra.session.ZCqlSession object instead")
  def executeParSimple(ss: SimpleStatement*): ZIO[ZCqlSession, QueryExecutionException, List[AsyncResultSet]] =
    ZIO.environmentWithZIO(_.get.executeParSimple(ss: _*))

  @deprecated("Use palanga.zio.cassandra.session.ZCqlSession object instead")
  def executeHeadOption[Out](s: ZStatement[Out]): ZIO[ZCqlSession, CassandraException, Option[Out]] =
    ZIO.environmentWithZIO(_.get.executeHeadOption(s))

  @deprecated("Use palanga.zio.cassandra.session.ZCqlSession object instead")
  def executeHeadOrFail[Out](s: ZStatement[Out]): ZIO[ZCqlSession, CassandraException, Out] =
    ZIO.environmentWithZIO(_.get.executeHeadOrFail(s))

  @deprecated("Use palanga.zio.cassandra.session.ZCqlSession object instead")
  def prepare(s: SimpleStatement): ZIO[ZCqlSession, PrepareStatementException, PreparedStatement] =
    ZIO.environmentWithZIO(_.get.prepare(s))

  @deprecated("Use palanga.zio.cassandra.session.ZCqlSession object instead")
  def preparePar(ss: SimpleStatement*): ZIO[ZCqlSession, PrepareStatementException, List[PreparedStatement]] =
    ZIO.environmentWithZIO(_.get.preparePar(ss: _*))

  @deprecated("Use palanga.zio.cassandra.session.ZCqlSession object instead")
  def stream[Out](s: ZStatement[Out]): ZStream[ZCqlSession, CassandraException, Chunk[Out]] =
    ZStream.environmentWithStream(_.get.stream(s))

  @deprecated("Use palanga.zio.cassandra.session.ZCqlSession object instead")
  def stream(s: BoundStatement): ZStream[ZCqlSession, CassandraException, Chunk[Row]] =
    ZStream.environmentWithStream(_.get.stream(s))

  @deprecated("Use palanga.zio.cassandra.session.ZCqlSession object instead")
  def stream(s: SimpleStatement): ZStream[ZCqlSession, CassandraException, Chunk[Row]] =
    ZStream.environmentWithStream(_.get.stream(s))

  @deprecated("Use palanga.zio.cassandra.session.ZCqlSession object instead")
  def streamResultSet(s: ZStatement[_]): ZStream[ZCqlSession, CassandraException, AsyncResultSet] =
    ZStream.environmentWithStream(_.get.streamResultSet(s))

  @deprecated("Use palanga.zio.cassandra.session.ZCqlSession object instead")
  def streamResultSet(s: BoundStatement): ZStream[ZCqlSession, CassandraException, AsyncResultSet] =
    ZStream.environmentWithStream(_.get.streamResultSet(s))

  @deprecated("Use palanga.zio.cassandra.session.ZCqlSession object instead")
  def streamResultSet(s: SimpleStatement): ZStream[ZCqlSession, CassandraException, AsyncResultSet] =
    ZStream.environmentWithStream(_.get.streamResultSet(s))

}
