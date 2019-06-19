// segmentui.js : isotopic trace/envelope drawing tools

SegmentEditor = function(graph) {
    this.graph = graph;
    
    // state variables
    this.addingOrModifying = false;
    this.selectedLines = new Set();
    this.selectedTraces = new Set();
    this.selectRectStart = new THREE.Vector3();
    this.selectRectEnd = new THREE.Vector3();
    this.editMode = "none";
    this.lastEditMode = "none";
    this.currentGroup = null;
    
    // color constants
    this.SELECTION_COLOR = 0xff0000;
    this.TRACE_UNTRACED_OPACITY = 0.5;
    this.BRUSH_INNER_GOOD = "#80ffff";
    this.BRUSH_OUTER_GOOD = "#000000";
    this.BRUSH_INNER_WARNING = "#80ffff";
    this.BRUSH_OUTER_WARNING = "#ff0000";
    this.CURRENT_BOOKMARK_COLOR = 0x6666ff;

    this.setBrushSize(40);
    this.setGuard(null);
    
    // make the selection rectangle
    var sRectGeo = new THREE.Geometry();
    for (var i=0; i<8; i++) { sRectGeo.vertices.push(new THREE.Vector3()); }
    var sRectMat = new THREE.LineBasicMaterial({ color: 0xff00ff });
    this.selectRect = new THREE.LineSegments(sRectGeo, sRectMat);
    this.selectRect.scale.set(this.graph.GRID_RANGE, 1, -this.graph.GRID_RANGE);
    this.selectRect.position.set(0, 0.1, this.graph.GRID_RANGE);
    this.graph.scene.add(this.selectRect);
    
    // begin listening for mouse events
    var el = graph.renderer.domElement;
    el.addEventListener( 'mousemove', this.selectionMouseMove.bind(this), false );
    el.addEventListener( 'mousedown', this.selectionMouseDown.bind(this), false );
    el.addEventListener( 'mouseup', this.selectionMouseUp.bind(this), false );
    
    // function that controls brush position and visibility
    var self = this;
    $(document).mousemove(function(e){
        var movingBrush = self.graph.containerEl.find('.moving-brush');
        var movingBrushDOM = movingBrush[0];
        if (e.target === el && self.graph.editor.editMode !== 'none') {
            if (movingBrushDOM.innerCircleRadius > 0 && !self.isRectangleSelect()) {
                el.style.cursor = 'none';
                movingBrush.css('display', 'initial');
                movingBrush.css('left', e.clientX - movingBrushDOM.width/2).css('top', e.clientY - movingBrushDOM.height/2);
            } else {
                el.style.cursor = 'crosshair';
                movingBrush.css('display', 'none');
            }
        } else {
            movingBrush.css('display', 'none');
            el.style.cursor = 'unset';
        }
    });

};

// returns true if rectangle selection mode is wanted by the user and currently available
SegmentEditor.prototype.isRectangleSelect = function() {
    return this.editMode === "trace" && (this.currentGroup !== null);
};

// gets a mouse vector based on the offset of a specific element (the real graph area, not the whole window)
SegmentEditor.getMouseVector = function(event, element) {
    return new THREE.Vector2( ( (event.clientX - element.offsetLeft) / element.offsetWidth) * 2 - 1,
    -( (event.clientY - element.offsetTop) / element.offsetHeight) * 2 + 1);
};
    
// returns an array of objects intersecting with the mouse by checking every visible line
SegmentEditor.prototype.getIntersections = function(event) {
    var mouse2D = SegmentEditor.getMouseVector(event, event.target);
    var raycaster = new THREE.Raycaster();
    raycaster.setFromCamera( mouse2D, this.graph.camera );

    var precisionSq = this.segmentationPrecision * this.segmentationPrecision;

    var intersections = [];
    this.graph.linesArray.forEach(function(line) {
        // calculate the position of `line` in world coordinates
        var bottom = new THREE.Vector3(0, 0, 0);
        var top = new THREE.Vector3(0, line.height, 0);
        line.localToWorld(bottom);
        line.localToWorld(top);

        if (raycaster.ray.distanceSqToSegment(bottom, top) < precisionSq) {
            if (this.editMode !== "trace" || this.isWithinGuard(line.mz)) {
                intersections.push(line);
            }
        }
    });
    return intersections;
};

// Finds the most frequent trace or envelope in 'intersects'. Returns 0 if nothing relevant is selected.
SegmentEditor.prototype.findMostFrequent = function(intersects) {
    var ids;
    if (this.editMode === "trace") {
        ids = intersects.map(function(i) { return i.trace; });
    } else {
        var traceManager = this.graph.traceManager;
        ids = intersects.map(function(i) { return traceManager.getEnvelopeForTrace(i.trace); });
    }

    // keep only IDs that are not 0 ("no trace/envelope") or -1 ("noise" trace)
    ids = ids.filter(function(id) { return (id !== 0 && id !== -1); });

    var counts = {};
    var maximum = 0;
    var mostFrequentId = 0;

    ids.forEach(function(id) {
        // update the count for this id
        counts[id] = counts[id] ? (counts[id] + 1) : 1;

        // check if this is a new maximum
        if (counts[id] > maximum) {
            maximum = counts[id];
            mostFrequentId = id;
        }
    });

    return mostFrequentId;
};

// adds lines or traces in intersects to selectedLines or selectedTraces
SegmentEditor.prototype.addSelections = function(intersects) {
    if (this.editMode === "trace") {
        intersects.forEach(this.selectedLines.add.bind(this.selectedLines));
    } else if (this.editMode === "envelope") {
        intersects.map(function(l) {return l.trace;}).forEach(this.selectedTraces.add.bind(this.selectedTraces));
    }
};

// begins selection of a group to work with, or begins tracking as the mouse moves
SegmentEditor.prototype.selectionMouseDown = function( event ) {
    //return if not in editMode or if the event is not a left mouse button event
    if (event.which !== 1 || this.editMode === "none") { return; }
    event.preventDefault();

    // disable rotation and controls while mouse is down
    this.graph.graphControls.enableRotate = false;
    this.graph.dataControls.enabled = false;

    var intersects = this.getIntersections(event);
    this.addSelections(intersects);

    this.addingOrModifying = (this.currentGroup !== null);

    if (this.addingOrModifying) {
        // there is already a selected trace/envelope
        if (this.isRectangleSelect()) {
            // start a new rectangle selection
            this.selectRectStart = this.graph.getMousePosition(event);
            this.selectRectEnd.copy(this.selectRectStart);
        }
    } else {
        // the user is choosing a trace/envelope to be the current trace/envelope
        // and store null as the currentGroup when it would be 0
        this.currentGroup = this.findMostFrequent(intersects, this.editMode) || null;
        this.colorizeSelections(intersects, this.currentGroup);
    }
};

// continues adding to a selection
SegmentEditor.prototype.selectionMouseMove = function( event ) {
    if (this.editMode === "none") { return; }
    event.preventDefault();

    // mouse move only tracks if a trace/envelope is active
    if (!this.addingOrModifying) { return; }

    if (this.isRectangleSelect() && this.selectRectStart !== null) {
        // update rectangle position
        var mousePoint = this.graph.getMousePosition(event);
        if (mousePoint === null) {
            mousePoint = this.selectRectStart;
        }
        this.selectRectEnd.copy(mousePoint);
        this.updateSelectRect(this.selectRectStart.x, this.selectRectEnd.x, this.selectRectStart.z, this.selectRectEnd.z);

        // get the range in mz,rt dimensions
        var range = this.graph.vectorsToMzRt(this.selectRectStart, this.selectRectEnd);
        this.constrainRange(range);

        // update the list of rectangle-selected lines
        // and reset the line colors
        this.selectedLines.clear();
        this.graph.linesArray.filter(function(l) {
            return l.mz > range.mzmin && l.mz < range.mzmax && l.rt > range.rtmin && l.rt < range.rtmax;
        }).forEach(this.selectedLines.add.bind(this.selectedLines));
        this.resetLineColors();
    } else {
        // add moused-over points to the selection list
        var intersects = this.getIntersections(event);
        this.addSelections(intersects);
        this.colorizeSelections(intersects);
    }
};

// finalizes a selection
SegmentEditor.prototype.selectionMouseUp = function( event ){
    //return if not in editMode or if the left mouse button was not released
    if (event.which !== 1 || this.editMode === "none") { return; }

    this.graph.dataControls.enabled = true;
    if (this.graph.toolbar.is2dSelected()) {
        // enable rotate if in 3d mode, keep disabled if in 2d mode.
        this.graph.graphControls.enableRotate = true;
    }

    if (!this.addingOrModifying) {
        // the user was choosing an IT/IE to be the current group
        this.selectedLines.clear();
        this.selectedTraces.clear();
        this.updateIndicator();
        this.resetLineColors();
        if (this.editMode === "pair" && this.currentGroup) {
            this.graph.toolbar.setBookmarkPair(this.currentGroup);
        }
        return;
    }

    // set destination group based on removing or adding to selection
    var removing = event.ctrlKey;
    var destinationGroup = removing ? 0 : this.currentGroup;
    var sourceGroup = removing ? this.currentGroup : 0;

    if (this.currentGroup !== null) {
        if (this.editMode === "trace") {
            // filter to lines that are in sourceGroup, or are noise points when untraced points are the source group
            var changingLines = Array.from(this.selectedLines)
                .filter(function(l) { return l.trace === sourceGroup || (sourceGroup === 0 && l.trace === -1); });

            // update the traceManager with the current selection
            if (this.isRectangleSelect() && this.selectRectStart !== null) {
                this.selectRect.visible = false;
                var range = this.graph.vectorsToMzRt(this.selectRectStart, this.selectRectEnd);
                this.constrainRange(range);
                this.graph.traceManager.setTrace(destinationGroup, changingLines, true);
                this.graph.traceManager.recordRectangle(this.currentGroup, range, !removing);
            } else {
                if (changingLines.length > 0) {
                    this.graph.traceManager.setTrace(destinationGroup, changingLines);
                }
            }
        } else if (this.editMode === "envelope") {
            // don't set the envelope of the not-trace or the noise trace,
            // and only modify traces that are in sourceGroup
            var traceManager = this.graph.traceManager;
            var changingTraces = Array.from(this.selectedTraces)
                .filter(function(tid) { return tid !== 0 && tid !== -1 && traceManager.getEnvelopeForTrace(tid) === sourceGroup; });

            if (changingTraces.length > 0) {
                this.graph.traceManager.setEnvelope(destinationGroup, changingTraces);
            }
        }
    }

    this.selectedLines.clear();
    this.selectedTraces.clear();
    this.addingOrModifying = false;
    this.resetLineColors();
};

// colorize the currently-being-selected points/traces while the mouse button is being dragged
SegmentEditor.prototype.colorizeSelections = function(intersects, selectedElement) {
    if (this.editMode === "trace") {
        if (this.addingOrModifying) {
            // the user is adding points to a trace
            intersects.forEach(function(line) {
                this.setColorOpacity(line, this.SELECTION_COLOR, 1);
            }, this);
            this.graph.renderImmediate();
        } else {
            //the user is selecting a trace to be the current trace
            this.graph.linesArray.forEach(function(line) {
                if (line.trace === selectedElement) {
                    this.setColorOpacity(line, this.SELECTION_COLOR, 1);
                }
            }, this);
            this.graph.renderDelayed();
        }
    } else if (this.editMode === "envelope") {
        // if the user already has a current envelope and wishes to modify it, addingOrModifying == true and therefore null is passed into the function for selectedEnvelope
        // if the user is selecting an envelope to be the current envelope, addingOrModifying == false and therefore selectedElement is passed into the function for selectedEnvelope
        this.colorizeLinesEnvelopeMode(this.addingOrModifying ? null : selectedElement);
        this.graph.renderDelayed();
    }
};

// resets all the line colors when switching edit and view modes or after a new selection
SegmentEditor.prototype.resetLineColors = function() {
    if (this.editMode === "trace") {
        // just set each trace to their color
        this.graph.linesArray.forEach(function(line) {
            var newColor, newOpacity = 1;
            if (this.selectedLines.has(line)) {
                newColor = this.SELECTION_COLOR;
            } else if (line.trace === 0) {
                newColor = line.maincolor;
                newOpacity = this.TRACE_UNTRACED_OPACITY;
            } else {
                newColor = SegmentColors.getTraceColor(line.trace);
            }
            this.setColorOpacity(line, newColor, newOpacity);
        }, this);
    } else if (this.editMode === "envelope") {
        this.colorizeLinesEnvelopeMode();
    } else if (this.editMode === "pair") {
        this.colorizeLinesEnvelopeMode();
    } else {
        // view-only mode, so use "main" (original) color
        this.graph.linesArray.forEach(function(line) {
            this.setColorOpacity(line, null, 1);
        }, this);
    }
    this.graph.renderDelayed();
};

// Colorize lines for envelope mode: prefer envelope's color, trace color if not in an envelope, and more transparent for untraced points.
// If selectedEnvelope is a nonzero integer, points in that envelope will be colored according to SELECTION_COLOR instead.
SegmentEditor.prototype.colorizeLinesEnvelopeMode = function(selectedEnvelope) {
    this.graph.linesArray.forEach(function(line) {
        // first try setting color by envelope, if no envelope then by trace
        var trace = line.trace;
        var envelope = this.graph.traceManager.getEnvelopeForTrace(trace);
        var noEnvelope = !envelope;

        var newColor = 0;
        var newOpacity = 1;

        var currentBookmark = this.graph.bookmarks.getCurrent();
        var currentBookmarkPair = currentBookmark ? currentBookmark.envelope : null;

        if (selectedEnvelope && envelope === selectedEnvelope) {
            // point is in a currently selected envelope
            newColor = this.SELECTION_COLOR;
        } else if (this.addingOrModifying && trace !== 0 && this.selectedTraces.has(trace)) {
            // point is in a selected trace
            newColor = this.SELECTION_COLOR;
        } else if (noEnvelope) {
            // point is not in an envelope
            newColor = SegmentColors.getTraceColor(trace);
            newOpacity = (trace === 0) ? 0.2 : 0.5;
        } else if (envelope === currentBookmarkPair) {
            // currently selected bookmark
            newColor = this.CURRENT_BOOKMARK_COLOR;
        } else {
            // in a regular envelope
            newColor = SegmentColors.getEnvelopeColor(envelope);
        }

        this.setColorOpacity(line, newColor, newOpacity);
    }, this);
};

// sets the color and opacity of a line and its pinhead
SegmentEditor.prototype.setColorOpacity = function(line, newColor, opacity) {
    var lmat = line.material;

    if (newColor === null) newColor = line.maincolor;

    lmat.color.set(newColor);
    lmat.opacity = opacity;
    lmat.transparent = (opacity !== 1);

    if (line.pinhead) {
        var pmat = line.pinhead.material;
        pmat.color.set(newColor);
        pmat.opacity = lmat.opacity;
        pmat.transparent = lmat.transparent;
    }
};


SegmentEditor.prototype.setAllNoise =  function(callback) {
    this.graph.traceManager.setNoise(this.graph.viewRange, callback);
    this.resetLineColors();
};

// set edit mode to "trace", "envelope", "pair" (for bookmarks), or "none"
SegmentEditor.EDIT_MODES = ["trace", "envelope", "pair", "none"];
SegmentEditor.prototype.setEditMode = function(newMode) {
    if (SegmentEditor.EDIT_MODES.indexOf(newMode) === -1) {
        throw new Error("Not a valid editing mode!");
    }
    this.selectedLines.clear();
    this.selectedTraces.clear();

    if (this.editMode === newMode) {
        // clicking same mode "unsets" all modes (go to none)
        this.lastEditMode = this.editMode;
        this.editMode = "none";
    } else {
        var oldMode = this.editMode;
        this.editMode = newMode;
        
        if (newMode !== "none") {
            // cases to immediately activate the "select" mode: switching between two modes,
            // switching out of "none" mode but not back to the last mode, or switching to pair mode
            if (oldMode !== "none" || newMode !== this.lastEditMode || newMode === "pair") {
                this.selectGroup();
            }
            this.makeBrushSizeCursor();
        }
        this.lastEditMode = oldMode;
    }

    this.resetLineColors();
    this.graph.toolbar.updateEditModeStates();
};

// set the brush size for editing
SegmentEditor.prototype.setBrushSize = function(newSize) {
    this.segmentationPrecision = 0.02 + (Math.pow(newSize,2)/7200);
};

// this function creates a image indicating function the size of the brush on canvas 'moving-brush' that moves with the mouse
SegmentEditor.prototype.makeBrushSizeCursor = function() {

    // get canvas
    var brush = this.graph.containerEl.find('.moving-brush')[0];
    var ctx = brush.getContext('2d');
    
    var isWarning = (this.editMode === "trace") && (this.graph.nPointsVisible >= this.graph.POINTS_PLOTTED_LIMIT);

    var brushSizeConstant = 0.03946372239; // empiracle constent used for calculating brush size indicator size
    var ratio = window.devicePixelRatio*this.segmentationPrecision*brushSizeConstant;  //used for calculating circle radius based on segmentation precision

    ctx.clearRect(0,0,brush.width,brush.height);

    // inner circle
    ctx.lineWidth = 3;
    ctx.strokeStyle = isWarning ? this.BRUSH_INNER_WARNING : this.BRUSH_INNER_GOOD;
    ctx.beginPath();
    var smallestCanvasDimension = Math.min(window.innerWidth, window.innerHeight); // The graph will be sized according to the smallest dimension of the canvas holding the graph. This calculates the smallest dimension of that canvas.
    var innerCircleRadius = ratio*smallestCanvasDimension - 3;

    // the innerCircleRadius is stored in the 'moving-brush' element, as this is used to determine if the canvas should be displayed
    // or if a crosshair cursor should be displayed
    brush.innerCircleRadius = innerCircleRadius;

    // if the innerCircleRadius is less than 0, ctx.arc breaks. It will be replaced with a crosshair in this case anyway so we return from the function.
    if (innerCircleRadius < 0) {return;}
    ctx.arc(brush.width/2,brush.height/2,innerCircleRadius,0,2*Math.PI);
    ctx.stroke();

    // outer circle
    ctx.lineWidth = 3;
    ctx.strokeStyle = isWarning ? this.BRUSH_OUTER_WARNING : this.BRUSH_OUTER_GOOD;
    ctx.beginPath();
    var outerCircleRadius = ratio*smallestCanvasDimension;
    ctx.arc(brush.width/2,brush.height/2,outerCircleRadius,0,2*Math.PI);
    ctx.stroke();
};

// this function updates the color of the filled circle indicating the current selected trace or envelope
// it should be called whenever this circle's color needs to change.
SegmentEditor.prototype.updateIndicator = function() {
    var color;
    if (this.editMode == 'trace') {
        color = SegmentColors.getTraceColor(this.currentGroup);
    } else if (this.editMode == 'envelope') {
        color = SegmentColors.getEnvelopeColor(this.currentGroup);
    }

    if (typeof color === 'number') {
        color = SegmentColors.colorAsString(color);
    }
    this.graph.containerEl.find('.indicator-circle').attr('fill', color);
    this.graph.containerEl.find('.indicator-text').text(this.currentGroup);
    this.graph.drawDataLabels();
};

// update the selection rectangle to be at the specified location
SegmentEditor.prototype.updateSelectRect = function(xmin, xmax, zmin, zmax) {
    var geo = this.selectRect.geometry;
    geo.vertices.splice(0);
    
    var vMinMin = new THREE.Vector3(xmin, 0, zmin);
    var vMinMax = new THREE.Vector3(xmin, 0, zmax);
    var vMaxMax = new THREE.Vector3(xmax, 0, zmax);
    var vMaxMin = new THREE.Vector3(xmax, 0, zmin);
    geo.vertices.push(
        vMinMin.clone(), vMinMax.clone(), vMinMax.clone(), vMaxMax.clone(),
        vMaxMax.clone(), vMaxMin.clone(), vMaxMin.clone(), vMinMin.clone()
    );
    
    geo.verticesNeedUpdate = true;
    this.selectRect.visible = true;
    this.graph.renderImmediate();
};

// switch to selecting an existing group to edit
SegmentEditor.prototype.selectGroup = function() {
    this.selectedLines.clear();
    this.selectedTraces.clear();

    this.currentGroup = null;
    this.updateIndicator();
};

// switch to a new group to edit
SegmentEditor.prototype.newGroup = function() {
    this.selectedLines.clear();
    this.selectedTraces.clear();

    // create a blank trace and retrieve its id
    if (this.editMode == "trace") {
        this.currentGroup = this.graph.traceManager.nextTraceNum;
    } else if (this.editMode == 'envelope') {
        this.currentGroup = this.graph.traceManager.nextEnvelopeNum;
    }
    this.updateIndicator();
};

// gets appropriate status line text describing what is in the current trace/envelope
SegmentEditor.prototype.getGroupStatusText = function() {
    var self = this;
    var count = null;
    if (this.editMode === "none" || !this.currentGroup) {
        return "No trace or envelope selected.";
    } else if (this.editMode === "trace") {
        // trace mode: use number of rendered lines that are part of the current trace
        count = this.graph.linesArray.filter(function(l) { return l.trace == self.currentGroup; }).length;
        return "Trace #" + this.currentGroup + ", " + count + " points rendered.";
    } else if (this.editMode === "envelope") {
        // envelope mode: use total number of traces in this envelope
        var traces = Object.keys(this.graph.traceManager.traceMap);
        count = traces.filter(function(t) { return self.graph.traceManager.traceMap[t] == self.currentGroup; }).length;
        return "Envelope #" + this.currentGroup + ", made up of " + count + " traces.";
    } else if (this.editMode === "pair") {
        // pair mode: prompt for a pairing
        return "Select an envelope to pair with this bookmark.";
    }
};

// sets a "guard" that trace segmentation will respect
// if "mz" is null, the guard will be cleared
SegmentEditor.prototype.setGuard = function(mz, width) {
    this.guardMz = mz;
    this.guardWidth = width;
};

SegmentEditor.prototype.setGuardWidth = function(width) {
    this.guardWidth = width;
};

// checks whether "mz" is within the defined guard
SegmentEditor.prototype.isWithinGuard = function(mz) {
    if (!this.guardMz) { return true; }

    return (mz > this.guardMz - this.guardWidth/2) &&
        (mz < this.guardMz + this.guardWidth/2);
};

// constrains a "range" minimum and maximum to lie within the guard
SegmentEditor.prototype.constrainRange = function(range) {
    if (!this.guardMz) { return range; }
    if (range.mzmin < this.guardMz - this.guardWidth/2) {
        range.mzmin = this.guardMz - this.guardWidth/2;
    }
    if (range.mzmax > this.guardMz + this.guardWidth/2) {
        range.mzmax = this.guardMz + this.guardWidth/2;
    }
};
