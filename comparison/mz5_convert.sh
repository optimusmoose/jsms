#!/bin/bash

# files to convert
filenames=("CHPP_Tricine_3022.mzML" 
		"CHPP_SDS_3009.mzML" 
		"CHPP_SDS_3002.mzML" 
		"GRAIN_DEVELOPMENT_Z71_3.mzML" 
		"STEM_12.mzML" 
		"18185_REP2_4pmol_UPS2_IDA_1.mzML" 
		"POLLEN_2.mzML" 
       		"Sheppard_Werner_RNAPORF145_09.mzML" 
	        "Sheppard_Werner_RNAPORF145_06.mzML" 
       		"Sheppard_Werner_RNAPORF145_03.mzML")

# set time format for this shell to real time only
TIMEFORMAT=%R

# create temp directory
mkdir mz5_out
echo "begin loop"
for filename in "${filenames[@]}"
do
	echo $filename
	for i in {1..10}
	do
		echo $(time msconvert ../../data/mzml/$filename --mz5 --filter "mslevel 1" -o ./mz5_out --outfile $filename > /dev/null)
		rm ./mz5_out/${filename:0:-5}.mz5
	done
done

# remove temp directory
rmdir mz5_out