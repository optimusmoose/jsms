// the green, blue, red axis markers
// with mz, rt, int labels
// that rotates along w/ the graph but is separate from it

var Legend = function(legendEl) {
	var scope = this;

	this.size = 120;
	this.length = 6;

	this.scene = new THREE.Scene();
	this.renderer = new THREE.WebGLRenderer( { antialias: true, alpha: true } );
	this.renderer.setSize(this.size, this.size);
	this.renderer.setClearColor(0x00FFFF, 0);
    
    $(legendEl).append(this.renderer.domElement);

	this.camera = new THREE.OrthographicCamera(
		-10, 10, 10, -10, -1, 100);

	this.camera.position.set(5, 5, 5);
	this.camera.lookAt(this.scene.position);

	// we have to define render() in advance of using it
	this.render = function() {
		scope.renderer.render(scope.scene, scope.camera);
	};


	// MAKE LINES
	this.group = new THREE.Group();

	var xMaterial = new THREE.LineBasicMaterial({color: 0xff0000});
	var yMaterial = new THREE.LineBasicMaterial({color: 0x0000ff});
	var zMaterial = new THREE.LineBasicMaterial({color: 0x2FA600});

	// draw x axis
	var geometry = new THREE.Geometry();
	geometry.vertices.push(
		new THREE.Vector3(0, 0, this.length),
		new THREE.Vector3(this.length, 0, this.length)
	);

	this.group.add(new THREE.Line(geometry, xMaterial));

	// draw y axis
	geometry = new THREE.Geometry();
	geometry.vertices.push(
		new THREE.Vector3(0, 0, this.length),
		new THREE.Vector3(0, this.length, this.length)
	);

	this.group.add(new THREE.Line(geometry, yMaterial));

	// draw z axis
	geometry = new THREE.Geometry();
	geometry.vertices.push(
		new THREE.Vector3(0, 0, this.length),
		new THREE.Vector3(0, 0, 0)
	);

	this.group.add(new THREE.Line(geometry, zMaterial));

	// MAKE TEXT
	var rtsprite = MsGraph.makeTextSprite("RT", {r: 0, g: 150, b:0}, 30);
	var mzsprite = MsGraph.makeTextSprite("MZ", {r: 255, g: 0, b:0}, 30);
	var intsprite = MsGraph.makeTextSprite("INT", {r: 0, g: 0, b:255}, 30);

	mzsprite.position.set(this.length, 0, this.length+3);
	rtsprite.position.set(-2, 0, 0);
	intsprite.position.set(-2, this.length/2, this.length+3);

	this.group.add(rtsprite, mzsprite, intsprite);
	
	// make view rectangle
	var viewRectGeo = new THREE.Geometry();
	for (var i=0; i<8; i++) { viewRectGeo.vertices.push(new THREE.Vector3()); }
	var viewRectMat = new THREE.LineBasicMaterial({ color: 0x000000 });
	this.viewRect = new THREE.LineSegments(viewRectGeo, viewRectMat);
	this.group.add(this.viewRect);

	this.group.position.set(10, 0, 10-this.length);
	this.scene.add(this.group);

	this.render();

	// MOVE LEGEND CAMERA TO MATCH GRAPH CAMERA
	this.updateCamera = function(newpos, newrot) {
		scope.camera.position.x = newpos.x;
		scope.camera.position.y = newpos.y;
		scope.camera.position.z = newpos.z;

		scope.camera.rotation.x = newrot.x;
		scope.camera.rotation.y = newrot.y;
		scope.camera.rotation.z = newrot.z;

		scope.render();
	};
	
	function normalizeRange(viewRange, dataRange) {
		return {
			mzmin: (viewRange.mzmin - dataRange.mzmin) / dataRange.mzrange,
			mzmax: (viewRange.mzmax - dataRange.mzmin) / dataRange.mzrange,
			rtmin: (viewRange.rtmin - dataRange.rtmin) / dataRange.rtrange,
			rtmax: (viewRange.rtmax - dataRange.rtmin) / dataRange.rtrange,
		};
	}
	
	// newViewRange = either a normalized (0..1) range or pass in dataRange
	this.updateViewRect = function(newViewRange, dataRange) {
		var nRange = newViewRange;
		if (dataRange !== undefined) {
			nRange = normalizeRange(newViewRange, dataRange);
		}
		
		var geo = this.viewRect.geometry;
		geo.vertices.splice(0);
		var vMinMin = new THREE.Vector3(nRange.mzmin, 0, nRange.rtmin);
		var vMinMax = new THREE.Vector3(nRange.mzmin, 0, nRange.rtmax);
		var vMaxMax = new THREE.Vector3(nRange.mzmax, 0, nRange.rtmax);
		var vMaxMin = new THREE.Vector3(nRange.mzmax, 0, nRange.rtmin);
		geo.vertices.push(
			vMinMin.clone(), vMinMax.clone(), vMinMax.clone(), vMaxMax.clone(),
			vMaxMax.clone(), vMaxMin.clone(), vMaxMin.clone(), vMinMin.clone()
		);
		
		geo.scale(this.length, 1, -this.length);
		geo.translate(0, 0, this.length);
		
		geo.verticesNeedUpdate = true;
	};
	
	this.updateViewRect({ mzmin: 0.5, mzmax: 1, rtmin: 0.5, rtmax: 1 });
};
