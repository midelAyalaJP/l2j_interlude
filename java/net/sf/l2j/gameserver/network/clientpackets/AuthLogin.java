package net.sf.l2j.gameserver.network.clientpackets;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.LoginServerThread;
import net.sf.l2j.gameserver.LoginServerThread.SessionKey;
import net.sf.l2j.gameserver.network.L2GameClient;

public final class AuthLogin extends L2GameClientPacket
{
	private String _loginName;
	private int _playKey1;
	private int _playKey2;
	private int _loginKey1;
	private int _loginKey2;
	 
	
	@Override
	protected void readImpl()
	{
		_loginName = readS().toLowerCase();
		_playKey2 = readD();
		_playKey1 = readD();
		_loginKey1 = readD();
		_loginKey2 = readD();
 
	}
	
	@Override
	protected void runImpl()
	{
	    final L2GameClient client = getClient();

	    final SessionKey key = new SessionKey(_loginKey1, _loginKey2, _playKey1, _playKey2);

	    if (Config.DEBUG)
	        _log.info("DEBUG " + getType() + ": user: " + _loginName + " key:" + key);

	    if (client.getAccountName() == null)
	    {
	        if (LoginServerThread.getInstance().addGameServerLogin(_loginName, client))
	        {
	            client.setAccountName(_loginName);
	            LoginServerThread.getInstance().addWaitingClientAndSendRequest(_loginName, client, key);
	        }
	        else
	        {
	            client.closeNow();
	        }
	    }
	}
}