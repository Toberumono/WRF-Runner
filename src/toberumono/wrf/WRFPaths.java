package toberumono.wrf;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

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
	 * The WRF root directory
	 */
	public final Path wrf;
	
	/**
	 * The WRF run directory
	 */
	public final Path run;
	
	/**
	 * The WPS root directory
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
	
	private final FileLock lock;
	private final Path active;
	
	/**
	 * While the <tt>root</tt> {@link Path} must be provided, the default values for all of the other {@link Path Paths} can
	 * be computed from it, so they can be {@code null}.<br>
	 * Regardless of whether they were {@code null} this constructor also normalizes all of the provided paths.<br>
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
		Files.createDirectories(root);
		active = this.root.resolve("active");
		lock = FileChannel.open(active, StandardOpenOption.CREATE, StandardOpenOption.WRITE).tryLock();
		if (lock == null)
			throw new IOException("Unable to lock the working directory.");
		this.wrf = (wrf == null ? root.resolve("WRFV3") : wrf).toAbsolutePath().normalize();
		this.run = this.wrf.resolve("run");
		this.wps = (wps == null ? root.resolve("WPS") : wps).toAbsolutePath().normalize();
		this.grib = (grib == null ? root.resolve("grib") : grib).toAbsolutePath().normalize();
		this.output = (output == null ? root.resolve("output") : output).toAbsolutePath().normalize();
		if (create) {
			Files.createDirectories(wrf);
			Files.createDirectories(wps);
			Files.createDirectories(grib);
			Files.createDirectories(output);
		}
	}
	
	/**
	 * Releases the lock on the root directory and deletes the locking file.
	 * 
	 * @throws IOException
	 *             if an I/O error occured
	 */
	public final void unlock() throws IOException {
		lock.release();
		lock.acquiredBy().close();
		Files.delete(active);
	}
}
