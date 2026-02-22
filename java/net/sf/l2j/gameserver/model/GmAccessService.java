package net.sf.l2j.gameserver.model;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.ConnectionPool;
import net.sf.l2j.gameserver.ThreadPool;
import net.sf.l2j.gameserver.datatables.AccessLevels;
import net.sf.l2j.gameserver.datatables.xml.GmData;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.Player.PunishLevel;
import net.sf.l2j.gameserver.model.holder.GMHolder;

public final class GmAccessService
{
	private static final Logger LOGGER = Logger.getLogger(GmAccessService.class.getName());
	
	private static final Path WATCH_DIR = Paths.get("./data/xml/custom/adminaccesslevel");
	private static final String WATCH_FILE = "gm_list.xml";
	
	private static final long RELOAD_DEBOUNCE_MS = 500;
	private static final long RELOAD_DELAY_MS = 250;
	
	private WatchService _watchService;
	private Thread _watchThread;
	
	private final AtomicLong _lastReloadAt = new AtomicLong(0);
	private volatile ScheduledFuture<?> _pendingReload;
	
	
	private GmAccessService()
	{
		startWatcher();
	}
	
	public void shutdown()
	{
		stopWatcher();
	}
	
	public static void onEnterWorld(Player player)
	{
		if (player == null)
			return;
		
		// Se o core estiver com "todo mundo é GM", não tem o que forçar.
		if (Config.EVERYBODY_HAS_ADMIN_RIGHTS)
			return;
		
		enforceForOnlinePlayer(player, "EnterWorld");
	}
	
	public static void reconcilePlayerByNameOrObj(String name, int objectId)
	{
		if (Config.EVERYBODY_HAS_ADMIN_RIGHTS)
			return;
		
		Player online = null;
		
		if (objectId > 0)
			online = L2World.getInstance().getPlayer(objectId);
		
		if (online == null && name != null && !name.isBlank())
			online = L2World.getInstance().getPlayer(name);
		
		if (online != null)
		{
			enforceForOnlinePlayer(online, "AdminReconcile");
		}
		else if (objectId > 0)
		{
			enforceForOfflineObjectId(objectId, "AdminReconcile");
		}
	}
	
	public static void reapplyAllOnline(String reason)
	{
		if (Config.EVERYBODY_HAS_ADMIN_RIGHTS)
			return;
		
		for (Player p : L2World.getInstance().getPlayers())
		{
			try
			{
				enforceForOnlinePlayer(p, reason);
			}
			catch (Exception e)
			{
				LOGGER.log(Level.WARNING, "Failed enforce GM access for " + p.getName(), e);
			}
		}
	}
	
	private static void enforceForOnlinePlayer(Player player, String reason)
	{
		final int objId = player.getObjectId();
		final GMHolder entry = GmData.getInstance().getEntry(objId);
		
		if (entry == null || !entry.isEnabled() || entry.getAccessLevel() <= 0)
		{
			if (player.isGM() || isAccessAboveUser(player))
				player.setAccessLevel(AccessLevels.USER_ACCESS_LEVEL_NUMBER);
			
			player.setPunishLevel(PunishLevel.ACC, 0);
			updateCharacterAccessLevel(objId, AccessLevels.USER_ACCESS_LEVEL_NUMBER);
			
			if (LOGGER.isLoggable(Level.INFO))
				LOGGER.info("GM denied: " + player.getName() + " objId=" + objId + " reason=" + reason);
			return;
		}
		
		final int desired = entry.getAccessLevel();
		final int current = getAccessLevelNumber(player);
		
		if (current != desired)
			player.setAccessLevel(desired);
		
		updateCharacterAccessLevel(objId, desired);
		
		if (LOGGER.isLoggable(Level.FINE))
			LOGGER.fine("GM ok: " + player.getName() + " objId=" + objId + " lvl=" + desired + " reason=" + reason);
	}
	
	private static void enforceForOfflineObjectId(int objId, String reason)
	{
		final GMHolder entry = GmData.getInstance().getEntry(objId);
		final int desired = (entry != null && entry.isEnabled()) ? Math.max(AccessLevels.USER_ACCESS_LEVEL_NUMBER, entry.getAccessLevel()) : AccessLevels.USER_ACCESS_LEVEL_NUMBER;
		
		updateCharacterAccessLevel(objId, desired);
		
		if (LOGGER.isLoggable(Level.INFO))
			LOGGER.info("Offline GM reconcile objId=" + objId + " => access=" + desired + " reason=" + reason);
	}
	
	private static int getAccessLevelNumber(Player player)
	{
		final var al = player.getAccessLevel();
		if (al == null)
			return AccessLevels.USER_ACCESS_LEVEL_NUMBER;
		
		// no seu core: al.getLevel()
		return al.getLevel();
	}
	
	private static boolean isAccessAboveUser(Player player)
	{
		return getAccessLevelNumber(player) != AccessLevels.USER_ACCESS_LEVEL_NUMBER;
	}
	
	private static void updateCharacterAccessLevel(int objId, int lvl)
	{
		final String sql = "UPDATE characters SET accesslevel=? WHERE obj_Id=?";
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(sql))
		{
			ps.setInt(1, lvl);
			ps.setInt(2, objId);
			ps.executeUpdate();
		}
		catch (Exception e)
		{
			LOGGER.log(Level.WARNING, "Failed update accesslevel objId=" + objId + " lvl=" + lvl, e);
		}
	}
	
	/* ================= WATCHER ================= */
	
	private void startWatcher()
	{
		try
		{
			_watchService = FileSystems.getDefault().newWatchService();
			WATCH_DIR.register(_watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
			
			_watchThread = new Thread(this::watchLoop, "gm-access-watcher");
			_watchThread.setDaemon(true);
			_watchThread.start();
			
			LOGGER.info("GM watcher started: " + WATCH_DIR.toAbsolutePath());
		}
		catch (IOException e)
		{
			LOGGER.log(Level.WARNING, "Failed to start GM watcher for " + WATCH_DIR, e);
		}
	}
	
	private void stopWatcher()
	{
		try
		{
			if (_watchService != null)
				_watchService.close();
		}
		catch (Exception ignored)
		{
		}
		
		if (_watchThread != null)
			_watchThread.interrupt();
		
		_watchThread = null;
		_watchService = null;
		
		final ScheduledFuture<?> pending = _pendingReload;
		if (pending != null)
			pending.cancel(false);
	}
	
	private void watchLoop()
	{
		while (true)
		{
			WatchKey key;
			try
			{
				key = _watchService.take();
			}
			catch (InterruptedException | ClosedWatchServiceException e)
			{
				return;
			}
			
			boolean hit = false;
			
			for (WatchEvent<?> event : key.pollEvents())
			{
				final WatchEvent.Kind<?> kind = event.kind();
				if (kind == StandardWatchEventKinds.OVERFLOW)
					continue;
				
				final Path changed = (Path) event.context();
				if (changed != null && WATCH_FILE.equalsIgnoreCase(changed.getFileName().toString()))
					hit = true;
			}
			
			key.reset();
			
			if (hit)
				scheduleReloadDebounced();
		}
	}
	
	private void scheduleReloadDebounced()
	{
		final long now = System.currentTimeMillis();
		final long last = _lastReloadAt.get();
		if (now - last < RELOAD_DEBOUNCE_MS)
			return;
		
		_lastReloadAt.set(now);
		
		// cancela reload anterior pendente
		final ScheduledFuture<?> prev = _pendingReload;
		if (prev != null)
			prev.cancel(false);
		
		_pendingReload = ThreadPool.schedule(() -> {
			try
			{
				GmData.getInstance().reload();
				reapplyAllOnline("XmlReload");
			}
			catch (Exception e)
			{
				LOGGER.log(Level.WARNING, "GM xml reload failed.", e);
			}
		}, RELOAD_DELAY_MS);
	}
	
	public static GmAccessService getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder
	{
		protected static final GmAccessService INSTANCE = new GmAccessService();
	}
}