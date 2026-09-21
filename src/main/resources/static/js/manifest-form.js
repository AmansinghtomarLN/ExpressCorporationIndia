document.addEventListener("DOMContentLoaded", function () {
    var form = document.getElementById("manifest-form");
    if (!form) return;

    var MIN_ROWS = 15;
    var tbody = document.querySelector("#manifest-lines tbody");
    var usedSet = new Set();
    var branches = [];
    var openShipments = [];
    try {
        var raw = document.getElementById("branch-options");
        branches = raw ? JSON.parse(raw.textContent) : [];
    } catch (e) {
        branches = [];
    }
    try {
        var openRaw = document.getElementById("open-shipments-data");
        openShipments = openRaw ? JSON.parse(openRaw.textContent) : [];
    } catch (e) {
        openShipments = [];
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
        return String(text == null ? "" : text)
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

    function selectBranch(combo, value, label, moveFocus) {
        var hidden = combo.querySelector(".combo-value");
        var search = combo.querySelector(".combo-search");
        if (hidden) {
            hidden.value = value || "";
            hidden.dispatchEvent(new Event("change", { bubbles: true }));
        }
        if (search) search.value = label || "";
        var list = combo.querySelector(".combo-list");
        if (list) list.hidden = true;
        if (moveFocus && search) {
            focusNext(search);
        }
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
            selectBranch(combo, li.getAttribute("data-value"), li.getAttribute("data-label"), true);
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
            '<td><select class="party-select nav-field" name="items[' + index + '].partyId">' +
            partyOptionsHtml() + "</select></td>" +
            '<td><input type="hidden" name="items[' + index + '].id" value="">' +
            '<input type="hidden" class="shipment-id" name="items[' + index + '].shipmentId" value="">' +
            '<input type="text" class="cno-input nav-field" name="items[' + index + '].consignmentNo" placeholder="Blank = auto" inputmode="numeric"></td>' +
            '<td><div class="combo">' +
            '<input type="hidden" class="combo-value dest-branch" name="items[' + index + '].destinationBranchId" value="">' +
            '<input type="search" class="combo-search nav-field" placeholder="Search branch…" autocomplete="off">' +
            '<ul class="combo-list" hidden></ul></div></td>' +
            '<td><input type="number" min="1" class="boxes-input nav-field" name="items[' + index + '].numberOfBoxes" placeholder="3"></td>' +
            '<td><input type="number" min="0.01" step="0.01" class="weight-input nav-field" name="items[' + index + '].weightKg" placeholder="40"></td>' +
            '<td class="cell-stack">' +
            '<input type="text" class="recv-name nav-field" name="items[' + index + '].receiverName" placeholder="Receiver name">' +
            '<input type="text" class="recv-phone nav-field" name="items[' + index + '].receiverPhone" placeholder="Phone (optional)"></td>' +
            '<td><button type="button" class="btn btn-sm btn-dark btn-remove-row">Remove</button></td>';
        return tr;
    }

    function reindex() {
        tbody.querySelectorAll("tr").forEach(function (tr, i) {
            var cell = tr.querySelector(".row-index");
            if (cell) cell.textContent = String(i + 1);
            tr.querySelectorAll("[name]").forEach(function (el) {
                el.name = el.name.replace(/items\[\d+]/, "items[" + i + "]");
            });
        });
    }

    function selectedShipmentIds() {
        var ids = {};
        tbody.querySelectorAll(".shipment-id").forEach(function (input) {
            if (input.value) ids[String(input.value)] = true;
        });
        return ids;
    }

    function findEmptyRow() {
        var rows = tbody.querySelectorAll("tr");
        for (var i = 0; i < rows.length; i++) {
            if (!rowHasData(rows[i])) return rows[i];
        }
        return null;
    }

    function rowHasData(tr) {
        var sid = (tr.querySelector(".shipment-id") || {}).value || "";
        var cno = (tr.querySelector(".cno-input") || {}).value || "";
        var destSel = tr.querySelector(".dest-branch");
        var dest = destSel ? destSel.value : "";
        var recv = (tr.querySelector(".recv-name") || {}).value || "";
        var party = (tr.querySelector(".party-select") || {}).value || "";
        var boxVal = parseFloat((tr.querySelector(".boxes-input") || {}).value || "0");
        var wtVal = parseFloat((tr.querySelector(".weight-input") || {}).value || "0");
        return !!(sid || cno.trim() || dest.trim() || recv.trim() || party || boxVal || wtVal);
    }

    function ensureHiddenParty(tr, partyId) {
        var cnoCell = tr.querySelector("td:nth-child(3)");
        if (!cnoCell) return;
        var hidden = cnoCell.querySelector('input[type="hidden"][name$=".partyId"]');
        if (!hidden) {
            hidden = document.createElement("input");
            hidden.type = "hidden";
            hidden.name = (tr.querySelector(".cno-input") || {}).name.replace(".consignmentNo", ".partyId");
            cnoCell.appendChild(hidden);
        }
        hidden.value = partyId || "";
    }

    function fillRowFromShipment(tr, shipment) {
        var sid = tr.querySelector(".shipment-id");
        if (sid) sid.value = shipment.id;
        var party = tr.querySelector(".party-select");
        if (party) {
            party.value = shipment.partyId != null ? String(shipment.partyId) : "";
            party.disabled = true;
            ensureHiddenParty(tr, party.value);
        }
        var cno = tr.querySelector(".cno-input");
        if (cno) {
            cno.value = shipment.trackingId || "";
            cno.readOnly = true;
        }
        var combo = tr.querySelector(".combo");
        if (combo && shipment.assignedBranchId) {
            selectBranch(combo, shipment.assignedBranchId, labelFor(shipment.assignedBranchId), false);
        }
        var boxes = tr.querySelector(".boxes-input");
        if (boxes) boxes.value = shipment.numberOfBoxes != null ? shipment.numberOfBoxes : "";
        var weight = tr.querySelector(".weight-input");
        if (weight) weight.value = shipment.weightKg != null ? shipment.weightKg : "";
        var recv = tr.querySelector(".recv-name");
        if (recv) recv.value = shipment.receiverName || "";
        var phone = tr.querySelector(".recv-phone");
        if (phone) phone.value = shipment.receiverPhone || "";
        tr.classList.add("from-open");
        tr.setAttribute("data-shipment", JSON.stringify(shipment));
    }

    function clearRow(tr) {
        var sid = tr.querySelector(".shipment-id");
        if (sid) sid.value = "";
        var idField = tr.querySelector('input[type="hidden"][name$=".id"]');
        if (idField) idField.value = "";
        var party = tr.querySelector(".party-select");
        if (party) {
            party.disabled = false;
            party.value = "";
        }
        var hiddenParty = tr.querySelector('input[type="hidden"][name$=".partyId"]');
        if (hiddenParty) hiddenParty.remove();
        var cno = tr.querySelector(".cno-input");
        if (cno) {
            cno.readOnly = false;
            cno.value = "";
        }
        var combo = tr.querySelector(".combo");
        if (combo) selectBranch(combo, "", "", false);
        ["boxes-input", "weight-input", "recv-name", "recv-phone"].forEach(function (cls) {
            var el = tr.querySelector("." + cls);
            if (el) el.value = "";
        });
        tr.classList.remove("from-open");
        tr.removeAttribute("data-shipment");
    }

    function shipmentFromRow(tr) {
        try {
            var raw = tr.getAttribute("data-shipment");
            if (raw) return JSON.parse(raw);
        } catch (e) { /* ignore */ }
        var sid = (tr.querySelector(".shipment-id") || {}).value;
        if (!sid) return null;
        var destSel = tr.querySelector(".dest-branch");
        var party = tr.querySelector(".party-select");
        return {
            id: Number(sid),
            trackingId: (tr.querySelector(".cno-input") || {}).value || "",
            partyId: party && party.value ? party.value : null,
            partyName: party && party.selectedOptions && party.selectedOptions[0] ? party.selectedOptions[0].text : "",
            assignedBranchId: destSel && destSel.value ? Number(destSel.value) : null,
            destinationCity: destSel ? labelFor(destSel.value) : "",
            numberOfBoxes: (tr.querySelector(".boxes-input") || {}).value || "",
            weightKg: (tr.querySelector(".weight-input") || {}).value || "",
            receiverName: (tr.querySelector(".recv-name") || {}).value || "",
            receiverPhone: (tr.querySelector(".recv-phone") || {}).value || ""
        };
    }

    function addOpenShipment(shipment) {
        var row = findEmptyRow();
        if (!row) {
            row = createRow(rowCount());
            tbody.appendChild(row);
            wireAllCombos();
        }
        fillRowFromShipment(row, shipment);
        reindex();
        renderOpenList();
        updateTotals();
        var boxes = row.querySelector(".boxes-input");
        if (boxes) boxes.focus();
    }

    function removeRow(tr) {
        var shipment = shipmentFromRow(tr);
        if (shipment && shipment.id && !openShipments.some(function (s) { return String(s.id) === String(shipment.id); })) {
            openShipments.unshift(shipment);
        }
        if (rowCount() > MIN_ROWS) {
            tr.remove();
        } else {
            clearRow(tr);
        }
        reindex();
        renderOpenList();
        updateTotals();
    }

    function renderOpenList() {
        var table = document.getElementById("open-shipments");
        var empty = document.getElementById("open-shipments-empty");
        if (!table) return;
        var body = table.querySelector("tbody");
        var filter = ((document.getElementById("open-shipment-filter") || {}).value || "").toLowerCase().trim();
        var selected = selectedShipmentIds();
        var visible = 0;
        body.innerHTML = "";
        openShipments.forEach(function (s) {
            if (selected[String(s.id)]) return;
            var hay = [s.trackingId, s.partyName, s.destinationCity, s.receiverName].join(" ").toLowerCase();
            if (filter && hay.indexOf(filter) === -1) return;
            visible += 1;
            var tr = document.createElement("tr");
            tr.innerHTML =
                "<td><strong>" + escapeHtml(s.trackingId) + "</strong></td>" +
                "<td>" + escapeHtml(s.partyName) + "</td>" +
                "<td>" + escapeHtml(s.destinationCity) + "</td>" +
                "<td>" + escapeHtml(s.numberOfBoxes) + "</td>" +
                "<td>" + escapeHtml(s.weightKg) + "</td>" +
                "<td>" + escapeHtml(s.receiverName) + "</td>" +
                '<td><button type="button" class="btn btn-sm btn-primary btn-add-open">Add</button></td>';
            tr.querySelector(".btn-add-open").addEventListener("click", function () {
                addOpenShipment(s);
            });
            body.appendChild(tr);
        });
        if (empty) empty.hidden = visible !== 0 || Object.keys(selected).length === openShipments.length && !filter;
        if (empty && visible === 0) {
            empty.hidden = false;
            empty.textContent = filter
                ? "No open shipments match that search."
                : (openShipments.length && Object.keys(selected).length === openShipments.length
                    ? "All open shipments are on this manifest. Remove a consignment row to put one back."
                    : "No open shipments. Book a shipment first, then add it here.");
        }
    }

    function updateTotals() {
        var lines = 0;
        var boxes = 0;
        var weight = 0;
        tbody.querySelectorAll("tr").forEach(function (tr) {
            var boxVal = parseFloat((tr.querySelector(".boxes-input") || {}).value || "0");
            var wtVal = parseFloat((tr.querySelector(".weight-input") || {}).value || "0");
            if (rowHasData(tr)) {
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

    function isNavField(el) {
        if (!el || el.disabled || el.readOnly) return false;
        if (el.type === "hidden") return false;
        if (el.tagName === "BUTTON") return false;
        return el.classList.contains("nav-field") || el.classList.contains("combo-search")
            || el.tagName === "SELECT" || el.tagName === "INPUT";
    }

    function navFields() {
        var fields = [];
        form.querySelectorAll(".manifest-head input, .manifest-head select, #manifest-lines .nav-field").forEach(function (el) {
            if (el.type === "hidden") return;
            if (el.disabled || el.readOnly) return;
            fields.push(el);
        });
        return fields;
    }

    function focusNext(fromEl) {
        var fields = navFields();
        var idx = fields.indexOf(fromEl);
        if (idx === -1) {
            var row = fromEl.closest("tr");
            if (row) {
                var rowFields = Array.prototype.slice.call(row.querySelectorAll(".nav-field")).filter(isNavField);
                idx = fields.indexOf(rowFields[rowFields.length - 1]);
            }
        }
        if (idx >= 0 && idx < fields.length - 1) {
            fields[idx + 1].focus();
            if (fields[idx + 1].select) {
                try { fields[idx + 1].select(); } catch (e) { /* ignore */ }
            }
            return;
        }
        if (fromEl.closest("#manifest-lines")) {
            var addBtn = document.getElementById("add-consignment");
            if (addBtn) addBtn.click();
        }
    }

    form.addEventListener("input", updateTotals);
    form.addEventListener("submit", function () {
        tbody.querySelectorAll(".shipment-id").forEach(function (input) {
            if (!String(input.value || "").trim()) input.disabled = true;
        });
    });
    form.addEventListener("change", function (ev) {
        if (ev.target && ev.target.classList.contains("party-select") && !ev.target.disabled) {
            var cno = ev.target.closest("tr") && ev.target.closest("tr").querySelector(".cno-input");
            if (cno && !cno.readOnly) cno.focus();
        }
        updateTotals();
    });
    form.addEventListener("keydown", function (ev) {
        if (ev.key !== "Enter") return;
        if (ev.target.closest("textarea")) return;
        if (ev.target.closest("button")) return;
        ev.preventDefault();
        focusNext(ev.target);
    });

    var addBtn = document.getElementById("add-consignment");
    if (addBtn) {
        addBtn.addEventListener("click", function () {
            var start = rowCount();
            var tr = createRow(start);
            tbody.appendChild(tr);
            wireAllCombos();
            reindex();
            updateTotals();
            var first = tr.querySelector(".party-select");
            if (first) first.focus();
        });
    }

    tbody.addEventListener("click", function (ev) {
        var btn = ev.target.closest(".btn-remove-row");
        if (!btn) return;
        removeRow(btn.closest("tr"));
    });

    var openFilter = document.getElementById("open-shipment-filter");
    if (openFilter) {
        openFilter.addEventListener("input", renderOpenList);
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
                    selectBranch(combo, v, label, false);
                }
            });
        });
    }
    tbody.querySelectorAll("tr.from-open").forEach(function (tr) {
        var sid = (tr.querySelector(".shipment-id") || {}).value;
        if (!sid) return;
        var match = openShipments.find(function (s) { return String(s.id) === String(sid); });
        if (match) tr.setAttribute("data-shipment", JSON.stringify(match));
    });
    renderOpenList();
    updateTotals();
    var firstParty = tbody.querySelector("tr:not(.from-open) .party-select");
    if (firstParty && !firstParty.disabled && !firstParty.value) {
        firstParty.focus();
    }
});
