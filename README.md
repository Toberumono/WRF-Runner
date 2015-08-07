# <a name="readme"></a><a name="Readme"></a>WRF-Runner
A simple script for running WRF.  This is written in *Java 8* and is designed for use on personal systems - due to the nature of large-scale systems, there is no guarantee that this will be compatible with its job-management system.

## Usage
### Experience
This guide does assume a basic level of comfort with a UNIX-based prompt.  If you are new to working with terminal, tutorial one at [http://www.ee.surrey.ac.uk/Teaching/Unix/](http://www.ee.surrey.ac.uk/Teaching/Unix/) will cover everything you need for this tutorial. (Its prompt likely looks a bit different, but those commands are effectively identical across UNIX shells)

### Setup
#### <a name="rp"></a>Required programs (these are all command line utilities)

* ruby
* curl
* Homebrew/Linuxbrew
* Java 8 JDK update 45+

If you don't have these, see [Getting the Required Programs](#gtrp) for how to get them.

#### <a name="rl"></a>Required Libraries

* [JSON Library](https://github.com/Toberumono/JSON-Library)
* [Namelist Parser](https://github.com/Toberumono/Namelist-Parser)
* [Structures](https://github.com/Toberumono/Structures)
* [Utils](https://github.com/Toberumono/Utils)

These are all downloaded, compiled, and linked as part of the installation process if you are using Homebrew/Linuxbrew.

#### Compiling the Program

1. Make sure that you have the [Required Programs](#rp).
	+ If you don't, follow the directions in [Getting the Required Programs](#gtrp).
2. Run `brew tap toberumono/tap` (This only needs to be run once.)
	+ If you're on Linux and it cannot find the brew command, run `export PATH=$HOME/.linuxbrew/bin:$PATH`.
3. Run `brew install wrf-runner`
	+ Linuxbrew may have trouble with a few dependencies, running `brew install` for each dependency, while annoying, will fix that problem.
4. While this does download and install the program, there is still the matter of linking it to a more accessible directory.  If you just intend on using it as a library, you can ignore the remaining steps, otherwise, continue.
5. cd into the directory into which you want to install the WRF-Runner program
6. Run `wrf-linker.sh`
7. Proceed to [Running a WRF process](#rawrfp)

### <a name="rawrfp"></a>Running a WRF process
#### A few quick notes

* This program does not override any fields in the namelist files other than the run_, start_, end_, wps paths (it just makes them absolute), and interval_seconds (however, it is overriden with a user-defined value) - everything else is preserved.  Therefore, this can still be used with more advanced configurations.
* This section describes only the minimal amount of configuration required for a successful run.  There are several options not visited here.
* See [Description of Configuration Variables](#docv) for information on each option in the configuration.json file.

#### <a name="c"></a>Configuring

1. This program downloads data that needs the NAM Vtable in WPS by default.  To ensure that the correct Vtable is used, run: `ln -sf ./ungrib/Variable_Tables/Vtable.NAM ./Vtable` in the WPS installation directory if you are using NAM data.
2. Edit the namelist files for WRF and WPS.  In the general case (aka "use your own discretion"), this requires:
	1. Setting max_dom in the domains and share sections in the WRF and WPS namelists respectively.
	2. Configuring the domains and geogrid sections in the WRF and WPS namelists respectively.
		+ This includes setting the geog_data_path value in namelist.wps
		+ Make sure that the number of values on lines that have per-domain values are equal to the value in max_dom
	3. Setting input_from_file to ".true." in time_control (make sure to set it for each domain)
	4. For NAM data, setting num_metgrid_levels to 40 and num_metgrid_soil_levels to 4 in namelist.input
	5. For runs *not* using wget, settting interval_seconds in the time_control and share sections (This is computed from the grib->timestep subsection when wget is being used)
3. Open the configuration file (configuration.json) in the directory into which you linked the WRFRunner.jar and configuration.json files.
	+ If you want to use a different file, just copy the contents of configuration.json into it before continuing and remember to change the configuration file path in the run step.
4. Configure the parallelization options in the general->parallel subsection:
	1. If you expect to have multiple simulations with the same start time and wish to keep them, set "use-suffix" to true.
	2. If you did not compile WRF in DMPAR mode, set "is-dmpar" to false and continue to step 3.
	3. Otherwise, set "processors" to the number of processors you would like to allow WRF to use.
		+ It is a good idea to set this value to at most two less than the number of processors (or cores) in your system.
5. Configure the paths section (All of these *must* be absolute paths):
	1. Set the "wrf" path to the root directory of your WRF installation.
	2. Set the "wps" path to the root directory of your WPS installation.
	3. Set the "working" path to an empty or non-existent directory.
6. Configure the timing section (These are used to determine the values used in the run_, start_, and end_ fields):
	1. Go through and set the variables as appropriate.  If you are unsure about "rounding", leave it enabled.
	2. Set the offset values if you want the simulation to start at a time different from that produced by rounding (e.g. with rounding set to current day, setting hours 6 and the other fields to 0 would cause the simulation to start 6:00am on the current day).  Otherwise, disable it (set "enabled" to false).
	3. Set the duration values to match how long you wish your script to run.
		- The fields accept extended values.  e.g. setting hours to 36 is equivalent to setting days to 1 and hours to 12.
7. Configure the grib section (this is only required if the wget feature is enabled):
	+ See [Writing a GRIB URL](#wagu) for the steps needed.
8. That's it.  Proceed to [Running](#r)

#### <a name="r"></a>Running
1. cd to the directory into which you linked the WRFRunner.jar and configuration.json files.
2. run `java -jar WRFRunner.jar configuration.json`
	+ If you are using a different configuration file, replace configuration.json with the path to your configuration file.

## Help
### <a name="docv"></a>Description of Configuration Variables

+ general
	+ features: These are toggles for the pieces of the script, mostly useful in debugging.  Generally, the first three must be enabled for an actual run.
		- wget: Toggle the wget step
		- wps: Toggle the WPS step
		- wrf: Toggle the WRF step
		- cleanup: If this is true, then the script will automatically delete downloaded or other intermediate files that are no longer needed.
	+ parallel:
		- is-dmpar: This tells the script whether WRF and WPS were set up with DMPAR mode.  This is effectively the toggle for all parallel components.
		- boot-lam: True indicates that the mpich call should include the boot flag.  This should only be used on personal machines that will not have another mpich process running on them.
		- processors: The number of processors to allow WRF to use.  If you intend to continue using the computer on which you are running the simulation while the simulation is in progress, leave your system at least 2 processors.
	+ wait-for-WRF: True indicates that the script should wait for WRF to complete.  This *must* be true for it to perform the final stage of cleanup.  Otherwise, this is a matter of preference.
	+ keep-logs: True indicates that the script should move log files out of the working directories.  This is generally only useful if you are encountering errors.
	+ always-suffix: True indicates that the script should add "+1" to simulation folder names.  This is generally only useful if you expect to have multiple simulations with the same start times.
	+ max-kept-outputs: The maximum number of completed simulations to keep around (well, the data from them, anyway).  A value of less than 1 disables automatic deletion.
+ paths: Absolute paths to the executable directories for WRF and WPS as well as the working and grib data directories.  These must *not* end in '/'.  
	- wrf: The path to the WRF *root* directory.
	- wps: The path to the WPS *root* directory.
	- working: A path to an arbitrary, preferably empty folder into which temporary files can be placed.
	- grib_data: A path to an arbitrary, preferably empty folder into which downloaded grib data can be placed.
+ grib: Settings for how the grib data is downloaded (This is only used if the wget feature is enabled)
	- url: The URL template to be used for downloading grib data.  See [Writing a GRIB URL](#wagu) for information on how to write it
	+ timestep: These are used to increment the non-constant flags in the url template.  Generally, hours should be the only non-zero value; however, the others are included just in case.  See [Writing a GRIB URL](#wagu) for more information on how to determine what these values should be.
		- days: Number of days to step forward per timestep.
		- hours: Number of hours to step forward per timestep.
		- minutes: Number of minutes to step forward per timestep.
		- seconds: Number of seconds to step forward per timestep.
+ timing: Settings for calculating the appropriate start and end times of the simulation.
	- use-computed-times: If true, then the times are computed at runtime using the values in this section.  Otherwise, they are statically copied from the namelist files.
	+ rounding: This is used to set the first non-zero field in the time settings and give it a simple offset.
		- enabled: Toggles this feature
		- diff: A quick offset setting, can be previous, current, or next.  Generally, leaving this on current is advisable.
		- magnitude: The first non-zero value.  While this can be second, minute, hour, day, month, or year, day is generally recommended.
	+ offset: These are used to fine-tune the start time of the simulation.  These values *can* be negative.
		- enabled: Toggles this feature
		- days: Number of days to shift the start time by.  This field's magnitude should generally never exceed 2.
		- hours: Number of hours to shift the start time by.  This field's magnitude should generally never exceed 48.
		- minutes: While it is possible to set an offset with minutes, this is highly inadvisable and will cause the simulation to fail with the default dataset.
		- seconds: While it is possible to set an offset with seconds, this is highly inadvisable and will cause the simulation to fail with the default dataset.
	+ duration: These control the duration of the simulation.  If this subsection is not in the configuration file, it will be populated using the values in the WRF namelist file.
		- days: Number of days over which the simulation will be run
		- hours: Number of hours over which the simulation will be run
		- minutes: Number of minutes over which the simulation will be run
		- seconds: Number of seconds over which the simulation will be run

### <a name="wagu"></a>Writing a GRIB URL
#### Instructions

1. Copy the URL for a *specific* data file of the type that you will be using in your simulation.
2. Paste it into the configuration.json file (or anywhere really, but you will have to paste it in there eventually, so why wait?)
2. Copy the URL for *another specific* data file and compare it to the first one.
3. Within the URL there will be values that change day to day, but won't change during a run of your simulation.
4. Replace these numbers with Java's [Date/Time formatter syntax](http://docs.oracle.com/javase/8/docs/api/java/util/Formatter.html#dt).  A few common suffixes are listed here.
	- Y: current year (with at least 4 digits)
	- y: last two digits of the year
	- m: current month (with padding)
	- d: current day (with padding)
	- H: hour of the day (24-hour clock, with padding)
5. There will also be values that change during your simulation. (e.g. the timestamp for the specific hour of a file)
6. Replace these with the same syntax that Java uses, but with the following modifications:
	- Instead of %t, use %i. e.g. %tH would become %iH
	- For minutes without padding, use i
	- For seconds without padding, use s
7. Edit the grib->timestep subsection with the appropriate values so that the %iX values will update in sync with the values in your source.
7. Save the configuration - that's it.

#### Example

1. The URLs:
	1. http://www.ftp.ncep.noaa.gov/data/nccf/com/nam/prod/nam.20150805/nam.t00z.awip3d00.tm00.grib2
	2. http://www.ftp.ncep.noaa.gov/data/nccf/com/nam/prod/nam.20150805/nam.t00z.awip3d03.tm00.grib2
	2. http://www.ftp.ncep.noaa.gov/data/nccf/com/nam/prod/nam.20150806/nam.t00z.awip3d03.tm00.grib2
2. Differences:
	1. For this source, the date on the folder is constant for all of the data files used in a run.
	2. However, the timestamp towards the end, "awip3d00" changes to "awip3d03" for the second file.
	3. Because this is the hour, we know that we will need to set the hour field in the grib->timestep subsection to 3, and all of the other fields to 0
3. Change the parts of the URL that don't change within the run:
	1. "nam.20150805" becomes "nam.%tY%tm%td"
4. The URL after the first set of changes:
	1. http://www.ftp.ncep.noaa.gov/data/nccf/com/nam/prod/nam.%tY%tm%td/nam.t00z.awip3d00.tm00.grib2
5. Change the parts of the URL that change within the run:
	1. "awip3d00" becomes "awip3d%iH"
6. The final URL:
	1. http://www.ftp.ncep.noaa.gov/data/nccf/com/nam/prod/nam.%tY%tm%td/nam.t00z.awip3d%iH.tm00.grib2
7. Set the values in grib->timestep as described in step 2.
8. That's it.

### <a name="gtrp"></a>Getting the Required Programs

- Linux (note: you may need to replace the names of the downloaded files or the directories they into which they unpacked to match the versions that you downloaded):
	1. Download the appropriate [Java 8 JDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html).
	2. Copy the following script into terminal, and change the values of version and update so that they match the version and update of the JDK you downloaded.  Then run it.
	```bash
	export version="8"; export update="51"; sudo mkdir /usr/lib/jvm; sudo tar zxvf "jdk-${version}u$update-linux-x64.tar.gz" -C /usr/lib/jvm; sudo ln -sf "/usr/lib/jvm/jdk1.$version.0_$update/bin/*" /usr/bin/
	```
	3. Install wget, git, ruby, and curl. Run: (For systems that use `yum`, replace `apt-get install` with `yum install`)</br>
	```bash
	sudo apt-get install build-essential curl git m4 ruby texinfo libbz2-dev libcurl4-openssl-dev libexpat-dev libncurses-dev zlib1g-dev
	```
	4. Install [Linuxbrew](https://github.com/Homebrew/linuxbrew).  **There is no need to edit the .bashrc or .zshrc files unless you expect to run Linuxbrew frequently**.
	5. Link the executables. Run: `export PATH=$HOME/.linuxbrew/bin:$PATH` (This is why there's no need to edit .bashrc and .zshrc).
- Mac: Ruby and Curl are already installed on Mac, so we don't need to worry about those.
	1. install the appropriate [Java 8 JDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html).
	2. install [Homebrew](http://brew.sh/).
