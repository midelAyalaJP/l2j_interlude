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

	    // telemode (gm)
	    if (activeChar.getTeleMode() > 0)
	    {
	        if (activeChar.getTeleMode() == 1)
	            activeChar.setTeleMode(0);

	        activeChar.sendPacket(ActionFailed.STATIC_PACKET);
	        activeChar.teleToLocation(_targetX, _targetY, _targetZ, 0);
	        return;
	    }

	    // =========================
	    // Anti packet malicioso (usa origin do client só pra check grosso)
	    // =========================
	    final long pdx = (long) _targetX - _originX;
	    final long pdy = (long) _targetY - _originY;
	    if ((pdx * pdx + pdy * pdy) > MAX_MOVE_SQ)
	    {
	        activeChar.sendPacket(ActionFailed.STATIC_PACKET);
	        return;
	    }

	    // =========================
	    // Server origin
	    // =========================
	    final int sx = activeChar.getX();
	    final int sy = activeChar.getY();
	    final int sz = activeChar.getZ();

	    // água (ou nado)
	    final boolean inWater = activeChar.isInsideZone(ZoneId.WATER);

	    // Se origin do client está muito longe do server, ignora origin client para anti-stuck
	    final long odx = (long) _originX - sx;
	    final long ody = (long) _originY - sy;
	    final boolean originFar = (odx * odx + ody * ody) > ORIGIN_DRIFT_MAX_SQ;

	    // =========================
	    // Anti-stuck teclado (opcional): só fora d'água e se origin client não for suspeito
	    // =========================
	    if (!inWater && !originFar && _moveMovement == 0)
	    {
	        final double dx = _targetX - _originX;
	        final double dy = _targetY - _originY;
	        final double dist = Math.sqrt(dx * dx + dy * dy);

	        if (dist > 1e-6)
	        {
	            final double checkDistance = Math.min(175.0, dist);
	            final double nx = dx / dist;
	            final double ny = dy / dist;

	            final int checkX = _originX + (int) (nx * checkDistance);
	            final int checkY = _originY + (int) (ny * checkDistance);

	            // usa Z coerente (geo no ponto do server)
	            final int sGeoZ = GeoEngine.getInstance().getHeight(sx, sy, sz);

	            if (!GeoEngine.getInstance().canMoveToTarget(_originX, _originY, sGeoZ, checkX, checkY, sGeoZ))
	            {
	                // aqui NÃO trava geral: apenas ignora o anti-stuck e segue para o pipeline normal
	                // (geodata imperfeita pode dar false negative)
	            }
	        }
	    }

	    // =========================
	    // PIPELINE DE MOVIMENTO
	    // =========================

	    // 1) Água: sempre soft (geo não é confiável)
	    if (inWater)
	    {
	        // Em água, o client controla o Z (subir/descer). Não fixe no Z do servidor.
	        int tz = _targetZ;

	        // clamp anti-exploit (ajuste fino)
	        final int dz = tz - sz;
	        if (dz > 1200) tz = sz + 1200;
	        if (dz < -1200) tz = sz - 1200;

	        activeChar.getAI().setIntention(CtrlIntention.MOVE_TO, new Location(_targetX, _targetY, tz));
	        return;
	    }


	    // 2) Chão: tenta “hard geo” primeiro (com clamp)
	    final int sGeoZ = GeoEngine.getInstance().getHeight(sx, sy, sz);

	    int tx = _targetX;
	    int ty = _targetY;
	    int tz = GeoEngine.getInstance().getHeight(tx, ty, sGeoZ);

	    // Se dá pra ir direto: usa.
	    if (GeoEngine.getInstance().canMoveToTarget(sx, sy, sGeoZ, tx, ty, tz))
	    {
	        activeChar.getAI().setIntention(CtrlIntention.MOVE_TO, new Location(tx, ty, tz));
	        return;
	    }

	    // Clamp: tenta achar um ponto alcançável no caminho (evita travar em muros/montanhas)
	    int cx = tx;
	    int cy = ty;
	    boolean found = false;

	    // Iterações: 12 = padrão bom; se quiser, pode virar constante.
	    for (int i = 0; i < 12; i++)
	    {
	        cx = (sx + cx) >> 1;
	        cy = (sy + cy) >> 1;
	        int cz = GeoEngine.getInstance().getHeight(cx, cy, sGeoZ);

	        if (GeoEngine.getInstance().canMoveToTarget(sx, sy, sGeoZ, cx, cy, cz))
	        {
	            tx = cx;
	            ty = cy;
	            tz = cz;
	            found = true;
	            break;
	        }
	    }

	    if (found)
	    {
	        activeChar.getAI().setIntention(CtrlIntention.MOVE_TO, new Location(tx, ty, tz));
	        return;
	    }

	    // 3) Fallback “soft” (geodata imperfeita):
	    // Só permite se for um movimento curto e razoável (para não liberar wallhack)
	    final long sdx = (long) _targetX - sx;
	    final long sdy = (long) _targetY - sy;
	    final long distSq = (sdx * sdx + sdy * sdy);

	    // limite seguro: 600~900 é um bom range. Eu recomendo 750.
	    final int SOFT_MAX_DIST = 750;
	    final long SOFT_MAX_DIST_SQ = (long) SOFT_MAX_DIST * SOFT_MAX_DIST;

	    if (distSq <= SOFT_MAX_DIST_SQ)
	    {
	        // move com Z do geo do server (não do client), pra não “subir parede”
	        activeChar.getAI().setIntention(CtrlIntention.MOVE_TO, new Location(_targetX, _targetY, sGeoZ));
	        return;
	    }

	    // muito longe e geo bloqueou: aí sim para
	    activeChar.sendPacket(ActionFailed.STATIC_PACKET);
	    activeChar.sendPacket(new StopMove(activeChar));
	}

}
