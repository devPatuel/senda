import { Routes, Route, Navigate } from 'react-router-dom'
import ProtectedRoute from './auth/ProtectedRoute'
import Layout from './components/Layout'
import SectionTabs from './components/SectionTabs'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import DashboardPage from './pages/DashboardPage'
import AdminPage from './pages/AdminPage'
import TransactionsPage from './pages/TransactionsPage'
import CategoriesPage from './pages/CategoriesPage'
import AccountsPage from './pages/AccountsPage'
import DebtsPage from './pages/DebtsPage'
import InvestmentsPage from './pages/InvestmentsPage'
import AllocationPage from './pages/AllocationPage'
import RecurringPage from './pages/RecurringPage'
import ImportPage from './pages/ImportPage'
import SpacePage from './pages/SpacePage'
import TokensPage from './pages/TokensPage'
import PasswordPage from './pages/PasswordPage'
import WishlistPage from './pages/WishlistPage'

const MOVEMENT_TABS = [
  { to: '/movimientos', label: 'Movimientos' },
  { to: '/recurrentes', label: 'Recurrentes' },
]
const ASSET_TABS = [
  { to: '/cuentas', label: 'Cuentas' },
  { to: '/inversiones', label: 'Inversiones' },
  { to: '/deudas', label: 'Deudas' },
]
const PLANNING_TABS = [
  { to: '/reparto', label: 'Reparto' },
  { to: '/categorias', label: 'Categorías' },
  { to: '/deseos', label: 'Deseos' },
]

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />

      <Route element={<ProtectedRoute />}>
        <Route element={<Layout />}>
          <Route path="/" element={<DashboardPage />} />
          <Route element={<SectionTabs label="Movimientos" tabs={MOVEMENT_TABS} />}>
            <Route path="/movimientos" element={<TransactionsPage />} />
            <Route path="/recurrentes" element={<RecurringPage />} />
            <Route path="/importar" element={<ImportPage />} />
          </Route>
          <Route element={<SectionTabs label="Activos" tabs={ASSET_TABS} />}>
            <Route path="/cuentas" element={<AccountsPage />} />
            <Route path="/inversiones" element={<InvestmentsPage />} />
            <Route path="/deudas" element={<DebtsPage />} />
          </Route>
          <Route element={<SectionTabs label="Planificación" tabs={PLANNING_TABS} />}>
            <Route path="/reparto" element={<AllocationPage />} />
            <Route path="/categorias" element={<CategoriesPage />} />
            <Route path="/deseos" element={<WishlistPage />} />
          </Route>
          <Route path="/pareja" element={<SpacePage />} />
          <Route path="/tokens" element={<TokensPage />} />
          <Route path="/contrasena" element={<PasswordPage />} />
          <Route path="/admin" element={<AdminPage />} />
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
