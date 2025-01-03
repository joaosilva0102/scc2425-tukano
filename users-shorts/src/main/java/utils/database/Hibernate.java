package utils.database;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.hibernate.exception.ConstraintViolationException;

import utils.Result;
import utils.Result.ErrorCode;

import static java.lang.String.format;

/**
 * A helper class to perform POJO (Plain Old Java Objects) persistence, using
 * Hibernate and a backing relational database.
 *
 */
public class Hibernate {
	private static final Logger Log = Logger.getLogger(Hibernate.class.getName());

	//private static final String HIBERNATE_CFG_FILE = "../../webapp/WEB-INF/hibernate.cfg.xml";
	private SessionFactory sessionFactory;
	private static Hibernate instance;

	private Hibernate() {
		try {
			sessionFactory = new Configuration().configure().buildSessionFactory();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Returns the Hibernate instance, initializing if necessary. Requires a
	 * configuration file (hibernate.cfg.xml)
	 *
	 * @return
	 */
	synchronized public static Hibernate getInstance() {
		Log.info("Accessing CosmosDB PostgresSQL...");
		if (instance == null)
			instance = new Hibernate();
		return instance;
	}

	public Result<Void> insertOne(Object  obj) {
		return execute( (hibernate) -> {
			hibernate.persist( obj );
		});
	}

	public <T> Result<T> updateOne(T obj) {
		return execute( hibernate -> {
			var res = hibernate.merge( obj );
			if( res == null)
				return Result.error( ErrorCode.NOT_FOUND );

			return Result.ok( res );
		});
	}

	public <T> Result<T> deleteOne(T obj) {
		return execute( hibernate -> {
			hibernate.remove( obj );
			return Result.ok( obj );
		});
	}

	public <T> Result<T> getOne(Object id, Class<T> clazz) {
		try (var session = sessionFactory.openSession()) {
			var res = session.find(clazz, id);
			if (res == null)
				return Result.error(ErrorCode.NOT_FOUND);
			else
				return Result.ok(res);
		} catch (Exception e) {
			throw e;
		}
	}

	public <T> List<T> sql(String sqlStatement, Class<T> clazz) {
		Log.info(() -> format("Query2: %s\n", sqlStatement));
		try (var session = sessionFactory.openSession()) {
			var query = session.createNativeQuery(sqlStatement, clazz);
			return query.list();
		} catch (Exception e) {
			throw e;
		}
	}

	public <T> Result<T> execute(Consumer<Session> proc) {
		return execute( (hibernate) -> {
			proc.accept( hibernate);
			return Result.ok();
		});
	}

	public <T> Result<T> execute(Function<Session, Result<T>> func) {
		Transaction tx = null;
		try (var session = sessionFactory.openSession()) {
			tx = session.beginTransaction();
			var res = func.apply( session );
			session.flush();
			tx.commit();
			return res;
		}
		catch (ConstraintViolationException __) {
			return Result.error(ErrorCode.CONFLICT);
		}
		catch (Exception e) {
			if( tx != null )
				tx.rollback();
			e.printStackTrace();
			throw e;
		}
	}
}