package toberumono.wrf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A container for the {@link Path Paths} that {@link WRFRunner} and {@link Simulation} need to pass around.
 * 
 * @author Toberumono
 */
public class WRFPaths {
	/**
	 * The timestamped working directory
	 */
	public final Path root;
	/**
	 * The WRF working directory
	 */
	public final Path wrf;
	/**
	 * The run working directory
	 */
	public final Path run;
	/**
	 * The WPS working directory
	 */
	public final Path wps;
	/**
	 * The directory into which the grib data is downloaded
	 */
	public final Path grib;
	/**
	 * The directory into which the wrfout files are moved
	 */
	public final Path output;
	
	/**
	 * Creates a {@link WRFPaths} by resolving "WRFV3", "WPS", and "grib" against root for the WRF, WPS, and grib working
	 * directories respectively.
	 * 
	 * @param root
	 *            a {@link Path} to the root working directory.
	 * @param output
	 *            the output directory
	 * @param create
	 *            if {@code true}, this will call {@link Files#createDirectories} for each {@link Path}
	 * @throws IOException
	 *             if a directory could not be created
	 * @throws NullPointerException
	 *             if <tt>root</tt> is {@code null}
	 */
	public WRFPaths(Path root, Path output, boolean create) throws IOException {
		this(root, root.resolve("WRFV3"), root.resolve("WPS"), root.resolve("grib"), output, create);
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
	 * @throws IOException
	 *             if a directory could not be created
	 * @throws NullPointerException
	 *             if any {@link Path} is {@code null}
	 */
	public WRFPaths(Path root, Path wrf, Path wps, Path grib, Path output, boolean create) throws IOException {
		if (root == null)
			throw new NullPointerException("The root path cannot be null.");
		this.root = root.toAbsolutePath().normalize();
		this.wrf = root.resolve(wrf).toAbsolutePath().normalize();
		run = this.wrf.resolve("run");
		this.wps = root.resolve(wps).toAbsolutePath().normalize();
		this.grib = root.resolve(grib).toAbsolutePath().normalize();
		this.output = root.resolve(output).toAbsolutePath().normalize();
		if (create) {
			Files.createDirectories(this.root);
			Files.createDirectories(this.wrf);
			Files.createDirectories(this.wps);
			Files.createDirectories(this.grib);
			Files.createDirectories(this.output);
		}
	}
}
