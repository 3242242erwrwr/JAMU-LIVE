// Global persistent state across Vercel serverless invocations
if (!global.users) {
    global.users = {};
}
if (!global.activeCalls) {
    global.activeCalls = {};
}
if (!global.webrtcSignals) {
    global.webrtcSignals = {}; // userId -> array of signals
}

module.exports = (req, res) => {
    // Enable CORS for Android client requests
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

    if (req.method === 'OPTIONS') {
        return res.status(200).end();
    }

    const { url, method } = req;
    const now = Date.now();
    const users = global.users;
    const activeCalls = global.activeCalls;
    const webrtcSignals = global.webrtcSignals;

    // Clean inactive users only after 1 hour
    Object.keys(users).forEach(id => {
        if (now - users[id].lastSeen > 3600000) {
            delete users[id];
        }
    });

    let body = req.body || {};
    if (typeof body === 'string') {
        try { body = JSON.parse(body); } catch (e) {}
    }

    // 1. GET /api or /api/users
    if (method === 'GET' && (url.includes('/users') || (!url.includes('/call') && !url.includes('/webrtc')))) {
        return res.status(200).json({
            status: "ok",
            server: "JAMU-LIVE Vercel WebRTC Signaling Server",
            users: Object.values(users)
        });
    }

    // 2. POST /api/users - Register or Heartbeat
    if (method === 'POST' && url.includes('/users')) {
        const { id, name, avatarColorHex } = body;
        if (!id || !name) {
            return res.status(400).json({ error: "Missing user id or name" });
        }
        users[id] = {
            id,
            name,
            status: "ONLINE",
            avatarColorHex: avatarColorHex || "#3F51B5",
            lastSeen: Date.now()
        };
        return res.status(200).json({ success: true, user: users[id] });
    }

    // 3. POST /api/call/start - Start a call
    if (method === 'POST' && url.includes('/call/start')) {
        const { callerId, callerName, targetId, targetName } = body;
        if (!callerId || !targetId) {
            return res.status(400).json({ error: "Missing callerId or targetId" });
        }

        const callId = `call_${callerId}_${targetId}_${Date.now()}`;
        activeCalls[callerId] = { callId, peerId: targetId, peerName: targetName, role: "caller", state: "calling" };
        activeCalls[targetId] = { callId, peerId: callerId, peerName: callerName, role: "callee", state: "incoming" };

        return res.status(200).json({ success: true, callId });
    }

    // 4. GET /api/call/check?userId=... - Poll call status
    if (method === 'GET' && url.includes('/call/check')) {
        const urlParams = new URLSearchParams(url.includes('?') ? url.split('?')[1] : '');
        const userId = urlParams.get('userId');

        if (!userId) {
            return res.status(400).json({ error: "Missing userId param" });
        }

        // Heartbeat
        if (users[userId]) {
            users[userId].lastSeen = Date.now();
        }

        const currentCall = activeCalls[userId] || null;
        const pendingSignals = webrtcSignals[userId] || [];
        webrtcSignals[userId] = []; // Clear read signals

        return res.status(200).json({
            success: true,
            call: currentCall,
            users: Object.values(users),
            signals: pendingSignals
        });
    }

    // 5. POST /api/call/respond - Accept, Decline or End Call
    if (method === 'POST' && url.includes('/call/respond')) {
        const { userId, action } = body;
        const currentCall = activeCalls[userId];

        if (currentCall) {
            const peerId = currentCall.peerId;
            if (action === 'accept') {
                activeCalls[userId].state = 'incall';
                if (activeCalls[peerId]) activeCalls[peerId].state = 'incall';
            } else if (action === 'decline' || action === 'end') {
                activeCalls[userId].state = 'ended';
                if (activeCalls[peerId]) activeCalls[peerId].state = 'ended';

                setTimeout(() => {
                    delete activeCalls[userId];
                    if (peerId) delete activeCalls[peerId];
                }, 3000);
            }
        }

        return res.status(200).json({ success: true, action });
    }

    // 6. POST /api/webrtc/signal - Relay SDP/ICE Candidates
    if (method === 'POST' && url.includes('/webrtc/signal')) {
        const { from, to, type, payload } = body;
        if (!to || !type || !payload) {
            return res.status(400).json({ error: "Missing to, type or payload" });
        }

        if (!webrtcSignals[to]) {
            webrtcSignals[to] = [];
        }
        webrtcSignals[to].push({ from, type, payload });

        return res.status(200).json({ success: true });
    }

    return res.status(200).json({
        status: "ok",
        message: "JAMU-LIVE Vercel Server API active",
        users: Object.values(users)
    });
};
