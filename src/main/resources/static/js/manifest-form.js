document.addEventListener("DOMContentLoaded", function () {
    var form = document.getElementById("manifest-form");
    if (!form) return;

    var partySelect = document.getElementById("partyId");
    var tbody = document.querySelector("#manifest-lines tbody");
    var usedSet = new Set();

    function branchOptionsHtml() {
        var first = document.querySelector("#manifest-lines .dest-branch");
        return first ? first.innerHTML : '<option value="">— Branch —</option>';
    }

    function rowCount() {
        return tbody.querySelectorAll("tr").length;
    }

    function createRow(index) {
        var tr = document.createElement("tr");
        tr.innerHTML =
            '<td class="row-index"></td>' +
            '<td><input type="hidden" name="items[' + index + '].id" value="">' +
            '<input type="text" class="cno-input" name="items[' + index + '].consignmentNo" placeholder="37191" inputmode="numeric"></td>' +
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
        document.querySelectorAll("#pool-available .cno-chip").forEach(function (chip) {
            chip.classList.toggle("is-picked", enteredCnos().indexOf(chip.dataset.cno) !== -1);
        });
    }

    function assignCno(cno) {
        if (usedSet.has(cno)) return;
        if (enteredCnos().indexOf(cno) !== -1) return;
        var input = firstEmptyCnoInput();
        if (!input) {
            tbody.appendChild(createRow(rowCount()));
            renumber();
            input = firstEmptyCnoInput();
        }
        if (input) {
            input.value = cno;
            input.focus();
        }
        updateTotals();
    }

    function renderPool(pool) {
        var hint = document.getElementById("pool-hint");
        var stats = document.getElementById("pool-stats");
        var ranges = document.getElementById("pool-ranges");
        var available = document.getElementById("pool-available");
        var used = document.getElementById("pool-used");
        usedSet = new Set(pool.usedNumbers || []);

        hint.textContent = pool.partyName
            ? ("Next unused C.No for " + pool.partyName + ": " + (pool.nextNumber || "none left"))
            : "Select a party.";
        stats.hidden = false;
        stats.innerHTML =
            "<div><strong>" + pool.allocated + "</strong><span>allocated</span></div>" +
            "<div><strong>" + pool.used + "</strong><span>booked</span></div>" +
            "<div><strong>" + pool.remaining + "</strong><span>free</span></div>";

        ranges.innerHTML = (pool.ranges || []).map(function (r) {
            return '<div class="range-pill">' + r.start + "–" + r.end +
                " · " + r.remaining + " free</div>";
        }).join("") || '<p class="muted">No ranges assigned. Open the party and add a range first.</p>';

        available.innerHTML = (pool.available || []).map(function (n) {
            return '<button type="button" class="cno-chip" data-cno="' + n + '">' + n + "</button>";
        }).join("") || '<p class="muted">No unused numbers in the first slice of the range.</p>';

        used.innerHTML = (pool.usedNumbers || []).map(function (n) {
            return '<span class="cno-chip is-used" title="Already booked">' + n + "</span>";
        }).join("") || '<p class="muted">None booked yet.</p>';

        available.querySelectorAll(".cno-chip").forEach(function (btn) {
            btn.addEventListener("click", function () {
                assignCno(btn.dataset.cno);
            });
        });
        markUsedOnForm();
    }

    function loadPool() {
        var partyId = partySelect && partySelect.value;
        if (!partyId) return;
        fetch("/admin/manifests/party-pool?partyId=" + encodeURIComponent(partyId))
            .then(function (res) { return res.json(); })
            .then(function (data) {
                if (data.message && data.ok === false) {
                    document.getElementById("pool-hint").textContent = data.message;
                    return;
                }
                renderPool(data);
            })
            .catch(function () {
                document.getElementById("pool-hint").textContent = "Could not load C.Nos.";
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

    if (partySelect) {
        partySelect.addEventListener("change", loadPool);
        if (partySelect.value) loadPool();
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
