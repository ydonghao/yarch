import { useEffect } from "react";
import { Routes, Route, useNavigate } from "react-router-dom";
import { supply } from "@supply";
import SubLayout from "../layouts/sub-layout";
import Invoices from "../pages/invoices";

function NavigatorSetup() {
  const navigate = useNavigate();
  useEffect(() => { supply.setupAppNavigator(navigate); }, [navigate]); // 独立=注册；集成=noop（十-2）
  return null;
}

export default function SubRouter() {
  return (
    <>
      <NavigatorSetup />
      <Routes>
        <Route path="/" element={<SubLayout />}>
          <Route index element={<Invoices />} />
          {/* 基座 manifest 菜单 path="/invoices" 指向的内部路由（四-2：子应用自治路由表） */}
          <Route path="invoices" element={<Invoices />} />
        </Route>
      </Routes>
    </>
  );
}
