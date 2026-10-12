// In-memory state for Vercel Serverless Function
let users = {};
let activeCalls = {};

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

    // Clean inactive users (idle for > 30 seconds)
    Object.keys(users).forEach(id => {
        if (now - users[id].lastSeen > 30000) {
            delete users[id];
        }
    });

    let body = req.body || {};
    if (typeof body === 'string') {
        try { body = JSON.parse(body); } catch (e) {}
    }

    // 1. GET /api/users or GET /api/index
    if (method === 'GET' && (url.includes('/users') || url === '/api' || url === '/api/index')) {
        return res.status(200).json({
            status: "ok",
            server: "JAMU-LIVE Vercel Signaling Server",
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

    // 3. POST /api/call - Start a call
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
        const urlParams = new URLSearchParams(url.split('?')[1] || '');
        const userId = urlParams.get('userId');

        if (!userId) {
            return res.status(400).json({ error: "Missing userId param" });
        }

        // Heartbeat
        if (users[userId]) {
            users[userId].lastSeen = Date.now();
        }

        const currentCall = activeCalls[userId] || null;
        return res.status(200).json({
            success: true,
            call: currentCall,
            users: Object.values(users)
        });
    }

    // 5. POST /api/call/respond - Accept, Decline or End Call
    if (method === 'POST' && url.includes('/call/respond')) {
        const { userId, action } = body;
        const currentCall = activeCalls[userId];

        if (!currentCall) {
            return res.status(200).json({ success: true, message: "No active call" });
        }

        const peerId = currentCall.peerId;

        if (action === 'accept') {
            activeCalls[userId].state = 'incall';
            if (activeCalls[peerId]) activeCalls[peerId].state = 'incall';
        } else if (action === 'decline' || action === 'end') {
            delete activeCalls[userId];
            if (activeCalls[peerId]) delete activeCalls[peerId];
        }

        return res.status(200).json({ success: true, action });
    }

    return res.status(200).json({
        status: "ok",
        message: "JAMU-LIVE Vercel Server API active",
        users: Object.values(users)
    });
};
