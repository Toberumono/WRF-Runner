#!/usr/bin/env bash
#A script to download the required libraries for this project from gitHub and then build it.
#Author: Toberumono (https://github.com/Toberumono)

use_release=true
if [ "$#" -gt "0" ] && [ "$1" == "use_latest" ]; then
	use_release=false
	shift
fi

#Determine the correct downloader to use.  Also, we want a progress bar for this script, hence the -# and --show-progress.
[ "$(which wget)" == "" ] && pull_command="curl -#fsSL" || pull_command="wget -qO -"

clone_project() {
	( $use_release ) && tar_name="$(git ls-remote --tags https://github.com/Toberumono/$1.git | grep -oE '([0-9]+\.)*[0-9]+$' | sort -t. -k 1,1n -k 2,2n -k 3,3n -k 4,4n | tail -1)" || tar_name="master"
	mkdir -p "../$1"
	$pull_command "https://github.com/Toberumono/$1/archive/$tar_name.tar.gz" | tar -xz --strip-components 1 -C "../$1"
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
	rm -r "../$1"
}

clone_build_project() {
	if [ ! -e "../$1" ] || [ ! -d "../$1" ] || [ "$(ls -A ../$1)" == "" ]; then
		clone_project "$1"
		build_project "$1"
	fi
}

clone_build_project "Namelist-Parser"
clone_build_project "JSON-Library"
clone_build_project "Structures"
clone_build_project "Utils"

ant "$@" #Yep.  That's the final step.
