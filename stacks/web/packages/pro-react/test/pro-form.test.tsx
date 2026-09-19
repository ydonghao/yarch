import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { ApiError } from "@yarch/contract";

// antd Button 对两个汉字自动插空格（「提交」→「提 交」）——匹配用容错正则
const BTN_SUBMIT = /提\s*交/;

import { ProForm } from "../src/pro-form";

vi.stubGlobal(
  "matchMedia",
  vi.fn().mockReturnValue({ matches: false, addListener: vi.fn(), removeListener: vi.fn(), addEventListener: vi.fn(), removeEventListener: vi.fn() }),
);

describe("ProForm（component-library.md 五）", () => {
  it("提交成功后重置表单", async () => {
    const submit = vi.fn(async () => {});
    render(
      <ProForm
        fields={[
          { name: "name", label: "姓名", required: true },
          { name: "email", label: "邮箱", required: true },
        ]}
        submit={submit}
      />,
    );

    await userEvent.type(screen.getByLabelText("姓名"), "张三");
    await userEvent.type(screen.getByLabelText("邮箱"), "z@e.com");
    await userEvent.click(screen.getByRole("button", { name: BTN_SUBMIT }));

    await waitFor(() => expect(submit).toHaveBeenCalledWith({ name: "张三", email: "z@e.com" }));
    await waitFor(() => expect((screen.getByLabelText("姓名") as HTMLInputElement).value).toBe(""));
  });

  it("required 校验拦截空提交（不触发 submit）", async () => {
    const submit = vi.fn(async () => {});
    render(
      <ProForm fields={[{ name: "name", label: "姓名", required: true }]} submit={submit} />,
    );
    await userEvent.click(screen.getByRole("button", { name: BTN_SUBMIT }));
    expect(await screen.findByText("姓名 不得为空")).toBeDefined();
    expect(submit).not.toHaveBeenCalled();
  });

  it("submit 抛 ApiError 呈现全局错误态（message + traceId）", async () => {
    const submit = vi.fn(async () => {
      throw new ApiError(3001, "用户名已存在", "tid-9", 409);
    });
    render(<ProForm fields={[{ name: "name", label: "姓名", required: true }]} submit={submit} />);

    await userEvent.type(screen.getByLabelText("姓名"), "重复名");
    await userEvent.click(screen.getByRole("button", { name: BTN_SUBMIT }));

    expect(await screen.findByText("提交失败（code 3001）")).toBeDefined();
    expect(await screen.findByText(/用户名已存在 · traceId tid-9/)).toBeDefined();
  });
});
