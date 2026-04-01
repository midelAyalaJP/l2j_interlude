package net.sf.l2j.protection.hwid;

import java.security.MessageDigest;

public class HwidFingerprint
{
    public static String generateHash(String raw)
    {
        try
        {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes("UTF-8"));

            StringBuilder hex = new StringBuilder();
            for (byte b : hash)
                hex.append(String.format("%02x", b));

            return hex.toString();
        }
        catch (Exception e)
        {
            throw new RuntimeException("HWID hash error", e);
        }
    }
}