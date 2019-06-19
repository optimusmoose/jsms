#!/usr/bin/env python3

import random
import sys

from queryconf import mzmin, mzmax, rtmin, rtmax
import query_util as qutil 
bounds = (mzmin,mzmax,rtmin,rtmax)

query_func = qutil.make_wide_query if sys.argv[1] == "-w" else qutil.make_tall_query


for x in range(100):
    query = query_func(bounds)
    print("{},{},{},{}".format(*query))
