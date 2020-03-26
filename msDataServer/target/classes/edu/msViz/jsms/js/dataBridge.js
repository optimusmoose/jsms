// dataAccess.js: communication with server-side API
function DataBridge(graph) {
    this.graph = graph;
    
    this.openCancelId = undefined;
    this.queueCancelIds = [];
    this.QUEUE_PLOT_DELAY = 200; // delay before drawing points
    this.REQUEST_DELAY = 100; // wait this many milliseconds before actually sending a request, in case another one shows up instead
    this.INTENSITY_MIN = 0; // minimum intensity to request from the server
}

var API_ROOT = "/api/v2";

// wait for a file to load on the server
DataBridge.prototype.openFileWaitLoop = function() {
    var self = this;
    
    // check the status of data model
    $.getJSON(API_ROOT + "/filestatus")
        .done(function(data, status, jqXHR) {
            
            switch(jqXHR.status)
            {
                case 200:
                    self.updateCommStatus("Data model completed");
                    
                    
                    if (data.mzmin - data.mzmax <= 1) {
                        data.mzmax += 0.1;
                        data.mzmin -= 0.1;
                    }
                    
                    if (data.rtmin - data.rtmax <= 1) {
                        data.rtmax += 0.1;
                        data.rtmin -= 0.1;
                    }
                    
                    // data is ready, decode the range
                    self.graph.dataRange = {
                        mzmin: data.mzmin, mzmax: data.mzmax, mzrange: data.mzmax - data.mzmin,
                        rtmin: data.rtmin, rtmax: data.rtmax, rtrange: data.rtmax - data.rtmin,
                        intmin: data.intmin, intmax: data.intmax, intrange: data.intmax - data.intmin,
                        progress: data.progress,
                    };
                    
                    // clear plot and set scope to returned area
                    self.graph.clearData();
                    self.graph.setViewingArea(self.graph.dataRange);
                    
                    // request points to plot
                    self.requestPointsFromServer();
                   
                    break;
                    
                case 204: // "No content"
                    self.updateCommStatus("No file loaded");
                    break;
            }
            
            
        })
        .fail(function(jqXHR, textStatus, errorThrown) {
            self.updateCommStatus(jqXHR.responseText);
            self.openCancelId = setTimeout(self.openFileWaitLoop.bind(self), 1000);
        });
};

// cancel waiting for the server
DataBridge.prototype.cancelFileWaitLoop = function() {
    if (this.openCancelId) {
        clearTimeout(this.openCancelId);
    }
};

// clears the graph and plots 'newPoints' after a short delay
DataBridge.prototype.queuePlotPoints = function(newPoints) {
    // cancel any existing plot requests
    while (this.queueCancelIds.length > 0) {
        clearTimeout(this.queueCancelIds.pop());
    }

    this.updateCommStatus("Drawing points...");
    this.graph.clearData();

    var self = this;
    var timeoutId = setTimeout(function() {
        self.graph.plotPoints(newPoints);
        self.graph.toolbar.checkNoiseEnabled();
        self.updateCommStatus("Ready");
    }, this.QUEUE_PLOT_DELAY);

    this.queueCancelIds.push(timeoutId);
}

// Requests the server for points in the current view range.
// When the server responds, the points will be plotted.
DataBridge.prototype.requestPointsFromServer = function() {
    // cancel any existing request that might be near-ready
    clearTimeout(this.requestCancelId);

    // set timeout: ask server for points 
    this.requestCancelId = setTimeout((function() {         
        
        // find plotting range
        var range = {mzmin: this.graph.viewRange.mzmin, mzmax: this.graph.viewRange.mzmax, rtmin: this.graph.viewRange.rtmin, rtmax: this.graph.viewRange.rtmax};
        
        // HTTP request parameters
        var params = {
            mzmin: range.mzmin, mzmax: range.mzmax, rtmin: range.rtmin, rtmax: range.rtmax,
            numpoints: this.graph.POINTS_PLOTTED_LIMIT, intmin: this.INTENSITY_MIN
        };

        this.updateCommStatus("Requesting points...");

        var self = this;
        // send HTTP GET request, accept JSON
        $.getJSON(API_ROOT + "/getpoints", params)
        
            // HTTP status codes 2XX
            .done(function(data, status, jqXHR) {
                switch(jqXHR.status){
                    case 200:
                        self.queuePlotPoints(data);
                        break;
                    case 204: // "No content"
                        self.updateCommStatus("No file loaded");
                        break;
                    default:
                        self.updateCommStatus("No points received");
                        break;
                }

            })
            
            // HTTP status codes 4XX/5XX
            .fail(function(jqXHR, textStatus, errorThrown) {
                self.updateCommStatus(jqXHR.responseText);
            });

        // also update the trace map
        $.getJSON(API_ROOT + "/gettracemap")
        
            // HTTP status codes 2XX
            .done(function(data, status, jqXHR) {
                switch(jqXHR.status){
                    case 200:
                        
                        self.graph.traceManager.setTraceMap(data);
                        break;
                    case 204: // "No content"
                        self.updateCommStatus("No file loaded");
                        break;
                    default:
                        self.updateCommStatus("No trace map received");
                        break;
                }

            })
            
            // HTTP status codes 4XX/5XX
            .fail(function(jqXHR, textStatus, errorThrown) {
                self.updateCommStatus(jqXHR.responseText);
            });

        // and the next IDs
        $.getJSON(API_ROOT + "/getnextids")

            // HTTP status codes 2XX
            .done(function(data, status, jqXHR) {
                switch(jqXHR.status){
                    case 200:
                        self.graph.traceManager.setNextIds(data);
                        break;
                    case 204: // "No content"
                        self.updateCommStatus("No file loaded");
                        break;
                    default:
                        self.updateCommStatus("No data");
                        break;
                }

            })

            // HTTP status codes 4XX/5XX
            .fail(function(jqXHR, textStatus, errorThrown) {
                self.updateCommStatus(jqXHR.responseText);
            });
    }).bind(this), this.REQUEST_DELAY);
};

// Updates segmentation data on the server side. See the server API
// for the format of "data". onSuccess and onFail are called with no arguments.
DataBridge.prototype.setPointsServer = function(data, onSuccess, onFail) {
    this.updateCommStatus("Saving segmentation...");
    
    var self = this;
    $.post({ url: API_ROOT + "/updatesegmentation", data: data, contentType: "application/json" })
        .done(function(data, status, jqXHR) {
            switch(jqXHR.status) {
                case 200:
                    self.updateCommStatus("Ready.");
                    onSuccess();
                    break;
                case 204: // "No content"
                    self.updateCommStatus("No data to save points to.");
                    onFail();
                    break;
            }
        })
        
        .fail(function(jqXHR, textStatus, errorThrown) {
            self.updateCommStatus("Failed to save points.");
            onFail();
        });
};

// Requests the highest intensity untraced point and
// runs the callback with an array-formatted point as the argument.
DataBridge.prototype.getHighestUntraced = function(callback)
{
    this.updateCommStatus("Finding next point...");
    
    var self = this;
    // send save instruction to server
    $.getJSON(API_ROOT + "/gethighestuntraced")
            
            // HTTP status codes 2XX
            .done(function(data,status,jqXHR){
				callback(data);
				self.updateCommStatus("Ready.");
            })
            
            // HTTP status codes 4XX/5XX
            .fail(function(jqXHR,textStatus,errorThrown){
                self.updateCommStatus("Could not get next point.");
            });
};

// Requests information for the specified envelopes.
// Runs the callback with an array of array-formatted envelope data
DataBridge.prototype.getEnvelopeInfo = function(envelopeIds, callback)
{
    this.updateCommStatus("Compiling bookmark report...");
    
    var self = this;
    
    // send query to server
    $.post({ url: API_ROOT + "/getenvelopeinfo", data: envelopeIds.join(","), dataType: "json" })
        // HTTP status codes 2XX
        .done(function(data,status,jqXHR){
            callback(data);
            self.updateCommStatus("Ready.");
        })
        
        // HTTP status codes 4XX/5XX
        .fail(function(jqXHR,textStatus,errorThrown){
            self.updateCommStatus("Bookmark report failed.");
        });
};

// Updates the on-screen display of the server status
DataBridge.prototype.updateCommStatus = function(status) {
    this.graph.containerEl.find(".commStatus").text(status);
};
