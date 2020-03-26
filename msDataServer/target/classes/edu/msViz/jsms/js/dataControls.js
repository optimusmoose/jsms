// keyboard and mouse interaction and zoom calculations

DataControls = function(graph) {
    
    var el = this.el = graph.renderer.domElement;
    this.camera = graph.camera;
    this.enabled = false;

    var scope = this;
    this.lastZoomFrom = null;

    var lastMousePoint = null;
    var mstart = null;
    var mend = new THREE.Vector3();
    var mdelta = new THREE.Vector3();

    // corresponds to KeyboardEvent.key values, in lowercase
    // see https://developer.mozilla.org/en-US/docs/Web/API/KeyboardEvent/key
    this.keys = {
        LEFT: "arrowleft", UP: "arrowup", RIGHT: "arrowright", DOWN: "arrowdown",
        THREED: "d", REFRESH: "r", VIEWALL: "a",
        TRACE: "t", ENVELOPE: "e", NOISE: "x", ADD: "n", SELECT: "m",
        UNDO: "z", REDO: "y", JUMP: "j", BACK: "z",
        ZOOMIN: "+", ZOOMOUT: "-", BOOKMARK: "b", PAIR: "p",
        COLOR: "l", TOTALION: "i", RULER: "`", GUARD: "g", HIDEGUARD: "h",
    };

    // hook up global input handlers
    this.el.addEventListener('mousedown', onMouseDown, false);
    document.addEventListener('mousemove', onMouseMove, false);
    document.addEventListener('mouseup', onMouseUp, false);

    window.addEventListener('keydown', onKeyDown, false);
    window.addEventListener('keyup', onKeyUp, false);
    this.el.addEventListener('wheel', onMouseWheel, false);

    function onMouseWheel(event){
        if (!scope.enabled) {return;}

        // gets the position of the mouse on the mz/rt plane
        var zoomPoint = graph.getMousePosition(event);
        
        performZoom(zoomPoint, event.deltaY);
    }
    
    function performZoom(point, direction) {
        if (point === null) {
            return;
        }
        
        // store current zoom range for convenience
        var vr = graph.viewRange;
        
        // mz/rtDist = fractional position of mouse
        var mzDist = point.x;
        var rtDist = point.z;

        // constrains points to within the graph bounds
        mzDist = Math.min(Math.max(mzDist, 0), 1);
        rtDist = Math.min(Math.max(rtDist, 0), 1);

        // calculate a new range based on the view
        var newmzrange, newrtrange;
        if (direction < 0) {
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
        
        document.activeElement.blur();

        if (event.button === 0 && graph.editor.editMode === "none") {
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
        lastMousePoint = mousePoint;
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
                scope.lastZoomFrom = graph.viewRange;
                var range = graph.vectorsToMzRt(mstart, mend);
                graph.setViewingArea(range.mzmin, range.mzmax - range.mzmin, range.rtmin, range.rtmax - range.rtmin);
                
                scope.dragZoom = graph.dragZoomRect.visible = false;
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
        
        var key = event.key.toLowerCase();
        
        // Ctrl+Z / Ctrl+Y
        if (event.ctrlKey) {
            if (key === scope.keys.UNDO) {
                graph.traceManager.changes.undo();
            } else if (key === scope.keys.REDO) {
                graph.traceManager.changes.redo();
            }
            return;
        }
        
        // panning shortcuts
        if (key === scope.keys.LEFT) {
            graph.panView(-0.1, 0);
        } else if (key === scope.keys.UP) {
            graph.panView(0, 0.1);
        } else if (key === scope.keys.RIGHT) {
            graph.panView(0.1, 0);
        } else if (key === scope.keys.DOWN) {
            graph.panView(0, -0.1);
        
        // toolbar button shortcuts
        } else if (key === scope.keys.THREED) {
            gcel.find(".button-_3d").click();
        } else if (key === scope.keys.REFRESH) {
            gcel.find(".button-refresh").click();
        } else if (key === scope.keys.VIEWALL) {
            gcel.find(".button-data").click();
        } else if (key === scope.keys.TRACE) {
            gcel.find(".button-trace").click();
        } else if (key === scope.keys.ENVELOPE) {
            gcel.find(".button-envelope").click();
        } else if (key === scope.keys.NOISE) {
            gcel.find(".button-noise").click();
        } else if (key === scope.keys.ADD) {
            gcel.find(".button-add").click();
        } else if (key === scope.keys.SELECT) {
            gcel.find(".button-select").click();
        } else if (key === scope.keys.GUARD) {
            var vr = graph.viewRange;
            var mouseMz = lastMousePoint.x * vr.mzrange + vr.mzmin;
            graph.drawGuard(mouseMz, graph.toolbar.getTraceWidth());
            graph.editor.setGuard(mouseMz, graph.toolbar.getTraceWidth());
            graph.guardGroup.visible = true;
            graph.renderImmediate();
        } else if (key === scope.keys.HIDEGUARD) {
            graph.drawGuard(null);
            graph.editor.setGuard(null);
        } else if (key === scope.keys.JUMP) {
            gcel.find(".button-jump").click();
        } else if (key === scope.keys.BACK) {
            if (scope.lastZoomFrom !== null) {
                graph.setViewingArea(scope.lastZoomFrom);
            }
        } else if (key === scope.keys.ZOOMIN) {
            performZoom(lastMousePoint, -1);
        } else if (key === scope.keys.ZOOMOUT) {
            performZoom(lastMousePoint, 1);
        } else if (key === scope.keys.BOOKMARK) {
            gcel.find(".button-bookmark").click();
        } else if (key === scope.keys.PAIR) {
            gcel.find(".button-bkpair").click();
        } else if (key === scope.keys.COLOR) {
            gcel.find(".colorblindCheckbox").click();
        } else if (key === scope.keys.TOTALION) {
            gcel.find(".button-totalion").click();
        } else if (key === scope.keys.RULER) {
            graph.ruler.visible = !graph.ruler.visible;
        } else if (key >= "1" && key <= "9") {
            var vr = graph.viewRange;
            var mouseMz = lastMousePoint.x * vr.mzrange + vr.mzmin;
            var mouseRt = lastMousePoint.z * vr.rtrange + vr.rtmin;

            graph.drawRuler(1.0 / (key - "0"), mouseMz, mouseRt);
            graph.ruler.visible = true;
            graph.renderImmediate();
        }
    }
    
    // some shortcuts, for example 2D toggle, are hold-to-activate shortcuts
    function onKeyUp(event) {
        if (!scope.enabled) { return; }

        // if an input has focus, don't do stuff on the graph
        if ($("input:focus").length > 0) return;

        // shift key 2d/3d toggle
        if (!event.shiftKey) {
            graph.toggleTopView(false);
        }
    }
};
