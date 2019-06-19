#!/usr/bin/env python3

import random
import query_util as qutil
import sys
from queryconf import mzmin, mzmax, rtmin, rtmax
mzrange = mzmax - mzmin
rtrange = rtmax - rtmin

bounds = (mzmin,mzmax,rtmin,rtmax)
zoom_factor = 1.1
step_size = 0.1

if sys.argv[1] == "-w":
	query = qutil.crop_query(bounds,qutil.make_wide_query(bounds))
else:
	query = qutil.crop_query(bounds,qutil.make_tall_query(bounds))

#north, east, south, west, in, out
actions = ["N", "E", "S", "W", "I", "O"]
zoomactions = ["I", "O"]

# start going randomly
action = random.choice(actions)

for x in range(100):
    # 15% chance to change action, 50% chance to change if zooming
    if (action in zoomactions and random.random() < 0.5) or\
        (action not in zoomactions and random.random() < 0.15):
        action = random.choice(actions)
    
    query = qutil.crop_query(bounds,qutil.actions[action](query))

    print("{},{},{},{}".format(*query))

