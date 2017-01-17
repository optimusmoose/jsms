// buttons.js: user interface controls and toolbar code

function Toolbar(graph, containerEl) {
    this.graph = graph;
    this.containerEl = $(containerEl);
    
    // Keep track of whether the buttons are toggled
    this.isButtonSelected = {
        "data" : false,             //All data mode on or off
        "detail": false,            //Detail level slider
        "_3d" : false,              //3d display on or off
        "hover" : false,            //Whether hover text is off; changed by clicking hover text button at top right
        "colorblind": false,
        "save_as" : false           //save as button
    };

    // Global settings to save in localStorage
    this.settings = {
        colorblind : false,
        hidetooltips : false,
    };

    // load settings out of localStorage
    if (localStorage.settings) {
        this.settings = JSON.parse(localStorage.settings);
        this.isButtonSelected.colorblind = this.settings.colorblind;
        this.isButtonSelected.hover = this.settings.hidetooltips;
    }
    
    this.connect();
}

// returns the filepath for a button's image. If the button cannot be toggled,
// use toggleStatus = false
Toolbar.imagePath = function(idString, toggleStatus) {
    var path = "images/buttons/" + idString;
    if (toggleStatus) {
        path += "_toggle";
    }
    return path + ".svg";
};

// persists the user settings
Toolbar.prototype.saveSettings = function() {
    localStorage.settings = JSON.stringify(this.settings);
};

// sets the hover texts and popout positions that do not change during the program's run
Toolbar.prototype.setupHoverTexts = function() {
    this.containerEl.find(".open-tooltip").attr("data-tooltip", "Open (o)");
    this.containerEl.find(".save_as-tooltip").attr("data-tooltip", "Save As");
    
    this.containerEl.find(".refresh-tooltip").attr("data-tooltip", "Refresh (r)");
    this.containerEl.find(".view-tooltip").attr("data-tooltip", "View All Data (a)");
    this.containerEl.find(".jump-tooltip").attr("data-tooltip", "Jump to m/z (J)");
    this.containerEl.find(".detail-tooltip").attr("data-tooltip", "Adjust Detail Level");

    this.updateDynamicHoverTexts();
};

// updates the hover texts that can change dynamically
Toolbar.prototype.updateDynamicHoverTexts = function() {
    this.containerEl.find("._3d-tooltip").attr("data-tooltip", this.isButtonSelected._3d ? "2d Mode (d)" : "3d Mode (d)");
};

// updates all icons to their correct toggled/untoggled state
Toolbar.prototype.updateIconStates = function() {
    Object.keys(this.isButtonSelected).forEach(function(state) {
        this.containerEl.find("." + state).attr("src", Toolbar.imagePath(state, this.isButtonSelected[state]));
    }, this);
    
    this.containerEl.toggleClass("nohover", this.settings.hidetooltips);
};

// positions a pop-out such as a slider next to its button
Toolbar.prototype.positionPopout = function(buttonSelector, popoutSelector) {
    var pos = this.containerEl.find(buttonSelector).offset();
    var width = this.containerEl.find(buttonSelector).outerWidth();
    this.containerEl.find(popoutSelector).css({
        position:"absolute",
        top: (pos.top - 20) + "px",
        left: (pos.left + width + 10) + "px",
    });
};

// toggle a popout by iconName. Don't pass newState for an automatic toggle
Toolbar.prototype.togglePopout = function(iconName, newState) {
    if (newState === undefined) {
        newState = !this.isButtonSelected[iconName];
    }
    this.isButtonSelected[iconName] = newState;
    
    var containerName = "." + iconName + "-tooltip";
    var popoutName = "." + iconName + "-popout";
    
    this.containerEl.find(containerName).toggleClass("nohover", newState);
    this.positionPopout(containerName, popoutName);
    this.containerEl.find(popoutName).toggle(newState);
};

// checks whether a popout needs to be hidden (not being hovered) and hides it if necessary
Toolbar.prototype.checkHidePopout = function(name) {
    if (!( this.containerEl.find("." + name + "-tooltip").is(":hover") || this.containerEl.find("." + name + "-popout").is(":hover") ))
    {
        this.togglePopout(name, false);
    }
};

// sets up the toolbar buttons for real
Toolbar.prototype.connect = function() {
    
this.setupHoverTexts();
this.updateIconStates();

var detailRange = this.containerEl.find(".detailRange")[0];
detailRange.value = this.graph.POINTS_PLOTTED_LIMIT;
this.containerEl.find(".detailLevelField").text(String(this.graph.POINTS_PLOTTED_LIMIT));

var toolbar = this;
var graph = this.graph;

/*************************************************
||                 ACCESSIBILITY                ||
*************************************************/

// Swithces cololblind mode on or off
this.containerEl.find(".colorblind").click(function() {
    toolbar.isButtonSelected.colorblind = toolbar.settings.colorblind = !toolbar.settings.colorblind;
    toolbar.saveSettings();
    
    // re-make gradient cache with the new colors and re-draw data
    graph.makeGradientCache();
    graph.clearData();
    graph.dataBridge.requestPointsFromServer();
    
    toolbar.updateIconStates();
});

// Toggles all tooltips when user clicks the "t" button in the top right corner
this.containerEl.find(".hover").click(function () {
    toolbar.isButtonSelected.hover = toolbar.settings.hidetooltips = !toolbar.settings.hidetooltips;
    toolbar.saveSettings();
    toolbar.updateIconStates();
});

// help menu button
this.containerEl.find(".instruction").click(function() {
    window.open('instruction.html', '_blank');
});


/*************************************************
||                 TOOL BAR                     ||
*************************************************/

// open file button
this.containerEl.find(".open").click(function() {
    graph.dataBridge.openFile();
});

// save popout "Save" button
this.containerEl.find(".saveSubmit").click(function(){
    var filename = graph.containerEl.find(".filename").val();
    graph.dataBridge.saveDataModel(filename);
    toolbar.togglePopout("save_as", false);
    toolbar.updateIconStates();
});

// save popout
this.containerEl.find(".save_as").click(function(){
    toolbar.togglePopout("save_as");
    toolbar.updateIconStates();
});

// 2d/3d view switch
this.containerEl.find("._3d").click(function() {
    toolbar.isButtonSelected._3d = !toolbar.isButtonSelected._3d;
    graph.toggleTopView(toolbar.isButtonSelected._3d);
    
    toolbar.updateIconStates();
    toolbar.updateDynamicHoverTexts();
});

// "refresh" button
this.containerEl.find(".refresh").click(function() {
    graph.dataBridge.requestPointsFromServer();
});

// "view all data" button
this.containerEl.find(".data").click(function() {
    graph.dataBridge.openFileWaitLoop();
});

// "jump to m/z" button
this.containerEl.find(".jump").click(function() {
    var newmz = prompt("Enter an mz value to jump to:");
    if (newmz && /^\d+\.?\d*$/.test(newmz)) {
        var vr = graph.viewRange;
        var newmzmin = +newmz - (vr.mzrange / 2);
        graph.setViewingArea(newmzmin, vr.mzrange, vr.rtmin, vr.rtrange);
    }
});

// detail level slider bar popout
this.containerEl.find(".detail").click(function() {
    toolbar.togglePopout("detail");
    toolbar.updateIconStates();
});

// add and select buttons
this.containerEl.find(".add").click(function() { graph.editor.newGroup(); });
this.containerEl.find(".select").click(function() { graph.editor.selectGroup(); });


// Toggles popouts off if clicked outside of
$("body").click(function()
{
    toolbar.checkHidePopout("save_as");
    toolbar.checkHidePopout("detail");
    toolbar.updateIconStates();
});

// detail level configuration
var detailField = this.containerEl.find(".detailLevelField");
this.containerEl.find(".detailRange").on("input", function() {
    detailField.text(String(this.value));
    graph.POINTS_PLOTTED_LIMIT = graph.POINTS_VISIBLE_LIMIT = this.value;
});
};
