document.addEventListener("DOMContentLoaded", function () {
    var form = document.getElementById("manifest-form");
    if (!form) return;

    var tbody = document.querySelector("#manifest-lines tbody");
    var usedSet = new Set();

    function branchOptionsHtml() {
        var first = document.querySelector("#manifest-lines .dest-branch");
        return first ? first.innerHTML : '<option value="">— Branch —</option>';
    }

    function partyOptionsHtml() {
        var first = document.querySelector("#manifest-lines .party-select");
        return first ? first.innerHTML : '<option value="">— Party —</option>';
    }

    function wireBranchSearch() {
        document.querySelectorAll(".branch-search").forEach(function (input) {
            var sel = document.getElementById(input.getAttribute("data-target"));
            if (!sel) return;
            input.addEventListener("input", function () {
                var q = (input.value || "").toLowerCase().trim();
                Array.prototype.forEach.call(sel.options, function (opt) {
                    if (!opt.value) {
                        opt.hidden = false;
                        return;
                    }
                    opt.hidden = !!(q && opt.text.toLowerCase().indexOf(q) === -1);
                });
            });
        });
    }

    function rowCount() {
        return tbody.querySelectorAll("tr").length;
    }

    function createRow(index) {
        var tr = document.createElement("tr");
        tr.innerHTML =
            '<td class="row-index"></td>' +
            '<td><select class="party-select" name="items[' + index + '].partyId">' +
            partyOptionsHtml() + '</select></td>' +
            '<td><input type="hidden" name="items[' + index + '].id" value="">' +
            '<input type="text" class="cno-input" name="items[' + index + '].consignmentNo" placeholder="Blank = auto" inputmode="numeric"></td>' +
            '<td><select class="dest-branch" name="items[' + index + '].destinationBranchId">' +
            branchOptionsHtml() + '</select></td>' +
            '<td><input type="number" min="1" class="boxes-input" name="items[' + index + '].numberOfBoxes" placeholder="3"></td>' +
            '<td><input type="number" min="0.01" step="0.01" class="weight-input" name="items[' + index + '].weightKg" placeholder="40"></td>' +
            '<td class="cell-stack">' +
            '<input type="text" name="items[' + index + '].receiverName" placeholder="REAL DIAGNOSTIC">' +
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
            var dest = destSel ? destSel.value : ((tr.querySelector('input[name$=".destinationCity"]') || {}).value || "");
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

    function enteredCnos() {
        var values = [];
        tbody.querySelectorAll(".cno-input").forEach(function (input) {
            var v = (input.value || "").trim();
            if (v) values.push(v);
        });
        return values;
    }

    function firstEmptyCnoInput() {
        var found = null;
        tbody.querySelectorAll(".cno-input").forEach(function (input) {
            if (!found && !input.readOnly && !(input.value || "").trim()) {
                found = input;
            }
        });
        return found;
    }

    function markUsedOnForm() {
        tbody.querySelectorAll(".cno-input").forEach(function (input) {
            var v = (input.value || "").trim();
            input.classList.toggle("cno-taken", !!(v && usedSet.has(v) && !input.readOnly));
        });
    }

    form.addEventListener("input", updateTotals);
    document.querySelectorAll("[data-add-rows]").forEach(function (btn) {
        btn.addEventListener("click", function () {
            var n = parseInt(btn.getAttribute("data-add-rows"), 10) || 10;
            var start = rowCount();
            for (var i = 0; i < n; i++) {
                tbody.appendChild(createRow(start + i));
            }
            renumber();
            updateTotals();
        });
    });

    wireBranchSearch();
    var lineFilter = document.getElementById("line-branch-filter");
    if (lineFilter) {
        lineFilter.addEventListener("input", function () {
            var q = (lineFilter.value || "").toLowerCase().trim();
            document.querySelectorAll("#manifest-lines .dest-branch").forEach(function (sel) {
                Array.prototype.forEach.call(sel.options, function (opt) {
                    if (!opt.value) {
                        opt.hidden = false;
                        return;
                    }
                    opt.hidden = !!(q && opt.text.toLowerCase().indexOf(q) === -1);
                });
            });
        });
    }
    var destHeader = document.querySelector('[name="destinationBranchId"]');
    if (destHeader) {
        destHeader.addEventListener("change", function () {
            var v = destHeader.value;
            document.querySelectorAll("#manifest-lines .dest-branch").forEach(function (sel) {
                if (!sel.value) sel.value = v;
            });
        });
    }
    updateTotals();
});
