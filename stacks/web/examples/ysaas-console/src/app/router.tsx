/**
 * 基座路由：登录页 + 布局壳（基座自有页面 + 子应用前缀分发 + 404 兜底，四-5）。
 * 子应用路由由 manifest 声明式装配（三-4：基座禁硬编码子应用内部路由）。
 */
import { Navigate, Routes, Route, useNavigate } from "react-router-dom";
import { useEffect, type ReactNode } from "react";
import { setupReactNavigator } from "@yarch/react";
import BasicLayout from "../layouts/basic-layout";
import Dashboard from "../pages/dashboard";
import Login from "../pages/login";
import NotFound from "../pages/not-found";
import SubAppView from "../micro/sub-app";
import { microApps } from "./micro-apps.config";
import { authStore } from "../stores/auth";

function NavigatorSetup() {
  const navigate = useNavigate();
  useEffect(() => { setupReactNavigator(navigate); }, [navigate]);
  return null;
}

/** 登录门禁（十-1 登录态唯一归基座）：未持 token 一律回登录页 */
function AuthGate({ children }: { children: ReactNode }) {
  if (!authStore.token) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

export default function App() {
  return (
    <>
      <NavigatorSetup />
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/" element={<AuthGate><BasicLayout /></AuthGate>}>
          <Route index element={<Dashboard />} />
          {microApps.map((reg) => (
            <Route key={reg.name} path={`${reg.routePrefix}/*`} element={<SubAppView appName={reg.name} />} />
          ))}
          <Route path="*" element={<NotFound />} />
        </Route>
      </Routes>
    </>
  );
}
