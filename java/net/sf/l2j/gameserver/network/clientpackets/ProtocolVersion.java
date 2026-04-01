package net.sf.l2j.gameserver.network.clientpackets;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import net.sf.l2j.gameserver.network.L2GameClient;
import net.sf.l2j.gameserver.network.serverpackets.KeyPacket;
import net.sf.l2j.gameserver.network.serverpackets.L2GameServerPacket;
import net.sf.l2j.protection.hwid.HwidManager;

public final class ProtocolVersion extends L2GameClientPacket
{
    private static final Logger LOGGER = Logger.getLogger(ProtocolVersion.class.getName());

    private static final byte[] HWID_MAGIC =
    {
        'B', 'H', 'W', 'D'
    };

    private int _version;
    private byte[] _extraData;

    @Override
    protected void readImpl()
    {
        _version = readD();

        if (_buf.remaining() > 0)
        {
            _extraData = new byte[_buf.remaining()];
            readB(_extraData);
        }
    }

    @Override
    protected void runImpl()
    {
        final L2GameClient client = getClient();

        switch (_version)
        {
            case 737:
            case 740:
            case 744:
            case 746:
            {
                final String payload = extractPayloadFromExtra(_extraData);
                if (payload == null || payload.isEmpty())
                {
                    LOGGER.warning("HWID payload nao encontrado no ProtocolVersion.");
                    client.close((L2GameServerPacket) null);
                    return;
                }

                String[] parts = payload.split("\\|");

                String cpu = parts[0];
                String hdd = parts[1];
                String mac = parts[2];
                String key = parts[3];

                final boolean ok = HwidManager.getInstance().validateClient(client, hdd, mac, cpu, key);

                if (!ok)
                {
                    LOGGER.warning("HWID INVALIDO - CONEXAO BLOQUEADA");
                    client.close((L2GameServerPacket) null);
                    return;
                }

                // libera se for válido
                client.setHwidAuthed(true);
                client.sendPacket(new KeyPacket(client.enableCrypt()));

                break;
            }

            default:
            {
        
                client.close((L2GameServerPacket) null);
                break;
            }
        }
    }

    private static String extractPayloadFromExtra(byte[] extra)
    {
        if (extra == null || extra.length == 0)
            return null;

        final int start = indexOf(extra, HWID_MAGIC);
        if (start < 0)
            return null;

        final int lenPos = start + 4;
        if (extra.length < lenPos + 4)
            return null;

        final ByteBuffer lenBuffer = ByteBuffer.wrap(extra, lenPos, 4).order(ByteOrder.LITTLE_ENDIAN);
        final int payloadLen = lenBuffer.getInt();

        if (payloadLen <= 0)
            return null;

        final int payloadStart = lenPos + 4;
        if (payloadStart + payloadLen > extra.length)
            return null;

        int realLen = payloadLen;
        if (extra[payloadStart + payloadLen - 1] == 0)
            realLen--;

        if (realLen <= 0)
            return null;

        return new String(extra, payloadStart, realLen, StandardCharsets.US_ASCII).trim();
    }

    private static int indexOf(byte[] data, byte[] pattern)
    {
        if (data == null || pattern == null || pattern.length == 0 || data.length < pattern.length)
            return -1;

        outer:
        for (int i = 0; i <= data.length - pattern.length; i++)
        {
            for (int j = 0; j < pattern.length; j++)
            {
                if (data[i + j] != pattern[j])
                    continue outer;
            }
            return i;
        }
        return -1;
    }
}