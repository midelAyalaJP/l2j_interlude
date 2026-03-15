package net.sf.l2j.gameserver.model.zone.custom;

import java.util.ArrayList;
import java.util.List;

import net.sf.l2j.commons.random.Rnd;
import net.sf.l2j.gameserver.datatables.SpawnDropZoneManager;
import net.sf.l2j.gameserver.model.actor.Attackable;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.zone.type.L2SpawnDropZone;

public final class SpawnDropZoneReward
{
	public static void reward(Attackable attackable, Player killer, L2SpawnDropZone zone, boolean isRaid)
	{
		if (killer == null || zone == null)
			return;
		
		final List<Player> targets = resolveTargets(killer, zone.getRewardTarget());
		if (targets.isEmpty())
			return;
		
		for (Player target : targets)
		{
			if (target == null)
				continue;
			
			for (ZoneDropCategory category : zone.getDropCategories())
			{
				if (Rnd.get(100) >= category.getChance())
					continue;
				
				final ZoneDropItemData item = rollOneItem(category);
				if (item == null)
					continue;
				
				final int count = (item.getMin() >= item.getMax()) ? item.getMin() : Rnd.get(item.getMin(), item.getMax());
				target.addItem("SpawnDropZone", item.getItemId(), count, target, true);
				SpawnDropZoneManager.getInstance().unregisterZoneNpc(attackable);
				
			}
		}
	}
	
	private static ZoneDropItemData rollOneItem(ZoneDropCategory category)
	{
		final List<ZoneDropItemData> success = new ArrayList<>();
		
		for (ZoneDropItemData item : category.getItems())
		{
			if (Rnd.get(100) < item.getChance())
				success.add(item);
		}
		
		if (success.isEmpty())
			return null;
		
		return success.get(Rnd.get(success.size()));
	}
	
	private static List<Player> resolveTargets(Player killer, ZoneRewardTargetType type)
	{
		final List<Player> result = new ArrayList<>();
		
		switch (type)
		{
			case SOLO:
				result.add(killer);
				break;
			
			case PARTY:
			case RANDOM_PARTY_MEMBER:
				if (killer.getParty() == null)
				{
					result.add(killer);
				}
				else
				{
					final List<Player> members = killer.getParty().getPartyMembers();
					if (!members.isEmpty())
						result.add(members.get(Rnd.get(members.size())));
				}
				break;
			
			case ALL_PARTY_MEMBERS:
				if (killer.getParty() == null)
				{
					result.add(killer);
				}
				else
				{
					result.addAll(killer.getParty().getPartyMembers());
				}
				break;
		}
		
		return result;
	}
}