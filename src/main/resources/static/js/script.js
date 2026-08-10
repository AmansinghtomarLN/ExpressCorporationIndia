document.addEventListener("DOMContentLoaded", function () {
    var quickTrackForm = document.getElementById("quick-track-form");
    if (!quickTrackForm) return;

    var resultBox = document.getElementById("quick-track-result");

    quickTrackForm.addEventListener("submit", function (e) {
        e.preventDefault();
        var input = document.getElementById("quick-track-input");
        var trackingId = (input.value || "").trim();
        if (!trackingId) return;

        resultBox.innerHTML = '<p class="hint">Fetching latest status&hellip;</p>';

        fetch("/api/track/" + encodeURIComponent(trackingId))
            .then(function (res) { return res.json(); })
            .then(function (data) {
                if (!data.found) {
                    resultBox.innerHTML =
                        '<div class="alert alert-error">No shipment found for tracking ID "' +
                        escapeHtml(trackingId) + '". Please check the number and try again.</div>';
                    return;
                }
                var s = data.shipment;
                var events = (s.events || []).slice().reverse();
                var html = '<div class="track-result">';
                html += '<p><strong>' + escapeHtml(s.trackingId) + '</strong> &middot; ' +
                        escapeHtml(s.originCity) + ' &rarr; ' + escapeHtml(s.destinationCity) +
                        ' &nbsp; <span class="status-badge status-' + s.status + '">' +
                        formatStatus(s.status) + '</span></p>';
                if (events.length) {
                    html += '<div class="timeline">';
                    events.forEach(function (ev) {
                        html += '<div class="timeline-item">' +
                            '<div class="t-status">' + formatStatus(ev.status) + ' &mdash; ' + escapeHtml(ev.location) + '</div>' +
                            '<div class="t-meta">' + (ev.eventTime || '') + '</div>' +
                            (ev.remarks ? '<div>' + escapeHtml(ev.remarks) + '</div>' : '') +
                            '</div>';
                    });
                    html += '</div>';
                }
                html += '<p style="margin-top:12px;"><a class="btn btn-primary btn-sm" href="/track?trackingId=' +
                        encodeURIComponent(s.trackingId) + '">View full tracking page</a></p>';
                html += '</div>';
                resultBox.innerHTML = html;
            })
            .catch(function () {
                resultBox.innerHTML = '<div class="alert alert-error">Something went wrong. Please try again.</div>';
            });
    });

    function formatStatus(status) {
        return (status || "").replace(/_/g, " ");
    }

    function escapeHtml(str) {
        var div = document.createElement("div");
        div.appendChild(document.createTextNode(str));
        return div.innerHTML;
    }
});
