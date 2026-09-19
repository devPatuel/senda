import { Routes, Route, Navigate } from 'react-router-dom'
import ProtectedRoute from './auth/ProtectedRoute'
import Layout from './components/Layout'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import DashboardPage from './pages/DashboardPage'
import TransactionsPage from './pages/TransactionsPage'
import CategoriesPage from './pages/CategoriesPage'
import AccountsPage from './pages/AccountsPage'
import DebtsPage from './pages/DebtsPage'
import InvestmentsPage from './pages/InvestmentsPage'
import AllocationPage from './pages/AllocationPage'
import NetWorthPage from './pages/NetWorthPage'
import RecurringPage from './pages/RecurringPage'
import ImportPage from './pages/ImportPage'
import SpacePage from './pages/SpacePage'
import TokensPage from './pages/TokensPage'
import WishlistPage from './pages/WishlistPage'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />

      <Route element={<ProtectedRoute />}>
        <Route element={<Layout />}>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/movimientos" element={<TransactionsPage />} />
          <Route path="/categorias" element={<CategoriesPage />} />
          <Route path="/cuentas" element={<AccountsPage />} />
          <Route path="/deudas" element={<DebtsPage />} />
          <Route path="/inversiones" element={<InvestmentsPage />} />
          <Route path="/reparto" element={<AllocationPage />} />
          <Route path="/recurrentes" element={<RecurringPage />} />
          <Route path="/importar" element={<ImportPage />} />
          {/* Patrimonio no longer has a menu entry; reachable by direct URL */}
          <Route path="/patrimonio" element={<NetWorthPage />} />
          <Route path="/deseos" element={<WishlistPage />} />
          <Route path="/pareja" element={<SpacePage />} />
          <Route path="/tokens" element={<TokensPage />} />
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
