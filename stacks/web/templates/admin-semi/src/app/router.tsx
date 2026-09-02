import { useNavigate, Routes, Route } from "react-router-dom";
import { useEffect } from "react";
import { setupReactNavigator } from "@yarch/react";
import BasicLayout from "../layouts/basic-layout";
import Dashboard from "../pages/dashboard";
import Login from "../pages/login";
import Products from "../pages/products";

function NavigatorSetup() {
  const navigate = useNavigate();
  useEffect(() => { setupReactNavigator(navigate); }, [navigate]);
  return null;
}

export default function App() {
  return (
    <>
      <NavigatorSetup />
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/" element={<BasicLayout />}>
          <Route index element={<Dashboard />} />
          <Route path="products" element={<Products />} />
        </Route>
      </Routes>
    </>
  );
}
