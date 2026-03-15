package net.sf.l2j.event.bossevent;

import net.sf.l2j.commons.random.Rnd;
import net.sf.l2j.gameserver.ThreadPool;
import net.sf.l2j.gameserver.model.actor.L2Summon;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.entity.Duel.DuelState;
import net.sf.l2j.gameserver.taskmanager.PvpFlagTaskManager;

public class KTBEventTeleporter implements Runnable
{
	/** The instance of the player to teleport */
	private Player _activeChar = null;
	/** Coordinates of the spot to teleport to */
	private int[] _coordinates = new int[3];
	/** Admin removed this player from event */
	private boolean _adminRemove = false;

	/**
	 * Initialize the teleporter and start the delayed task<br><br>
	 *
	 * @param activeChar as L2PcInstance<br>
	 * @param coordinates as int[]<br>
	 * @param fastSchedule 
	 * @param adminRemove as boolean<br>
	 */
	public KTBEventTeleporter(Player activeChar, int[] coordinates, boolean fastSchedule, boolean adminRemove)
	{
		_activeChar = activeChar;
		_coordinates = coordinates;
		_adminRemove = adminRemove;

		loadTeleport(fastSchedule);
	}

	/**
	 * Initialize the teleporter and start the delayed task<br><br>
	 *
	 * @param activeChar as L2PcInstance<br>
	 * @param fastSchedule 
	 * @param adminRemove as boolean<br>
	 */
	public KTBEventTeleporter(Player activeChar, boolean fastSchedule, boolean adminRemove)
	{
		_activeChar = activeChar;
		_coordinates = KTBConfig.KTB_EVENT_PLAYER_COORDINATES.get(Rnd.get(KTBConfig.KTB_EVENT_PLAYER_COORDINATES.size()));
		_adminRemove = adminRemove;

		loadTeleport(fastSchedule);
	}

	private void loadTeleport(boolean fastSchedule)
	{
		long delay = (KTBEvent.isStarted() ? KTBConfig.KTB_EVENT_RESPAWN_TELEPORT_DELAY : KTBConfig.KTB_EVENT_START_LEAVE_TELEPORT_DELAY) * 1000;
		ThreadPool.schedule(this, fastSchedule ? 0 : delay);
	}
	@Override
	public void run()
	{
		if (_activeChar == null) return;

		L2Summon summon = _activeChar.getPet();

		if (summon != null)
			summon.unSummon(_activeChar);

		
		
	//	if (KTBConfig.KTB_EVENT_EFFECTS_REMOVAL == 0 || (KTBConfig.KTB_EVENT_EFFECTS_REMOVAL == 1 && (_activeChar.getTeam() == 0 || (_activeChar.isInDuel() && _activeChar.getDuelState() != DuelState.INTERRUPTED))))
		if (KTBConfig.KTB_EVENT_EFFECTS_REMOVAL == 0 || (KTBConfig.KTB_EVENT_EFFECTS_REMOVAL == 1 && ((_activeChar.getTeam() == 0 || _activeChar.isInDuel() && _activeChar.getDuelState() != DuelState.INTERRUPTED))))
			_activeChar.stopAllEffectsExceptThoseThatLastThroughDeath();

		if (_activeChar.isInDuel())
			_activeChar.setDuelState(DuelState.INTERRUPTED);

		_activeChar.doRevive();

		if (KTBEvent.isStarted() && !_adminRemove)
		{
			_activeChar.teleToLocation(_coordinates[0] + Rnd.get(101) - 50, _coordinates[1] + Rnd.get(101) - 50, _coordinates[2], 0);
			PvpFlagTaskManager.getInstance().remove(_activeChar);
			_activeChar.updatePvPFlag(0);
		}
		else
		{
			_activeChar.teleToLocation(_activeChar.getLastX(), _activeChar.getLastY(), _activeChar.getLastZ(), 0);
			_activeChar.setLastCords(0, 0, 0);
		}
		
		if (KTBEvent.isStarted() && !_adminRemove)
			_activeChar.setTeam(1); // Azul 1, Red 2
		//	_activeChar.setTeam(TeamType.BLUE);
		else
			_activeChar.setTeam(0);
		//	_activeChar.setTeam(TeamType.NONE);

		_activeChar.setCurrentCp(_activeChar.getMaxCp());
		_activeChar.setCurrentHp(_activeChar.getMaxHp());
		_activeChar.setCurrentMp(_activeChar.getMaxMp());

		_activeChar.broadcastStatusUpdate();
		_activeChar.broadcastTitleInfo();
		_activeChar.broadcastUserInfo();		
	}
}