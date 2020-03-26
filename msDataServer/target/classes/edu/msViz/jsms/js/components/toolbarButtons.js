// toolbarButtons.js: Vue components for the toolbar

Vue.component('ToolbarButton', {
    props: ['iconName', 'altText', 'tooltipText'],
    data: function() {
        var classObject = { 'center-block': true };
        classObject['button-' + this.iconName] = true;
        return { classObject: classObject };
    },
    computed: {
        imageSrc: function() {
            return "images/buttons/" + this.iconName + ".svg";
        }
    },
    template: `
    <li><a href="#" v-bind:data-tooltip="tooltipText">
        <img v-bind:class="classObject" v-bind:src="imageSrc" v-bind:alt="altText" v-on:click="$emit('click')"/>
    </a></li>`
});

Vue.component('ToolbarToggleButton', {
    props: ['iconName', 'isSelected', 'altText', 'tooltipText', 'tooltipTextToggled'],
    computed: {
        currentIconName: function() {
            return this.iconName + (this.isSelected ? "_toggle" : "");
        },
        currentTooltip: function() {
            if (this.isSelected) {
                return this.tooltipTextToggled || this.tooltipText;
            } else {
                return this.tooltipText;
            }
        }
    },
    template: `
        <ToolbarButton v-bind:icon-name="currentIconName" v-bind:alt-text="altText" v-bind:tooltip-text="currentTooltip" v-on:click="$emit('click')"/>
    `
});

Vue.component('ToolbarEnableButton', {
    props: ['iconName', 'isEnabled', 'altText', 'tooltipText'],
    computed: {
        currentIconName: function() {
            return this.iconName + (this.isEnabled ? "_enabled" : "");
        },
    },
    methods: {
        onClick: function() {
            if (this.isEnabled) {
                this.$emit('click');
            }
        },
    },
    template: `
        <ToolbarButton v-bind:icon-name="currentIconName" v-bind:alt-text="altText" v-bind:tooltip-text="tooltipText" v-on:click="onClick"/>
    `
});

Vue.component('ToolbarAutoToggleButton', {
    props: ['iconName', 'altText', 'tooltipText', 'tooltipTextToggled', 'hideTooltip'],
    data: function() {
        return {
            isSelected: false,
        };
    },
    computed: {
        shouldHideTooltip: function() {
            return this.hideTooltip !== undefined && this.hideTooltip;
        },
    },
    methods: {
        onClick: function() {
            this.isSelected = !this.isSelected;
            this.$emit('toggle', this.isSelected);
        },
    },
    template: `
        <ToolbarToggleButton v-bind:class="{ nohover: shouldHideTooltip }" v-bind:icon-name="iconName" v-bind:alt-text="altText" v-bind:tooltip-text="tooltipText" v-bind:tooltip-text-toggled="tooltipTextToggled" v-bind:is-selected="isSelected" v-on:click="onClick"/>
    `
});
