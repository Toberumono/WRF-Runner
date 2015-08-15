#A script to download the required libraries for this project from gitHub and then build it.
#Author: Toberumono

clone_project() {
	git clone "https://github.com/Toberumono/$1.git" "../$1"
}

build_project() {
	local stored="$(pwd)"
	cd "../$1"
	if [ -e "build_brewless.sh" ]; then
		bash build_brewless.sh
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

ant #Yep.  That's the final step.