import { Navigate, Route, Routes } from "react-router";
import { Layout } from "./components/Layout";
import { CountriesPage } from "./pages/CountriesPage";
import { LocationsPage } from "./pages/LocationsPage";
import { DiscountsPage } from "./pages/DiscountsPage";
import { ExtrasPage } from "./pages/ExtrasPage";
import { NamedPage } from "./pages/NamedPage";
import { RatesPage } from "./pages/RatesPage";
import { ReservationsPage } from "./pages/ReservationsPage";
import { StockPage } from "./pages/StockPage";
import { TransfersPage } from "./pages/TransfersPage";
import { SessionGate } from "./session";

export function App() {
  return (
    <Routes>
      <Route
        element={
          <SessionGate>
            <Layout />
          </SessionGate>
        }
      >
        <Route index element={<Navigate to="/stock" replace />} />
        <Route path="/countries" element={<CountriesPage />} />
        <Route path="/car-types" element={<NamedPage kind="car-types" />} />
        <Route path="/car-classes" element={<NamedPage kind="car-classes" />} />
        <Route path="/locations" element={<LocationsPage />} />
        <Route path="/stock" element={<StockPage />} />
        <Route path="/transfers" element={<TransfersPage />} />
        <Route path="/rates" element={<RatesPage />} />
        <Route path="/extras" element={<ExtrasPage />} />
        <Route path="/discounts" element={<DiscountsPage />} />
        <Route path="/reservations" element={<ReservationsPage />} />
        <Route path="*" element={<Navigate to="/stock" replace />} />
      </Route>
    </Routes>
  );
}
