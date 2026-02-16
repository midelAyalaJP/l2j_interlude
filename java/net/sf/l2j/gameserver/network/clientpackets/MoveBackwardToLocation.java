package net.sf.l2j.gameserver.network.clientpackets;

import java.nio.BufferUnderflowException;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.ai.CtrlIntention;
import net.sf.l2j.gameserver.geoengine.GeoEngine;
import net.sf.l2j.gameserver.model.Location;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.zone.ZoneId;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.ActionFailed;
import net.sf.l2j.gameserver.network.serverpackets.EnchantResult;
import net.sf.l2j.gameserver.network.serverpackets.StopMove;
import net.sf.l2j.gameserver.util.Util;

public class MoveBackwardToLocation extends L2GameClientPacket
{
	private int _targetX;
	private int _targetY;
	private int _targetZ;
	private int _originX;
	private int _originY;
	private int _originZ;

	private int _moveMovement;

	// =========================
	// TUNING
	// =========================
	private static final int LIMBO_TRIGGER = 200;     // margem (100~200 ideal)
	private static final int LIMBO_MAX_DZ = 2000;     // evita layer absurdo (topo, etc.)
	private static final int MAX_MOVE_SQ = 98010000;  // 9900*9900
	private static final int ORIGIN_DRIFT_MAX_SQ = 600 * 600; // origin(client) longe do server? não corrige.

	@Override
	protected void readImpl()
	{
		_targetX = readD();
		_targetY = readD();
		_targetZ = readD();
		_originX = readD();
		_originY = readD();
		_originZ = readD();

		try
		{
			_moveMovement = readD(); // 0=keyboard, 1=mouse
		}
		catch (BufferUnderflowException e)
		{
			if (Config.L2WALKER_PROTECTION)
			{
				final Player activeChar = getClient().getActiveChar();
				if (activeChar != null)
					Util.handleIllegalPlayerAction(activeChar, activeChar.getName() + " is trying to use L2Walker.", Config.DEFAULT_PUNISH);
			}
		}
	}

	@Override
	protected void runImpl()
	{
		final Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;

		if (activeChar.isOutOfControl())
		{
			activeChar.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}

		// cancela enchant ao mover
		if (activeChar.getActiveEnchantItem() != null)
		{
			activeChar.setActiveEnchantItem(null);
			activeChar.sendPacket(EnchantResult.CANCELLED);
			activeChar.sendPacket(SystemMessageId.ENCHANT_SCROLL_CANCELLED);
		}

		// se não moveu
		if (_targetX == _originX && _targetY == _originY && _targetZ == _originZ)
		{
			activeChar.sendPacket(new StopMove(activeChar));
			return;
		}

		// Correcting targetZ from floor level to head level
		_targetZ += activeChar.getCollisionHeight();

		// telemode (gm)
		if (activeChar.getTeleMode() > 0)
		{
			if (activeChar.getTeleMode() == 1)
				activeChar.setTeleMode(0);

			activeChar.sendPacket(ActionFailed.STATIC_PACKET);
			activeChar.teleToLocation(_targetX, _targetY, _targetZ, 0);
			return;
		}

		final double dx = _targetX - _originX;
		final double dy = _targetY - _originY;

		// anti-pacote malicioso (distância gigante)
		if ((dx * dx + dy * dy) > MAX_MOVE_SQ)
		{
			activeChar.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}

		// Movimento cancelado se tiver obstáculo colado (teclado) - ant bug
		if (_moveMovement == 0)
		{
			final double distance = Math.sqrt(dx * dx + dy * dy);
			if (distance > 1e-6)
			{
				final double checkDistance = Math.min(175, distance);

				final double normX = dx / distance;
				final double normY = dy / distance;

				final int checkX = _originX + (int) (normX * checkDistance);
				final int checkY = _originY + (int) (normY * checkDistance);

				// Z de referência do servidor (não confia no client)
				final int checkZ = activeChar.getZ();

				if (!GeoEngine.getInstance().canMoveToTarget(_originX, _originY, checkZ, checkX, checkY, checkZ))
				{
					activeChar.sendPacket(ActionFailed.STATIC_PACKET);
					return;
				}
			}
		}

		// =========================
		// LIMBO FIX (L2OFF-like)
		// =========================
		final int sx = activeChar.getX();
		final int sy = activeChar.getY();
		final int serverZ = activeChar.getZ();

		// Evita correção em água/nado (catacumbas/underwater bug)
		final boolean inWater = activeChar.isInsideZone(ZoneId.WATER);

		// Se origin do client está muito longe do server, não usa limbo fix (evita pacote atrasado/exploit)
		final long odx = (long) _originX - sx;
		final long ody = (long) _originY - sy;
		final boolean originFar = (odx * odx + ody * ody) > ORIGIN_DRIFT_MAX_SQ;

		if (!inWater && !originFar)
		{
			// Usa X/Y do servidor para corrigir (nunca do pacote)
			final int geoZ = GeoEngine.getInstance().getHeight(sx, sy, serverZ);
			final int dz = geoZ - serverZ;

			// Só corrige se o geoZ está "um pouco acima" do serverZ e plausível
			if (dz > LIMBO_TRIGGER && dz < LIMBO_MAX_DZ)
			{
				if (!activeChar.canFixLimbo())
					return;

				activeChar.stopMove(null);
				activeChar.sendPacket(ActionFailed.STATIC_PACKET);

				activeChar.teleToLocation(sx, sy, geoZ, 75);
				activeChar.markLimboFixed();
				return;
			}
		}

		// segue movimento normal
		activeChar.getAI().setIntention(CtrlIntention.MOVE_TO, new Location(_targetX, _targetY, _targetZ));
	}
}
