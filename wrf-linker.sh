if [ "$(which brew)" == "" ]; then
	echo "brew is not int your path.  Please put it in your path before running this script."
	kill -INT $$
fi
prefix="$(brew --prefix)"
config="$prefix/etc/wrf-runner/configuration.json" 
if [ $# -ne 1 ]; then
	echo "Invalid arguments.  Please enter the directory into which the WRF Runner configuration and executable files should be linked.  This directory need not exist prior to running the script."
elif [ "$1" == "--configure-commands"]; then
	echo "Writing command paths to configuration.json"
	perl -0777 -i -pe 's/("(bash)")[^\n]*\n/$1 : "$(which $2)",\n/is' "$config"
	perl -0777 -i -pe 's/("(rm)")[^\n]*\n/$1 : "$(which $2)",\n/is' "$config"
	perl -0777 -i -pe 's/("(wget)")[^\n]*\n/$1 : "$(which $2)",\n/is' "$config"
else
	if [ ! -e "$1" ] || [ ! -d "$1" ]; then
		echo "Making $1"
		mkdir "$1"
	fi
	echo "Linking into $1"
	ln -sf "$prefix/lib/WRFRunner.jar" "$1/WRFRunner.jar"
	ln -sf "$config" "$1/configuration.json"
fi