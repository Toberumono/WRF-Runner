# <a name="readme"></a><a name="Readme"></a>WRF-Runner
## <a name="wii"></a>What is it?
This is a Java 8 program that simplifies automatically running predictive WRF simulations.

## A Summary of Features

1. Runs on personal systems, desktops, workstations, etc. (It has *not* been tested or deployed on large-scale systems or clusters with job-management systems)
2. Simplifies the process by deriving as much information as possible, and shifts the repeated configuration values into a central location so that they only need to be set once.
3. Automatically executes all parts of a basic WRF simulation on real data.
4. Supports multiple parallel jobs
5. Cleans up old simulation output on a rolling basis (Because 8+ GB of data per day adds up).
6. Allows for a large amount of customization, but only requires a small amount.
7. Can be used as a library for programmers that want an even deeper level of customization.

## <a name="wdtpd"></a>What does this program do?

1. Automatically acquires GRIB data
  + By default, this is set to the NAM 212 AWIPS Grid - Regional - CONUS Double Resolution (40-km Resolution) dataset
    Available at: [http://www.nco.ncep.noaa.gov/pmb/products/nam/](http://www.nco.ncep.noaa.gov/pmb/products/nam/)
  + This dataset is generally good for predictive models that forecast up to 84 hours in advance.
2. Executes WPS
  1. Writes the derived fields into the namelist (e.g. timing, file locations)
  2. Links the GRIB (by default NAM) data
  3. Runs ungrib.exe and geogrid.exe (in parallel)
  4. Runs metgrid.exe and places the output files in the WRF run directory
3. Executes WRF
  1. Writes the derived fields into the namelist (e.g. timing, file locations)
  2. Runs real.exe
  3. Runs wrf.exe (With mpi and related parallelization options as appropriate)
4. Cleans up temporary files
5. Deletes old forecasts if there are more forecasts than the user-defined limit

## <a name="wdtpnd"></a>What does this program not do?

1. Set up WRF and WPS - Check out my [WRF Setup Script](https://github.com/Toberumono/WRF-Setup-Script) for automating that.
2. Postprocess data
3. Run more complex simulations (e.g. those requiring ndown.exe or tc.exe)

## Usage
### Experience
This guide does assume a basic level of comfort with a UNIX-based prompt.  If you are new to working with terminal, tutorial one at [http://www.ee.surrey.ac.uk/Teaching/Unix/](http://www.ee.surrey.ac.uk/Teaching/Unix/) will cover everything you need for this tutorial. (Its prompt likely looks a bit different, but those commands are effectively identical across UNIX shells)

### Setup
#### <a name="rp"></a>Required programs (these are all command line utilities)

* ruby
* curl
* git
* Homebrew/Linuxbrew
* [Java 8 JDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html) (update 45 or higher)

If you don't have these, see [Getting the Required Programs](#gtrp) for how to get them.

#### <a name="rl"></a>Required Libraries
These are all my libraries.

* [JSON Library](https://github.com/Toberumono/JSON-Library)
* [Namelist Parser](https://github.com/Toberumono/Namelist-Parser)
* [Structures](https://github.com/Toberumono/Structures)
* [Utils](https://github.com/Toberumono/Utils)

These are all automatically downloaded, compiled, and linked as part of the installation process if you are using Homebrew/Linuxbrew.

#### Compiling WRFRunner.jar

1. Make sure that you have the [Required Programs](#rp).
  + If you don't, follow the directions in [Getting the Required Programs](#gtrp).
2. Run `brew tap toberumono/tap` (This only needs to be run once.)
  + If you're on Linux and it cannot find the brew command, run:

    ```bash
    bash <(wget -qO - https://raw.githubusercontent.com/Toberumono/Miscellaneous/master/linuxbrew/append_paths.sh)
    ```
    and then re-open Terminal.
    + See [How to Use `append_paths.sh`](https://github.com/Toberumono/Miscellaneous/tree/master/linuxbrew#htulap) in the [Linuxbrew section](https://github.com/Toberumono/Miscellaneous/linuxbrew) of my [Miscellaneous](https://github.com/Toberumono/Miscellaneous) repo for information on what that command does.
3. Run `brew install wrf-runner`
  + Linuxbrew may have trouble with a few dependencies, running `brew install` for each dependency, while annoying, will fix that problem.
4. While this does download and install the program, there is still the matter of creating symbolic links to WRFRunner.jar and configuration.json in the directory from which you want to run WRFRunner.jar.  If you just intend on using WRFRunner.jar as a library, you can ignore the remaining steps, otherwise, continue.
5. cd into the directory into from which you want to run WRFRunner.jar
6. Run `wrf-linker.sh`
7. Proceed to [Running a WRF process](#rawrfp)

### <a name="rawrfp"></a>Running a WRF process
#### A few quick notes

* This program does not override any fields in the namelist files other than the run_, start_, end_, wps paths (it just makes them absolute), and interval_seconds (however, it is overriden with a user-defined value) - everything else is preserved.  Therefore, this can still be used with more advanced configurations.
* This section describes only the minimal amount of configuration required for a successful run.  There are several options not visited here.
  See [Description of Configuration Variables](#docv) for information on each option in the configuration.json file.

#### <a name="c"></a>Configuring

1. This program downloads data that needs the NAM Vtable in WPS by default.  To ensure that the correct Vtable is used, run: `ln -sf ./ungrib/Variable_Tables/Vtable.NAM ./Vtable` in the WPS installation directory if you are using NAM data.
2. Edit the namelist files for WRF and WPS.  In the general case, this requires:
  1. Set max_dom in the domain section in the WRF and share section in the WPS namelists.
  2. Configure the domains and geogrid sections in the WRF and WPS namelists respectively.
    + This includes setting the geog_data_path value in namelist.wps
    + Make sure that the number of values on lines that have per-domain values are equal to the value in max_dom
  3. Setting input_from_file to ".true." in time_control (make sure to set it for each domain)
  4. For NAM data, setting num_metgrid_levels to 40 and num_metgrid_soil_levels to 4 in namelist.input
  5. For runs *not* using wget, settting interval_seconds in the time_control and share sections (This is computed from the grib->timestep subsection when wget is being used)
3. Open the configuration file (configuration.json) in the directory into which you linked the WRFRunner.jar and configuration.json files.
4. Configure the parallelization options in the general->parallel subsection:
  1. If you expect to have multiple simulations with the same start time and wish to keep them, set "use-suffix" to true.
  2. Set "is-dmpar" to false in case you did not compile wrf in DMPAR mode.
  3. If WRF was compiled in DMPAR mode, set "processors" to the number of processors you would like to allow WRF to use.
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

#### <a name="cron"></a>Setting up a Cron task
In order for this to run automatically, we need to set up a Cron task.

1. Get the path to your your linked directory with location with `pwd`
2. Copy the output somewhere so that you can see it outside of Terminal (or memorize it)
3. Open your crontab file for editing.  Use `EDITOR=vim crontab -e` to edit it.
  * You can use whichever terminal editor your prefer - vim is just here because it is what I use.
    + While some GUI editors (such as gedit) might work, the catch is that you cannot have the editor open before executing the command and you must completely close the editor when you are done editing the file.  So, don't use GUI editors unless you absolutely have to.
    + If you don't have a preferred editor and don't have vim installed, you can install it with:
      - For Linux: `sudo apt-get install vim`
      - For Mac: `brew install vim`
    + If you are new to using vim, see [http://vim.wikia.com/wiki/Tutorial](http://vim.wikia.com/wiki/Tutorial) for help.
4. Add a cron task that runs: `cd linked_directory java -jar WRFRunner.jar` where `linked_directory` is the path obtained in step 1.
  * See [http://www.nncron.ru/help/EN/working/cron-format.htm](http://www.nncron.ru/help/EN/working/cron-format.htm) for syntax help.
5. Save and quit the editor.

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

#### Linux

1. Download the appropriate [Java 8 JDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html).
2. `cd` into the directory into which you downloaded the JDK (`cd $HOME/Downloads` will likely do it) and run:
  
  ```bash
  bash <(wget -qO - https://raw.githubusercontent.com/Toberumono/Miscellaneous/master/java/sudoless_install.sh)
  ```
  + For information on what the script does, see its section in the readme of my [Miscellaneous](https://github.com/Toberumono/Miscellaneous#htujsi) repo.
3. Install [Linuxbrew](https://github.com/Homebrew/linuxbrew#install-linuxbrew-tldr). Run one of the following:
  1. If you have sudo privileges, do the following:
    1. Run the following script to get the required libraries (For systems that use `yum`, replace `apt-get install` with `yum install`):

      ```bash
      sudo apt-get install build-essential curl git m4 ruby texinfo libbz2-dev libcurl4-openssl-dev libexpat-dev libncurses-dev zlib1g-dev
      ```
    2. Install [Linuxbrew](https://github.com/Homebrew/linuxbrew#install-linuxbrew-tldr).
      + If you are not comfortable modifying your .bashrc or .zshrc files, follow step c.  Otherwise, modify them.
    3. Run:
  
      ```bash
      bash <(wget -qO - https://raw.githubusercontent.com/Toberumono/Miscellaneous/master/linuxbrew/append_paths.sh)
      ```
      + This adds the additional lines to your .bashrc and/or .zshrc files. For more information on how it works, see its section in the readme of my [Miscellaneous](https://github.com/Toberumono/Miscellaneous#htulap) repo.
  2. If you do not have sudo privileges, then you can run the following script.  It will attempt to install [Linuxbrew](https://github.com/Homebrew/linuxbrew) without sudo privileges or will list the missing software that you should ask your system administrator to install if it cannot do so.

    ```bash
    bash <(wget -qO - https://raw.githubusercontent.com/Toberumono/Miscellaneous/master/linuxbrew/sudoless_install.sh)
    ```

#### Mac

1. Ruby and Curl are already installed on Mac, so we don't need to worry about those.
2. install the appropriate [Java 8 JDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html).
3. install [Homebrew](http://brew.sh/).
