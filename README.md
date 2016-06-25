# WRF-Runner
## What is it?
This is a Java 8 program that simplifies automatically running WRF simulations.

## A Summary of Features

1. Runs on personal systems, desktops, workstations, etc. (It has *not* been tested or deployed on large-scale systems or clusters with job-management systems)
2. Simplifies the process by deriving as much information as possible, and shifts the repeated configuration values into a central location so that they only need to be set once.
3. Automatically executes all parts of a basic WRF simulation on real data.
4. Supports multiple parallel jobs
5. Cleans up old simulation output on a rolling basis (Because 8+ GB of data per day adds up).
6. Allows for a large amount of customization, but only requires a small amount.
7. Can be used as a library for programmers that want an even deeper level of customization.

## What does this program do?

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

## What does this program not do?

1. Set up WRF and WPS - Check out my [WRF Setup Script](https://github.com/Toberumono/WRF-Setup-Script) for automating that.
2. Postprocess data
3. Run more complex simulations (e.g. those requiring ndown.exe or tc.exe)

## Usage
### Experience Needed
This guide does assume a basic level of comfort with a UNIX-based prompt.  If you are new to working with terminal, tutorial one at [http://www.ee.surrey.ac.uk/Teaching/Unix/](http://www.ee.surrey.ac.uk/Teaching/Unix/) will cover everything you need for this tutorial. (Its prompt likely looks a bit different, but those commands are effectively identical across UNIX shells)

### Setup
#### Required Programs
##### (these are all command line utilities)

* curl
* git
* ruby
* wget
* Homebrew/Linuxbrew
* [Java 8 JDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html) (update 45 or higher)

If you don't have these, see [Getting the Required Programs](#getting-the-required-programs) for how to get them.
If you do not want to use Homebrew/Linuxbrew, follow the instructions in the [Brewless Setup](#brewless-setup) section.

#### Required Libraries
These are all my libraries.

* [JSON Library](https://github.com/Toberumono/JSON-Library)
* [Namelist Parser](https://github.com/Toberumono/Namelist-Parser)
* [Structures](https://github.com/Toberumono/Structures)
* [Utils](https://github.com/Toberumono/Utils)

These are all automatically downloaded, compiled, and linked as part of the installation process if you are using Homebrew/Linuxbrew.

#### Compiling WRFRunner.jar

1. Make sure that you have the [Required Programs](#required-programs).
  + If you don't, follow the directions in [Getting the Required Programs](#getting-the-required-programs).
2. Run `brew tap toberumono/tap` (This only needs to be run once.)
  + If you're on Linux and it cannot find the brew command, follow steps b and c in the [Linuxbrew Installation](#lbrewinstall) instructions.
  + If that fails, follow the instructions in the [Brewless Setup](#brewless-setup) sections instead.
3. Run `brew install wrf-runner`
  + Linuxbrew may have trouble with a few dependencies, running `brew install` for each dependency, while annoying, will likely fix that problem.
  + If that fails, follow the instructions in the [Brewless Setup](#brewless-setup) sections instead.
4. While this does download and install the program, there is still the matter of creating symbolic links to WRFRunner.jar and configuration.json in the directory from which you want to run WRFRunner.jar.  If you just intend on using WRFRunner.jar as a library, you can ignore the remaining steps, otherwise, continue.
5. cd into the directory into from which you want to run WRFRunner.jar
6. Run `wrf-linker.sh`
7. Proceed to [Running a WRF process](#running-a-wrf-process)

### Running a WRF process
#### A few quick notes

* This program does not override any fields in the namelist files other than the run_, start_, end_, wps paths (it just makes them absolute), and interval_seconds (however, it is overriden with a user-defined value) - everything else is preserved.  Therefore, this can still be used with more advanced configurations.
* This section describes only the minimal amount of configuration required for a successful run.  There are several options not visited here.
  See [Description of Configuration Variables](https://github.com/Toberumono/WRF-Runner/wiki/Description-of-Configuration-Variables) for information on each option in the configuration.json file.

#### Configuring

1. This program downloads data that needs the NAM Vtable in WPS by default.  To ensure that the correct Vtable is used, run: `ln -sf ./ungrib/Variable_Tables/Vtable.NAM ./Vtable` in the WPS installation directory if you are using NAM data.
2. Edit the namelist files for WRF and WPS.  In the general case, this requires:
  1. Set max_dom in the domain section in the WRF and share section in the WPS namelists.
  2. Configure the domains and geogrid sections in the WRF and WPS namelists respectively.
    + This includes setting the geog_data_path value in namelist.wps
    + Make sure that the number of values on lines that have per-domain values are equal to the value in max_dom
  3. Setting input_from_file to ".true." in time_control (make sure to set it for each domain)
  4. For NAM data, setting num_metgrid_levels to 40 and num_metgrid_soil_levels to 4 in namelist.input
3. Open the configuration file (configuration.json) in the directory into which you linked the WRFRunner.jar and configuration.json files.
4. Set the working directory (general -> "working-directory") to an *empty or uncreated* directory.
  - This is path from which the simulations will be run and where the timestamped output folders will be placed.
5. Configure the parallelization options in the wrf->parallel subsection:
  1. Set "is-dmpar" to false if you are using a non-DMPAR build of WRF.
  2. If WRF was compiled in DMPAR mode, set "processors" to the number of processors you would like to allow WRF to use.
    + It is a good idea to set this value to at most two less than the number of processors (or cores) in your system.
6. If you expect to regularly have multiple simulations with the same start time, and wish to have a constant folder-naming scheme, set general -> "always-suffix" to true.
7. Configure the path section (While these can be relative paths, it is *highly recommended* that they be absolute paths):
  1. Set the "wrf" path to the root directory of your WRF installation.
  2. Set the "wps" path to the root directory of your WPS installation.
8. Configure the timing section (These are used to determine the values used in the run_, start_, and end_ fields):
  1. Go through and set the variables as appropriate.  If you are unsure about "rounding", leave it enabled.
  2. Set the offset values if you want the simulation to start at a time different from that produced by rounding (e.g. with rounding set to current day, setting hours 6 and the other fields to 0 would cause the simulation to start 6:00am on the current day).  Otherwise, disable it (set "enabled" to false).
  3. Set the duration values to match how long you wish your script to run.
    - The fields accept extended values.  e.g. setting hours to 36 is equivalent to setting days to 1 and hours to 12.
9. Configure the grib section (this is only required if the GRIB Module is enabled):
  + See [Writing a GRIB URL](https://github.com/Toberumono/WRF-Runner/wiki/Grib-Module#writing-a-grib-url) for the steps needed.
10. That's it.  Proceed to [Running](#running)

#### Running
1. cd to the directory into which you linked the WRFRunner.jar and configuration.json files.
2. run `java -jar WRFRunner.jar configuration.json`

#### Setting Up a Cron Task
In order for this to run automatically, we need to set up a Cron task.

1. cd to the directory into which you linked the WRFRunner.jar and configuration.json files.
2. Get the path to your your linked directory with `pwd`.
3. Copy the output somewhere so that you can see it outside of Terminal (or memorize it).
4. Open your crontab file for editing.  Use `EDITOR=vim crontab -e` to edit it.
  * You can use whichever terminal editor your prefer - vim is just here because it is what I use.
    + While some GUI editors (such as gedit) might work, the catch is that you cannot have the editor open before executing the command and you must completely close the editor when you are done editing the file.  So, don't use GUI editors unless you absolutely have to.
    + If you don't have a preferred editor and don't have vim installed, you can install it with:
      - For Linux: `sudo apt-get install vim`
      - For Mac: `brew install vim`
    + If you are new to using vim, see [http://vim.wikia.com/wiki/Tutorial](http://vim.wikia.com/wiki/Tutorial) for help.
5. Add a cron task that runs: `cd linked_directory && java -jar WRFRunner.jar` where `linked_directory` is the path obtained in step 2.
  * Note: You might need to use the full path to the java executable instead of just `java`.  Run `which java` to get it.
  * See [http://www.nncron.ru/help/EN/working/cron-format.htm](http://www.nncron.ru/help/EN/working/cron-format.htm) for syntax help.
6. Save the file and quit the editor.

## Help
### Brewless Setup
These instructions will walk you through setting up the program without Homebrew/Linuxbrew.  While this is *strongly* discouraged on Macs, Linuxbrew has been known to have some issues on Linux-based machines.  These instructions are an equally stable alternative to Homebrew/Linuxbrew; however, they do take a few more steps.<br>
Note that, unlike every other section of the tutorial, these commands use curl.  This is because installing wget on a Mac without Homebrew can be a pain.

1. Install `curl` if needed:
  1. Run `which curl`.  If a path is printed, you have curl, so continue to step 2.  Otherwise, run one of the following to install it:
    + On Debian-based Linux (e.g. Ubuntu), run `sudo apt-get install curl`
    + On RedHat-based Linux (e.g. Fedora), run `sudo yum install curl`
2. Download and Install the appropriate [Java 8 JDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html).
  * To install the JDK on Linux, cd into the directory into which you downloaded the JDK's .tar.gz and run:
  
    ```bash
    bash <(curl -#fsSL "https://raw.githubusercontent.com/Toberumono/Miscellaneous/master/java/sudoless_install.sh")
    ```
3. Download and Install the appropriate version of ANT.
  * It's all automated now.  Just run:

    ```bash
    bash <(curl -#fsSL "https://raw.githubusercontent.com/Toberumono/Miscellaneous/master/ant/automatic_install.sh")
    ```
4. Get the latest stable version of the WRF-Runner program:
  1. create and/or cd into an empty directory from which you want to run the .jar file.
  2. Run:

    ```bash
    git clone "https://github.com/Toberumono/WRF-Runner.git"; git checkout "$(git describe --tags)"
    ```
5. Build everything:

    ```bash
    cd WRF-Runner; ./build_brewless.sh -Dpackage.libs=true; cp configuration.json ../; cd ../
    ```
6. You're all set.  Proceed to [Running a WRF Process](#running-a-wrf-process).

### Getting the Required Programs
If you do not want to use Homebrew/Linuxbrew, follow the instructions in the [Brewless Setup](#brewless-setup) section.

#### Linux

1. Download the appropriate [Java 8 JDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html).
2. `cd` into the directory into which you downloaded the JDK (`cd $HOME/Downloads` will likely do it) and run:
  
  ```bash
  bash <(wget -qO - https://raw.githubusercontent.com/Toberumono/Miscellaneous/master/java/sudoless_install.sh)
  ```
  + For information on what the script does, see its section in the Readme of my [Miscellaneous](https://github.com/Toberumono/Miscellaneous#htujsi) repo.
3. <a name="lbrewinstall"></a>Install [Linuxbrew](https://github.com/Homebrew/linuxbrew#installation). Run one of the following:
  1. If you have sudo privileges, do the following:
    1. Install [Linuxbrew](https://github.com/Homebrew/linuxbrew#installation).
      * **Do not** modify your .bashrc or .zshrc files.  Instead, follow steps b and c.
    2. Run:
  
      ```bash
      bash <(wget -qO - https://raw.githubusercontent.com/Toberumono/Miscellaneous/master/linuxbrew/append_paths.sh)
      ```
      + This adds the additional lines to your .bashrc and/or .zshrc files. For more information on how it works, see its section in the readme of my [Miscellaneous](https://github.com/Toberumono/Miscellaneous#htulap) repo.
    3. Restart Terminal or run:
      
      ```bash
      . <(wget -qO - https://raw.githubusercontent.com/Toberumono/Miscellaneous/master/general/get_profile.sh); source "$profile"
      ```
      + This command loads the `profile` variable, which holds the path to your shell's profile file, and then uses the builtin command, `source`, to load it.
      + Note that, regardless of which option you choose, you will only have to perform steps b and c once.
  2. If you do not have sudo privileges, then you can run the following script.  It will attempt to install [Linuxbrew](https://github.com/Homebrew/linuxbrew) without sudo privileges or will list the missing software that you should ask your system administrator to install if it cannot do so.

    ```bash
    bash <(wget -qO - https://raw.githubusercontent.com/Toberumono/Miscellaneous/master/linuxbrew/sudoless_install.sh)
    ```
    * This is **not** recommended; however, it is technically a valid option.

#### Mac
A Quick Note: Ruby and Curl are already installed on Macs, so we don't need to worry about them.

1. Install the appropriate [Java 8 JDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html).
2. Install [Homebrew](http://brew.sh/).
3. Run `brew install wget`

### Upgrading to Version 4+
If you are upgrading from a version below 4, you may have invalid Strings in your configuration file due to the configuration file changes between versions 3 and 4. For safety, the WRF-Runner program checks for potential issues before attempting to run a Simulation. If it finds any problems, it will notify you where they are, and provide a description of the problem. Fixing these can be done manually or through the interactive upgrade helper.

To run the interactive upgrade helper, follow the following steps:

1. cd to the directory into which you linked the WRFRunner.jar and configuration.json files.
2. run `java -jar WRFRunner.jar --interactive-upgrade configuration.json`
  * Replace `configuration.json` with the path to your configuration file as needed.
