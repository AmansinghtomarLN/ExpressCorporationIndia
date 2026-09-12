document.addEventListener("DOMContentLoaded", function () {
    var form = document.getElementById("manifest-form");
    if (!form) return;

    var tbody = document.querySelector("#manifest-lines tbody");
    var usedSet = new Set();
    var branches = [];
    try {
        var raw = document.getElementById("branch-options");
        branches = raw ? JSON.parse(raw.textContent) : [];
    } catch (e) {
        branches = [];
    }

    function partyOptionsHtml() {
        var first = document.querySelector("#manifest-lines .party-select");
        return first ? first.innerHTML : '<option value="">— Party —</option>';
    }

    function labelFor(id) {
        if (id == null || id === "") return "";
        var token = String(id);
        for (var i = 0; i < branches.length; i++) {
            if (String(branches[i].id) === token) return branches[i].label;
        }
        return "";
    }

    function listHtml() {
        var html = '<li data-value="" data-label="">— Select branch —</li>';
        branches.forEach(function (b) {
            html += '<li data-value="' + String(b.id) + '" data-label="' + escapeHtml(b.label) + '">' +
                escapeHtml(b.label) + "</li>";
        });
        return html;
    }

    function escapeHtml(text) {
        return String(text)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;");
    }

    function closeAll(except) {
        document.querySelectorAll(".combo").forEach(function (combo) {
            if (combo !== except) {
                var list = combo.querySelector(".combo-list");
                if (list) list.hidden = true;
            }
        });
    }

    function filterList(combo, query) {
        var q = (query || "").toLowerCase().trim();
        combo.querySelectorAll(".combo-list li").forEach(function (li) {
            var label = (li.getAttribute("data-label") || li.textContent || "").toLowerCase();
            var empty = !li.getAttribute("data-value");
            li.hidden = !!(!empty && q && label.indexOf(q) === -1);
        });
    }

    function selectBranch(combo, value, label) {
        var hidden = combo.querySelector(".combo-value");
        var search = combo.querySelector(".combo-search");
        if (hidden) {
            hidden.value = value || "";
            hidden.dispatchEvent(new Event("change", { bubbles: true }));
        }
        if (search) search.value = label || "";
        var list = combo.querySelector(".combo-list");
        if (list) list.hidden = true;
    }

    function wireCombo(combo) {
        if (!combo || combo.getAttribute("data-wired") === "1") return;
        combo.setAttribute("data-wired", "1");
        var hidden = combo.querySelector(".combo-value");
        var search = combo.querySelector(".combo-search");
        var list = combo.querySelector(".combo-list");
        if (!hidden || !search || !list) return;
        list.innerHTML = listHtml();
        if (hidden.value) {
            search.value = labelFor(hidden.value);
        }
        search.addEventListener("focus", function () {
            closeAll(combo);
            filterList(combo, search.value);
            list.hidden = false;
        });
        search.addEventListener("input", function () {
            list.hidden = false;
            filterList(combo, search.value);
            if (!search.value.trim()) {
                hidden.value = "";
            }
        });
        search.addEventListener("keydown", function (ev) {
            if (ev.key === "Escape") {
                list.hidden = true;
            }
        });
        list.addEventListener("mousedown", function (ev) {
            var li = ev.target.closest("li");
            if (!li) return;
            ev.preventDefault();
            selectBranch(combo, li.getAttribute("data-value"), li.getAttribute("data-label"));
        });
    }

    function wireAllCombos() {
        document.querySelectorAll(".combo").forEach(wireCombo);
    }

    function rowCount() {
        return tbody.querySelectorAll("tr").length;
    }

    function createRow(index) {
        var tr = document.createElement("tr");
        tr.innerHTML =
            '<td class="row-index"></td>' +
            '<td><select class="party-select" name="items[' + index + '].partyId">' +
            partyOptionsHtml() + "</select></td>" +
            '<td><input type="hidden" name="items[' + index + '].id" value="">' +
            '<input type="text" class="cno-input" name="items[' + index + '].consignmentNo" placeholder="Blank = auto" inputmode="numeric"></td>' +
            '<td><div class="combo">' +
            '<input type="hidden" class="combo-value dest-branch" name="items[' + index + '].destinationBranchId" value="">' +
            '<input type="search" class="combo-search" placeholder="Search branch…" autocomplete="off">' +
            '<ul class="combo-list" hidden></ul></div></td>' +
            '<td><input type="number" min="1" class="boxes-input" name="items[' + index + '].numberOfBoxes" placeholder="3"></td>' +
            '<td><input type="number" min="0.01" step="0.01" class="weight-input" name="items[' + index + '].weightKg" placeholder="40"></td>' +
            '<td class="cell-stack">' +
            '<input type="text" name="items[' + index + '].receiverName" placeholder="Receiver name">' +
            '<input type="text" name="items[' + index + '].receiverPhone" placeholder="Phone (optional)"></td>';
        return tr;
    }

    function renumber() {
        tbody.querySelectorAll("tr").forEach(function (tr, i) {
            var cell = tr.querySelector(".row-index");
            if (cell) cell.textContent = String(i + 1);
        });
    }

    function updateTotals() {
        var lines = 0;
        var boxes = 0;
        var weight = 0;
        tbody.querySelectorAll("tr").forEach(function (tr) {
            var cno = (tr.querySelector(".cno-input") || {}).value || "";
            var destSel = tr.querySelector(".dest-branch");
            var dest = destSel ? destSel.value : "";
            var recv = (tr.querySelector('input[name$=".receiverName"]') || {}).value || "";
            var boxVal = parseFloat((tr.querySelector(".boxes-input") || {}).value || "0");
            var wtVal = parseFloat((tr.querySelector(".weight-input") || {}).value || "0");
            if (cno.trim() || dest.trim() || recv.trim() || boxVal || wtVal) {
                lines += 1;
                boxes += isNaN(boxVal) ? 0 : boxVal;
                weight += isNaN(wtVal) ? 0 : wtVal;
            }
        });
        document.getElementById("total-lines").textContent = String(lines);
        document.getElementById("total-boxes").textContent = String(boxes);
        document.getElementById("total-weight").textContent = weight ? weight.toFixed(2) : "0";
        markUsedOnForm();
    }

    function markUsedOnForm() {
        tbody.querySelectorAll(".cno-input").forEach(function (input) {
            var v = (input.value || "").trim();
            input.classList.toggle("cno-taken", !!(v && usedSet.has(v) && !input.readOnly));
        });
    }

    form.addEventListener("input", updateTotals);
    var addBtn = document.getElementById("add-consignment");
    if (addBtn) {
        addBtn.addEventListener("click", function () {
            var start = rowCount();
            tbody.appendChild(createRow(start));
            wireAllCombos();
            renumber();
            updateTotals();
            var last = tbody.querySelector("tr:last-child .cno-input");
            if (last) last.focus();
        });
    }

    document.addEventListener("click", function (ev) {
        if (!ev.target.closest(".combo")) closeAll();
    });

    wireAllCombos();
    var destHeader = document.querySelector(".dest-header");
    if (destHeader) {
        destHeader.addEventListener("change", function () {
            var v = destHeader.value;
            var label = labelFor(v);
            document.querySelectorAll("#manifest-lines .combo").forEach(function (combo) {
                var hidden = combo.querySelector(".combo-value");
                if (hidden && !hidden.value && v) {
                    selectBranch(combo, v, label);
                }
            });
        });
    }
    updateTotals();
});
