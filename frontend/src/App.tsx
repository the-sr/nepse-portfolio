import { Toaster } from 'sonner';
import { Navigate, Route, Routes } from 'react-router-dom';
import Layout from '@/components/layout/Layout';
import ProtectedRoute from '@/components/layout/ProtectedRoute';
import Login from '@/pages/Login';
import Register from '@/pages/Register';
import Dashboard from '@/pages/Dashboard';
import PortfolioPage from '@/pages/Portfolio';
import StockDetailPage from '@/pages/StockDetail';
import Analysis from '@/pages/Analysis';
import Alerts from '@/pages/Alerts';
import Reports from '@/pages/Reports';
import Settings from '@/pages/Settings';
import Contact from '@/pages/Contact';
import NotFound from '@/pages/NotFound';

export default function App() {
  return (
    <>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route
          element={(
            <ProtectedRoute>
              <Layout />
            </ProtectedRoute>
          )}
        >
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/portfolio" element={<PortfolioPage />} />
          <Route path="/stocks/:symbol" element={<StockDetailPage />} />
          <Route path="/analysis" element={<Analysis />} />
          <Route path="/alerts" element={<Alerts />} />
          <Route path="/reports" element={<Reports />} />
          <Route path="/settings" element={<Settings />} />
          <Route path="/contact" element={<Contact />} />
        </Route>
        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="*" element={<NotFound />} />
      </Routes>
      <Toaster
        position="top-right"
        richColors
        closeButton
        toastOptions={{ style: { borderRadius: '14px' } }}
      />
    </>
  );
}
