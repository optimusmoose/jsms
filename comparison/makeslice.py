#!/usr/bin/env python3

import random

from queryconf import mzmin, mzmax, rtmin, rtmax

mzspan = mzmax - mzmin
rtspan = rtmax - rtmin

def makerandom(mzlen, rtlen):
    mzlow = mzmin
    mzhigh = mzmax - mzlen
    mzr = mzhigh - mzlow
    
    rtlow = rtmin
    rthigh = rtmax - rtlen
    rtr = rthigh - rtlow
    
    mz = random.random() * mzr + mzlow
    rt = random.random() * rtr + rtlow
    return mz, mz+mzlen, rt, rt+rtlen
    
for x in range(50):
    # mzlen = [.25, .5, 1, 2, 4]
    mzlen = 2**(random.randrange(5)-2)
    rtlen = random.randrange(rtspan/2) + (rtspan/2)
    mzm, mzx, rtm, rtx = makerandom(mzlen, rtlen)
    print("{},{},{},{}".format(mzm, mzx, rtm, rtx))

for x in range(50):
    mzlen = random.randrange(mzspan/2) + (mzspan/2)
    rtlen = 2**(random.randrange(5)-2)
    mzm, mzx, rtm, rtx = makerandom(mzlen, rtlen)
    print("{},{},{},{}".format(mzm, mzx, rtm, rtx))
