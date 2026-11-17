import { useEffect, useState } from 'react'
import type { InstallPrompt } from '../installPrompt'

export default function InstallApp() {
  const [prompt, setPrompt] = useState<InstallPrompt | null>(null)
  const [installed, setInstalled] = useState(false)

  useEffect(() => {
    const isStandalone = window.matchMedia('(display-mode: standalone)').matches ||
      (navigator as Navigator & { standalone?: boolean }).standalone === true
    setInstalled(isStandalone)

    setPrompt(window.ortofruttaInstallPrompt ?? null)
    function onInstalled() {
      setInstalled(true)
      setPrompt(null)
    }
    function onInstallPromptReady() {
      setPrompt(window.ortofruttaInstallPrompt ?? null)
      if (!window.ortofruttaInstallPrompt && window.matchMedia('(display-mode: standalone)').matches) {
        setInstalled(true)
      }
    }

    window.addEventListener('appinstalled', onInstalled)
    window.addEventListener('ortofruttainstallready', onInstallPromptReady)
    return () => {
      window.removeEventListener('appinstalled', onInstalled)
      window.removeEventListener('ortofruttainstallready', onInstallPromptReady)
    }
  }, [])

  async function install() {
    if (!prompt) return
    const currentPrompt = prompt
    setPrompt(null)
    await currentPrompt.prompt()
    const choice = await currentPrompt.userChoice
    if (choice.outcome === 'accepted') setInstalled(true)
  }

  return (
    <section className="card install-card" aria-labelledby="install-app-title">
      <span className="install-icon" aria-hidden="true">📲</span>
      <div className="install-copy">
        <p className="dashboard-eyebrow">SEMPRE A PORTATA DI MANO</p>
        <h2 id="install-app-title">Ortofrutta sul telefono</h2>
        <p className="muted">
          {installed
            ? 'L’app è installata: aprila dalla schermata Home.'
            : 'Aggiungi l’app alla schermata Home per aprirla a tutto schermo.'}
        </p>
        {!installed && prompt && (
          <button className="btn btn-primary install-button" onClick={() => void install()}>
            Installa app
          </button>
        )}
        {!installed && !prompt && (
          <p className="install-help">
            Android: menu ⋮ → <strong>Installa app</strong>. iPhone: <strong>Condividi</strong> →
            <strong> Aggiungi alla schermata Home</strong>.
          </p>
        )}
      </div>
    </section>
  )
}
