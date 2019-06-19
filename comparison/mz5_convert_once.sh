#!/bin/bash

# files to convert
filenames=( "CHPP_SDS_3002.mzML" 
		"GRAIN_DEVELOPMENT_Z71_3.mzML" 
		"STEM_12.mzML" 
		"18185_REP2_4pmol_UPS2_IDA_1.mzML" 
		"POLLEN_2.mzML" 
       		"Sheppard_Werner_RNAPORF145_09.mzML" 
	        "Sheppard_Werner_RNAPORF145_06.mzML" 
       		"Sheppard_Werner_RNAPORF145_03.mzML")

# create temp directory
for filename in "${filenames[@]}"
do
	msconvert ./mzml/$filename --mz5 --filter "mslevel 1" -o ./mz5_out --outfile $filename
	echo $filename
done
