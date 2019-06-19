// buttons.js: user interface controls and toolbar code

var Toolbar = function(graph, containerEl) {
    this.graph = graph;
    this.containerEl = $(containerEl);

    this.connect();
};

// returns the selection state of 2d mode
Toolbar.prototype.is2dSelected = function() {
    return this.toolbarVM.is2d;
};

Toolbar.prototype.getTraceWidth = function() {
    return +this.settingsVM.settings.traceWidth;
};

Toolbar.prototype.isColorblind = function() {
    return this.settingsVM.settings.colorblind;
};

// disable the noise button
Toolbar.prototype.disableNoiseButton = function() {
    this.toolbarVM.isNoiseEnabled = false;
}

// check and set noise button enabled
Toolbar.prototype.checkNoiseEnabled = function() {
    this.toolbarVM.isNoiseEnabled = this.graph.nPointsVisible < this.graph.POINTS_PLOTTED_LIMIT;
};

// update the icon states of the edit mode buttons
Toolbar.prototype.updateEditModeStates = function() {
    var editMode = this.graph.editor.editMode;
    this.toolbarVM.isTrace = (editMode === "trace");
    this.toolbarVM.isEnvelope = (editMode === "envelope");
};

// pairs the current bookmark with the specified envelope
Toolbar.prototype.setBookmarkPair = function(envelopeId) {
    var bookmark = this.graph.bookmarks.getCurrent();
    if (!bookmark) {
        return;
    }
    
    bookmark.envelope = envelopeId;

    var editor = this.graph.editor;
    if (editor.editMode === "pair") {
        editor.setEditMode(editor.lastEditMode);
    }
};

//Jump bookmark with button and clicking on name
Toolbar.prototype.jumpBookmark = function(newBook) {
    
    var mzLocation = newBook.mz;
    var rtLocation = newBook.rt;

    var vr = this.graph.viewRange;
    var newmzrange = Math.min(vr.mzrange, 10);
    //var newmzrange = 1.847; //This line replaces above to set specific range
    var newmzmin = mzLocation - (newmzrange / 2);
    var newrtmin, newrtrange;

    if (rtLocation) {
        newrtrange = Math.min(vr.rtrange, 30);
        //newrtrange = 1.44; //This line replaces above to set specifc range
        newrtmin = rtLocation - (newrtrange / 2);
    } else {
        newrtmin = this.graph.dataRange.rtmin;
        newrtrange = this.graph.dataRange.rtrange;
    }
    
    console.log("test jump bookmark");
    console.log(newmzmin);

    this.graph.setViewingArea(newmzmin, newmzrange, newrtmin, newrtrange);
    this.graph.lastJumpedTo = { mz: mzLocation, rt: rtLocation };
    this.bookmarksVM.refresh();
};



// sets up the toolbar buttons for real
Toolbar.prototype.connect = function() {

var toolbar = this;
var graph = this.graph;

/*************************************************
||                 TOOL BAR                     ||
*************************************************/

this.toolbarVM = new Vue({
    el: ".toolbar",
    data: function() {
        return {
            is2d: false,
            isTrace: false,
            isEnvelope: false,
            isNoiseEnabled: false,
            showBookmark: false,
        };
    },
    computed: {
        showTraceTools: function() {
            return this.isTrace || this.isEnvelope;
        },
    },
    methods: {
        onRefresh: function() {
            graph.dataBridge.requestPointsFromServer();
        },
        onAllData: function() {
            graph.dataBridge.openFileWaitLoop();
        },
        on3d: function() {
            this.is2d = !this.is2d;
            graph.toggleTopView(this.is2d);
        },
        onTotalIon: function() {
            // first, force back to 3d mode
            this.is2d = false;
            graph.toggleTopView(this.is2d);

            // go to the TIC angle
            graph.gotoTotalIonCurrent();
        },
        onBookmark: function(value) {
            this.showBookmark = value;
            toolbar.containerEl.find(".bookmark-tooltip").toggleClass("nohover", value);
            toolbar.containerEl.find(".bookmark-sidebar").toggle(value);
            toolbar.graph.resizeCamera();
        },
        onJump: function() {
            if(toolbar.settingsVM.settings.jumpOptions) {
                graph.dataBridge.getHighestUntraced(function(data) {
                    if (data == null) {
                        graph.dataBridge.requestPointsFromServer();
                        return;
                    }

                    var mz = data[2];
                    var rt = data[3];

                    var vr = graph.viewRange;
                    var newmzrange = Math.min(vr.mzrange, 10);
                    var newrtrange = Math.min(vr.rtrange, 30);
                    var newmzmin = mz - (newmzrange / 2);
                    var newrtmin = rt - (newrtrange / 2);
                    graph.setViewingArea(newmzmin, newmzrange, newrtmin, newrtrange);
                    graph.lastJumpedTo = { mz: mz, rt: rt };
                });
            } else {
                var book = graph.bookmarks.getNext();
                if (book) {
                    toolbar.jumpBookmark(book);
                }
            }
        },
        onJumpBack: function() {
            // can't jump backward through trace intensities, so only do it for bookmarks
            if(!toolbar.settingsVM.settings.jumpOptions) {
                var book = graph.bookmarks.getPrevious();
                if (book) {
                    toolbar.jumpBookmark(book);
                }
            }
        },
        onTraceEnvelope: function(which) {
            graph.editor.setEditMode(which);
        },
        onNoise: function() {
            var onJump = this.onJump;
            graph.editor.setAllNoise(function() { onJump(); });
        },
        onAdd: function() {
            graph.editor.newGroup();
        },
        onSelect: function() {
            graph.editor.selectGroup();
        },
    },
});

this.bookmarksVM = new Vue({
    el: ".bookmark-sidebar",
    created: function() {
        this.manager = toolbar.graph.bookmarks;
        this.refresh();
    },
    data: function() {
        return {
            bookmarks: [],
            currentIdx: null,

            newName: null,
            newMz: null,
            newRt: null,
        };
    },
    methods: {
        refresh: function(event) {
            this.bookmarks = toolbar.graph.bookmarks.getAll();
            this.currentIdx = toolbar.graph.bookmarks.current;
        },
        onFileInput: function(event) {
            var file = event.target.files[0];
            var reader = new FileReader();
            var self = this;
            reader.onload = function(event) {
                self.manager.import_str(event.target.result);
                self.refresh();
            };
            reader.readAsText(file);
        },
        onExport: function() {
            var data = this.manager.export_str();
            var blob = new Blob([data]);
            FileSaver.saveAs(blob, "bookmarks.tsv");
        },
        onPair: function() {
            var bookmark = this.manager.getCurrent();
            if (!bookmark) {
                return;
            }

            if (bookmark.envelope) {
                toolbar.setBookmarkPair(null);
                toolbar.graph.editor.resetLineColors();
            } else {
                toolbar.graph.editor.setEditMode("pair");
            }
        },
        onReport: function() {
            this.manager.generateReport(function(data) {
                var blob = new Blob([data]);
                FileSaver.saveAs(blob, "report.tsv");
            });
        },

        onActivate: function(model) {
            this.manager.setCurrent(model);
            toolbar.jumpBookmark(model);
        },
        onDelete: function(model) {
            toolbar.graph.bookmarks.remove(model);
            this.refresh();
        },
        onEdit: function(model) {
            toolbar.graph.bookmarks.storeAll();
        },

        onGetMidPoint: function(o) {
            o.midPoint = toolbar.graph.getMidPoint();
        },

        onSetCurrent: function() {
            var midPoint = toolbar.graph.getMidPoint();
            this.newMz = midPoint.mz;
            this.newRt = midPoint.rt;
        },
        onAdd: function() {
            var newBook = {
                name: this.newName,
                mz: this.newMz,
                rt: this.newRt,
                envelope: null,
            };
            toolbar.graph.bookmarks.add(newBook);
            this.refresh();
            this.newName = null;
            this.newMz = null;
            this.newRt = null;
        },
    },
});

this.settingsVM = new Vue({
    el: ".settings",
    data: function() {
        var settings = {
            colorblind: false,
            showtooltips: true,
            jumpOptions: true,
            traceWidth: 0.1,
        };

        // load settings out of localStorage
        if (localStorage.settings) {
            settings = JSON.parse(localStorage.settings);
        }

        return {
            settings: settings,
            detailPoints: graph.POINTS_PLOTTED_LIMIT,
            useLogHeight: graph.USE_LOG_SCALE_HEIGHT,
            scalingFactor: graph.LOG_SCALAR,
            useCylinders: graph.useCylinders,
            precision: {
                mz: 3,
                rt: 3,
                intensity: 3,
            },
        };
    },
    created: function() {
        this.onTooltipsChange();
    },
    methods: {
        saveSettings: function() {
            localStorage.settings = JSON.stringify(this.settings);
        },

        onDetailLevelChange: function() {
            graph.POINTS_PLOTTED_LIMIT = this.detailPoints;
            toolbar.toolbarVM.onRefresh();
        },
        onLogHeightChange: function() {
            graph.USE_LOG_SCALE_HEIGHT = this.useLogHeight;
            toolbar.toolbarVM.onRefresh();
        },
        onScalingFactorChange: function() {
            graph.LOG_SCALAR = +this.scalingFactor;
            toolbar.toolbarVM.onRefresh();
        },
        onCylindersChange: function() {
            graph.useCylinders = this.useCylinders;
            toolbar.toolbarVM.onRefresh();
        },
        onJumpModeChange: function() {
            this.saveSettings();
        },

        onPrecisionChange: function() {
            graph.ROUND_MZ = this.precision.mz;
            graph.ROUND_RT = this.precision.rt;
            graph.ROUND_INT = this.precision.intensity;
            graph.drawDataLabels();
            graph.renderDelayed();
        },
        
        goToWindow: function() {

            mzMin = parseFloat($('#mz-min').val());
            mzMax = parseFloat($('#mz-max').val());
            rtMin = parseFloat($('#rt-min').val());
            rtMax = parseFloat($('#rt-max').val());
            
            graph.setWindowArea(mzMin, mzMax, rtMin, rtMax);
        },

        onTraceWidthChange: function() {
            this.saveSettings();
            graph.setGuardWidth(+this.settings.traceWidth);
            graph.editor.setGuardWidth(+this.settings.traceWidth);
            graph.renderDelayed();
        },

        // Switches colorblind mode on or off
        onColorblindChange: function() {
            this.saveSettings();

            // re-make gradient cache with the new colors and re-draw data
            graph.makeGradientCache();
            graph.clearData();
            graph.dataBridge.requestPointsFromServer();
        },
        // Toggles all tooltips when user clicks the "t" button in the top right corner
        onTooltipsChange: function() {
            this.saveSettings();

            toolbar.containerEl.toggleClass("nohover", !this.settings.showtooltips);
        },
    },
})

};

function goToWindow() {
    console.log("test");
    mzMin = $('#mz-min').val();
    mzMax = $('#mz-max').val();
    rtMin = $('#rt-min').val();
    rtMax = $('#rt-max').val();
    
    console.log(mzMin);
    console.log(mzMax);
    console.log(rtMin);
    console.log(rtMax);
    
    console.log(Toolbar.graph);
    
    graph.setViewingArea(mzMin, mzMax, rtMin, rtMax);
}
