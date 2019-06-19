# msDataServer Web API

###HTTP GET /*
(.html, .js, etc.)
		
Serves the contents of the "jsms" folder (i.e. the user interface files).

---

###HTTP GET /api/v2/filestatus
Checks the status of the server's open-file process. Returns additional file data if a file has been loaded and data model is ready.. 

####Server response:
		
	HTTP 200 (OK): File has been loaded and ata model is ready, begin querying for points.
		Payload: { "mzmin" : float, "mzmax" : float, "rtmin" : float, "rtmax" : float, "intmin" : float, "intmax" : float, "pointcount": integer, "progress": float }
	HTTP 204 (No Content): No file has been selected, open a file before continuing.
	HTTP 400 (Bad Request): Malformed request (usually a missing parameter)
	HTTP 403 (Forbidden): The file is currently being selected
	HTTP 406 (Not Acceptable): The selected file is of the wrong file format, reselect file before continuing.
	HTTP 409 (Conflict): The server is selecting a file or processing the selected file. Continue checking file status.

---

###HTTP GET /api/v2/getpoints

Queries the database for points to display on the graph. Requests a specific number of points within the given bounds. Server determines the detail level that would provide the same or more points than requested and samples this set to the requested number of points.

####URL parameters:
	mzmin (double): mz lower bound (0 for global mz minimum)
	mzmax (double): mz upper bound (0 for global mz maximum)
	rtmin (float): rt lower bound (0 for global rt minimum)
	rtmax (float): rt upper bound (0 for global rt maximum)
	numpoints (int): the number of points to be returned (0 for no limit)

####Server response:
	HTTP 200 (OK): Query successfully serviced, returning points
		Payload: [[<pointId>,<traceId>,<mz>,<rt>,<intensity>], ... ]
	HTTP 204 (No Content): No file has been selected, open a file before continuing.
	HTTP 400 (Bad Request): Malformed request, missing parameter or invalid query range (i.e. mzmin > mzmax).
	HTTP 406 (Not Acceptable): The previously selected file is of the wrong file format, reselect file before continuing.
	HTTP 409 (Conflict): The server is selecting a file or processing the selected file. Continue checking file status.

---

###HTTP GET /api/v2/gethighestuntraced
		
Queries the database for the highest intensity point that has not yet been assigned to a trace.
	
####Server response:
	HTTP 200 (OK): Query serviced, returning a single point
		[<pointId>, <traceId>, <mz>, <rt>, <intensity>]
	HTTP 204 (No Content): No file has been selected, open a file before continuing.
	HTTP 400 (Bad Request): Malformed request, missing parameter or invalid query range (i.e. mzmin > mzmax).
	HTTP 406 (Not Acceptable): The previously selected file is of the wrong file format, reselect file before continuing.
	HTTP 409 (Conflict): The server is selecting a file or processing the selected file. Continue checking file status.

---

###HTTP GET /api/v2/gettracemap
Returns the current Trace Map containing traceID->envelopeID mappings.

####Server response:
	HTTP 200 (OK): Query successfully serviced, returning Trace Map
		Payload: { "<traceID>":<envID>, ... }
	HTTP 204 (No Content): No file has been selected, open a file before continuing.
	HTTP 406 (Not Acceptable): The previously selected file is of the wrong file format, reselect file before continuing.
 	HTTP 409 (Conflict): The server is selecting a file or processing the selected file. Continue checking file status.

---

###HTTP GET /api/v2/getnextids
Returns the next trace ID and envelope ID. "Next" is defined as the ID after the largest used ID for that type.

####Server response:
	HTTP 200 (OK): Query successfully serviced, returning next IDs
		Payload: { "nextTrace":<traceId>, "nextEnvelope":<envelopeId> }
	HTTP 204 (No Content): No file has been selected, open a file before continuing.
	HTTP 406 (Not Acceptable): The previouslyj selected file is of the wrong file format, reselect file before continuing.
 	HTTP 409 (Conflict): The server is selecting a file or processing the selected file. Continue checking file status.

---

### HTTP POST /api/v2/updatesegmentation
Sends segmentation modifications to the server to update the mzTree data model. 
####POST Parameters (JSON):
	[ <action>, <action>, ... ]
	Action format one of following:
		{ "type": "undo" }
		{ "type": "redo" }
		{ "type": "set-trace", "trace": <traceid>, "points": [<pointid>, ...] }
		{ "type": "set-envelope", "envelope": <envelopeid>, "traces": [<traceid>, ...] }
		{ "type": "rectangle", "bounds": [<lower_mz>, <upper_mz>, <lower_rt>, <upper_rt>], "id": <traceID>, "isAdd": <boolean> }

	
####Server response:
	HTTP 200 (OK): Successfully updated segmentation data.
	HTTP 500 (INTERNAL_SERVER_ERROR): Failed to update segmentation data.

---

### HTTP POST /api/v2/getenvelopeinfo
Requests that the server recompile trace data, and returns envelope statistics.
####POST Parameters (urlencoded):
	id1,id2,id3,...
	where each id is an envelope ID

####Server response:
	HTTP 200 (OK): Successful query, returning envelope information
		Payload: { "env_id": [ env_mz, env_rt, env_intensity, [ [trace1_mz, trace1_intensity], [trace2_mz, trace2_intensity], ... ] ], ... }
	HTTP 400 (Bad Request): Malformed request (a given ID was not parseable as an integer)
	HTTP 500 (INTERNAL_SERVER_ERROR): An error occurred.
