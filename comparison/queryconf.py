#!/usr/bin/env python3

num_points = 5000

file_name = "/home/kyle/Documents/data/qref/OEMMA"
mzmin, mzmax, rtmin, rtmax = 350, 1800, 0, 155

#file_name = "qref/18185_query_ref"
#mzmin, mzmax, rtmin, rtmax = 400, 1300, 0, 7200

#file_name = "qref/sample2"
#mzmin, mzmax, rtmin, rtmax = 500, 2500, 5, 55

file_mzTree = file_name + ".mzTree"
file_mz5 = file_name + ".mz5" 
file_mzml = file_name + ".mzML"
file_mzdb = file_name + ".mzDB"
