/**
 * <micro-app> 基座侧封装：loading 骨架（五-2：mount 完成前 UI 态由基座持有）+
 * 加载失败页含重试入口（四-5：禁白屏）。子应用路由前缀分发在 router.tsx 由 manifest 装配（三-4）。
 */
import { useEffect, useRef, useState } from "react";
import { Button, Spin, Typography } from "@douyinfe/semi-ui";
import { microApps } from "../app/micro-apps.config";

declare module "react" {
  namespace JSX {
    interface IntrinsicElements {
      "micro-app": React.DetailedHTMLProps<React.HTMLAttributes<HTMLElement>, HTMLElement> & {
        name: string;
        url: string;
      };
    }
  }
}

export default function SubAppView({ appName }: { appName: string }) {
  const reg = microApps.find((r) => r.name === appName);
  const hostRef = useRef<HTMLElement | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    const el = hostRef.current;
    if (!el || !reg) return;
    // 自定义元素事件经 addEventListener（成对移除，七-4）
    const onMounted = () => { setLoading(false); setFailed(false); };
    const onError = () => { setLoading(false); setFailed(true); };
    el.addEventListener("mounted", onMounted);
    el.addEventListener("error", onError);
    return () => {
      el.removeEventListener("mounted", onMounted);
      el.removeEventListener("error", onError);
    };
  }, [reg, retry]);

  if (!reg) {
    return <Typography.Text type="danger">未登记的子应用：{appName}（见 micro-apps.config.ts，十二-2）</Typography.Text>;
  }

  return (
    <div data-sub-app={reg.name}>
      {loading && <Spin size="large" style={{ display: "flex", justifyContent: "center", padding: 48 }} />}
      {failed && (
        <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 12, padding: 48 }}>
          <Typography.Text type="danger">子应用 {reg.name} 加载失败（入口 {reg.entry}）</Typography.Text>
          <Button theme="solid" type="warning" onClick={() => { setLoading(true); setRetry((n) => n + 1); }}>
            重试
          </Button>
        </div>
      )}
      <micro-app
        key={`${reg.name}-${retry}`}
        ref={(el: HTMLElement | null) => { hostRef.current = el; }}
        name={reg.name}
        url={reg.entry}
        style={{ display: failed ? "none" : "block" }}
      />
    </div>
  );
}
