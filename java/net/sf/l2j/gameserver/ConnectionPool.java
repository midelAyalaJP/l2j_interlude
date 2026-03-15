package net.sf.l2j.gameserver;

import java.sql.Connection;
import java.sql.SQLException;

import net.sf.l2j.Config;
import net.sf.l2j.commons.lang.CLogger;

import org.mariadb.jdbc.MariaDbPoolDataSource;

public final class ConnectionPool
{
	private static final CLogger LOGGER = new CLogger(ConnectionPool.class.getName());
	
	private static volatile MariaDbPoolDataSource _source;
	private static volatile boolean _initialized = false;
	
	private ConnectionPool()
	{
		throw new IllegalStateException("Utility class");
	}
	
	public static synchronized void init()
	{
		if (_initialized)
		{
			LOGGER.warn("ConnectionPool já foi inicializado. Ignorando nova inicialização.");
			return;
		}
		
		try
		{
			final MariaDbPoolDataSource source = new MariaDbPoolDataSource();
			source.setUrl(Config.DATABASE_URL);
			
			// Teste inicial simples para falhar no boot se a conexão estiver inválida.
			try (Connection con = source.getConnection())
			{
				if ((con == null) || !con.isValid(2))
					throw new SQLException("Falha na validação inicial da conexão.");
			}
			
			_source = source;
			_initialized = true;
			
			LOGGER.info("MariaDB ConnectionPool iniciado com sucesso.");
		}
		catch (SQLException e)
		{
			LOGGER.error("Erro ao inicializar o pool MariaDB.", e);
			shutdown();
		}
	}
	
	public static Connection getConnection() throws SQLException
	{
		final MariaDbPoolDataSource source = _source;
		if (!_initialized || (source == null))
			throw new SQLException("ConnectionPool não inicializado.");
		
		return source.getConnection();
	}
	
	public static synchronized void shutdown()
	{
		final MariaDbPoolDataSource source = _source;
		_source = null;
		_initialized = false;
		
		if (source != null)
		{
			try
			{
				source.close();
				LOGGER.info("MariaDB ConnectionPool finalizado.");
			}
			catch (Exception e)
			{
				LOGGER.error("Erro ao finalizar o pool MariaDB.", e);
			}
		}
	}
	
	public static boolean isInitialized()
	{
		return _initialized && (_source != null);
	}
}