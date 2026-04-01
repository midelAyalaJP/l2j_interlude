package net.sf.l2j.protection.hwid;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import net.sf.l2j.gameserver.ConnectionPool;
import net.sf.l2j.gameserver.model.actor.Player;

public class HwidDAO
{
	public int getOrCreateDevice(String cpu, String hdd, String mac)
	{
		try (Connection con = ConnectionPool.getConnection())
		{
			PreparedStatement ps = con.prepareStatement("SELECT id FROM hwid_devices WHERE cpu=? AND hdd=? AND mac=?");
			
			ps.setString(1, cpu);
			ps.setString(2, hdd);
			ps.setString(3, mac);
			
			ResultSet rs = ps.executeQuery();
			if (rs.next())
				return rs.getInt("id");
			
			ps = con.prepareStatement("INSERT INTO hwid_devices (cpu, hdd, mac, first_seen, last_seen) VALUES (?, ?, ?, NOW(), NOW())", PreparedStatement.RETURN_GENERATED_KEYS);
			
			ps.setString(1, cpu);
			ps.setString(2, hdd);
			ps.setString(3, mac);
			
			ps.executeUpdate();
			
			rs = ps.getGeneratedKeys();
			if (rs.next())
				return rs.getInt(1);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		
		return -1;
	}
	
	public boolean isBanned(int deviceId)
	{
		try (Connection con = ConnectionPool.getConnection())
		{
			PreparedStatement ps = con.prepareStatement("SELECT banned FROM hwid_devices WHERE id=?");
			ps.setInt(1, deviceId);
			
			ResultSet rs = ps.executeQuery();
			if (rs.next())
				return rs.getBoolean("banned");
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		
		return false;
	}
	
	public void updateLastSeen(int deviceId)
	{
		try (Connection con = ConnectionPool.getConnection())
		{
			PreparedStatement ps = con.prepareStatement("UPDATE hwid_devices SET last_seen=NOW() WHERE id=?");
			ps.setInt(1, deviceId);
			ps.executeUpdate();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public void linkAccount(String account, int deviceId)
	{
		try (Connection con = ConnectionPool.getConnection())
		{
			PreparedStatement ps = con.prepareStatement("INSERT INTO hwid_accounts (account_name, device_id, first_seen, last_seen) " + "VALUES (?, ?, NOW(), NOW()) " + "ON DUPLICATE KEY UPDATE last_seen=NOW()");
			
			ps.setString(1, account);
			ps.setInt(2, deviceId);
			ps.executeUpdate();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public void createSession(String account, int deviceId, String ip, String token)
	{
		try (Connection con = ConnectionPool.getConnection())
		{
			PreparedStatement ps = con.prepareStatement("INSERT INTO hwid_sessions (account_name, device_id, ip_address, token, login_time, last_heartbeat) " + "VALUES (?, ?, ?, ?, NOW(), NOW())");
			
			ps.setString(1, account);
			ps.setInt(2, deviceId);
			ps.setString(3, ip);
			ps.setString(4, token);
			
			ps.executeUpdate();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public void deactivateOldSessions(String account)
	{
		try (Connection con = ConnectionPool.getConnection())
		{
			PreparedStatement ps = con.prepareStatement("UPDATE hwid_sessions SET active=0 WHERE account_name=? AND active=1");
			
			ps.setString(1, account);
			ps.executeUpdate();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	public void restartAndDisconnetion(Player player)
	{
		try (Connection con = ConnectionPool.getConnection())
		{
			PreparedStatement ps = con.prepareStatement("UPDATE hwid_sessions SET active=0 WHERE account_name=? AND active=1");
			
			ps.setString(1, player.getAccountName());
			ps.executeUpdate();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public int countActiveSessionsByHWID(int deviceId)
	{
		try (Connection con = ConnectionPool.getConnection())
		{
			PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM hwid_sessions " + "WHERE device_id=? AND active=1 " + "AND last_heartbeat > NOW() - INTERVAL 60 SECOND");
			
			ps.setInt(1, deviceId);
			
			ResultSet rs = ps.executeQuery();
			if (rs.next())
				return rs.getInt(1);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		
		
		
		return 0;
	}
}