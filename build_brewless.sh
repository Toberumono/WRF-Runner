#A script to download the required libraries for this project from gitHub and then build it.
#Author: Toberumono

use_release=true
if [ "$#" > 0 ] && [ "$1" == "use_latest" ]; then
	use_release=false
	shift
fi

clone_project() {
	if ( $use_release ); then
		git clone -b "$(git ls-remote --tags https://github.com/Toberumono/$1.git | grep -o -E '([0-9]+\.)*[0-9]+$' | sort -g | tail -1)" --depth=1 "https://github.com/Toberumono/$1.git" "../$1" >/dev/null 2>/dev/null
	else	
		git clone "https://github.com/Toberumono/$1.git" "../$1"
	fi
}

build_project() {
	local stored="$(pwd)"
	cd "../$1"
	if [ -e "build_brewless.sh" ]; then
		"$(ps -o comm= -p $$ | sed -e 's/-\{0,1\}\(.*\)/\1/')" build_brewless.sh
	else
		ant
	fi
	cd "$stored"
}

clone_build_project() {
	if [ ! -e "../$1" ] || [ ! -d "../$1" ] || [ "$(ls -A $1)" == "" ]; then
		clone_project "$1"
		build_project "$1"
	fi
}

clone_build_project "Namelist-Parser"
clone_build_project "JSON-Library"
clone_build_project "Structures"
clone_build_project "Utils"

ant "$@" #Yep.  That's the final step.