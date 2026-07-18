package dev.vixxer.mensajero.llamadas

import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

abstract class PeerObservadorBase : PeerConnection.Observer
{
    override fun onSignalingChange(estado: PeerConnection.SignalingState) {}
    override fun onIceConnectionChange(estado: PeerConnection.IceConnectionState) {}
    override fun onIceConnectionReceivingChange(recibiendo: Boolean) {}
    override fun onIceGatheringChange(estado: PeerConnection.IceGatheringState) {}
    override fun onIceCandidate(candidato: IceCandidate) {}
    override fun onIceCandidatesRemoved(candidatos: Array<out IceCandidate>) {}
    override fun onAddStream(stream: MediaStream) {}
    override fun onRemoveStream(stream: MediaStream) {}
    override fun onDataChannel(canal: DataChannel) {}
    override fun onRenegotiationNeeded() {}
    override fun onAddTrack(receptor: RtpReceiver, streams: Array<out MediaStream>) {}
    override fun onConnectionChange(nuevo: PeerConnection.PeerConnectionState) {}
}

abstract class SdpObservadorBase : SdpObserver
{
    override fun onCreateSuccess(descripcion: SessionDescription) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String?) {}
    override fun onSetFailure(error: String?) {}
}
