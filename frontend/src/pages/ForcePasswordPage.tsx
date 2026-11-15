import { useAuth } from '../auth'
import ChangePasswordForm from '../components/ChangePasswordForm'

/** Schermata a pieno schermo: finché la password temporanea non viene cambiata, il resto dell'app non è accessibile. */
export default function ForcePasswordPage() {
  const { profile, refresh, logout } = useAuth()
  return (
    <div className="login">
      <div className="card login-card">
        <h1 className="center">Scegli la tua password</h1>
        <p className="muted center">
          Ciao <strong>{profile?.username}</strong>! La password attuale è temporanea: sceglierne una tua è
          obbligatorio prima di continuare.
        </p>
        <ChangePasswordForm onDone={() => void refresh()} />
        <button className="btn btn-sm" onClick={logout}>
          Esci
        </button>
      </div>
    </div>
  )
}
