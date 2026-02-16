package net.sf.l2j.gameserver.network.clientpackets;

import net.sf.l2j.gameserver.instancemanager.BoatManager;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.Vehicle;
import net.sf.l2j.gameserver.model.item.type.WeaponType;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.ActionFailed;
import net.sf.l2j.gameserver.network.serverpackets.MoveToLocationInVehicle;
import net.sf.l2j.gameserver.network.serverpackets.StopMoveInVehicle;

public final class RequestMoveToLocationInVehicle extends L2GameClientPacket
{
	private int _boatId;
	private int _targetX;
	private int _targetY;
	private int _targetZ;
	private int _originX;
	private int _originY;
	private int _originZ;

	// =========================
	// TUNING (L2OFF-like)
	// =========================
	// máximo “passo” por packet dentro do barco (ajuste conforme seu core/boats)
	private static final int MAX_VEHICLE_STEP = 1200; // 800~1600 costuma ser ok
	private static final int MAX_VEHICLE_STEP_SQ = MAX_VEHICLE_STEP * MAX_VEHICLE_STEP;

	// se origin do client estiver muito longe do server-origin, ignoramos o origin do client
	private static final int ORIGIN_DRIFT_MAX = 600;
	private static final int ORIGIN_DRIFT_MAX_SQ = ORIGIN_DRIFT_MAX * ORIGIN_DRIFT_MAX;

	@Override
	protected void readImpl()
	{
		_boatId = readD();
		_targetX = readD();
		_targetY = readD();
		_targetZ = readD();
		_originX = readD();
		_originY = readD();
		_originZ = readD();
	}

	@Override
	protected void runImpl()
	{
		final Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;

		// se não moveu (client mandou igual)
		if (_targetX == _originX && _targetY == _originY && _targetZ == _originZ)
		{
			activeChar.sendPacket(new StopMoveInVehicle(activeChar, _boatId));
			return;
		}

		// bow attack travando move
		if (activeChar.isAttackingNow() && activeChar.getAttackType() == WeaponType.BOW)
		{
			activeChar.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}

		if (activeChar.isSitting() || activeChar.isMovementDisabled())
		{
			activeChar.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}

		if (activeChar.getPet() != null)
		{
			activeChar.sendPacket(SystemMessageId.RELEASE_PET_ON_BOAT);
			activeChar.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}

		// resolve boat
		final Vehicle boat;
		if (activeChar.isInBoat())
		{
			boat = activeChar.getBoat();
			if (boat == null || boat.getObjectId() != _boatId)
			{
				activeChar.sendPacket(ActionFailed.STATIC_PACKET);
				return;
			}
		}
		else
		{
			boat = BoatManager.getInstance().getBoat(_boatId);
			if (boat == null || !boat.isInsideRadius(activeChar, 300, true, false))
			{
				activeChar.sendPacket(ActionFailed.STATIC_PACKET);
				return;
			}
			activeChar.setVehicle(boat);
		}

		// =========================
		// SERVER-AUTHORITATIVE ORIGIN
		// =========================
		// origem real no veículo (servidor)
		final int sx = activeChar.getVehiclePosition().getX();
		final int sy = activeChar.getVehiclePosition().getY();
		final int sz = activeChar.getVehiclePosition().getZ();

		// 1) valida “passo” do target relativo ao origin do servidor (anti exploit)
		final long dxS = (long) _targetX - sx;
		final long dyS = (long) _targetY - sy;

		if ((dxS * dxS + dyS * dyS) > MAX_VEHICLE_STEP_SQ)
		{
			// grande demais: rejeita e força stop (evita teleporte dentro do barco)
			activeChar.sendPacket(new StopMoveInVehicle(activeChar, _boatId));
			activeChar.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}

		// 2) drift do origin do client: se muito diferente do server-origin, não usamos origin do client pro broadcast
		final long dxO = (long) _originX - sx;
		final long dyO = (long) _originY - sy;
		final boolean originFar = (dxO * dxO + dyO * dyO) > ORIGIN_DRIFT_MAX_SQ;

		final int oX = originFar ? sx : _originX;
		final int oY = originFar ? sy : _originY;
		final int oZ = originFar ? sz : _originZ;

		// aplica target no servidor (posição no veículo)
		activeChar.getVehiclePosition().set(_targetX, _targetY, _targetZ);

		// broadcast com origem “limpa” (server ou client se estiver coerente)
		activeChar.broadcastPacket(new MoveToLocationInVehicle(activeChar, _targetX, _targetY, _targetZ, oX, oY, oZ));
	}
}
