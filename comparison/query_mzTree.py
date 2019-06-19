#!/usr/bin/env python3

import io
import sys
import os
import subprocess
import shlex
import shutil
import time
import queryconf as qc

"""
*************** DECLARATIONS ***************
"""
# returns the time taken to run a command in seconds
def calctime(cmd):
    start = time.perf_counter()
    subprocess.run(shlex.split(cmd), stdout=subprocess.DEVNULL)
    return time.perf_counter() - start

# command templates
cmd_tree="java -Xmx24g -jar ../msDataServer/target/msDataServer-1.0.jar"

usage_str="usage: query_mzTree.py queryfile"

"""
*************** COMMAND LINE ARGUMENTS ***************
"""

if len(sys.argv) != 2:
    print(usage_str)
    sys.exit(1)

# map argv to files list
file_queries=sys.argv[1]

print(qc.file_mzTree, file_queries)


# make sure files exist
if not (os.path.isfile(qc.file_mzTree) and os.path.isfile(file_queries)):
    print("Please make sure all files exist.")
    sys.exit(1)


"""
*************** MZTREE BATCH + SUMMARY ***************
"""

# run mzTree in batch (open db once, multiple queries) mode and collect times
mzt_args = shlex.split(" ".join([cmd_tree,qc.file_mzTree,file_queries]))
mzt_batch = subprocess.run(mzt_args, stdout=subprocess.PIPE, universal_newlines=True)
mzt_batch_times = mzt_batch.stdout.split("\n")
mzt_summary = subprocess.run(mzt_args + [str(qc.num_points)], stdout=subprocess.PIPE, universal_newlines=True)
mzt_summary_times = mzt_summary.stdout.split("\n")

"""
*************** MZTREE, MZ5, MZML, MZDB ONE-OFF QUERIES ***************
"""

# read queries file
f_queries = open(file_queries, "r")

print("points,mzTree,mzTree-batched,mzTree-summ")

point_total, mzt_total, mzb_total, mzs_total = 0, 0, 0, 0

# iterate through queries
i = 0
for line in f_queries:

    # query bounds
    mzmin, mzmax, rtmin, rtmax = [float(x) for x in line.split(",")]
    
    # ----------- mzTree ------------
    mzt_cmd = "{} {} {} {} {} {}".format(cmd_tree, qc.file_mzTree, mzmin, mzmax, rtmin, rtmax)
    mzt_res = subprocess.run(shlex.split(mzt_cmd),
                             stdout=subprocess.PIPE, universal_newlines=True)
    mzt_time = mzt_res.stdout.split("\n")[0].split(",")[1]
    
    # corresponding mzTree_batch and mzt_summarized times (w/ a point_count reference)
    point_count, mzt_batch_time = mzt_batch_times[i].split(",")
    mzt_summ_time = mzt_summary_times[i].split(",")[1]
    
    # print results for this query, including corresponding batch/summarized times 
    print( "{},{},{},{}".format(point_count, mzt_time, mzt_batch_time, mzt_summ_time), flush=True)

    # accumulate # points and query times 
    point_total += int(point_count)
    mzt_total += float(mzt_time)
    mzb_total += float(mzt_batch_time)
    mzs_total += float(mzt_summ_time)

    i += 1

# print totals
print("{},{},{},{}".format(point_total, mzt_total, mzb_total, mzs_total))
