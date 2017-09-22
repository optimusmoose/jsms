// graph.js: graph initialization code and helper methods

function MsGraph(containerEl, graphEl, legendEl) {
    this.containerEl = $(containerEl);
    this.graphEl = graphEl;
    this.legendEl = legendEl;

    // Adjusts the size of the baseline grid
    this.GRID_RANGE = 20;
    this.GRID_RANGE_VERTICAL = 6;
    this.VIEW_SIZE = 28; // in world units; large enough to fit the graph and labels at reasonable angles
    
    this.graphPlane = new THREE.Plane(new THREE.Vector3(0,1,0), 0);
    this.rangeTransform = new THREE.Vector3(1/this.GRID_RANGE, 1/this.GRID_RANGE_VERTICAL, 1/this.GRID_RANGE);

    this.USE_LOG_SCALE_COLOR = true;
    this.USE_LOG_SCALE_HEIGHT = false;

    this.gradientCache = [];

    this.dataRange = { mzmin: 0, mzmax: 1, mzrange: 1, rtmin: 0, rtmax: 1, rtrange: 1, intmin: 0, intmax: 1000, intrange: 1000 };

    this.POINTS_PLOTTED_LIMIT = 5000; // points that can be loaded for viewing
    this.POINTS_VISIBLE_LIMIT = 5000; // points that can be displayed on the screen

    this.nPointsVisible = 0;
    this.nPointsInRange = 0;

    this.dimensionMode = "3D";

    this.labels = {};
    
    this.RENDER_DELAY_THROTTLE = 100;
    this.renderRequested = false;
}

// actually creates the graph scene and sets it up. Should be called
// after the document is ready and all javascript has loaded
MsGraph.prototype.init = function(){
    
    var scene = this.scene = new THREE.Scene();
    
    // rendering element
    var renderer = this.renderer = new THREE.WebGLRenderer( { antialias: true } );
    renderer.setPixelRatio( window.devicePixelRatio );
    renderer.setSize(window.innerWidth, window.innerHeight);
    renderer.setClearColor(0xcccccc);
    $(this.graphEl).append(renderer.domElement);
    
    // camera
    var camera = this.camera = new THREE.OrthographicCamera( 1, 1, 1, 1, - 300, 300 );
    camera.position.set(15, 15, 30);
    this.prevCameraPos = camera.position.clone();
    
    // window resizing
    window.addEventListener("resize", this.resizeCamera.bind(this));
    this.resizeCamera();
    
    // right-click rotation controls
    var graphControls = this.graphControls = new THREE.OrbitControls( camera, renderer.domElement );
    graphControls.mouseButtons = { ORBIT: THREE.MOUSE.RIGHT };
    graphControls.addEventListener( 'change', this.renderImmediate.bind(this) );
    graphControls.target.set(this.GRID_RANGE/2, 0, this.GRID_RANGE/2); // focus on the center of the grid
    graphControls.enableZoom = false;
    graphControls.enablePan = false;
    graphControls.enabled = true;
    
    // keyboard/mouse shortcuts
    this.dataControls = new DataControls(this);
    this.dataControls.enabled = true;
    
    // make grouping constructs
    var gridgroup = new THREE.Group();
    this.datagroup = new THREE.Group();
    this.labelgroup = new THREE.Group();
    
    // plotting objects
    this.linesArray = [];
    this.plotGroup = new THREE.Group();
    this.pinGroup = new THREE.Group();
    this.edgesGroup = new THREE.Group();
    this.ticksGroup = new THREE.Group();
    
    // "flatten" the pins to the graph
    this.pinGroup.scale.set(1, 1/1000, 1);

    this.datagroup.add(this.plotGroup);
    this.datagroup.add(this.pinGroup);
    this.datagroup.add(this.edgesGroup);
    this.datagroup.add(this.ticksGroup);
    
    // graph "surface" (gray underside)
    var surfaceGeo = new THREE.PlaneGeometry(this.GRID_RANGE, this.GRID_RANGE);
    var surfaceMat = new THREE.MeshBasicMaterial({ color: 0xbbbbbb, side: THREE.DoubleSide });
    var surface = new THREE.Mesh(surfaceGeo, surfaceMat);
    surface.rotateX(Math.PI/2);
    surface.position.set(this.GRID_RANGE / 2, -0.05, this.GRID_RANGE / 2);
    gridgroup.add(surface);
    gridgroup.add(this.drawGrid());
    
    // drag-to-zoom rectangle
    var dzRectGeo = new THREE.Geometry();
    for (var i=0; i<8; i++) { dzRectGeo.vertices.push(new THREE.Vector3()); }
    var dzRectMat = new THREE.LineBasicMaterial({ color: 0xff8000 });
    var dragZoomRect = this.dragZoomRect = new THREE.LineSegments(dzRectGeo, dzRectMat);
    dragZoomRect.scale.set(this.GRID_RANGE, 1, -this.GRID_RANGE);
    dragZoomRect.position.set(0, 0.1, this.GRID_RANGE);
    gridgroup.add(dragZoomRect);
    
    // add objects to the scene
    scene.add(gridgroup);
    scene.add(this.datagroup);
    scene.add(this.labelgroup);
    
    // add server communication module
    this.dataBridge = new DataBridge(this);
    
    // add rest of UI
    this.legend = new Legend(this.legendEl);
    this.toolbar = new Toolbar(this, this.containerEl);
    
    // generate gradient cache
    this.makeGradientCache();
    
    this.updateViewRange(this.dataRange);
    
    // for graph rendering during rotation
    function animate() {
        requestAnimationFrame( animate );
        graphControls.update();
    };
    animate();
    
    this.renderImmediate();
    
    // every RENDER_THROTTLE ms, render the graph if it needs to be
    setInterval((function() {
        if (this.renderRequested) {
            this.renderImmediate();
        }
    }).bind(this), this.RENDER_DELAY_THROTTLE);
};

// resizes the renderer and camera, especially in response to a window resize
MsGraph.prototype.resizeCamera = function() {
    // reset the renderer to fit the window
    this.renderer.setSize(window.innerWidth, window.innerHeight);
    var aspectRatio = this.renderer.getSize().width / this.renderer.getSize().height;

    var camera = this.camera;
    var vs = this.VIEW_SIZE;
    if (aspectRatio > 1) {
        // width greater than height; scale height to view size to fit content
        // and scale width based on the aspect ratio (this creates extra space on the sides)
        camera.left = vs * aspectRatio / -2;
        camera.right = vs * aspectRatio / 2;
        camera.top = vs / 2;
        camera.bottom = vs / -2;
    } else {
        // height greater than width; same as above but with top+bottom switched with left+right
        camera.left = vs / -2;
        camera.right = vs / 2;
        camera.top = vs / aspectRatio / 2;
        camera.bottom = vs / aspectRatio / -2;
    }
    
    // render the view to show the changes
    camera.updateProjectionMatrix();
    this.renderDelayed();
};

// returns the mouse position in mz,rt,int space as a fraction of the viewing window
// returns null if the mouse is outside of the plane of the graph
MsGraph.prototype.getMousePosition = function(event) {
    var el = this.renderer.domElement;
    
    // find mouse position, normalized to a [-1,1] in both x/y-axes on screen
    var coord = {
        x: ((event.clientX - el.offsetLeft) / el.offsetWidth)  * 2 - 1,
        y: - ((event.clientY - el.offsetTop) / el.offsetHeight) * 2 + 1
    };
    var raycaster = new THREE.Raycaster();
    raycaster.setFromCamera(coord, this.camera);

    var pos = raycaster.ray.intersectPlane(this.graphPlane);
    if (pos) {
        // convert world coordinates to graph-fractional coordinates
        pos.multiply(this.rangeTransform);
        pos.z = 1 - pos.z;
    }
    return pos;
};

// converts two vectors in fraction of visible space (e.g. from getMousePosition)
// to an {mzmin,mzmax,rtmin,rtmax} object based on current view range
MsGraph.prototype.vectorsToMzRt = function(vec1, vec2) {
    var vr = this.viewRange;

    var mzmin = vec1.x * vr.mzrange + vr.mzmin;
    var rtmin = vec1.z * vr.rtrange + vr.rtmin;
    var mzmax = vec2.x * vr.mzrange + vr.mzmin;
    var rtmax = vec2.z * vr.rtrange + vr.rtmin;

    // swap max/mins if the rectangle was dragged "backwards"
    if (mzmax < mzmin) {
        var tmz = mzmin;
        mzmin = mzmax;
        mzmax = tmz;
    }
    if (rtmax < rtmin) {
        var trt = rtmin;
        rtmin = rtmax;
        rtmax = trt;
    }
    
    return { mzmin: mzmin, mzmax: mzmax, rtmin: rtmin, rtmax: rtmax };
};

//generate gradient cache
MsGraph.prototype.makeGradientCache = function()
{
    this.gradientCache = [];
    var cachesize = 50;
    for (var i = 0; i < cachesize; i++) {
        var gradientcolor = this.getGradientColor(i, cachesize);
        this.gradientCache.push(new THREE.Color(gradientcolor));
    }
};

// render the graph immediately (i.e. can't wait for timed update)
MsGraph.prototype.renderImmediate = function() {    
    // if camera is positioned directly above the plot use 2D, otherwise 3D
    var newDimensionMode = (this.camera.position.y >= 24.5) ? "2D" : "3D";
    
    // if the mode has changed, toggle between lines and squares
    if (this.dimensionMode !== newDimensionMode) {
        this.plotGroup.visible = (newDimensionMode === "3D");
        this.pinGroup.visible = (newDimensionMode === "2D");
        this.dimensionMode = newDimensionMode;
    }
    
    this.renderer.render( this.scene, this.camera );
    this.legend.updateCamera(this.camera.position, this.camera.rotation);
    this.renderRequested = false;
};

// render the graph at the next automatic interval
MsGraph.prototype.renderDelayed = function() {
    this.renderRequested = true;
};

MsGraph.prototype.drawDataLabels = function() {
    //draws sprite labels for min/max data
    
    // dispose all labels on the graph
    while(this.labelgroup.children.length > 0) {
        var obj = this.labelgroup.children.pop();
        MsGraph.disposeObject(obj);
    }
    
    var mzmintext = MsGraph.roundTo(this.viewRange.mzmin,3);
    var mzmaxtext = MsGraph.roundTo(this.viewRange.mzmax,3);
    var rtmintext = MsGraph.roundTo(this.viewRange.rtmin,3);
    var rtmaxtext = MsGraph.roundTo(this.viewRange.rtmax,3);
    
    this.containerEl.find(".status-p-visible").text(this.nPointsVisible);
    
    this.containerEl.find(".status-r-mzmin").text(mzmintext);
    this.containerEl.find(".status-r-mzmax").text(mzmaxtext);
    this.containerEl.find(".status-r-rtmin").text(rtmintext);
    this.containerEl.find(".status-r-rtmax").text(rtmaxtext);
    this.containerEl.find(".status-r-intmin").text(MsGraph.roundTo(this.viewRange.intmin, 3));
    this.containerEl.find(".status-r-intmax").text(MsGraph.roundTo(this.viewRange.intmax, 3));
    
    this.labels.mzMin = MsGraph.makeTextSprite(mzmintext, {r: 0, g: 0, b: 0}, 16);
    this.labels.mzMax = MsGraph.makeTextSprite(mzmaxtext,{r: 0, g: 0, b: 0}, 16);
    this.labels.rtMin = MsGraph.makeTextSprite(rtmintext,{r: 0, g: 0, b: 0}, 16);
    this.labels.rtMax = MsGraph.makeTextSprite(rtmaxtext,{r: 0, g: 0, b: 0}, 16);
    
    this.labels.mzMin.position.set(0.5, -0.5, this.GRID_RANGE + 1.5);
    this.labels.mzMax.position.set(this.GRID_RANGE, -0.5, this.GRID_RANGE + 1.5);
    this.labels.rtMin.position.set(-1.5, -0.5, this.GRID_RANGE);
    this.labels.rtMax.position.set(-1.5, -0.5, 0.5);
    
    this.labelgroup.add(this.labels.mzMin, this.labels.mzMax, this.labels.rtMin, this.labels.rtMax);
};

// labels the closest point to the mouse
MsGraph.prototype.drawHoverLabel = function(point) {
    var currentPtText = "[ No point near mouse ]";
    if (point) {
        currentPtText = "[ m/z: " + MsGraph.roundTo(point.mz, 3) +
            ", RT: " + MsGraph.roundTo(point.rt, 3) +
            ", intensity: " + MsGraph.roundTo(point.int, 3) +
            ", trace: " + point.trace + " ]";
    }
    this.containerEl.find(".status-current-pt").text(currentPtText);
};

MsGraph.makeTextSprite = function(msg, textColor, fontsize) {
    // creates a texture and a sprite so three.js will render a floating text label
    var canvas = document.createElement('canvas');
    var context = canvas.getContext('2d');
    var fontwidth = context.measureText(msg).width;
    context.font = fontsize + "px Arial";
    context.fillStyle = "rgba("+textColor.r+", "+textColor.g+", "+textColor.b+", 1.0)";
    context.fillText(msg, ((canvas.width/2) - (fontwidth/2)), ((canvas.height/2) - (fontsize/2)));
    
    var texture = new THREE.Texture(canvas);
    texture.needsUpdate = true;
    texture.minFilter = THREE.LinearFilter;
    
    var spriteMaterial = new THREE.SpriteMaterial( { map: texture } );
    var sprite = new THREE.Sprite( spriteMaterial );
    sprite.scale.set(0.5 * fontsize, 0.25 * fontsize, 0.75 * fontsize);
    return sprite;
};

// === DATA ===

MsGraph.roundTo = function(number, places) {
    // Returns number rounded to places decimals
    var power = Math.pow(10, places);
    return Math.round(number * power) / power;
};

// update labels and legend to reflect a new view range
MsGraph.prototype.updateViewRange = function(newViewRange) {
    this.viewRange = newViewRange;
    this.drawDataLabels();
    this.legend.updateViewRect(newViewRange, this.dataRange);
    this.renderDelayed();
};

MsGraph.prototype.getGradientColor = function(val, max) {
    // calculates the color representing val based on a gradient
    var mid = max/2;
    var redColor = 0;
    var greenColor = 0;
    var blueColor = 0;
    
    // fraction of the value between target points
    var frac = (val < mid) ? (val / mid) : ((val - mid) / mid);
    
    // If colorblind mode is inactive
    if (!this.toolbar.settings.colorblind) {
        if (val < mid) {
            // green to blue
            redColor = 10;
            greenColor = Math.round(200 * (1 - frac));
            blueColor = Math.round(255 * frac);
        } else {
            // blue to red
            redColor = Math.round(255 * frac);
            greenColor = 0;
            blueColor = Math.round(255 * (1 - frac));
        }
    }
    
    // If colorblind mode is active
    else {
        if (val < mid) {
            // blue to yellow
            redColor = Math.round(255 * frac);
            greenColor = Math.round(255 * frac);
            blueColor = Math.round(255 * (1 - frac));
        } else {
            // yellow to red
            redColor = 255;
            greenColor = Math.round(255 * (1 - frac));
            blueColor = 0;
        }
    }
    
    return "rgb(" + redColor + ", " + greenColor + ", " + blueColor +")";
};

MsGraph.prototype.drawGrid = function() {
    // returns a 1x1 unit grid, GRID_RANGE units long in the x and z dimension
    
    var y = 0;
    
    var gridgeo = new THREE.Geometry();
    var gridmaterial = new THREE.LineBasicMaterial({ color: 0x000000 });
    
    for (var i = 0; i <= this.GRID_RANGE; i++) {
        gridgeo.vertices.push(new THREE.Vector3(i, y, 0));
        gridgeo.vertices.push(new THREE.Vector3(i, y, this.GRID_RANGE));
        
        gridgeo.vertices.push(new THREE.Vector3(0, y, i));
        gridgeo.vertices.push(new THREE.Vector3(this.GRID_RANGE, y, i));
    }
    
    gridgeo.vertices.push(new THREE.Vector3(0, y, this.GRID_RANGE));
    gridgeo.vertices.push(new THREE.Vector3(0, this.GRID_RANGE_VERTICAL, this.GRID_RANGE));
    
    return new THREE.LineSegments(gridgeo, gridmaterial);
};

MsGraph.disposeObject = function(obj) {
    // disposes everything related to an individual label
    
    if (obj.material.map) {
        obj.material.map.dispose();
    }
    if (obj.material) {
        obj.material.dispose();
    }
    if (obj.geometry) {
        obj.geometry.dispose();
    }
    if (obj.dispose) {
        obj.dispose();
    }
};

// enables and disables the 2d/3d view locking
MsGraph.prototype.toggleTopView = function(lockstatus) {
    if (lockstatus || this.toolbar.isButtonSelected._3d) {
        if (this.camera.position.y < 26.6) {
            this.prevCameraPos = this.camera.position.clone();
        }
        this.camera.position.set(10, 27, 10);
        this.graphControls.enableRotate = false;
    } else {
        if (this.camera.position.y > 26.6) {
            this.camera.position.copy(this.prevCameraPos);
        }
        this.graphControls.enableRotate = true;
    }
    this.renderImmediate();
};

mainGraph = new MsGraph("body", ".col2", ".legend");
$(document).ready(function() { mainGraph.init(); });
