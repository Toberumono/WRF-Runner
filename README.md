# <a name="readme"></a>WRF-Runner
A simple script for running WRF.  This is written in *Java 8*.

## Usage
### Setup
#### <a name="rp"></a>Required programs (these are all command line utilities)
* wget
* ant
* javac (via the [Java 8 SDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html))

If you don't have these, see [Getting the required programs](#gtrp) for how to get them.

#### <a name="rl"></a>Required Libraries
* [Lexer](https://github.com/Toberumono/Lexer)
* [JSON library](https://bitbucket.org/toberumono/json-library)
* [Namelist Parser](https://github.com/Toberumono/Namelist-Parser)
* [Additional Structures](https://github.com/Toberumono/Additional-Structures) projects.

If you don't have these, see [Getting the required libraries](#gtrl) for how to get them.

#### Compiling the Program
1. Make sure that you have the [Required Programs](#rp) and [Required Libraries](#rl)
2. cd into the directory with all of the .jars from the [Required Libraries](#rl)
3. run this script: `mkdir WRF-Runner; cd WRF-Runner; git init; git pull https://github.com/Toberumono/WRF-Runner.git; ant;`

### Running a WRF process
#### Configuring
*Note*: This describes only the minimal amount of configuration required for a successful run.  There are several options not visited here.
*See the settings.json file included in the git-pull for more detailed information on each option.*

1. Open the settings file (using the settings.json file included in the git-pull is ideal)
	a. if you are using a different file, just copy the contents of settings.json into it
2. Configure the parallelization options in general->parallel:
	a. If you did not compile WRF in DMPAR mode, set "is-dmpar" to false and continue to step 3
	b. Set "processors" to the number of processors you would like to allow WRF to use
3. Configure paths:
	a. Set the "wrf" path to the *run* directory of your WRF installation
	b. Set the "wps" path to the root directory of your WPS installation
	c. Set the "working" path to an empty or non-existent directory
	d. Set the "grib_data" path to a sub-directory of the "working" directory (this still has to be the full path)
4. Configure timing:
	a. Go through and set the variables as appropriate.  If you are unsure about "rounding", leave it enabled.  (Actually, in the default implementation of the wget function, this *must* be enabled)
	b. Configure the offset if you so desire, or disable it.  It is not required by any components of the script.
5. Configure commands:
	a. To get the paths to each command, run the following:
		```bash
		echo -e "\t\t\"bash\" : \"$(which bash)\",\n\t\t\"rm\" : \"$(which rm)\",\n\t\t\"wget\" : \"$(which wget)\""
		```
	b. Paste the output of that command into the "commands" section

#### Running
1. cd to the directory into which you pulled the WRF Runner repository
2. run `java -jar WRFRunner.jar settings.json` (where settings.json is the path to your settings file)

## Help
### <a name="gtrp"></a>Getting the Required Programs
- Linux:
	1. install the appropriate [Java 8 SDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
	2. run `sudo apt-get install build-essential wget ant`
- Mac:
	1. install [Homebrew](http://brew.sh/) if you haven't already
	2. run `brew install wget`
	3. run `brew install ant`

### <a name="gtrl"></a>Getting the Required Libraries
**This script only applies to Unix-based operating systems (Mac OSX, Linux, Unix, probably some others)**
Further, this script assumes you have ANT installed (if you don't, I highly recommend using [Homebrew](http://brew.sh/) to install it on Macs).
cd into the directory in which you would like to build the libraries and then run the following in terminal (you can just copy and paste it):

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
git pull https://bitbucket.org/toberumono/json-library.git;
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
**It is tempting to change the**
```xml
<fileset id="libraries" dir="${libs}">
```
	*library names*
```xml
</fileset>
```
**block, but do *not* do so**.