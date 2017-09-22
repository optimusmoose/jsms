// keyboard and mouse interaction and zoom calculations

DataControls = function(graph) {
    
    var el = this.el = graph.renderer.domElement;
    this.camera = graph.camera;
    this.enabled = false;

    var scope = this;

    var mstart = null;
    var mend = new THREE.Vector3();
    var mdelta = new THREE.Vector3();

    // keys list available on MDN
    this.keys = {
        LEFT: 37, UP: 38, RIGHT: 39, DOWN: 40,
        OPEN: 79, THREED: 68, REFRESH: 82, VIEWALL: 65,
        JUMP: 74
    };

    // hook up global input handlers
    this.el.addEventListener('mousedown', onMouseDown, false);
    // ok to use document here because mouseDown is attached to the specific target element
    document.addEventListener('mousemove', onMouseMove, false);
    document.addEventListener('mouseup', onMouseUp, false);

    window.addEventListener('keydown', onKeyDown, false);
    window.addEventListener('keyup', onKeyUp, false);
    this.el.addEventListener('wheel', onMouseWheel, false);

    function onMouseWheel(event){
        if (!scope.enabled) {return;}
        // store current zoom range for convenience
        var vr = graph.viewRange;

        // gets the position of the mouse on the mz/rt plane
        var zoomPoint = graph.getMousePosition(event);
        
        if (zoomPoint === null) {
            return;
        }
        
        // mz/rtDist = fractional position of mouse
        var mzDist = zoomPoint.x;
        var rtDist = zoomPoint.z;

        // constrains points to within the graph bounds
        mzDist = Math.min(Math.max(mzDist, 0), 1);
        rtDist = Math.min(Math.max(rtDist, 0), 1);

        // calculate a new range based on the view
        var newmzrange, newrtrange;
        if (event.deltaY < 0) {
            // scroll up = zoom in - view range shrinks
            newmzrange = vr.mzrange * 0.9;
            newrtrange = vr.rtrange * 0.9;
        } else {
            // scroll down = zoom out - view range grows
            newmzrange = vr.mzrange * 1.1;
            newrtrange = vr.rtrange * 1.1;
        }
        
        var mzPoint = mzDist * vr.mzrange + vr.mzmin;
        var rtPoint = rtDist * vr.rtrange + vr.rtmin;
        var newmzmin = (mzPoint) - (newmzrange / vr.mzrange) * (mzPoint - vr.mzmin);
        var newrtmin = (rtPoint) - (newrtrange / vr.rtrange) * (rtPoint - vr.rtmin);
        
        graph.setViewingArea(newmzmin, newmzrange, newrtmin, newrtrange);
    }
    
    // begin panning
    function onMouseDown(event) {
        if (!scope.enabled) { return; }
        
        if (event.button === 0) {
            var mousePoint = graph.getMousePosition(event);
            if (mousePoint === null) {
                return;
            }
            
            mstart = new THREE.Vector3();
            mstart.copy(mousePoint);
            
            scope.dragZoom = graph.dragZoomRect.visible = event.ctrlKey;
        }
    }
    
    // continue panning
    function onMouseMove(event) {
        if (!scope.enabled) { return; }

        var mousePoint = graph.getMousePosition(event);
        if (mousePoint === null) {
            return;
        }
        
        if (mstart) {
            mend.copy(mousePoint);
            mdelta.subVectors(mend, mstart);
            
            if (scope.dragZoom) {
                graph.updateDragZoomRect(mstart.x, mend.x, mstart.z, mend.z);
            } else {
                graph.panView(-mdelta.x, -mdelta.z);
                mstart.copy(mend);
            }
        }

        // find point closest to mouse
        var vr = graph.viewRange;
        var mouseMz = mousePoint.x * vr.mzrange + vr.mzmin;
        var mouseRt = mousePoint.z * vr.rtrange + vr.rtmin;

        var closest, closestDist = Number.MAX_VALUE;
        graph.linesArray.forEach(function(line) {
            var mzD = Math.abs(line.mz - mouseMz);
            var rtD = Math.abs(line.rt - mouseRt);

            // only consider points within 0.5 m/z and RT units, then go by closest distance to mouse
            if (mzD > 0.5 || rtD > 0.5) {
                return;
            }

            var dist = Math.sqrt(mzD * mzD + rtD * rtD);
            if (dist < closestDist) {
                closest = line;
                closestDist = dist;
            }
        });

        graph.drawHoverLabel(closest);
    }
    
    // end panning
    function onMouseUp(event) {
        if (!scope.enabled) { return; }
        
        if (event.button === 0) {
            
            if (scope.dragZoom) {
                var range = graph.vectorsToMzRt(mstart, mend);
                graph.setViewingArea(range.mzmin, range.mzmax - range.mzmin, range.rtmin, range.rtmax - range.rtmin);
                
                graph.dragZoomRect.visible = false;
                graph.renderImmediate();
            }

            mstart = null;
        }
    }
    
    // all keyboard shortcuts should be handled here
    function onKeyDown(event) {
        if (!scope.enabled) { return; }
        
        // if an input has focus, don't do stuff on the graph
        if ($("input:focus").length > 0) return;
        
        // shift key 2d/3d toggle
        if (event.shiftKey) {
            graph.toggleTopView(true);
        }
        
        var gcel = graph.containerEl;
        
        // panning shortcuts
        if (event.keyCode === scope.keys.LEFT) {
            graph.panView(-0.1, 0);
        } else if (event.keyCode === scope.keys.UP) {
            graph.panView(0, 0.1);
        } else if (event.keyCode === scope.keys.RIGHT) {
            graph.panView(0.1, 0);
        } else if (event.keyCode === scope.keys.DOWN) {
            graph.panView(0, -0.1);
        
        // toolbar button shortcuts
        } else if (event.keyCode === scope.keys.OPEN) {
            gcel.find(".open").click();
        } else if (event.keyCode === scope.keys.THREED) {
            gcel.find("._3d").click();
        } else if (event.keyCode === scope.keys.REFRESH) {
            gcel.find(".refresh").click();
        } else if (event.keyCode === scope.keys.VIEWALL) {
            gcel.find(".data").click();
        }  else if (event.keyCode === scope.keys.JUMP) {
            gcel.find(".jump").click();
        } 
    }
    
    // some shortcuts, for example 2D toggle, are hold-to-activate shortcuts
    function onKeyUp(event) {
        if (!scope.enabled) { return; }
        
        // shift key 2d/3d toggle
        if (!event.shiftKey) {
            graph.toggleTopView(false);
        }
    }
};
