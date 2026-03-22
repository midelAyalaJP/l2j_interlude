package net.sf.l2j.loginserver;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.LineNumberReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import net.sf.l2j.Config;
import net.sf.l2j.commons.lang.StringUtil;
import net.sf.l2j.commons.mmocore.SelectorConfig;
import net.sf.l2j.commons.mmocore.SelectorThread;
import net.sf.l2j.gameserver.ConnectionPool;
import net.sf.l2j.gameserver.instancemanager.ServerStatusManager;
import net.sf.l2j.gameserver.instancemanager.ServerStatusManager.ServerType;
import net.sf.l2j.launcher.LoginServerLaucher;

/**
 * @author KenM
 */
public class L2LoginServer
{
	private static final Logger _log = Logger.getLogger(L2LoginServer.class.getName());
	
	public static final int PROTOCOL_REV = 0x0102;
	
	private static L2LoginServer loginServer;
	public static ServerStatusManager STATUS;
	private GameServerListener _gameServerListener;
	private SelectorThread<L2LoginClient> _selectorThread;
	
	public static void main(String[] args)
	{
		try
		{
			loginServer = new L2LoginServer();
		}
		catch (Exception e)
		{
			_log.severe("Failed to start LoginServer: " + e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	private static boolean isWindows()
	{
		return System.getProperty("os.name").toLowerCase().contains("win");
	}
	
	public L2LoginServer() throws Exception
	{
		if (isWindows())
		{
			try
			{
				if (!GraphicsEnvironment.isHeadless())
				{
					System.out.println("Auth: Running in Interface GUI.");
					new LoginServerLaucher();
				}
			}
			catch (Throwable t)
			{
				// Nunca deixar GUI quebrar o LoginServer
			}
		}
		
		startLoginServer();
	}
	
	public static L2LoginServer getInstance()
	{
		return loginServer;
	}
	
	private void startLoginServer() throws Exception
	{
		final String LOG_FOLDER = "./log";
		final String LOG_NAME = "config/log.cfg";
		
		new File(LOG_FOLDER).mkdir();
		
		try (InputStream is = new FileInputStream(LOG_NAME))
		{
			LogManager.getLogManager().readConfiguration(is);
		}
		
		StringUtil.printSection("Master");
		
		Config.loadLoginServer();
		
		ConnectionPool.init();
		
		StringUtil.printSection("LoginController");
		LoginController.load();
		GameServerTable.getInstance();
		StringUtil.printSection("Ban List");
		loadBanFile();
		
		bindNetwork();
	}
	
	private void bindNetwork()
	{
		InetAddress bindAddress = null;
		
		if (!Config.LOGIN_BIND_ADDRESS.equals("*"))
		{
			try
			{
				bindAddress = InetAddress.getByName(Config.LOGIN_BIND_ADDRESS);
			}
			catch (UnknownHostException e)
			{
				_log.severe("Invalid bind address, using all IPs.");
			}
		}
		
		final SelectorConfig sc = new SelectorConfig();
		sc.MAX_READ_PER_PASS = Config.MMO_MAX_READ_PER_PASS;
		sc.MAX_SEND_PER_PASS = Config.MMO_MAX_SEND_PER_PASS;
		sc.SLEEP_TIME = Config.MMO_SELECTOR_SLEEP_TIME;
		sc.HELPER_BUFFER_COUNT = Config.MMO_HELPER_BUFFER_COUNT;
		
		try
		{
			_selectorThread = new SelectorThread<>(sc, new SelectorHelper(), new L2LoginPacketHandler(), new SelectorHelper(), new SelectorHelper());
			_selectorThread.openServerSocket(bindAddress, Config.PORT_LOGIN);
			_selectorThread.start();
		}
		catch (IOException e)
		{
			_log.severe("FATAL: Failed to start LoginServer socket.");
			System.exit(1);
		}
		
		try
		{
			_gameServerListener = new GameServerListener();
			_gameServerListener.start();
			STATUS = ServerStatusManager.start(ServerType.LOGIN);
		}
		catch (IOException e)
		{
			_log.severe("FATAL: Failed to start GameServer listener.");
			System.exit(1);
		}
		
		_log.info("Loginserver ready on " + (bindAddress == null ? "*" : bindAddress.getHostAddress()) + ":" + Config.PORT_LOGIN);
	}
	
	public GameServerListener getGameServerListener()
	{
		return _gameServerListener;
	}
	
	private static void loadBanFile()
	{
		File banFile = new File("config/banned_ip.cfg");
		if (banFile.exists() && banFile.isFile())
		{
			try (LineNumberReader reader = new LineNumberReader(new FileReader(banFile)))
			{
				String line;
				String[] parts;
				
				while ((line = reader.readLine()) != null)
				{
					line = line.trim();
					// check if this line isnt a comment line
					if (line.length() > 0 && line.charAt(0) != '#')
					{
						// split comments if any
						parts = line.split("#");
						
						// discard comments in the line, if any
						line = parts[0];
						parts = line.split(" ");
						
						String address = parts[0];
						long duration = 0;
						
						if (parts.length > 1)
						{
							try
							{
								duration = Long.parseLong(parts[1]);
							}
							catch (NumberFormatException e)
							{
								_log.warning("Skipped: Incorrect ban duration (" + parts[1] + ") on banned_ip.cfg. Line: " + reader.getLineNumber());
								continue;
							}
						}
						
						try
						{
							LoginController.getInstance().addBanForAddress(address, duration);
						}
						catch (UnknownHostException e)
						{
							_log.warning("Skipped: Invalid address (" + parts[0] + ") on banned_ip.cfg. Line: " + reader.getLineNumber());
						}
					}
				}
			}
			catch (IOException e)
			{
				_log.warning("Error while reading banned_ip.cfg. Details: " + e.getMessage());
				if (Config.DEVELOPER)
					e.printStackTrace();
			}
			_log.info("Loaded " + LoginController.getInstance().getBannedIps().size() + " IP(s) from banned_ip.cfg.");
		}
		else
			_log.warning("banned_ip.cfg is missing. Ban listing is skipped.");
	}
	
	public void shutdown(boolean restart)
	{
		Runtime.getRuntime().addShutdownHook(new Thread(() ->
		{
		    if (STATUS != null)
		        STATUS.shutdown();
		}));
		
		Runtime.getRuntime().exit(restart ? 2 : 0);
		
	}
}