import { useCallback, useEffect, useState } from "react";
import { fetchCaptcha } from "../api";

/** 验证码获取/刷新（失败不阻断——登录演示可降级为无验证码提交） */
export function useCaptcha() {
  const [image, setImage] = useState("");
  const [key, setKey] = useState("");

  const refresh = useCallback(async () => {
    try {
      const captcha = await fetchCaptcha();
      setKey(captcha.key);
      setImage(`data:image/png;base64,${captcha.imageBase64}`);
    } catch {
      // 验证码不可用时不阻断登录演示
    }
  }, []);

  useEffect(() => { refresh(); }, [refresh]);
  return { image, key, refresh };
}
