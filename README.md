# <a name="readme"></a><a name="Readme"></a>WRF-Runner
A simple script for running WRF.  This is written in <i>Java 8</i>.

## Usage
### Setup
#### <a name="rp"></a>Required programs (these are all command line utilities)
* wget
* ant
* javac (via the [Java 8 SDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html))

If you don't have these, see [Getting the Required Programs](#gtrp) for how to get them.

#### <a name="rl"></a>Required Libraries
* [Lexer](https://github.com/Toberumono/Lexer)
* [JSON library](https://github.com/Toberumono/JSON-Library)
* [Namelist Parser](https://github.com/Toberumono/Namelist-Parser)
* [Additional Structures](https://github.com/Toberumono/Additional-Structures)

If you don't have these, see [Getting the Required Libraries](#gtrl) for how to get them.

#### Compiling the Program
1. Make sure that you have the [Required Programs](#rp) and [Required Libraries](#rl)
2. cd into the directory with all of the .jars from the [Required Libraries](#rl)
3. run this script: `mkdir WRF-Runner; cd WRF-Runner; git init; git pull https://github.com/Toberumono/WRF-Runner.git; ant;`

### Running a WRF process
#### Configuring
<i>Note</i>: This describes only the minimal amount of configuration required for a successful run.  There are several options not visited here.</br>
<i>The configuration.json file included in the git-pull contains detailed information on each option.</i>
1. Edit the WRF and WPS Namelist files such that they could be used for a single run (basically, set everything other than the start and end dates and times in both the Namelist.input and Namelist.wps files)
2. Open the configuration file (configuration.json) in the directory into which you pulled the WRF Runner project data.
	1. if you want to use a different file, just copy the contents of configuration.json into it before continuing and remember to change the configuration file path in the run step.
3. Configure the parallelization options in general->parallel:
	1. If you did not compile WRF in DMPAR mode, set "is-dmpar" to false and continue to step 3.
	2. Set "processors" to the number of processors you would like to allow WRF to use.
4. Configure paths:
	1. Set the "wrf" path to the *run* directory of your WRF installation.
	2. Set the "wps" path to the root directory of your WPS installation.
	3. Set the "working" path to an empty or non-existent directory.
	4. Set the "grib_data" path to an empty directory, preferably a sub-directory of the working directory (either way, this requires the full path)
5. Configure timing:
	1. Go through and set the variables as appropriate.  If you are unsure about "rounding", leave it enabled.  (Actually, in the default implementation of the wget function, this *must* be enabled)
	2. Configure the offset if you so desire, or disable it.  It is not required by any components of the script.
6. Configure commands:
	1. To get the paths to each command, run the following:</br>
		```
		echo -e "\t\t\"bash\" : \"$(which bash)\",\n\t\t\"rm\" : \"$(which rm)\",\n\t\t\"wget\" : \"$(which wget)\""
		```
	2. Paste the output of that command into the "commands" section.

#### Running
1. cd to the directory into which you pulled the WRF Runner repository.
2. run `java -jar WRFRunner.jar configuration.json` (where configuration.json is the path to your configuration file).

## Help
### <a name="gtrp"></a>Getting the Required Programs
- Linux:
	1. install the appropriate [Java 8 SDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html).
	2. run `sudo apt-get install build-essential wget ant`.
- Mac:
	1. install the appropriate [Java 8 SDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html).
	2. install [Homebrew](http://brew.sh/) if you haven't already.
	3. run `brew install wget`.
	4. run `brew install ant`.

### <a name="gtrl"></a>Getting the Required Libraries
<b>This script only applies to Unix-based operating systems (Mac OSX, Linux, Unix, probably some others)</b>
Further, this script assumes you have ANT installed (if you don't, I highly recommend using [Homebrew](http://brew.sh/) to install it on Macs).
1. cd into the directory in which you would like to build the libraries
2. Run the following in terminal (you can just copy and paste it):
```bash
mkdir Additional-Structures;
cd Additional-Structures;
git init;
git pull https://github.com/Toberumono/Additional-Structures.git;
ant;
cd ../;
mkdir Lexer;
cd Lexer;
git init;
git pull https://github.com/Toberumono/Lexer.git;
ant;
cd ../;
mkdir JSON-Library;
cd JSON-Library;
git init;
git pull https://github.com/Toberumono/JSON-Library.git;
ant;
cd ../;
mkdir Namelist-Parser;
cd Namelist-Parser;
git init;
git pull https://github.com/Toberumono/Namelist-Parser.git;
ant;
cd ../;
```

### <a name="ctld"></a>Changing the Library Directory
If one of my projects uses additional libraries, I include the line `<property name="libs" location="../" />` towards the top.
Therefore, to change the library directory, simply change the value of location for that property.
<b>It is tempting to change the</b>
```xml
<fileset id="libraries" dir="${libs}">
	<i>library names</i>
</fileset>
```
<i>*block, but do </i>not* do so**.