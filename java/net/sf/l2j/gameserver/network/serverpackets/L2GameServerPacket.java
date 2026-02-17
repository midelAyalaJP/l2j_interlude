package net.sf.l2j.gameserver.network.serverpackets;

import java.util.logging.Logger;

import net.sf.l2j.Config;
import net.sf.l2j.commons.mmocore.SendablePacket;
import net.sf.l2j.gameserver.network.L2GameClient;

public abstract class L2GameServerPacket extends SendablePacket<L2GameClient>
{
	protected static final Logger _log = Logger.getLogger(L2GameServerPacket.class.getName());
	
	@Override
	protected void write()
	{
		if (Config.PACKET_HANDLER_DEBUG)
			_log.info(getType());
		
		try
		{
			writeImpl();
		}
		catch (Throwable t)
		{
			_log.severe("Client: " + getClient().toString() + " - Failed writing: " + getType());
			t.printStackTrace();
		}
	}
	
	public void runImpl()
	{
	}
	
	protected abstract void writeImpl();
	
	public String getType()
	{
		return "[S] " + getClass().getSimpleName();
	}
}