if [ "$(which brew)" == "" ]; then
	echo "brew is not int your path.  Please put it in your path before running this script."
	kill -INT $$
fi
prefix="$(brew --prefix)"
config="$prefix/etc/wrf-runner/configuration.json"
jar="$prefix/lib/WRFRunner.jar"
path=""
if [ "$#" -eq "0" ]; then
	path="$(pwd)"
else
	path="$1"
fi
if [ ! -e "$path" ] || [ ! -d "$path" ]; then
	echo "Making $path"
	mkdir -p "$path"
fi
echo "Linking into $path"
ln -sf "$jar" "$path/WRFRunner.jar"
ln -sf "$config" "$path/configuration.json"