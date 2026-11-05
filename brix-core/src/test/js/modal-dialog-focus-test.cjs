const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

let onKeydown;
let focused;
const element = (name, options = {}) => ({
    name, tabIndex: 0,
    matches: () => false,
    getClientRects: () => [1],
    closest: () => overlay,
    focus() { focused = this; },
    ...options
});
const first = element('first');
const middle = element('middle');
const last = element('last');
const hidden = element('hidden', { getClientRects: () => [] });
const disabled = element('disabled', { matches: () => true });
const overlay = element('overlay', { querySelectorAll: () => [first, middle, last, hidden, disabled] });

vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../main/java/org/brixcms/web/BrixModalDialog.js'), 'utf8'), {
    document: { addEventListener(type, handler, capture) {
        assert.equal(type, 'keydown');
        assert.equal(capture, true);
        onKeydown = handler;
    } },
    getComputedStyle: () => ({ visibility: 'visible' })
});

function press(target, shiftKey = false, key = 'Tab') {
    focused = null;
    let prevented = false;
    let stopped = false;
    onKeydown({ target, shiftKey, key,
        preventDefault() { prevented = true; },
        stopImmediatePropagation() { stopped = true; }
    });
    assert.equal(prevented, focused !== null);
    assert.equal(stopped, prevented);
    return focused;
}
assert.equal(press(first, true), last);
assert.equal(press(last), first);
assert.equal(press(overlay), first);
assert.equal(press(middle), null);
assert.equal(press(first, false, 'Escape'), null);
assert.equal(press(element('outside', { closest: () => null })), null);
console.log('Modal dialog focus checks passed');
