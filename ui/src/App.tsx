import { Navigate, Route, Routes } from "react-router";
import { Layout } from "./components/Layout";
import { MyReservationsPage } from "./pages/MyReservationsPage";
import { PricePage } from "./pages/PricePage";
import { ProfilePage } from "./pages/ProfilePage";
import { ReservationPage } from "./pages/ReservationPage";
import { SearchPage } from "./pages/SearchPage";
import { SelectionPage } from "./pages/SelectionPage";

export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<SearchPage />} />
        <Route path="/selection" element={<SelectionPage />} />
        <Route path="/price" element={<PricePage />} />
        <Route path="/reservations" element={<MyReservationsPage />} />
        <Route path="/reservations/:country/:id" element={<ReservationPage />} />
        <Route path="/profile" element={<ProfilePage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
