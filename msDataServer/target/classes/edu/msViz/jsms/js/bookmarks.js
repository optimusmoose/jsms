// Creates a new bookmark manager with no bookmarks inside
function BookmarkManager(graph) {
    this.list = [];
    this.current = -1;
    this.graph = graph;
    this.restoreAll();
}

// Adds 'bookmark' to the managed list. bookmarks have these fields:
// 'name', 'mz', 'rt', 'envelope'
BookmarkManager.prototype.add = function(bookmark) {
    this.list.push(bookmark);
    this.storeAll();
};

// Removes 'bookmark' from the list. 'bookmark' must be the same
// object that was added to the list earlier.
BookmarkManager.prototype.remove = function(bookmark) {
    var i = this.list.indexOf(bookmark);
    if (i === -1) {
        return;
    }

    // deleting from earlier in the list than the 'current' bookmark or 'current' itself, so shift current back one as well
    if (i < this.current) {
        this.current -= 1;
    } else if (i == this.current) {
        this.current = -1;
    }
    var removed = this.list.splice(i, 1)[0];
    this.storeAll();
    return removed;
};

// Removes all bookmarks from the list
BookmarkManager.prototype.removeAll = function() {
    this.current = -1;
    var removed = this.list.splice(0);
    this.storeAll();
    return removed;
};

// Gets the bookmark in the list at the position 'index'
BookmarkManager.prototype.get = function(index) {
    return this.list[index];
};

// Retrieves all bookmarks from the list
BookmarkManager.prototype.getAll = function() {
    return this.list.concat([]);
};

// Gets the current bookmark
BookmarkManager.prototype.getCurrent = function() {
    if (this.current === -1) {
        return null;
    } else {
        return this.get(this.current);
    }
}

// Advances to the next bookmark in the list and returns it
BookmarkManager.prototype.getNext = function() {
    this.current += 1;
    if (this.current >= this.list.length) {
        this.current = 0;
    }
    var bookmark = this.get(this.current);
    return bookmark;
};

// Rewinds to the previous bookmark in the list and returns it
BookmarkManager.prototype.getPrevious = function() {
    this.current -= 1;
    if (this.current < 0) {
        this.current = this.list.length - 1;
    }
    
    var bookmark = this.get(this.current);
    return bookmark; 
};

// Sets the 'current' bookmark. 'current' must be a previously added bookmark
BookmarkManager.prototype.setCurrent = function(current) {
    this.current = this.list.indexOf(current);
};

// Saves the bookmark data in localStorage for use with restoreAll()
BookmarkManager.prototype.storeAll = function() {
    var str = JSON.stringify({ list: this.getAll() });
    localStorage.setItem("bookmarks", str);
};

// Loads the previously stored bookmarks back out of localStorage
BookmarkManager.prototype.restoreAll = function() {
    var str = localStorage.getItem("bookmarks");
    try {
        var data = JSON.parse(str);
        if (data && data.list) {
            this.removeAll();
            data.list.forEach(this.add.bind(this));
        }
    } catch (ex) {
        console.warn("could not restore bookmarks", ex);
    }
};

// Imports a string into the BookmarkManager.
// 'data' should be in tab-separated format 'name\tm/z\trt' with newlines separating entries
BookmarkManager.prototype.import_str = function(data) {
    this.removeAll();
    var lines = data.split("\n");
    lines.forEach(function(line, index) {
        var parts = line.split("\t");

        // only lines with 3 split parts are valid
        if (parts.length !== 3) { return; }

        var name = parts[0];
        var mz = Number.parseFloat(parts[1]);
        var rt = Number.parseFloat(parts[2]);
        
        mz = mz.toFixed(3);
        rt = rt.toFixed(3);

        // skip lines with non-numeric m/z values, including header rows
        if (isNaN(mz)) { return; }

        // blank rt value will read as NaN, but they should be allowed in
        if (isNaN(rt)) {
            rt = null;
        }

        var mark = { "name": name, "mz": mz, "rt": rt };
        this.add(mark);
    }, this);
};

// Exports the managed bookmarks as a string in TSV format
BookmarkManager.prototype.export_str = function() {
    var s = "label\tmz\trt\n";
    this.getAll().forEach(function(mark) {
        var saferName = mark.name.replace(/[ \t\n]+/g, ' ');
        s = s + saferName + "\t" + mark.mz + "\t" + mark.rt + "\n";
    });
    return s;
};

// Generates a report including mz, rt, and envelope data.
// Runs 'callback' with the report data as the first parameter
BookmarkManager.prototype.generateReport = function(callback) {
    // make a copy of the relevant bookmark info
    var pairs = this.getAll().map(function(m) {
       return { label: m.name, env: m.envelope }; 
    });
    
    var idList = pairs.map(function(p) { return p.env; }).filter(function(p) { return p; });
    this.graph.dataBridge.getEnvelopeInfo(idList, function(envInfo) {
        var r = "label\tmz\trt\tintensity\ttrace0_mz\ttrace0_intensity\ttrace1_mz\ttrace1_intensity\t...\n";
        
        // blindly assume the server is returning the data in the order we expect in the arrays
        pairs.forEach(function(pair) {
            // skip unpaired bookmarks
            if (!pair.env) { return; }
            
            var e = envInfo[pair.env];
            // skip envelopes that have no data (meaning contained no traces) 
            if (e === null) { return; }

            // extract envelope properties
            var mz = e[0];
            var rt = e[1];
            var inten = e[2];
            var traces = e[3];
            
            r = r + pair.label + "\t" + mz + "\t" + rt + "\t" + inten;

            // assume the server returns trace data as an array of [mz,intensity] arrays
            traces.forEach(function(trace) {
                r = r + "\t" + trace.join("\t");
            });
            r = r + "\n";
        });
        
        // run our callback with the generated report
        callback(r);
    });
};
