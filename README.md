# WRF-Runner
A simple script for running WRF.  This *requires* the [Java 8 SDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html).

## Usage
### Setup
#### Required Libraries
* [Lexer](https://github.com/Toberumono/Lexer)
* [JSON library](https://bitbucket.org/toberumono/json-library)
* [Namelist Parser](https://github.com/Toberumono/Namelist-Parser)
* [Additional Structures](https://github.com/Toberumono/Additional-Structures) projects.
If you don't have these, see [Getting the required libraries](#gtrl) for how to get them.
A quick tip: If you use the same root directory for all of the libraries, and checkout into subdirectories, all of the ant files will work without any modifications, and you'll have reduced directory clutter.
1. Make sure that you have all of the required libraries.  If you don't

#### Compiling the library
1. checkout the repository into any directory
2. the ant file assumes that the libraries are in the directory *above* the one you just performed a checkout in.
	a. if you wish to change this, see [changing the library directory]



## Help
### <a name="gtrl"></a>Getting the required libraries
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