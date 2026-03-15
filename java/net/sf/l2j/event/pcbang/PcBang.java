package net.sf.l2j.event.pcbang;

import java.util.logging.Logger;

import net.sf.l2j.Config;
import net.sf.l2j.commons.random.Rnd;
import net.sf.l2j.gameserver.model.L2World;
import net.sf.l2j.gameserver.model.actor.Player;

public class PcBang implements Runnable
{
	private static final Logger LOG = Logger.getLogger(PcBang.class.getName());
	private final long REWARD_INTERVAL = Config.PCB_INTERVAL * 1000L;
	
	protected PcBang()
	{
		LOG.info("PcBang point event started.");
	}

	public static PcBang getInstance()
	{
		return SingletonHolder.INSTANCE;
	}

	@Override
	public void run()
	{
		for (Player player : L2World.getInstance().getPlayers())
		{
			if (player == null)
				continue;

			tryReward(player);
		}
	}
	
	public void tryReward(Player player)
	{
		if (player == null)
			return;

		if (!canReceiveReward(player))
			return;

		final long now = System.currentTimeMillis();
		if ((now - player.getLastPcBangRewardTime()) < REWARD_INTERVAL)
			return;

		giveReward(player, now);
	}

	private static boolean canReceiveReward(Player player)
	{
		if (!Config.PCB_ENABLE)
			return false;

		if (!player.isOnline())
			return false;

		if (player.getLevel() < Config.PCB_MIN_LEVEL)
			return false;

		if ((player.getClient() == null) || player.getClient().isDetached())
			return false;

		if (player.isOff() || player.isOffShop())
			return false;

		return true;
	}

	private static void giveReward(Player player, long now)
	{
		int score = Rnd.get(Config.PCB_POINT_MIN, Config.PCB_POINT_MAX);
		boolean doubled = false;

		if (Rnd.get(100) < Config.PCB_CHANCE_DUAL_POINT)
		{
			score *= 2;
			doubled = true;
		}

		player.addPcBangScore(score);
		player.setLastPcBangRewardTime(now);
		player.updatePcBangWnd(score, true, doubled);
	}


	private static class SingletonHolder
	{
		protected static final PcBang INSTANCE = new PcBang();
	}
}