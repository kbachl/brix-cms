window.BrixModalDialog = {
    mount: function (id) {
        const host = document.getElementById(id);
        const overlay = host.querySelector(".brix-modal-overlay");
        if (!overlay) {
            return;
        }
        // Leave grid cells and their styles/events, but keep controls in their enclosing form.
        const destination = host.closest("form") || document.body;
        jQuery.event.special.brixModalRemove = {
            remove: function (handle) { handle.handler(); }
        };
        jQuery(host).on("brixModalRemove", function () { jQuery(overlay).remove(); });
        destination.appendChild(overlay);
        requestAnimationFrame(function () {
            const selected = overlay.querySelector(".node-picker tr.imxt-selected");
            if (selected) {
                const scroller = selected.closest(".imxt-body-container1");
                scroller.scrollTop += selected.getBoundingClientRect().top - scroller.getBoundingClientRect().top;
            }
        });
    }
};

// shortcut: Wicket 10.9.1 calls .trigger() on DOM nodes at Tab boundaries; remove when TrapFocusBehavior is fixed.
document.addEventListener("keydown", function (event) {
    if (event.key !== "Tab") {
        return;
    }
    const overlay = event.target.closest(".brix-modal-overlay.current-focus-trap");
    if (!overlay) {
        return;
    }
    const focusable = Array.from(overlay.querySelectorAll(
        "a[href], area[href], input, select, textarea, button, iframe, object, embed, [tabindex], [contenteditable]"
    )).filter(element => element.tabIndex >= 0 && !element.matches(":disabled") &&
        element.getClientRects().length > 0 && getComputedStyle(element).visibility !== "hidden");
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (first && (event.target === overlay ||
        (event.shiftKey ? event.target === first : event.target === last))) {
        event.preventDefault();
        event.stopImmediatePropagation();
        (event.shiftKey ? last : first).focus();
    }
}, true);
