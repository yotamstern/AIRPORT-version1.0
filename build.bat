@echo off
dir /s /B *.java > sources.txt
javac -d bin @sources.txt
echo Build Complete
