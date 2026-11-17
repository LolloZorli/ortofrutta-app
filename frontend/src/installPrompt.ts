export interface InstallPrompt extends Event {
  prompt: () => Promise<void>
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed'; platform: string }>
}

declare global {
  interface Window {
    ortofruttaInstallPrompt?: InstallPrompt
  }
}

window.addEventListener('beforeinstallprompt', (event) => {
  event.preventDefault()
  window.ortofruttaInstallPrompt = event as InstallPrompt
  window.dispatchEvent(new Event('ortofruttainstallready'))
})

window.addEventListener('appinstalled', () => {
  window.ortofruttaInstallPrompt = undefined
  window.dispatchEvent(new Event('ortofruttainstallready'))
})
