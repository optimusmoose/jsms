// Implements tracking groups of points and sending updates to the server

// an object that tracks changes and sends them to the server in batches
function ChangeQueue(dataBridge) {
    this.dataBridge = dataBridge;
    
    this.actions = [];
    
    this.STANDARD_INTERVAL = 3000;
    this.RETRY_INTERVAL = 5000;
    this.pendingUpdates = 0;
    
    this.updateLoop();
}

// sets the trace t for the specified points
// must also specify the envelope e that the trace belongs to
ChangeQueue.prototype.setTrace = function(points, t) {
    this.actions.push({ type: "set-trace", trace: t, points: points });
    this.sendUpdate();
};

// sets the envelope e for the specified traces
ChangeQueue.prototype.setEnvelope = function(traces, e) {
    this.actions.push({ type: "set-envelope", envelope: e, traces: traces });
    this.sendUpdate();
};

//sets all points in viewrange not belonging to trace as noise (trace ID = -1)
ChangeQueue.prototype.setNoise = function(bounds, callback) {
    this.actions.push({ type: "rectangle", bounds: [bounds.mzmin, bounds.mzmax, bounds.rtmin, bounds.rtmax],
        id: -1, isAdd: true});
    this.sendUpdate(callback);
};

// sets a rectangle for trace within bounds{mzmin, mzmax, rtmin, rtmax}
ChangeQueue.prototype.addRectangle = function(trace, bounds, isAdd) {
    this.actions.push({ type: "rectangle", bounds: [bounds.mzmin, bounds.mzmax, bounds.rtmin, bounds.rtmax], id: trace, isAdd: isAdd });
    this.sendUpdate();
};

// requests an undo from the server
ChangeQueue.prototype.undo = function() {
    this.actions.push({ type: "undo" });
    this.sendUpdate(this.dataBridge.requestPointsFromServer.bind(this.dataBridge));
};

// requests a redo from the server
ChangeQueue.prototype.redo = function() {
    this.actions.push({ type: "redo" });
    this.sendUpdate(this.dataBridge.requestPointsFromServer.bind(this.dataBridge));
};

// gets a string (JSON) for the server command
ChangeQueue.prototype.getCommand = function() {
    return JSON.stringify(this.actions);
};

// sends the server command and clears the queue to prepare for more commands
ChangeQueue.prototype.sendUpdate = function(callback) {
    // get data to send to server
    var cmd = this.getCommand();
    
    // clear queue of commands
    this.actions = [];
    
    var self = this;
    
    function onFinish(success) {
        self.adjustPendingUpdates(-1);
        if (typeof callback === "function") {
			callback(success);
        }
    }
    
    // perform server request
    this.dataBridge.setPointsServer(cmd, function() { onFinish(true); }, function() { onFinish(false); });
    this.adjustPendingUpdates(1);
};

// updates the on-screen label of pending sever queries
ChangeQueue.prototype.adjustPendingUpdates = function(amount) {
    this.pendingUpdates += amount;
    this.dataBridge.graph.containerEl.find(".status-pending-updates").text(this.pendingUpdates);
};

// called to send update commands periodically
ChangeQueue.prototype.updateLoop = function() {
    // send changes if there are any, otherwise try again later
    if(this.hasChanges()) {
        this.sendUpdate(function(success) { window.setTimeout(self.updateLoop.bind(self), success ? self.STANDARD_INTERVAL : self.RETRY_INTERVAL); });
    } else {
        window.setTimeout(this.updateLoop.bind(this), this.STANDARD_INTERVAL);
    }
};

// boolean function checking for queued changes
ChangeQueue.prototype.hasChanges = function() {
    return this.actions.length > 0;
};

// class that manages all traces of points for a graph
function IsotopicTraceManager(graph) {
    this.graph = graph;
    this.changes = new ChangeQueue(graph.dataBridge);

    // these values are used to assign items to an empty (new) trace/envelope
    this.nextTraceNum = 1;
    this.nextEnvelopeNum = 1;
}

// gets the ids of the specified points
IsotopicTraceManager.getIDs = function(points) {
    return points.map(function(p) { return p.pointid; });
};

// sets the trace id for specified points, or null to indicate a new trace
// returns an IsotopicTrace object corresponding to the assigned trace
// if bypassQueue is set to true, then the list of points will not be sent to the server
// in case of e.g. rectangle select which is a separate server command
IsotopicTraceManager.prototype.setTrace = function(traceNum, points, bypassQueue) {

    if (traceNum === null || traceNum === this.nextTraceNum) {
        traceNum = this.nextTraceNum;
        this.nextTraceNum += (window.USER_COUNT !== undefined) ? window.USER_COUNT : 1;
    }
    
    if (traceNum < 0 || (traceNum % 1) !== 0) {
        throw new Error("Trace number must be a positive integer");
    }
    
    if (this.traceMap[traceNum] === undefined) {
        // "new" trace, need to create the envelope
        this.setEnvelope(0, [traceNum], true);
    }

    // set the trace variable on each point
    points.forEach(function(p) {
        p.trace = traceNum;
    });

    if (!bypassQueue) {
        // update the server's trace and envelope mappings
        this.changes.setTrace(IsotopicTraceManager.getIDs(points), traceNum);
    }
};

// sets the envelope for specified traces by trace number.
// envNum of null indicates that a new envelope is being set
// if bypassQueue is set, the envelope will not be sent to the server (useful for initial trace creation)
// returns the id of the new envelope
IsotopicTraceManager.prototype.setEnvelope = function(envNum, traceNums, bypassQueue) {

    if (envNum === null || envNum === this.nextEnvelopeNum) {
        envNum = this.nextEnvelopeNum;
        this.nextEnvelopeNum += (window.USER_COUNT !== undefined) ? window.USER_COUNT : 1;
    }

    if (envNum < 0 || (envNum % 1) !== 0) {
        throw new Error("Envelope number must be a positive integer");
    }

    traceNums.forEach(function(tNum) {
        if (tNum === 0 && envNum !== 0) {
            throw new Error("Cannot assign the non-trace to an envelope");
        }

        this.traceMap[tNum] = envNum;
    }, this);

    if (!bypassQueue) {
        this.changes.setEnvelope(traceNums, envNum);
    }
    return envNum;
};

//add set noise command to changes list
IsotopicTraceManager.prototype.setNoise = function(range, callback) {
    this.changes.setNoise(range, callback);
};

// removes traces from their envelope by ID
IsotopicTraceManager.prototype.unEnvelope = function(traceNums) {
    this.setEnvelope(0, traceNums);
};

// records the fact that a rectangle selection occurred to send the info to the server
// traceNum is an ID and bounds is {mzmin, mzmax, rtmin, rtmax}
IsotopicTraceManager.prototype.recordRectangle = function(traceNum, bounds, isAdd) {
    this.changes.addRectangle(traceNum, bounds, isAdd);
};

// gets an IsotopicTrace object by number
IsotopicTraceManager.prototype.getEnvelopeForTrace = function(traceNum) {
    var n = this.traceMap[traceNum];
    return n === undefined ? 0 : n;
};

// sets a new trace map and ensures that 0 maps to 0
IsotopicTraceManager.prototype.setTraceMap = function(newTraceMap) {
    this.traceMap = newTraceMap;
    this.traceMap[0] = 0;
    
    // TODO: implement better multi-user system
    // multi-user hack. ensures that the tracenums being used don't overlap
    if (window.USER_COUNT !== undefined && window.USER_NUM !== undefined) {
        while (this.nextTraceNum % window.USER_COUNT !== window.USER_NUM) {
            this.nextTraceNum++;
        }
        while (this.nextEnvelopeNum % window.USER_COUNT !== window.USER_NUM) {
            this.nextEnvelopeNum++;
        }
    }
};

// sets the next IDs to use for segmentation
IsotopicTraceManager.prototype.setNextIds = function(data) {
    this.nextTraceNum = data.nextTrace;
    this.nextEnvelopeNum = data.nextEnvelope;
};
