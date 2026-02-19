package net.sf.l2j.gameserver.model.actor.instance;

import java.util.concurrent.ScheduledFuture;

import net.sf.l2j.Config;
import net.sf.l2j.dungeon.Dungeon;
import net.sf.l2j.gameserver.model.MinionList;
import net.sf.l2j.gameserver.model.actor.Attackable;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.knownlist.MonsterKnownList;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.network.serverpackets.CreatureSay;

/**
 * This class manages monsters.
 * <ul>
 * <li>L2MonsterInstance</li>
 * <li>L2RaidBossInstance</li>
 * <li>L2GrandBossInstance</li>
 * </ul>
 */
public class L2MonsterInstance extends Attackable
{
	private L2MonsterInstance _master;
	private MinionList _minionList;
	
	/**
	 * Constructor of L2MonsterInstance (use L2Character and L2NpcInstance constructor).
	 * <ul>
	 * <li>Call the L2Character constructor to set the _template of the L2MonsterInstance (copy skills from template to object and link _calculators to NPC_STD_CALCULATOR)</li>
	 * <li>Set the name of the L2MonsterInstance</li>
	 * <li>Create a RandomAnimation Task that will be launched after the calculated delay if the server allow it</li>
	 * </ul>
	 * @param objectId Identifier of the object to initialized
	 * @param template L2NpcTemplate to apply to the NPC
	 */
	public L2MonsterInstance(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public void initKnownList()
	{
		setKnownList(new MonsterKnownList(this));
	}
	
	@Override
	public final MonsterKnownList getKnownList()
	{
		return (MonsterKnownList) super.getKnownList();
	}
	
	@Override
	public boolean isAutoAttackable(Creature attacker)
	{
		// FIXME: to test to allow monsters hit others monsters
		if (attacker instanceof L2MonsterInstance)
			return false;
		
		return true;
	}
	
	@Override
	public boolean isAggressive()
	{
		if (Config.AGGRESSION_CHAMPION && isChampion())
		{
			return false;
		}
		return getTemplate().getAggroRange() > 0;
	}
	
	@Override
	public void onSpawn()
	{
		if (!isTeleporting())
		{
			if (_master != null)
			{
				setIsNoRndWalk(true);
				setIsRaidMinion(_master.isRaid());
				_master.getMinionList().onMinionSpawn(this);
			}
			// delete spawned minions before dynamic minions spawned by script
			else if (_minionList != null)
				getMinionList().deleteSpawnedMinions();
			
			startMaintenanceTask();
		}
		
		// dynamic script-based minions spawned here, after all preparations.
		super.onSpawn();
	}
	
	@Override
	public void onTeleported()
	{
		super.onTeleported();
		
		if (_minionList != null)
			getMinionList().onMasterTeleported();
	}
	
	/**
	 * Spawn minions.
	 */
	protected void startMaintenanceTask()
	{
		if (!getTemplate().getMinionData().isEmpty())
			getMinionList().spawnMinions();
		
	}
	
	@Override
	public boolean doDie(Creature killer)
	{
		if (!super.doDie(killer))
			return false;
		
		if (_master != null)
			_master.getMinionList().onMinionDie(this, _master.getSpawn().getRespawnDelay() * 1000 / 2);
		
		if (killer != null)
		{
			final Player player = killer.getActingPlayer();
			{
				if (_dungeon != null)
					_dungeon.onMobKill(this);
				
				if (Config.MISSION_LIST_MONSTER.contains(Integer.valueOf(getTemplate().getNpcId())) && Config.ACTIVE_MISSION)
					if (player.getParty() == null)
					{
						if (!player.check_obj_mission(player.getObjectId()))
							player.updateMission();
						if (!player.isMobCompleted() && player.getMonsterKills() < Config.MISSION_MOB_CONT)
							player.setMonsterKills(player.getMonsterKills() + 1);
					}
				
				if (getNpcId() == Config.monsterId && Config.ACTIVE_MISSION)
					if (player.getParty() != null)
					{
						for (Player member : player.getParty().getPartyMembers())
						{
							if (member.isInsideRadius(getX(), getY(), getZ(), 2000, false, false))
							{
								if (!member.check_obj_mission(member.getObjectId()))
									member.updateMission();
								if ((!member.isPartyMobCompleted() && member.getPartyMonsterKills() < Config.MISSION_PARTY_MOB_CONT) || member.getHWID().equals(player.getHWID()))
									// if ((!member.isPartyMobCompleted() && member.getPartyMonsterKills() < Config.MISSION_PARTY_MOB_CONT) || member.getClient().getConnection().getInetAddress().getHostAddress().equals(player.getClient().getConnection().getInetAddress().getHostAddress()))
									member.setPartyMonsterKills(member.getPartyMonsterKills() + 1);
								continue;
							}
							CreatureSay cs = new CreatureSay(member.getObjectId(), 2, "[Party Event]", "Voce esta muito longe de sua Party..");
							member.sendPacket(cs);
						}
					}
					else if (player.getParty() == null)
					{
						if (!player.check_obj_mission(player.getObjectId()))
							player.updateMission();
						if (!player.isPartyMobCompleted() && player.getPartyMonsterKills() < Config.MISSION_PARTY_MOB_CONT)
							player.setPartyMonsterKills(player.getPartyMonsterKills() + 1);
					}
			}
		}
		
		return true;
	}
	
	@Override
	public void deleteMe()
	{
		if (_minionList != null)
			getMinionList().onMasterDie(true);
		else if (_master != null)
			_master.getMinionList().onMinionDie(this, 0);
		
		super.deleteMe();
	}
	
	@Override
	public L2MonsterInstance getLeader()
	{
		return _master;
	}
	
	public void setLeader(L2MonsterInstance leader)
	{
		_master = leader;
	}
	
	public boolean hasMinions()
	{
		return _minionList != null;
	}
	
	public MinionList getMinionList()
	{
		if (_minionList == null)
			_minionList = new MinionList(this);
		
		return _minionList;
	}
	
	private Dungeon _dungeon;
	
	public void setDungeon(Dungeon dungeon)
	{
		_dungeon = dungeon;
	}
	
	public Dungeon getDungeon()
	{
		return _dungeon;
	}
	
	protected ScheduledFuture<?> _maintenanceTask;
}