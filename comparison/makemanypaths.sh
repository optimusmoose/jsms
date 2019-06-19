#!/bin/bash
mkdir "$1"

for i in {0..100}
do
	echo $i
	printf "$(python3 ./makepath.py "$2")" > "$1/$i.csv"
done