package net.sf.l2j.hwid.hwidmanager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import net.sf.l2j.gameserver.ConnectionPool;
import net.sf.l2j.gameserver.network.L2GameClient;

public class HWIDManager
{
	protected static Logger _log = Logger.getLogger(HWIDManager.class.getName());
	private static HWIDManager _instance;
	public static Map<Integer, HWIDInfoList> _listHWID;
	
	public HWIDManager()
	{
		_listHWID = new HashMap<>();
		load();
		System.out.println("- HWID Info: Loaded " + _listHWID.size() + " HWIDs");
	}
	
	public static HWIDManager getInstance()
	{
		if (_instance == null)
		{
			_instance = new HWIDManager();
		}
		return _instance;
	}
	
	private static void load()
	{
		final String sql = "SELECT HWID, WindowsCount, Account, PlayerID, LockType FROM hwid_info";
		
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(sql); ResultSet rset = ps.executeQuery())
		{
			int counterHWIDInfo = 0;
			
			while (rset.next())
			{
				HWIDInfoList hInfo = new HWIDInfoList(counterHWIDInfo);
				hInfo.setHwids(rset.getString("HWID"));
				hInfo.setCount(rset.getInt("WindowsCount"));
				hInfo.setLogin(rset.getString("Account"));
				hInfo.setPlayerID(rset.getInt("PlayerID"));
				
				String lockType = rset.getString("LockType");
				if (lockType != null)
				{
					hInfo.setLockType(HWIDInfoList.LockType.valueOf(lockType));
				}
				
				_listHWID.put(counterHWIDInfo, hInfo);
				counterHWIDInfo++;
			}
		}
		catch (Exception e)
		{
			_log.info("Erro ao carregar hwid_info" + e);
		}
	}
	
	public static void reload()
	{
		_instance = new HWIDManager();
	}
	
	public static void updateHWIDInfo(L2GameClient client, int windowscount)
	{
		updateHWIDInfo(client, windowscount, HWIDInfoList.LockType.NONE);
	}
	
	public static void updateHWIDInfo(L2GameClient client, int windowsCount, HWIDInfoList.LockType lockType)
	{
		int counterHwidInfo = _listHWID.size();
		boolean isFound = false;
		
		for (int i = 0; i < _listHWID.size(); i++)
		{
			if (!_listHWID.get(Integer.valueOf(i)).getHWID().equals(client.getHWID()))
				continue;
			
			isFound = true;
			counterHwidInfo = i;
			break;
		}
		
		HWIDInfoList hInfo = new HWIDInfoList(counterHwidInfo);
		hInfo.setHwids(client.getHWID());
		hInfo.setCount(windowsCount);
		hInfo.setLogin(client.getAccountName());
		hInfo.setPlayerID(client.getPlayerId());
		hInfo.setLockType(lockType);
		_listHWID.put(Integer.valueOf(counterHwidInfo), hInfo);
		
		if (isFound)
		{
			try
			{
				Connection con = ConnectionPool.getConnection();
				Throwable localThrowable3 = null;
				try
				{
					PreparedStatement statement = con.prepareStatement("UPDATE hwid_info SET WindowsCount=?,Account=?,PlayerID=?,LockType=? WHERE HWID=?");
					statement.setInt(1, windowsCount);
					statement.setString(2, client.getAccountName());
					statement.setInt(3, client.getPlayerId());
					statement.setString(4, lockType.toString());
					statement.setString(5, client.getHWID());
					statement.execute();
				}
				catch (Throwable localThrowable1)
				{
					localThrowable3 = localThrowable1;
					throw localThrowable1;
				}
				finally
				{
					if (con != null)
					{
						if (localThrowable3 != null)
						{
							try
							{
								con.close();
							}
							catch (Throwable x2)
							{
								localThrowable3.addSuppressed(x2);
							}
						}
						else
						{
							con.close();
						}
					}
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		else
		{
			try
			{
				Connection con = ConnectionPool.getConnection();
				Throwable localThrowable3 = null;
				try
				{
					PreparedStatement statement = con.prepareStatement("INSERT INTO hwid_info (HWID, WindowsCount, Account, PlayerID, LockType) values (?,?,?,?,?)");
					statement.setString(1, client.getHWID());
					statement.setInt(2, windowsCount);
					statement.setString(3, client.getAccountName());
					statement.setInt(4, client.getPlayerId());
					statement.setString(5, lockType.toString());
					statement.execute();
				}
				catch (Throwable localThrowable2)
				{
					localThrowable3 = localThrowable2;
					throw localThrowable2;
				}
				finally
				{
					if (con != null)
					{
						if (localThrowable3 != null)
						{
							try
							{
								con.close();
							}
							catch (Throwable x2)
							{
								localThrowable3.addSuppressed(x2);
							}
						}
						else
						{
							con.close();
						}
					}
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}
	
	public static void updateHWIDInfo(L2GameClient client, HWIDInfoList.LockType lockType)
	{
		updateHWIDInfo(client, 1, lockType);
	}
	
	public static boolean checkLockedHWID(L2GameClient client)
	{
		if (_listHWID.size() == 0)
			return false;
		
		boolean result = false;
		
		for (int i = 0; i < _listHWID.size(); i++)
		{
			switch (_listHWID.get(Integer.valueOf(i)).getLockType().ordinal())
			{
				case 1:
					break;
				case 2:
					if ((client.getPlayerId() == 0) || (_listHWID.get(Integer.valueOf(i)).getPlayerID() != client.getPlayerId()))
						continue;
					
					if (_listHWID.get(Integer.valueOf(i)).getHWID().equals(client.getHWID()))
						return false;
					
					result = true;
					break;
				case 3:
					if (!_listHWID.get(Integer.valueOf(i)).getLogin().equals(client.getLoginName()))
						continue;
					
					if (_listHWID.get(Integer.valueOf(i)).getHWID().equals(client.getHWID()))
						return false;
					
					result = true;
			}
			
		}
		
		return result;
	}
	
	public static int getAllowedWindowsCount(L2GameClient client)
	{
		if (_listHWID.size() == 0)
			return -1;
		
		for (int i = 0; i < _listHWID.size(); i++)
		{
			if (!_listHWID.get(Integer.valueOf(i)).getHWID().equals(client.getHWID()))
				continue;
			
			if (_listHWID.get(Integer.valueOf(i)).getHWID().equals(""))
				return -1;
			
			return _listHWID.get(Integer.valueOf(i)).getCount();
		}
		
		return -1;
	}
	
	public static int getCountHwidInfo()
	{
		return _listHWID.size();
	}
}