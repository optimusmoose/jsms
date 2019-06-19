#!/usr/bin/env python3

import random
random.seed()

#****************************************
#				WIDE/TALL				#
#****************************************

# (mzmin, mzmax, rtmin, rtmax)
def make_tall_query(global_bounds):
	mzmin = global_bounds[0]
	mzmax = global_bounds[1]
	rtmin = global_bounds[2]
	rtmax = global_bounds[3]
	mzrange = mzmax - mzmin
	rtrange = rtmax - rtmin
	while True:
		# ( mz, rt )
		pointmz, pointrt = point_in_range(global_bounds)
		max_h = rtmax - pointrt

		# width is bounded by maximum available height to preserve the condition w < h
		width = random.random() * min(max_h,rtrange)

		# height bounded by max_h
		height = random.random() * max_h

		# (mzmin, mzmax, rtmin, rtmax)
		query = (pointmz, pointmz + width, pointrt, pointrt + height)
		query = crop_query(global_bounds,query)

		if is_tall(query):
			return query


# (mzmin, mzmax, rtmin, rtmax)
def make_wide_query(global_bounds):
	mzmin = global_bounds[0]
	mzmax = global_bounds[1]
	rtmin = global_bounds[2]
	rtmax = global_bounds[3]
	mzrange = mzmax - mzmin
	rtrange = rtmax - rtmin
	while True:
		# ( mz, rt )
		pointmz, pointrt= point_in_range(global_bounds)
		max_w = mzmax - pointmz

		# height is bounded by maximum available width to preserve the condition h < w
		height = random.random() * min(max_w,mzrange)

		# width bounded by max_w
		width = random.random() * max_w

		# (mzmin, mzmax, rtmin, rtmax)
		query = (pointmz, pointmz + width, pointrt, pointrt + height)
		query = crop_query(global_bounds,query)

		if is_wide(query):
			return query

#****************************************
#			TRAVERSAL ACTIONS			#
#****************************************

STEP_SIZE = .1
ZOOOM_FACTOR = 1.1

def N(bounds, step_size=STEP_SIZE):
	return (bounds[0],bounds[1],bounds[2]+step_size,bounds[3]+step_size)

def S(bounds, step_size=STEP_SIZE):
	return (bounds[0],bounds[1],bounds[2]-step_size,bounds[3]-step_size)

def E(bounds, step_size=STEP_SIZE):
	return (bounds[0]+step_size,bounds[1]+step_size,bounds[2],bounds[3])

def W(bounds, step_size=STEP_SIZE):
	return (bounds[0]-step_size,bounds[1]-step_size,bounds[2],bounds[3])

def I(bounds, zoom_factor=ZOOOM_FACTOR):
	mz_range = bounds[1] - bounds[0]
	rt_range = bounds[3] - bounds[2]
	new_mz_range = mz_range * zoom_factor
	new_rt_range = rt_range * zoom_factor
	return (bounds[0],bounds[0]+new_mz_range,bounds[2],bounds[2]+new_mz_range)


def O(bounds, zoom_factor=ZOOOM_FACTOR):
	mz_range = bounds[1] - bounds[0]
	rt_range = bounds[3] - bounds[2]
	new_mz_range = mz_range / zoom_factor
	new_rt_range = rt_range / zoom_factor
	return (bounds[0],bounds[0]+new_mz_range,bounds[2],bounds[2]+new_mz_range)

# easy switching access
actions = {"N":N,"S":S,"E":E,"W":W,"I":I,"O":O}

#****************************************
#				HELPERS					#
#****************************************

def is_tall(query):
	width = query[1] - query[0] 
	height = query[3] - query[2]
	return width < height

def is_wide(query):
	width = query[1] - query[0] 
	height = query[3] - query[2]
	return width > height

def crop_query(bounds, query):
	cropped = (max(bounds[0], query[0]), min(bounds[1], query[1]), max(bounds[2], query[2]), min(bounds[3], query[3]))
	return cropped

def point_in_range(bounds):
	x_range = bounds[1] - bounds[0]
	y_range = bounds[3] - bounds[2]

	x = (random.random() * x_range) + bounds[0]
	y = (random.random() * y_range) + bounds[2]
	return (x,y)

