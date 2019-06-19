

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
tmpdir = "/tmp/tmpquery"
cmd_pwiz="msaccess --filter='msLevel 1' -o " + tmpdir + " -x"
cmd_tree="java -Xmx24g -jar ../msDataServer/target/msDataServer-1.0.jar"
cmd_mzdb="java -Xmx24g -jar /home/kyle/MS_applications_sources/mzdb-access/target/mzdb-access-0.5.0.jar -mzdb {} -mz1 {} -mz2 {} -t1 {} -t2 {}"

usage_str="usage: query.py queryfile"

"""
*************** COMMAND LINE ARGUMENTS ***************
"""

if len(sys.argv) != 2:
    print(usage_str)
    sys.exit(1)

# map argv to files list
file_queries=sys.argv[1]
print(qc.file_mzTree, qc.file_mz5, qc.file_mzdb, qc.file_mzml, file_queries)


# make sure files exist
if not (os.path.isfile(qc.file_mzTree)):
    print("mzTree file not found")
    sys.exit(1)

if not (os.path.isfile(qc.file_mz5)):
    print("mz5 file not found")
    sys.exit(1)

if not (os.path.isfile(qc.file_mzml)):
    print("mzml file not found")
    sys.exit(1)

if not (os.path.isfile(qc.file_mzdb)):
    print("mzdb file not found")
    sys.exit(1)


if not (os.path.isfile(file_queries)):
    print("query file not found")
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

print("points,mz5,mzML,mzdb,mzTree,mzTree-batched,mzTree-summ")

point_total, mz5_total, mzml_total,mzdb_total, mzt_total, mzb_total, mzs_total = 0, 0, 0, 0, 0, 0, 0

# iterate through queries
i = 0
for line in f_queries:

    # query bounds
    mzmin, mzmax, rtmin, rtmax = [float(x) for x in line.split(",")]
    
    # ----------- mz5 ------------
    mz5_time = calctime("{} \"slice mz={},{} rt={},{}\" {}".format(cmd_pwiz, mzmin, mzmax, rtmin, rtmax, qc.file_mz5))

    # ----------- mzML ------------
    mzml_time = calctime("{} \"slice mz={},{} rt={},{}\" {}".format(cmd_pwiz, mzmin, mzmax, rtmin, rtmax, qc.file_mzml))

    # delete the temp directory created by pwiz (mzml queries)
    shutil.rmtree(tmpdir, ignore_errors=True)
    
    # ----------- mzDB ------------
    mzdb_time = calctime(cmd_mzdb.format(qc.file_mzdb, mzmin, mzmax, rtmin, rtmax))
    
    # ----------- mzTree ------------
    # java -Xmx24g -jar ../msDataServer/target/msDataServer-1.0.jar file, mzmin, mzmax, rtmin, rtmax
    mzt_cmd = "{} {} {} {} {} {}".format(cmd_tree, qc.file_mzTree, mzmin, mzmax, rtmin, rtmax)
    mzt_res = subprocess.run(shlex.split(mzt_cmd),
                             stdout=subprocess.PIPE, universal_newlines=True)
    mzt_time = mzt_res.stdout.split("\n")[0].split(",")[1]
    
    # corresponding mzTree_batch and mzt_summarized times (w/ a point_count reference)
    point_count, mzt_batch_time = mzt_batch_times[i].split(",")
    mzt_summ_time = mzt_summary_times[i].split(",")[1]
    
    # print results for this query, including corresponding batch/summarized times 
    print( "{},{},{},{},{},{},{}".format(point_count, mz5_time, mzml_time, mzdb_time, mzt_time, mzt_batch_time, mzt_summ_time), flush=True)

    # accumulate # points and query times 
    point_total += int(point_count)
    mz5_total += float(mz5_time)
    mzml_total += float(mzml_time)
    mzdb_total += float(mzdb_time)
    mzt_total += float(mzt_time)
    mzb_total += float(mzt_batch_time)
    mzs_total += float(mzt_summ_time)

    i += 1

# print totals
print("{},{},{},{},{},{},{}".format(point_total, mz5_total, mzml_total, mzdb_total, mzt_total, mzb_total, mzs_total))
