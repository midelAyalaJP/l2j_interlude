package net.sf.l2j.protection.hwid;

public class HwidSession
{
	private final int _deviceId;
	private final String _cpu;
	private final String _hdd;
	private final String _mac;
	private final long _loginTime;
	
	public HwidSession(int deviceId, String cpu, String hdd, String mac)
	{
		_deviceId = deviceId;
		_cpu = cpu;
		_hdd = hdd;
		_mac = mac;
		_loginTime = System.currentTimeMillis();
	}
	
	public int getDeviceId()
	{
		return _deviceId;
	}
	
	public String getCpu()
	{
		return _cpu;
	}
	
	public String getHdd()
	{
		return _hdd;
	}
	
	public String getMac()
	{
		return _mac;
	}
	
	public long getLoginTime()
	{
		return _loginTime;
	}
}