package toberumono.wrf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A container for the {@link Path Paths} that {@link WRFRunner} needs to pass around.
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
	 * While the <tt>root</tt> {@link Path} must be provided, the default values for all of the other {@link Path Paths} can
	 * be computed from it, so they can be {@code null}.<br>
	 * Regardless of whether they were {@code null} this constructor also normalizes all of the provided paths.<br>
	 * <b>NOTE:</b> This does <i>not</i> create any directories. Make sure that they are created prior to putting stuff in
	 * them.
	 * 
	 * @param root
	 *            a {@link Path} to the timestamped working directory. This cannot be {@code null}.
	 * @param wrf
	 *            a {@link Path} to the WRFV3 subfolder in <tt>root</tt>
	 * @param wps
	 *            a {@link Path} to the WPS subfolder in <tt>root</tt>
	 * @param grib
	 *            a {@link Path} to the grib subfolder in <tt>root</tt>
	 * @param output
	 *            a {@link Path} to the output subfolder in <tt>root</tt>
	 * @param create
	 *            if {@code true}, then this will call {@link Files#createDirectories} for each {@link Path}
	 * @throws IOException
	 *             if a directory could not be created
	 */
	public WRFPaths(Path root, Path wrf, Path wps, Path grib, Path output, boolean create) throws IOException {
		if (root == null)
			throw new NullPointerException("The root path cannot be null.");
		this.root = root.toAbsolutePath().normalize();
		this.wrf = (wrf == null ? root.resolve("WRFV3") : wrf).toAbsolutePath().normalize();
		this.wps = (wps == null ? root.resolve("WPS") : wps).toAbsolutePath().normalize();
		this.grib = (grib == null ? root.resolve("grib") : grib).toAbsolutePath().normalize();
		this.output = (output == null ? root.resolve("output") : output).toAbsolutePath().normalize();
		if (create) {
			Files.createDirectories(root);
			Files.createDirectories(wrf);
			Files.createDirectories(wps);
			Files.createDirectories(grib);
			Files.createDirectories(output);
		}
	}
}
