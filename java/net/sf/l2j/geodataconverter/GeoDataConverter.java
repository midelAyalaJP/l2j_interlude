package net.sf.l2j.geodataconverter;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Scanner;

import net.sf.l2j.Config;
import net.sf.l2j.commons.config.ExProperties;
import net.sf.l2j.gameserver.geoengine.geodata.ABlock;
import net.sf.l2j.gameserver.geoengine.geodata.BlockComplex;
import net.sf.l2j.gameserver.geoengine.geodata.BlockFlat;
import net.sf.l2j.gameserver.geoengine.geodata.BlockMultilayer;
import net.sf.l2j.gameserver.geoengine.geodata.GeoFormat;
import net.sf.l2j.gameserver.geoengine.geodata.GeoStructure;
import net.sf.l2j.gameserver.model.L2World;

public final class GeoDataConverter
{
	private static GeoFormat _format;
	private static ABlock[][] _blocks;

	private static final int MAX_TOLERATED_REMAINING_BYTES = 8;

	public static void main(String[] args)
	{
		Config.loadGeodataConverter();

		String type = "";
		try (Scanner scn = new Scanner(System.in))
		{
			while (!(type.equalsIgnoreCase("J") || type.equalsIgnoreCase("O") || type.equalsIgnoreCase("E")))
			{
				System.out.println("GeoDataConverter: Select source geodata type:");
				System.out.println("  J: L2J");
				System.out.println("  O: L2OFF");
				System.out.println("  E: Exit");
				System.out.print("Choice: ");
				type = scn.next();
			}
		}

		if (type.equalsIgnoreCase("E"))
			System.exit(0);

		_format = type.equalsIgnoreCase("J") ? GeoFormat.L2J : GeoFormat.L2OFF;

		System.out.println("GeoDataConverter: Starting conversion from " + _format);

		_blocks = new ABlock[GeoStructure.REGION_BLOCKS_X][GeoStructure.REGION_BLOCKS_Y];
		BlockMultilayer.initialize();

		final ExProperties props = Config.initProperties(Config.GEOENGINE_FILE);
		int converted = 0;

		for (int rx = L2World.TILE_X_MIN; rx <= L2World.TILE_X_MAX; rx++)
		{
			for (int ry = L2World.TILE_Y_MIN; ry <= L2World.TILE_Y_MAX; ry++)
			{
				if (!props.containsKey(rx + "_" + ry))
					continue;

				final String input = String.format(_format.getFilename(), rx, ry);
				if (!loadGeoBlocks(input))
					continue;

				if (!recalculateNswe())
					continue;

				final String output = String.format(GeoFormat.L2D.getFilename(), rx, ry);
				if (!saveGeoBlocks(output))
					continue;

				converted++;
				System.out.println("GeoDataConverter: Created " + output);
			}
		}

		System.out.println("GeoDataConverter: Successfully converted " + converted + " region(s).");
		BlockMultilayer.release();
	}

	private static boolean loadGeoBlocks(String filename)
	{
		File file = new File(Config.GEODATA_PATH + filename);

		if (!file.exists())
		{
			System.out.println("GeoDataConverter: File not found -> " + filename);
			return false;
		}

		try (RandomAccessFile raf = new RandomAccessFile(file, "r");
			 FileChannel fc = raf.getChannel())
		{
			MappedByteBuffer buffer = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
			buffer.order(ByteOrder.LITTLE_ENDIAN);

			if (_format == GeoFormat.L2OFF && buffer.remaining() >= 18)
				buffer.position(buffer.position() + 18);

			for (int ix = 0; ix < GeoStructure.REGION_BLOCKS_X; ix++)
			{
				for (int iy = 0; iy < GeoStructure.REGION_BLOCKS_Y; iy++)
				{
					if (!buffer.hasRemaining())
						return false;

					if (_format == GeoFormat.L2J)
					{
						byte type = buffer.get();

						switch (type)
						{
							case GeoStructure.TYPE_FLAT_L2J_L2OFF:
								_blocks[ix][iy] = new BlockFlat(buffer, _format);
								break;
							case GeoStructure.TYPE_COMPLEX_L2J:
								_blocks[ix][iy] = new BlockComplex(buffer, _format);
								break;
							case GeoStructure.TYPE_MULTILAYER_L2J:
								_blocks[ix][iy] = new BlockMultilayer(buffer, _format);
								break;
							default:
								System.out.println("Unknown block type: " + type + " in " + filename);
								return false;
						}
					}
					else
					{
						short type = buffer.getShort();

						switch (type)
						{
							case GeoStructure.TYPE_FLAT_L2J_L2OFF:
								_blocks[ix][iy] = new BlockFlat(buffer, _format);
								break;
							case GeoStructure.TYPE_COMPLEX_L2OFF:
								_blocks[ix][iy] = new BlockComplex(buffer, _format);
								break;
							default:
								_blocks[ix][iy] = new BlockMultilayer(buffer, _format);
								break;
						}
					}
				}
			}

			int remaining = buffer.remaining();
			if (remaining > MAX_TOLERATED_REMAINING_BYTES)
			{
				System.out.println("GeoDataConverter: Possible corruption in " + filename +
						" (" + remaining + " extra bytes)");
			}

			return true;
		}
		catch (Exception e)
		{
			System.out.println("GeoDataConverter: Failed loading " + filename + " -> " + e.getMessage());
			return false;
		}
	}

	private static boolean recalculateNswe()
	{
		try
		{
			for (int x = 0; x < GeoStructure.REGION_CELLS_X; x++)
			{
				for (int y = 0; y < GeoStructure.REGION_CELLS_Y; y++)
				{
					ABlock block = _blocks[x / GeoStructure.BLOCK_CELLS_X][y / GeoStructure.BLOCK_CELLS_Y];
					if (block == null || block instanceof BlockFlat)
						continue;

					short height = Short.MAX_VALUE;
					int index;

					while ((index = block.getIndexBelow(x, y, height)) != -1)
					{
						height = block.getHeight(index);
						byte nswe = block.getNswe(index);
						block.setNswe(index, updateNsweBelow(x, y, height, nswe));
					}
				}
			}
			return true;
		}
		catch (Exception e)
		{
			System.out.println("NSWE recalculation failed: " + e.getMessage());
			return false;
		}
	}

	private static byte updateNsweBelow(int x, int y, short z, byte nswe)
	{
		short height = (short)(z + GeoStructure.CELL_IGNORE_HEIGHT);

		byte nsweN = getNsweBelow(x, y - 1, height);
		byte nsweS = getNsweBelow(x, y + 1, height);
		byte nsweW = getNsweBelow(x - 1, y, height);
		byte nsweE = getNsweBelow(x + 1, y, height);

		if (((nswe & GeoStructure.CELL_FLAG_N) != 0 && (nsweN & GeoStructure.CELL_FLAG_W) != 0) ||
			((nswe & GeoStructure.CELL_FLAG_W) != 0 && (nsweW & GeoStructure.CELL_FLAG_N) != 0))
			nswe |= GeoStructure.CELL_FLAG_NW;

		if (((nswe & GeoStructure.CELL_FLAG_N) != 0 && (nsweN & GeoStructure.CELL_FLAG_E) != 0) ||
			((nswe & GeoStructure.CELL_FLAG_E) != 0 && (nsweE & GeoStructure.CELL_FLAG_N) != 0))
			nswe |= GeoStructure.CELL_FLAG_NE;

		if (((nswe & GeoStructure.CELL_FLAG_S) != 0 && (nsweS & GeoStructure.CELL_FLAG_W) != 0) ||
			((nswe & GeoStructure.CELL_FLAG_W) != 0 && (nsweW & GeoStructure.CELL_FLAG_S) != 0))
			nswe |= GeoStructure.CELL_FLAG_SW;

		if (((nswe & GeoStructure.CELL_FLAG_S) != 0 && (nsweS & GeoStructure.CELL_FLAG_E) != 0) ||
			((nswe & GeoStructure.CELL_FLAG_E) != 0 && (nsweE & GeoStructure.CELL_FLAG_S) != 0))
			nswe |= GeoStructure.CELL_FLAG_SE;

		return nswe;
	}

	private static byte getNsweBelow(int geoX, int geoY, short worldZ)
	{
		if (geoX < 0 || geoX >= GeoStructure.REGION_CELLS_X ||
			geoY < 0 || geoY >= GeoStructure.REGION_CELLS_Y)
			return 0;

		ABlock block = _blocks[geoX / GeoStructure.BLOCK_CELLS_X][geoY / GeoStructure.BLOCK_CELLS_Y];
		if (block == null)
			return 0;

		int index = block.getIndexBelow(geoX, geoY, worldZ);
		return index == -1 ? 0 : block.getNswe(index);
	}

	private static boolean saveGeoBlocks(String filename)
	{
		try (BufferedOutputStream bos = new BufferedOutputStream(
				new FileOutputStream(Config.GEODATA_PATH + filename)))
		{
			for (int ix = 0; ix < GeoStructure.REGION_BLOCKS_X; ix++)
				for (int iy = 0; iy < GeoStructure.REGION_BLOCKS_Y; iy++)
					if (_blocks[ix][iy] != null)
						_blocks[ix][iy].saveBlock(bos);

			bos.flush();
			return true;
		}
		catch (Exception e)
		{
			System.out.println("Save failed for " + filename + " -> " + e.getMessage());
			return false;
		}
	}
}
