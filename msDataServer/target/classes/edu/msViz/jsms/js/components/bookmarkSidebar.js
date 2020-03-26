Vue.component('BookmarkRow', {
    props: ['bookmark', 'isCurrent'],
    data: function() {
        return {
            isEditing: false,
            editName: null,
            editMz: null,
            editRt: null,
        };
    },
    computed: {
        classObject: function() {
            return {
                "bookmark-row": true,
                "current": this.isCurrent,
            };
        }
    },
    methods: {
        onEdit: function() {
            this.editName = this.bookmark.name;
            this.editMz = this.bookmark.mz;
            this.editRt = this.bookmark.rt;

            this.isEditing = true;
        },
        onSetCurrent: function() {
            var m = {};
            var midPoint = this.$emit('get-mid-point', m);
            this.editMz = m.midPoint.mz; //m.mz for specific range
            this.editRt = m.midPoint.rt; //m.rt for specific range
        },
        onOkEdit: function() {
            this.bookmark.name = this.editName;
            this.bookmark.mz = this.editMz;
            this.bookmark.rt = this.editRt;

            this.$emit("edit", this.bookmark);

            this.isEditing = false;
        },
        onCancelEdit: function() {
            this.isEditing = false;
        },
    },
    template: `
        <tr v-if="!isEditing" v-bind:class="classObject">
            <td class="bookmark-labels">
                <span v-bind:title="bookmark.name" v-on:click="$emit('activate', bookmark)">{{bookmark.name}}</span>
            </td>
            <td>{{bookmark.mz}}</td>
            <td>{{bookmark.rt}}</td>
            <td><a href='#'><img class='button-mini' src='images/buttons/bk_edit.svg' alt='Edit' v-on:click="onEdit"></a></td>
            <td><a href='#'><img class='button-mini' src='images/buttons/bk_delete.svg' alt='Delete' v-on:click="$emit('delete', bookmark)"></a></td>
        </tr>
        <tr v-else class="bookmark-row">
            <td><input type="text" required size="10" v-model="editName" /></td>
            <td><input type="text" required size="8" v-model="editMz" /></td>
            <td><input type="text" required size="8" v-model="editRt" /></td>
            <td><a href='#'><img class='button-mini' src='images/buttons/bk_selectcurrent.svg' alt='Select Current Location' title='Select Current Location' v-on:click="onSetCurrent"></a></td>
            <td><a href='#'><img class='button-mini' src='images/buttons/bk_ok.svg' alt='Ok' v-on:click="onOkEdit"></a></td>
            <td><a href='#'><img class='button-mini' src='images/buttons/bk_cancel.svg' alt='Cancel' v-on:click="onCancelEdit"></a></td>
        </tr>
    `
});
