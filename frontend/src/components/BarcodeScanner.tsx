import { useEffect, useRef, useState } from 'react'
import Modal from './Modal'

interface DetectedBarcode {
  rawValue: string
}

interface Detector {
  detect: (source: HTMLVideoElement) => Promise<DetectedBarcode[]>
}

interface DetectorConstructor {
  new (options: { formats: string[] }): Detector
  getSupportedFormats: () => Promise<string[]>
}

interface Props {
  onDetected: (code: string) => void
  onClose: () => void
}

const FORMATS = ['ean_13', 'ean_8', 'upc_a', 'upc_e', 'code_128']

export default function BarcodeScanner({ onDetected, onClose }: Props) {
  const video = useRef<HTMLVideoElement>(null)
  const onDetectedRef = useRef(onDetected)
  const [status, setStatus] = useState('Avvio della fotocamera…')

  useEffect(() => {
    onDetectedRef.current = onDetected
  }, [onDetected])

  useEffect(() => {
    let active = true
    let stream: MediaStream | null = null
    let timer = 0
    const BarcodeDetector = (window as unknown as { BarcodeDetector?: DetectorConstructor }).BarcodeDetector

    async function start() {
      if (!navigator.mediaDevices?.getUserMedia || !BarcodeDetector) {
        setStatus('La scansione con fotocamera non è supportata da questo browser. Usa uno scanner USB/Bluetooth o inserisci il codice nel campo cassa.')
        return
      }

      try {
        const supported = await BarcodeDetector.getSupportedFormats()
        const formats = FORMATS.filter((format) => supported.includes(format))
        if (!active) return
        if (formats.length === 0) {
          setStatus('Questo browser non supporta i formati di codice a barre usati dai prodotti.')
          return
        }

        const detector = new BarcodeDetector({ formats })
        stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: 'environment' } }, audio: false })
        if (!active) {
          stream.getTracks().forEach((track) => track.stop())
          return
        }

        const element = video.current
        if (!element) return
        element.srcObject = stream
        await element.play()
        setStatus('Inquadra il codice a barre')

        const scan = async () => {
          if (!active || !video.current) return
          try {
            const codes = await detector.detect(video.current)
            const code = codes[0]?.rawValue
            if (code) {
              onDetectedRef.current(code)
              return
            }
          } catch {
            // Il frame può non essere ancora pronto: continua la scansione.
          }
          if (active) timer = window.setTimeout(() => void scan(), 180)
        }
        void scan()
      } catch {
        if (active) setStatus('Fotocamera non disponibile. Controlla i permessi del browser o inserisci il codice manualmente.')
      }
    }

    void start()
    return () => {
      active = false
      window.clearTimeout(timer)
      stream?.getTracks().forEach((track) => track.stop())
    }
  }, [])

  return (
    <Modal title="Scansiona codice a barre" onClose={onClose}>
      <video ref={video} className="barcode-video" muted playsInline aria-label="Anteprima della fotocamera" />
      <p className="barcode-status" role="status">{status}</p>
      <button className="btn btn-block" onClick={onClose}>Chiudi fotocamera</button>
    </Modal>
  )
}
