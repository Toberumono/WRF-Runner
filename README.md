# WRF-Runner
A simple script for running WRF.  This *requires* the [Java 8 SDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html).

This project requires my [Lexer](https://github.com/Toberumono/Lexer) [JSON library](https://bitbucket.org/toberumono/json-library), [Namelist Parser](https://github.com/Toberumono/Namelist-Parser), and [Additional Structures](https://github.com/Toberumono/Additional-Structures) projects.  See [Getting the required libraries](#gtrl)

Assuming you have ANT installed (if you don't, I highly recommend using homebrew to install it on Macs),
cd into the directory in which you would like to build the libraries and then run the following in terminal:
```bash
mkdir Lexer;
mkdir JSON-Library;
mkdir Namelist-Parser;
mkdir Additional-Structures;
cd Additional-Structures;
git init;
git pull https://github.com/Toberumono/Additional-Structures.git;
ant;
cd ../;
cd Lexer;
git init;
git pull https://github.com/Toberumono/Lexer.git;
ant;
cd ../;
cd json-library;
git init;
git pull https://bitbucket.org/toberumono/json-library.git;
ant;
cd ../;
cd Namelist-Parser;
git init;
git pull https://github.com/Toberumono/Namelist-Parser.git;
ant;
cd ../;
```

# Usage
## Setup
A quick tip: If you use the same root directory for all of the libraries, and checkout into subdirectories, all of the ant files will work, and you'll have reduced directory clutter.
### Getting the dependencies
1. 

### Compiling the library
1. checkout the repository into any directory
2. the ant file assumes that the libraries are in the directory *above* the one you just performed a checkout in.
	a. if you wish to change this, see [changing the library directory]



# Help
## <a name="gtrl"></a>Getting the required libraries