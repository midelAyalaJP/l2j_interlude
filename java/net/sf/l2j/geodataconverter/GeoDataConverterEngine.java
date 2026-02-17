package net.sf.l2j.geodataconverter;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import net.sf.l2j.Config;
import net.sf.l2j.commons.config.ExProperties;
import net.sf.l2j.gameserver.geoengine.geodata.ABlock;
import net.sf.l2j.gameserver.geoengine.geodata.BlockComplex;
import net.sf.l2j.gameserver.geoengine.geodata.BlockFlat;
import net.sf.l2j.gameserver.geoengine.geodata.BlockMultilayer;
import net.sf.l2j.gameserver.geoengine.geodata.GeoFormat;
import net.sf.l2j.gameserver.geoengine.geodata.GeoStructure;
import net.sf.l2j.gameserver.model.L2World;

/**
 * Engine de conversão: sem UI, sem console.
 */
public final class GeoDataConverterEngine
{
	private static final int MAX_TOLERATED_REMAINING_BYTES = 8;
	
	private GeoFormat format;
	private ABlock[][] blocks;
	
	public enum Level
	{
		INFO,
		WARN,
		ERROR
	}
	
	public static final class Result
	{
		public final int converted;
		public final int total;
		public final boolean cancelled;
		
		public Result(int converted, int total, boolean cancelled)
		{
			this.converted = converted;
			this.total = total;
			this.cancelled = cancelled;
		}
	}
	
	public Result convertAll(String geodataPath, GeoFormat format, BiConsumer<Level, String> onLog, BiConsumer<Integer, Integer> onProgress, AtomicBoolean cancelFlag)
	{
		this.format = format;
		
		if (geodataPath == null || geodataPath.isEmpty())
			geodataPath = Config.GEODATA_PATH;
		
		final File root = new File(geodataPath);
		final File inputRoot = (format == GeoFormat.L2J) ? pickExistingOrRoot(root, "l2j") : pickExistingOrRoot(root, "l2off");
		
		final File outputRoot = new File(root, "l2d");
		if (!outputRoot.exists() && !outputRoot.mkdirs())
		{
			onLog.accept(Level.ERROR, "Cannot create output folder: " + outputRoot.getAbsolutePath());
			return new Result(0, 0, false);
		}
		
		final String inputBase = norm(inputRoot.getAbsolutePath());
		final String outputBase = norm(outputRoot.getAbsolutePath());
		
		onLog.accept(Level.INFO, "Starting conversion from " + format);
		onLog.accept(Level.INFO, "Input : " + inputBase);
		onLog.accept(Level.INFO, "Output: " + outputBase);
		
		blocks = new ABlock[GeoStructure.REGION_BLOCKS_X][GeoStructure.REGION_BLOCKS_Y];
		BlockMultilayer.initialize();
		
		final ExProperties props = Config.initProperties(Config.GEOENGINE_FILE);
		
		int total = 0;
		for (int rx = L2World.TILE_X_MIN; rx <= L2World.TILE_X_MAX; rx++)
			for (int ry = L2World.TILE_Y_MIN; ry <= L2World.TILE_Y_MAX; ry++)
				if (props.containsKey(rx + "_" + ry))
					total++;
				
		int done = 0;
		int converted = 0;
		
		for (int rx = L2World.TILE_X_MIN; rx <= L2World.TILE_X_MAX; rx++)
		{
			for (int ry = L2World.TILE_Y_MIN; ry <= L2World.TILE_Y_MAX; ry++)
			{
				if (cancelFlag != null && cancelFlag.get())
				{
					onLog.accept(Level.WARN, "Cancelled by user.");
					BlockMultilayer.release();
					return new Result(converted, total, true);
				}
				
				if (!props.containsKey(rx + "_" + ry))
					continue;
				
				done++;
				onProgress.accept(done, total);
				
				final String input = String.format(format.getFilename(), rx, ry);
				if (!loadGeoBlocks(inputBase, input, onLog))
					continue;
				
				if (!recalculateNswe(onLog))
					continue;
				
				final String output = String.format(GeoFormat.L2D.getFilename(), rx, ry);
				if (!saveGeoBlocks(outputBase, output, onLog))
					continue;

			
				converted++;
				onLog.accept(Level.INFO, "Created " + output);
			}
		}
		
		BlockMultilayer.release();
		onLog.accept(Level.INFO, "Done. Converted " + converted + " region(s).");
		return new Result(converted, total, false);
	}
	
	private boolean loadGeoBlocks(String basePath, String filename, BiConsumer<Level, String> log)
	{
		final File file = new File(basePath + filename);
		
		if (!file.exists())
		{
			log.accept(Level.WARN, "File not found -> " + filename);
			return false;
		}
		
		try (RandomAccessFile raf = new RandomAccessFile(file, "r"); FileChannel fc = raf.getChannel())
		{
			final MappedByteBuffer buffer = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
			buffer.order(ByteOrder.LITTLE_ENDIAN);
			
			if (format == GeoFormat.L2OFF && buffer.remaining() >= 18)
				buffer.position(buffer.position() + 18);
			
			for (int ix = 0; ix < GeoStructure.REGION_BLOCKS_X; ix++)
			{
				for (int iy = 0; iy < GeoStructure.REGION_BLOCKS_Y; iy++)
				{
					if (!buffer.hasRemaining())
						return false;
					
					if (format == GeoFormat.L2J)
					{
						final byte type = buffer.get();
						
						switch (type)
						{
							case GeoStructure.TYPE_FLAT_L2J_L2OFF:
								blocks[ix][iy] = new BlockFlat(buffer, format);
								break;
							case GeoStructure.TYPE_COMPLEX_L2J:
								blocks[ix][iy] = new BlockComplex(buffer, format);
								break;
							case GeoStructure.TYPE_MULTILAYER_L2J:
								blocks[ix][iy] = new BlockMultilayer(buffer, format);
								break;
							default:
								log.accept(Level.ERROR, "Unknown block type: " + type + " in " + filename);
								return false;
						}
					}
					else
					{
						final short type = buffer.getShort();
						
						switch (type)
						{
							case GeoStructure.TYPE_FLAT_L2J_L2OFF:
								blocks[ix][iy] = new BlockFlat(buffer, format);
								break;
							case GeoStructure.TYPE_COMPLEX_L2OFF:
								blocks[ix][iy] = new BlockComplex(buffer, format);
								break;
							default:
								blocks[ix][iy] = new BlockMultilayer(buffer, format);
								break;
						}
					}
				}
			}
			
			final int remaining = buffer.remaining();
			if (remaining > MAX_TOLERATED_REMAINING_BYTES)
			{
				log.accept(Level.WARN, "Possible corruption in " + filename + " (" + remaining + " extra bytes)");
			}
			return true;
		}
		catch (Exception e)
		{
			log.accept(Level.ERROR, "Failed loading " + filename + " -> " + e.getMessage());
			return false;
		}
	}
	
	private boolean recalculateNswe(BiConsumer<Level, String> log)
	{
		try
		{
			for (int x = 0; x < GeoStructure.REGION_CELLS_X; x++)
			{
				for (int y = 0; y < GeoStructure.REGION_CELLS_Y; y++)
				{
					final ABlock block = blocks[x / GeoStructure.BLOCK_CELLS_X][y / GeoStructure.BLOCK_CELLS_Y];
					if (block == null || block instanceof BlockFlat)
						continue;
					
					short height = Short.MAX_VALUE;
					int index;
					
					while ((index = block.getIndexBelow(x, y, height)) != -1)
					{
						height = block.getHeight(index);
						final byte nswe = block.getNswe(index);
						block.setNswe(index, updateNsweBelow(x, y, height, nswe));
					}
				}
			}
			return true;
		}
		catch (Exception e)
		{
			log.accept(Level.ERROR, "NSWE recalculation failed: " + e.getMessage());
			return false;
		}
	}
	
	private byte updateNsweBelow(int x, int y, short z, byte nswe)
	{
		final short height = (short) (z + GeoStructure.CELL_IGNORE_HEIGHT);
		
		final byte nsweN = getNsweBelow(x, y - 1, height);
		final byte nsweS = getNsweBelow(x, y + 1, height);
		final byte nsweW = getNsweBelow(x - 1, y, height);
		final byte nsweE = getNsweBelow(x + 1, y, height);
		
		if (((nswe & GeoStructure.CELL_FLAG_N) != 0 && (nsweN & GeoStructure.CELL_FLAG_W) != 0) || ((nswe & GeoStructure.CELL_FLAG_W) != 0 && (nsweW & GeoStructure.CELL_FLAG_N) != 0))
			nswe |= GeoStructure.CELL_FLAG_NW;
		
		if (((nswe & GeoStructure.CELL_FLAG_N) != 0 && (nsweN & GeoStructure.CELL_FLAG_E) != 0) || ((nswe & GeoStructure.CELL_FLAG_E) != 0 && (nsweE & GeoStructure.CELL_FLAG_N) != 0))
			nswe |= GeoStructure.CELL_FLAG_NE;
		
		if (((nswe & GeoStructure.CELL_FLAG_S) != 0 && (nsweS & GeoStructure.CELL_FLAG_W) != 0) || ((nswe & GeoStructure.CELL_FLAG_W) != 0 && (nsweW & GeoStructure.CELL_FLAG_S) != 0))
			nswe |= GeoStructure.CELL_FLAG_SW;
		
		if (((nswe & GeoStructure.CELL_FLAG_S) != 0 && (nsweS & GeoStructure.CELL_FLAG_E) != 0) || ((nswe & GeoStructure.CELL_FLAG_E) != 0 && (nsweE & GeoStructure.CELL_FLAG_S) != 0))
			nswe |= GeoStructure.CELL_FLAG_SE;
		
		return nswe;
	}
	
	private byte getNsweBelow(int geoX, int geoY, short worldZ)
	{
		if (geoX < 0 || geoX >= GeoStructure.REGION_CELLS_X || geoY < 0 || geoY >= GeoStructure.REGION_CELLS_Y)
			return 0;
		
		final ABlock block = blocks[geoX / GeoStructure.BLOCK_CELLS_X][geoY / GeoStructure.BLOCK_CELLS_Y];
		if (block == null)
			return 0;
		
		final int index = block.getIndexBelow(geoX, geoY, worldZ);
		return index == -1 ? 0 : block.getNswe(index);
	}
	
	private boolean saveGeoBlocks(String basePath, String filename, BiConsumer<Level, String> log)
	{
		try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(basePath + filename)))
		{
			for (int ix = 0; ix < GeoStructure.REGION_BLOCKS_X; ix++)
				for (int iy = 0; iy < GeoStructure.REGION_BLOCKS_Y; iy++)
					if (blocks[ix][iy] != null)
						blocks[ix][iy].saveBlock(bos);
					
			bos.flush();
			return true;
		}
		catch (Exception e)
		{
			log.accept(Level.ERROR, "Save failed for " + filename + " -> " + e.getMessage());
			return false;
		}
	}
	
	private static String norm(String p)
	{
		if (p == null || p.trim().isEmpty())
			p = ".";
		if (!p.endsWith(File.separator))
			p += File.separator;
		return p;
	}
	
	private static File pickExistingOrRoot(File root, String child)
	{
		File d = new File(root, child);
		return d.isDirectory() ? d : root;
	}
	
	
}
