package net.sf.l2j.AutoFarm;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.ThreadPool;
import net.sf.l2j.gameserver.handler.tutorialhandlers.Autofarm;
import net.sf.l2j.gameserver.handler.voicedcommandhandlers.VoicedMenu;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.zone.ZoneId;

public enum AutofarmManager
{
	INSTANCE;
	
	private final Long iterationSpeedMs = 500L;
	
	private final ConcurrentHashMap<Integer, AutofarmPlayerRoutine> activeFarmers = new ConcurrentHashMap<>();
	private ScheduledFuture<?> onUpdateTask = ThreadPool.scheduleAtFixedRate("AutofarmManager: onUpdate Task", onUpdate(), 1000, iterationSpeedMs);
	
	private Runnable onUpdate()
	{
		return () -> activeFarmers.forEach((integer, autofarmPlayerRoutine) -> autofarmPlayerRoutine.executeRoutine());
	}
	
	public void startFarm(Player player)
	{
		if (Config.ENABLE_COMMAND_VIP_AUTOFARM)
		{
			if (!player.isVip())
			{
				VoicedMenu.showMenuHtml(player);
				player.sendMessage("You are not VIP member.");
				return;
			}
		}
		
		if (Config.NO_USE_FARM_IN_PEACE_ZONE)
		{
			if (player.isInsideZone(ZoneId.PEACE))
			{
				VoicedMenu.showMenuHtml(player);
				player.sendMessage("No Use Auto farm in Peace Zone.");
				return;
			}
		}
		activeFarmers.put(player.getObjectId(), new AutofarmPlayerRoutine(player));
		player.sendMessage("Autofarming Activated.");
		player.broadcastUserInfo();
		// player.doCast(SkillTable.getInstance().getInfo(9501, 1));
	}
	
	public void stopFarm(Player player)
	{
		
		activeFarmers.remove(player.getObjectId());
		player.sendMessage("Autofarming Disabled.");
		Autofarm.showAutoFarm(player);
		player.broadcastUserInfo();
		// player.stopSkillEffects(9501);
	}
	
	public synchronized void stopFarmTask()
	{
		if (onUpdateTask != null)
		{
			onUpdateTask.cancel(false);
			onUpdateTask = null;
		}
	}
	
	public void toggleFarm(Player player)
	{
		if (isAutofarming(player))
		{
			stopFarm(player);
			return;
		}
		if (Config.NO_USE_FARM_IN_PEACE_ZONE)
		{
			if (player.isInsideZone(ZoneId.PEACE))
			{
				VoicedMenu.showMenuHtml(player);
				player.sendMessage("No Use Auto farm in Peace Zone.");
				return;
			}
		}
		startFarm(player);
	}
	
	public Boolean isAutofarming(Player player)
	{
		return activeFarmers.containsKey(player.getObjectId());
	}
	
	public void onPlayerLogout(Player player)
	{
		stopFarm(player);
	}
	
	public void onDeath(Player player)
	{
		if (isAutofarming(player))
			activeFarmers.remove(player.getObjectId());
		player.setAutoFarm(false);
		Autofarm.showAutoFarm(player);
		player.broadcastUserInfo();
	}
}