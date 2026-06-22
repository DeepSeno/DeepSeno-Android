package com.enmooy.deepseno.service.relay

import android.util.Log
import kotlinx.serialization.json.JsonObject
import org.webrtc.*
import java.nio.ByteBuffer

/**
 * WebRTC DataChannel handler for P2P direct connection between phone and desktop.
 *
 * When the phone scans the QR code, the desktop becomes the "answerer" and the
 * phone becomes the "offerer". The phone creates an RTCPeerConnection, creates
 * a DataChannel, generates an SDP offer, and sends it via the relay server's
 * signaling endpoint. The desktop responds with an SDP answer. ICE candidates
 * are exchanged via the same signaling path.
 *
 * Once the DataChannel is open, all application data flows P2P — the server
 * is not involved in the data path.
 */
class RelayWebRTC(
    private val stunServers: List<String>,
    private val aesKey: ByteArray,
    private val sendSignal: (String) -> Unit,
    private val requestHandler: RelayRequestHandler,
    private val onStatusChange: (RelayTransportMode) -> Unit,
) {
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    private var factory: PeerConnectionFactory? = null
    val isConnected: Boolean get() = dataChannel?.state() == DataChannel.State.OPEN

    /** Create the PeerConnection and send an SDP offer via signaling. */
    fun initiate(context: android.content.Context) {
        // Initialize PeerConnectionFactory
        val initOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initOptions)

        factory = PeerConnectionFactory.builder()
            .createPeerConnectionFactory()

        val iceServers = stunServers.map { PeerConnection.IceServer.builder(it).createIceServer() }
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN

        peerConnection = factory!!.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    val candidateJson = """{"candidate":"${it.sdp}","sdpMid":"${it.sdpMid}","sdpMLineIndex":${it.sdpMLineIndex}}"""
                    sendSignal("""{"type":"ice-candidate","candidate":$candidateJson}""")
                }
            }
            override fun onDataChannel(ch: DataChannel?) {
                dataChannel = ch
                attachDataChannelHandlers(ch)
            }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> onStatusChange(RelayTransportMode.P2P)
                    PeerConnection.IceConnectionState.DISCONNECTED,
                    PeerConnection.IceConnectionState.FAILED -> onStatusChange(RelayTransportMode.RELAY)
                    else -> {}
                }
            }
            // Required overrides
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
        })

        // Create a DataChannel (we're the offerer)
        val dcInit = DataChannel.Init().apply { ordered = true }
        dataChannel = peerConnection!!.createDataChannel("deepseno", dcInit)
        attachDataChannelHandlers(dataChannel)

        // Create SDP offer
        peerConnection!!.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let {
                    peerConnection!!.setLocalDescription(object : SdpObserver {
                        override fun onSetSuccess() {
                            sendSignal("""{"type":"offer","sdp":"${it.description}"}""")
                        }
                        override fun onSetFailure(p0: String?) { Log.e(TAG, "setLocalDescription failed: $p0") }
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onCreateFailure(p0: String?) {}
                    }, it)
                }
            }
            override fun onCreateFailure(p0: String?) { Log.e(TAG, "createOffer failed: $p0") }
            override fun onSetSuccess() {}
            override fun onSetFailure(p0: String?) {}
        }, MediaConstraints())
    }

    /** Handle a signaling message from the desktop (answer or ICE candidate). */
    fun handleSignal(signalJson: String) {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .parseToJsonElement(signalJson) as? JsonObject ?: return
        val type = json["type"]?.toString()?.trim('"') ?: return

        when (type) {
            "answer" -> {
                val sdp = json["sdp"]?.toString()?.trim('"') ?: return
                peerConnection?.setRemoteDescription(object : SdpObserver {
                    override fun onSetSuccess() {}
                    override fun onSetFailure(p0: String?) { Log.e(TAG, "setRemoteDescription failed: $p0") }
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
            }
            "ice-candidate" -> {
                val candidate = json["candidate"] as? JsonObject ?: return
                val sdp = candidate["candidate"]?.toString()?.trim('"') ?: return
                val mid = candidate["sdpMid"]?.toString()?.trim('"') ?: ""
                val idx = candidate["sdpMLineIndex"]?.toString()?.toIntOrNull() ?: 0
                peerConnection?.addIceCandidate(IceCandidate(mid, idx, sdp))
            }
        }
    }

    /** Push an event to the desktop via the DataChannel. */
    fun pushEvent(encryptedFrame: ByteArray) {
        val buf = DataChannel.Buffer(ByteBuffer.wrap(encryptedFrame), false)
        dataChannel?.send(buf)
    }

    fun close() {
        dataChannel?.close()
        dataChannel = null
        peerConnection?.close()
        peerConnection = null
        factory?.dispose()
        factory = null
    }

    private fun attachDataChannelHandlers(ch: DataChannel?) {
        ch ?: return
        ch.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(p0: Long) {}
            override fun onStateChange() {
                val st = ch.state()
                if (st == DataChannel.State.OPEN) {
                    onStatusChange(RelayTransportMode.P2P)
                } else if (st == DataChannel.State.CLOSED) {
                    onStatusChange(RelayTransportMode.RELAY)
                }
            }
            override fun onMessage(buffer: DataChannel.Buffer?) {
                buffer ?: return
                val data = ByteArray(buffer.data.remaining())
                buffer.data.get(data)
                handleDataChannelMessage(data)
            }
        })
    }

    private fun handleDataChannelMessage(data: ByteArray) {
        if (data.isEmpty()) return
        val msgType = data[0].toInt() and 0xFF
        val payload = data.copyOfRange(1, data.size)

        if (msgType == 0x01) {
            // Request: payload is concatenated encrypted frames
            val frames = RelayCrypto.splitFrames(payload)
            Thread {
                try {
                    val request = RelayCrypto.decryptRequest(aesKey, frames)
                    // Note: requestHandler.handleRequest is suspend, but we're in a background
                    // thread. We use runBlocking here for simplicity; in production this should
                    // use a proper coroutine scope.
                    val response = kotlinx.coroutines.runBlocking {
                        requestHandler.handleRequest(
                            request.method, request.path, request.headers, request.body,
                        )
                    }
                    val respFrames = RelayCrypto.encryptResponse(
                        aesKey, response.status, response.headers, response.body,
                    )
                    val msg = ByteArray(1) + RelayCrypto.concat(respFrames)
                    msg[0] = 0x81.toByte()
                    val buf = DataChannel.Buffer(ByteBuffer.wrap(msg), false)
                    dataChannel?.send(buf)
                } catch (e: Exception) {
                    Log.e(TAG, "P2P request handling error", e)
                }
            }.start()
        }
    }

    companion object {
        private const val TAG = "RelayWebRTC"
    }
}
