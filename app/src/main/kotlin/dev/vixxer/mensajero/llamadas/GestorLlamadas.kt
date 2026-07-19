package dev.vixxer.mensajero.llamadas

import android.content.Context
import android.media.AudioManager
import dev.vixxer.mensajero.nucleo.ConexionSocket
import dev.vixxer.mensajero.nucleo.EventosLlamada
import org.json.JSONObject
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

enum class FaseLlamada
{
    LIBRE,
    LLAMANDO,
    ENTRANTE,
    ACTIVA,
}

data class EstadoLlamada(
    val fase: FaseLlamada,
    val con: String?,
    val nombre: String,
    val video: Boolean,
    val local: VideoTrack?,
    val remoto: VideoTrack?,
)

object GestorLlamadas
{
    val eglBase: EglBase = EglBase.create()

    private var contexto: Context? = null
    private var factory: PeerConnectionFactory? = null
    private var administradorAudio: AudioManager? = null

    private var pc: PeerConnection? = null
    private var fuenteVideo: VideoSource? = null
    private var capturador: VideoCapturer? = null
    private var ayudanteTextura: SurfaceTextureHelper? = null
    private var pistaAudio: AudioTrack? = null
    private var pistaVideoLocal: VideoTrack? = null
    private var pistaVideoRemoto: VideoTrack? = null

    private var conId: String? = null
    private var conNombre: String = ""
    private var fase: FaseLlamada = FaseLlamada.LIBRE
    private var esVideo: Boolean = false
    private var ofertaPendiente: JSONObject? = null
    private val candidatosPendientes = mutableListOf<IceCandidate>()
    private var altavoz = false
    private var camaraFrontal = true

    private val oyentes = mutableSetOf<(EstadoLlamada) -> Unit>()

    fun preparar(aplicacion: Context)
    {
        if (contexto != null)
        {
            return
        }
        contexto = aplicacion.applicationContext
        administradorAudio = aplicacion.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(aplicacion.applicationContext).createInitializationOptions(),
        )
        val codificador = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decodificador = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(codificador)
            .setVideoDecoderFactory(decodificador)
            .createPeerConnectionFactory()
    }

    fun llamadasDisponibles(): Boolean = factory != null

    fun estadoLlamada(): EstadoLlamada =
        EstadoLlamada(fase, conId, conNombre, esVideo, pistaVideoLocal, pistaVideoRemoto)

    fun altavozActivo(): Boolean = altavoz

    fun alLlamada(cb: (EstadoLlamada) -> Unit): () -> Unit
    {
        oyentes.add(cb)
        return { oyentes.remove(cb) }
    }

    private fun avisar()
    {
        val e = estadoLlamada()
        for (cb in oyentes.toList())
        {
            cb(e)
        }
    }

    private fun servidores(): List<PeerConnection.IceServer> = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
            .setUsername(dev.vixxer.mensajero.BuildConfig.TURN_USUARIO)
            .setPassword(dev.vixxer.mensajero.BuildConfig.TURN_CREDENCIAL)
            .createIceServer(),
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443")
            .setUsername(dev.vixxer.mensajero.BuildConfig.TURN_USUARIO)
            .setPassword(dev.vixxer.mensajero.BuildConfig.TURN_CREDENCIAL)
            .createIceServer(),
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443?transport=tcp")
            .setUsername(dev.vixxer.mensajero.BuildConfig.TURN_USUARIO)
            .setPassword(dev.vixxer.mensajero.BuildConfig.TURN_CREDENCIAL)
            .createIceServer(),
    )

    private fun abrirMedia(video: Boolean)
    {
        val fabrica = factory ?: return
        val restriccionesAudio = MediaConstraints()
        val origenAudio = fabrica.createAudioSource(restriccionesAudio)
        pistaAudio = fabrica.createAudioTrack("audio0", origenAudio)
        if (video)
        {
            abrirCamara()
        }
        val audio = administradorAudio
        if (audio != null)
        {
            audio.mode = AudioManager.MODE_IN_COMMUNICATION
            altavoz = video
            audio.isSpeakerphoneOn = altavoz
        }
    }

    private fun abrirCamara()
    {
        val fabrica = factory ?: return
        val ctx = contexto ?: return
        val enumerador = Camera2Enumerator(ctx)
        val capturadorNuevo = crearCapturador(enumerador) ?: return
        capturador = capturadorNuevo
        val ayudante = SurfaceTextureHelper.create("captura", eglBase.eglBaseContext)
        ayudanteTextura = ayudante
        val origen = fabrica.createVideoSource(capturadorNuevo.isScreencast)
        fuenteVideo = origen
        capturadorNuevo.initialize(ayudante, ctx, origen.capturerObserver)
        capturadorNuevo.startCapture(1280, 720, 30)
        pistaVideoLocal = fabrica.createVideoTrack("video0", origen)
    }

    private fun crearCapturador(enumerador: Camera2Enumerator): VideoCapturer?
    {
        for (nombre in enumerador.deviceNames)
        {
            if (enumerador.isFrontFacing(nombre))
            {
                val capturadorNuevo = enumerador.createCapturer(nombre, null)
                if (capturadorNuevo != null)
                {
                    camaraFrontal = true
                    return capturadorNuevo
                }
            }
        }
        for (nombre in enumerador.deviceNames)
        {
            val capturadorNuevo = enumerador.createCapturer(nombre, null)
            if (capturadorNuevo != null)
            {
                camaraFrontal = false
                return capturadorNuevo
            }
        }
        return null
    }

    fun alternarAltavoz(): Boolean
    {
        altavoz = !altavoz
        administradorAudio?.isSpeakerphoneOn = altavoz
        return altavoz
    }

    private fun crearPc(paraId: String)
    {
        val fabrica = factory ?: return
        val configuracion = PeerConnection.RTCConfiguration(servidores())
        configuracion.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        pc = fabrica.createPeerConnection(configuracion, object : PeerObservadorBase()
        {
            override fun onIceCandidate(candidato: IceCandidate)
            {
                ConexionSocket.obtener()?.emit(
                    EventosLlamada.ICE,
                    JSONObject()
                        .put("para", paraId)
                        .put("candidato", candidatoAJson(candidato)),
                )
            }

            override fun onAddTrack(receptor: RtpReceiver, streams: Array<out MediaStream>)
            {
                val pista = receptor.track()
                if (pista is VideoTrack)
                {
                    pistaVideoRemoto = pista
                    avisar()
                }
            }

            override fun onConnectionChange(nuevo: PeerConnection.PeerConnectionState)
            {
                if (nuevo == PeerConnection.PeerConnectionState.FAILED ||
                    nuevo == PeerConnection.PeerConnectionState.CLOSED)
                {
                    limpiar()
                    avisar()
                }
            }
        })
        val streamId = listOf("local0")
        pistaAudio?.let { pc?.addTrack(it, streamId) }
        pistaVideoLocal?.let { pc?.addTrack(it, streamId) }
    }

    private fun vaciarCandidatos()
    {
        for (c in candidatosPendientes)
        {
            pc?.addIceCandidate(c)
        }
        candidatosPendientes.clear()
    }

    fun iniciarLlamada(paraId: String, nombre: String, video: Boolean): Boolean
    {
        if (fase != FaseLlamada.LIBRE || factory == null)
        {
            return false
        }
        esVideo = video
        conId = paraId
        conNombre = nombre
        fase = FaseLlamada.LLAMANDO
        candidatosPendientes.clear()
        abrirMedia(esVideo)
        crearPc(paraId)
        val restricciones = MediaConstraints()
        pc?.createOffer(object : SdpObservadorBase()
        {
            override fun onCreateSuccess(descripcion: SessionDescription)
            {
                pc?.setLocalDescription(object : SdpObservadorBase() {}, descripcion)
                ConexionSocket.obtener()?.emit(
                    EventosLlamada.OFRECER,
                    JSONObject()
                        .put("para", paraId)
                        .put("sdp", sdpAJson(descripcion))
                        .put("video", esVideo),
                )
                avisar()
            }
        }, restricciones)
        avisar()
        return true
    }

    fun contestar(): Boolean
    {
        val of = ofertaPendiente ?: return false
        if (factory == null)
        {
            return false
        }
        val de = of.optString("de")
        val sdp = jsonASdp(of.getJSONObject("sdp"))
        abrirMedia(esVideo)
        crearPc(de)
        pc?.setRemoteDescription(object : SdpObservadorBase()
        {
            override fun onSetSuccess()
            {
                vaciarCandidatos()
                pc?.createAnswer(object : SdpObservadorBase()
                {
                    override fun onCreateSuccess(descripcion: SessionDescription)
                    {
                        pc?.setLocalDescription(object : SdpObservadorBase() {}, descripcion)
                        ConexionSocket.obtener()?.emit(
                            EventosLlamada.CONTESTAR,
                            JSONObject()
                                .put("para", de)
                                .put("sdp", sdpAJson(descripcion)),
                        )
                        ofertaPendiente = null
                        fase = FaseLlamada.ACTIVA
                        avisar()
                    }
                }, MediaConstraints())
            }
        }, sdp)
        return true
    }

    fun colgar()
    {
        conId?.let {
            ConexionSocket.obtener()?.emit(EventosLlamada.COLGAR, JSONObject().put("para", it))
        }
        limpiar()
        avisar()
    }

    private fun limpiar()
    {
        val audio = administradorAudio
        if (audio != null)
        {
            audio.isSpeakerphoneOn = false
            audio.mode = AudioManager.MODE_NORMAL
        }
        altavoz = false
        try
        {
            capturador?.stopCapture()
        }
        catch (e: Exception)
        {
        }
        capturador?.dispose()
        capturador = null
        ayudanteTextura?.dispose()
        ayudanteTextura = null
        fuenteVideo?.dispose()
        fuenteVideo = null
        pc?.close()
        pc = null
        pistaAudio = null
        pistaVideoLocal = null
        pistaVideoRemoto = null
        conId = null
        conNombre = ""
        fase = FaseLlamada.LIBRE
        esVideo = false
        ofertaPendiente = null
        candidatosPendientes.clear()
    }

    fun alternarSilencio(): Boolean
    {
        val pista = pistaAudio ?: return false
        pista.setEnabled(!pista.enabled())
        return !pista.enabled()
    }

    fun cambiarCamara()
    {
        val cam = capturador as? CameraVideoCapturer ?: return
        cam.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler
        {
            override fun onCameraSwitchDone(frontal: Boolean)
            {
                camaraFrontal = frontal
            }

            override fun onCameraSwitchError(error: String?)
            {
            }
        })
    }

    fun alternarCamara(): Boolean
    {
        val pista = pistaVideoLocal ?: return false
        pista.setEnabled(!pista.enabled())
        return !pista.enabled()
    }

    fun camaraFrontalActiva(): Boolean = camaraFrontal

    fun alRecibirOferta(data: JSONObject)
    {
        if (fase != FaseLlamada.LIBRE)
        {
            ConexionSocket.obtener()?.emit(
                EventosLlamada.COLGAR,
                JSONObject().put("para", data.optString("de")),
            )
            return
        }
        ofertaPendiente = data
        fase = FaseLlamada.ENTRANTE
        conId = data.optString("de")
        conNombre = data.optString("usuario", "")
        esVideo = data.optBoolean("video", false)
        candidatosPendientes.clear()
        avisar()
    }

    fun alRecibirRespuesta(data: JSONObject)
    {
        if (pc == null || data.optString("de") != conId)
        {
            return
        }
        pc?.setRemoteDescription(object : SdpObservadorBase()
        {
            override fun onSetSuccess()
            {
                vaciarCandidatos()
                fase = FaseLlamada.ACTIVA
                avisar()
            }
        }, jsonASdp(data.getJSONObject("sdp")))
    }

    fun alRecibirIce(data: JSONObject)
    {
        if (data.optString("de") != conId || !data.has("candidato"))
        {
            return
        }
        val candidato = jsonACandidato(data.getJSONObject("candidato"))
        if (pc != null && pc?.remoteDescription != null)
        {
            pc?.addIceCandidate(candidato)
        }
        else
        {
            candidatosPendientes.add(candidato)
        }
    }

    fun alRecibirColgar(data: JSONObject)
    {
        if (data.optString("de") == conId)
        {
            limpiar()
            avisar()
        }
    }

    private fun sdpAJson(descripcion: SessionDescription): JSONObject =
        JSONObject()
            .put("type", descripcion.type.canonicalForm())
            .put("sdp", descripcion.description)

    private fun jsonASdp(json: JSONObject): SessionDescription =
        SessionDescription(
            SessionDescription.Type.fromCanonicalForm(json.getString("type")),
            json.getString("sdp"),
        )

    private fun candidatoAJson(candidato: IceCandidate): JSONObject =
        JSONObject()
            .put("candidate", candidato.sdp)
            .put("sdpMid", candidato.sdpMid)
            .put("sdpMLineIndex", candidato.sdpMLineIndex)

    private fun jsonACandidato(json: JSONObject): IceCandidate =
        IceCandidate(
            json.optString("sdpMid"),
            json.optInt("sdpMLineIndex"),
            json.optString("candidate"),
        )
}
