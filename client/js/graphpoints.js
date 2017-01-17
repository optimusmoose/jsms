// graphpoints.js : draws and manages the points on the screen and panning/zooming

// plots multiple points on the graph and redraws it
MsGraph.prototype.plotPoints = function(points) {
    // Plots points on the graph immediately. points should be an array of coordinate arrays.
    for (var i = 0; i < points.length && this.linesArray.length < this.POINTS_PLOTTED_LIMIT; i++) {
        this.plotPoint(points[i]);
    }
    
    // make sure the groups are plotted and update the view
    
    this.repositionPlot(this.viewRange);
    this.updateViewRange(this.viewRange);
};

MsGraph.PIN_GEO = new THREE.PlaneGeometry(0.2, 0.2);
// plots a single point on the graph
MsGraph.prototype.plotPoint = function(point) {
    // point: an array of the following values
    var id = point[0];
    var mz = point[1];
    var rt = point[2];
    var inten = point[3];
    
    // find line color and calculate geometry
    var gradientscale = this.USE_LOG_SCALE_COLOR ? Math.log(inten) / Math.log(this.dataRange.intmax) : inten / this.dataRange.intmax;
    gradientscale = Math.max(0, Math.min(gradientscale, 1)); // must be in range 0-1
    
    var gradientindex = Math.floor((this.gradientCache.length - 1) * gradientscale);
    var linemat = new THREE.LineBasicMaterial({color: this.gradientCache[gradientindex]});
    var pinmat = new THREE.MeshBasicMaterial({color: this.gradientCache[gradientindex]});
    var geo = new THREE.Geometry();
    
    var x = mz;
    var y = this.USE_LOG_SCALE_HEIGHT ? Math.log(inten) : inten;
    var z = rt;
    
    geo.vertices.push(
        new THREE.Vector3(x, 0, z),
        new THREE.Vector3(x, y, z)
    );
    
    // create the line part
    var line = new THREE.Line(geo, linemat);
    line.maincolor = linemat.color.clone();
    
    line.pointid = id;
    line.mz = mz;
    line.rt = rt;
    line.int = inten;
    
    // create the pinhead
    var pinhead = new THREE.Mesh(MsGraph.PIN_GEO, pinmat);
    pinhead.position.set(x, y, z);
    pinhead.rotateX(-Math.PI/2);
    
    line.pinhead = pinhead;
    
    this.linesArray.push(line);
    this.plotGroup.add(line);
    this.pinGroup.add(pinhead);
};

// scales and positions the plot depending on the new viewing range
MsGraph.prototype.repositionPlot = function(r) {
    // set plot positions and scales
    var heightScale = this.USE_LOG_SCALE_HEIGHT ? Math.log(this.dataRange.intmax) : this.dataRange.intmax;
    this.datagroup.scale.set(this.GRID_RANGE / r.mzrange, this.GRID_RANGE_VERTICAL / heightScale, -this.GRID_RANGE / r.rtrange);
    this.pinGroup.children.forEach(function(p) {
        // y and z are swapped from expectation because the pinheads are rotated
        p.scale.set(r.mzrange / this.GRID_RANGE, -r.rtrange / this.GRID_RANGE, 1);
    }, this);
    
    this.datagroup.position.set((-r.mzmin) * this.datagroup.scale.x, 0, this.GRID_RANGE + (-r.rtmin) * this.datagroup.scale.z);
    
    // update the set of visible points
    var nVisible = 0, nInRange = 0;
    var intmin = Infinity, intmax = 0;
    this.plotGroup.children.forEach(function(p) {
        p.visible = false;
        if (p.mz >= r.mzmin - 1e-4 && p.mz <= r.mzmax + 1e-4 && p.rt >= r.rtmin - 1e-4 && p.rt <= r.rtmax + 1e-4) {
            nInRange++;
            if (nVisible < this.POINTS_VISIBLE_LIMIT) {
                p.visible = true;
                nVisible++;
                intmin = Math.min(intmin, p.int);
                intmax = Math.max(intmax, p.int);
            }
        }
        p.pinhead.visible = p.visible;
    }, this);
    
    this.nPointsVisible = nVisible;
    this.nPointsInRange = nInRange;
    
    r.intmin = intmin;
    r.intmax = intmax;
    
    // update edge indicators
    MsGraph.emptyGroup(this.edgesGroup);
    if (r.mzmin <= this.dataRange.mzmin + 1e-4) {
        this.makeEdgeLine(r.mzmin, r.mzmin, r.rtmin, r.rtmax);
    }
    if (r.mzmax >= this.dataRange.mzmax - 1e-4) {
        this.makeEdgeLine(r.mzmax, r.mzmax, r.rtmin, r.rtmax);
    }
    if (r.rtmin <= this.dataRange.rtmin + 1e-4) {
        this.makeEdgeLine(r.mzmin, r.mzmax, r.rtmin, r.rtmin);
    }
    if (r.rtmax >= this.dataRange.rtmax - 1e-4) {
        this.makeEdgeLine(r.mzmin, r.mzmax, r.rtmax, r.rtmax);
    }
    
    // update tick marks
    MsGraph.emptyGroup(this.ticksGroup);
    
    // calculate tick frequency
    var mzSpacing = Math.pow(10, Math.floor(Math.log(r.mzrange)/Math.log(10) - 0.5));
    var rtSpacing = Math.pow(10, Math.floor(Math.log(r.rtrange)/Math.log(10) - 0.5));
    
    // place mz marks...
    var mzFirst = r.mzmin - (r.mzmin % mzSpacing);
    for (var mz = mzFirst + mzSpacing; mz < r.mzmax; mz += mzSpacing) {
        this.makeTickMark(true, mz, r, Math.abs(mz / (mzSpacing * 10) %1) <= 1e-4 ? 0.05 : 0.02);
    }
    
    // ...and rt marks
    var rtFirst = r.rtmin - (r.rtmin % rtSpacing);
    for (var rt = rtFirst + rtSpacing; rt < r.rtmax; rt += rtSpacing) {
        this.makeTickMark(false, rt, r, Math.abs(rt / (rtSpacing * 10) %1) <= 1e-4 ? 0.05 : 0.02);
    }
};

// draws a line for the edge of the data to indicate can't pan any more
MsGraph.prototype.makeEdgeLine = function(mzmin, mzmax, rtmin, rtmax) {
    var edgeGeo = new THREE.Geometry();
    edgeGeo.vertices.push(new THREE.Vector3(mzmin, 0, rtmin));
    edgeGeo.vertices.push(new THREE.Vector3(mzmax, 0, rtmax));
    var edgeMat = new THREE.LineBasicMaterial({ color: "red" });
    var edgeLine = new THREE.Line(edgeGeo, edgeMat);
    this.edgesGroup.add(edgeLine);
};

// draws a tick mark. isMz should be true for mz, false for rt tick mark.
MsGraph.prototype.makeTickMark = function(isMz, where, r, length) {
    var mzmin, mzmax, rtmin, rtmax;
    
    // calculate the "ends" of the line based on which kind of mark is being drawn
    if (isMz) {
        rtmin = r.rtmin;
        rtmax = r.rtmin - length * r.rtrange;
        mzmin = mzmax = where;
    } else {
        mzmin = r.mzmin;
        mzmax = r.mzmin - length * r.mzrange;
        rtmin = rtmax = where;
    }
    
    var markGeo = new THREE.Geometry();
    markGeo.vertices.push(new THREE.Vector3(mzmin, 0, rtmin));
    markGeo.vertices.push(new THREE.Vector3(mzmax, 0, rtmax));
    var markMat = new THREE.LineBasicMaterial({ color: 0x000000});
    var markLine = new THREE.Line(markGeo, markMat);
    this.ticksGroup.add(markLine);
};

// clears out objects in a group
MsGraph.emptyGroup = function(g) {
    while (g.children.length > 0) {
        var obj = g.children.pop();
        MsGraph.disposeObject(obj);
    }
};

// clears the points from the screen
MsGraph.prototype.clearData = function() {
    MsGraph.emptyGroup(this.plotGroup);
    MsGraph.emptyGroup(this.pinGroup);
    MsGraph.emptyGroup(this.edgesGroup);
    MsGraph.emptyGroup(this.ticksGroup);
    
    this.linesArray = [];
    this.dimensionMode = "unset";
};

// prevent user from going outside the data range or zooming in so far that math breaks down
MsGraph.prototype.constrainBounds = function(r) {
    var dataRange = this.dataRange;
    
    // prevent mzrange and rtrange from getting too small and causing bizarre floating point errors
    var newmzrange = Math.min(Math.max(r.mzrange, 0.01), dataRange.mzrange);
    var newrtrange = Math.min(Math.max(r.rtrange, 0.01), dataRange.rtrange);
    var mzmid = (r.mzmin + r.mzmax) / 2;
    var rtmid = (r.rtmin + r.rtmax) / 2;
    var newmzmin = mzmid - newmzrange / 2;
    var newrtmin = rtmid - newrtrange / 2;
    
    // stay within data range
    newmzmin = Math.min(Math.max(newmzmin, dataRange.mzmin), dataRange.mzmax - newmzrange);
    newrtmin = Math.min(Math.max(newrtmin, dataRange.rtmin), dataRange.rtmax - newrtrange);
    
    return {
        mzmin: newmzmin, mzmax: newmzmin + newmzrange, mzrange: newmzrange,
        rtmin: newrtmin, rtmax: newrtmin + newrtrange, rtrange: newrtrange,
    };
};

// pans the data view. x and z should be what percentage of the grid to pan by
MsGraph.prototype.panView = function(x, z) {
    var viewRange = this.viewRange;
    var mzmin = viewRange.mzmin + (x * viewRange.mzrange);
    var rtmin = viewRange.rtmin + (z * viewRange.rtrange);
    this.setViewingArea(mzmin, viewRange.mzrange, rtmin, viewRange.rtrange);
}

// updates the location of the drag-to-zoom rectangle
MsGraph.prototype.updateDragZoomRect = function(xmin, xmax, zmin, zmax) {
    var geo = this.dragZoomRect.geometry;
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
    this.renderImmediate();
};

// zooms the view to a specific view range.
// alternatively, pass an object with properties named after the parameters
MsGraph.prototype.setViewingArea = function(mzmin, mzrange, rtmin, rtrange) {
    var r = mzmin;
    
    if (typeof mzmin === "number") {
        r = {
            mzmin: mzmin, mzmax: mzmin + mzrange, mzrange: mzrange,
            rtmin: rtmin, rtmax: rtmin + rtrange, rtrange: rtrange,
        };
    }
    r = this.constrainBounds(r);
    this.repositionPlot(r);
    this.updateViewRange(r);
    
    this.dataBridge.requestPointsFromServer();
};
