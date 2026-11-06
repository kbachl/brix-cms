const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const removals = new Map();
const hosts = new Map();
const frames = [];
function container() {
    return { appendChild(child) { child.parent = this; } };
}
const body = container();
const form = container();
function jQuery(element) {
    return {
        on(event, handler) { removals.set(element, handler); },
        remove() { element.parent = null; }
    };
}
jQuery.event = { special: {} };
const context = { window: {}, jQuery, requestAnimationFrame: callback => frames.push(callback),
    document: { body, getElementById: id => hosts.get(id), addEventListener() {} }
};
vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../main/java/org/brixcms/web/BrixModalDialog.js'), 'utf8'), context);

const scroller = { scrollTop: 0, getBoundingClientRect: () => ({ top: 100 }) };
const selected = { closest: () => scroller, getBoundingClientRect: () => ({ top: 700 }) };
const overlay = { querySelector: () => selected };
const host = { querySelector: () => overlay.parent === host ? overlay : null, closest: () => form };
overlay.parent = host;
hosts.set('picker', host);

context.window.BrixModalDialog.mount('picker');
assert.equal(overlay.parent, form, 'dialog must leave the selected grid row but remain in its form');
frames.shift()();
assert.equal(scroller.scrollTop, 600, 'the current selection must be brought into view');
context.window.BrixModalDialog.mount('picker');
assert.equal(frames.length, 0, 'an already mounted overlay must not be mounted again');
jQuery.event.special.brixModalRemove.remove({ handler: removals.get(host) });
assert.equal(overlay.parent, null, 'replacing the original component must remove the relocated overlay');

const plainOverlay = { querySelector: () => null };
const plainHost = { querySelector: () => plainOverlay, closest: () => null };
hosts.set('plain', plainHost);
context.window.BrixModalDialog.mount('plain');
assert.equal(plainOverlay.parent, body, 'dialogs without an enclosing form use the document body');
frames.shift()();
jQuery.event.special.brixModalRemove.remove({ handler: removals.get(plainHost) });
assert.equal(plainOverlay.parent, null);
console.log('Modal dialog mounting checks passed');
