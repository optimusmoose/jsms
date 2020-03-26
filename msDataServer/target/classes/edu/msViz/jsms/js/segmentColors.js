// segmentColors.js : methods for calculating the colors of traces and envelopes and other methods relating to colors
//
// authors: André Gillan, Jeb Rosen

SegmentColors = {};

// converts an rgb color as a number to a string of the form "#aaff00"
SegmentColors.colorAsString = function(number) {
    var hex = Number(number).toString(16);
    var color = '#' + '0'.repeat(6 - hex.length) + hex;  // gives a '#'-preceeded 6-digit hexadecimal string
    return color;
};

// "mixes" a color deterministically based on seq and factor (an arbitrary fraction), and a range of hues to exclude (optional)
SegmentColors.mixColor = function(seq, factor, excludedHuesLeft, excludedHuesRight) {
    if (seq === 0 || seq === null) return 0;
    else if (seq === -1) return "hsl(0,0%,50%)"; //noise will always be grey
    if (!excludedHuesLeft && !excludedHuesRight) { // if excluded hues are not given, we set the values to include an empty range
        excludedHuesLeft = 0;
        excludedHuesRight = 0;
    }
    
    var h = Math.round(SegmentColors.hueMapExcludeRange(seq * factor %1,excludedHuesLeft,excludedHuesRight));
    var s = 100;
    var l = Math.floor(SegmentColors.lightnessMap(seq * factor * (factor+1) %1)*100);
    return "hsl(" + h + "," + s + "%," + l + "%)";
};

// maps a number in the range 0-1 to a hue value in the range 0-360
// such that, by André's naive estimate (based on a piecewise linear function), the perceptual distance between
// two hue values is proportional to the difference between two inputs
SegmentColors.hueMap = function(input) {
    var premaps = [0,0.0217391304,0.3695652174,0.4347826087,0.6956521739,0.7391304348,0.9565217391,1];
    var hues = [0,15,75,150,210,255,330,360];
    if (!(input >= premaps[0] && input <= premaps[premaps.length - 1])) {return;} // if input is not of type number or is not in the correct range
    for (var i = 1; i < premaps.length; i++) {
        if (input <= premaps[i]) {
            return hues[i-1] + (hues[i] - hues[i-1])*(input - premaps[i-1])/(premaps[i] - premaps[i-1]);
        }
    }
};

// maps a hue value (0-360) to the premap of that value in hueMap (0-1).
// This function is useful for creating a mapping of hues that excludes ranges of values.
SegmentColors.inverseHueMap = function(input) {
    var premaps = [0,0.0217391304,0.3695652174,0.4347826087,0.6956521739,0.7391304348,0.9565217391,1];
    var hues = [0,15,75,150,210,255,330,360];
    if (!(input > -Infinity && input < Infinity)) {return;} // if input is not of type number or is not a real number
    input = input%360 + ((input < 0) ? 360 : 0); // put input into the range 0-360
    for (var i = 1; i < premaps.length; i++) {
        if (input <= hues[i]) {
            return premaps[i-1] + (premaps[i] - premaps[i-1])*(input - hues[i-1])/(hues[i] - hues[i-1]);
        }
    }
};

// maps an input value (0-1) to an output hue (0-360) based on hueMap, but excluding
// hues in the range (excludedRangeLeft - excludedRangeRight). Note that excludedRangeLeft can be greater
// than excludedRangeRight (such as in the case of excluding hues of the range 330 - 15)
SegmentColors.hueMapExcludeRange = function(input, excludedRangeLeft, excludedRangeRight) {
    var excludedRangeLeftPremap = SegmentColors.inverseHueMap(excludedRangeLeft);
    var excludedRangeRightPremap = SegmentColors.inverseHueMap(excludedRangeRight);
    var newRange, newPremap;
    if (excludedRangeLeftPremap > excludedRangeRightPremap) { // in this case, we have to exclude two ranges: (0 - excludedRangeRight), and (excludedRangeLeft - 360)
        newRange = 1 - (excludedRangeRightPremap) - (1 - excludedRangeLeftPremap); // This value is in the range (0-1). It specifies the size of the range of values that will be used for calculating hues
        newPremap = excludedRangeRightPremap + input*newRange;
    } else {
        newRange = 1 - (excludedRangeRightPremap - excludedRangeLeftPremap); // This value is in the range (0-1). It specifies the size of the range of values that will be used for calculating hues
        newPremap = (input*newRange < excludedRangeLeftPremap) ? input*newRange : excludedRangeRightPremap + (input*newRange - excludedRangeLeftPremap);
    }
    return SegmentColors.hueMap(newPremap);
};

// maps a number in the range (0-1) linearly to a number between MIN_LIGHTNESS and MAX_LIGHTNESS
SegmentColors.lightnessMap = function(input) {
    var MIN_LIGHTNESS = 0.25;
    var MAX_LIGHTNESS = 0.9;
    return MIN_LIGHTNESS + input*(MAX_LIGHTNESS-MIN_LIGHTNESS);
};

// calculates a color for a trace
SegmentColors.getTraceColor = function(traceNum) {
    var factor = 0.113;
    var excludedRangeLeft = 280;
    var excludedRangeRight = 340;

    return SegmentColors.mixColor(traceNum,factor,excludedRangeLeft,excludedRangeRight);
};

// calculates a color for an envelope
SegmentColors.getEnvelopeColor = function(envNum) {
    var factor = 0.157;
    var excludedRangeLeft = 340;
    var excludedRangeRight = 280;

    return SegmentColors.mixColor(envNum,factor,excludedRangeLeft,excludedRangeRight);
};
