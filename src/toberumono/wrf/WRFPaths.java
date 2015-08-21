package toberumono.wrf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import toberumono.utils.general.MutedLogger;

/**
 * A container for the {@link Path Paths} that {@link WRFRunner} and {@link Simulation} need to pass around.
 * 
 * @author Toberumono
 */
public class WRFPaths extends HashMap<String, Path> {
	protected final boolean create;
	protected final Logger log;
	
	/**
	 * The timestamped working directory
	 */
	public final Path root;
	/**
	 * The directory into which the output files are moved
	 */
	public final Path output;
	
	/**
	 * Creates a {@link WRFPaths} with the given root and output directories.
	 * 
	 * @param root
	 *            a {@link Path} to the root working directory.
	 * @param output
	 *            a {@link Path} to the output directory
	 * @param create
	 *            if {@code true}, this will call {@link Files#createDirectories} for each {@link Path}
	 * @param log
	 *            the {@link Logger} to use in the {@link WRFPaths} operations
	 * @throws IOException
	 *             if a directory could not be created
	 * @throws NullPointerException
	 *             if <tt>root</tt> is {@code null}
	 */
	public WRFPaths(Path root, Path output, boolean create, Logger log) throws IOException {
		if (root == null)
			throw new NullPointerException("The root path cannot be null.");
		if (log == null)
			log = MutedLogger.getMutedLogger();
		this.log = log;
		this.create = create;
		this.root = root.toAbsolutePath().normalize();
		this.output = root.resolve(output).toAbsolutePath().normalize();
	}
	
	/**
	 * Creates a {@link WRFPaths} by resolving all of the {@link Path Paths} against <tt>root</tt>.<br>
	 * <b>NOTE:</b> this does <i>not</i> preclude the wrf, wps, grib, and output paths from being absolute.
	 * 
	 * @param root
	 *            a {@link Path} to the root working directory.
	 * @param wrf
	 *            a {@link Path} to the WRFV3 working directory
	 * @param wps
	 *            a {@link Path} to the WPS working directory
	 * @param grib
	 *            a {@link Path} to the grib working directory
	 * @param output
	 *            a {@link Path} to the output directory
	 * @param create
	 *            if {@code true}, this will call {@link Files#createDirectories} for each {@link Path}
	 * @param log
	 *            the {@link Logger} to use in the {@link WRFPaths} operations
	 * @throws IOException
	 *             if a directory could not be created
	 * @throws NullPointerException
	 *             if any {@link Path} is {@code null}
	 */
	public WRFPaths(Path root, Path wrf, Path wps, Path grib, Path output, boolean create, Logger log) throws IOException {
		if (root == null)
			throw new NullPointerException("The root path cannot be null.");
		if (log == null)
			log = MutedLogger.getMutedLogger();
		this.log = log;
		this.create = create;
		this.root = root.toAbsolutePath().normalize();
		this.output = root.resolve(output).toAbsolutePath().normalize();
		if (create) {
			Files.createDirectories(this.root);
			Files.createDirectories(this.output);
		}
		put("wrf", root.resolve(wrf).toAbsolutePath().normalize());
		put("wrf.run", get("wrf").resolve("run"));
		put("wps", root.resolve(wps).toAbsolutePath().normalize());
		put("grib", root.resolve(grib).toAbsolutePath().normalize());
	}
	
	@Override
	public Path put(String key, Path value) {
		if (create)
			try {
				Files.createDirectories(value);
			}
			catch (IOException e) {
				log.log(Level.SEVERE, "Unable to create " + value.toString(), e);
			}
		return super.put(key, value);
	}
	
	@Override
	public void putAll(Map<? extends String, ? extends Path> m) {
		for (Map.Entry<? extends String, ? extends Path> e : m.entrySet())
			put(e.getKey(), e.getValue());
	}
}
