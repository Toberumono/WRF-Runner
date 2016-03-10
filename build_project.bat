@echo OFF
SET startpath="%~dp0"

cd ..\
powershell -Command "(New-Object Net.WebClient).DownloadFile('https://github.com/Toberumono/JSON-Library/archive/master.zip', 'json-library.zip')"
"%JAVA_HOME%\bin\jar" xf json-library.zip
del json-library.zip
Rename JSON-Library-master json-library

cd json-library
if exist setup_project.bat (call setup_project.bat) else (call ant)

cd ..\
cd %startpath%
call ant
