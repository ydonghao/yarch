import { mount } from "@vue/test-utils";
import { describe, expect, it, vi } from "vitest";

import { ApiError } from "@yarch/contract";

import { ProForm, type ProField } from "../src/pro-form";

class FakeResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}
vi.stubGlobal("ResizeObserver", FakeResizeObserver);
vi.stubGlobal(
  "matchMedia",
  vi.fn().mockReturnValue({ matches: false, addListener: vi.fn(), removeListener: vi.fn(), addEventListener: vi.fn(), removeEventListener: vi.fn() }),
);

const fields: ProField[] = [
  { name: "name", label: "姓名", required: true },
  { name: "email", label: "邮箱", required: true },
  { name: "role", label: "角色", widget: "select", options: [{ label: "管理员", value: 1 }] },
];

function findInput(wrapper: ReturnType<typeof mount>, label: string) {
  // ElFormItem 按 label 定位对应控件（label 渲染为 .el-form-item__label）
  const item = wrapper.findAll(".el-form-item").find((it) => it.find(".el-form-item__label").text() === label);
  return item!.find("input");
}

describe("ProForm（component-library.md 五）", () => {
  it("必填校验：空表单提交不触发 submit 并呈现字段错误", async () => {
    const submit = vi.fn(async () => {});
    const wrapper = mount(ProForm, { props: { fields, submit } });

    const button = wrapper.findAll("button").find((b) => b.text() === "提交");
    await button!.trigger("click");
    await vi.waitFor(() => expect(wrapper.text()).toContain("姓名 不得为空"));
    expect(submit).not.toHaveBeenCalled();
  });

  it("提交成功：submit 收到表单值且表单重置", async () => {
    const submit = vi.fn(async () => {});
    const wrapper = mount(ProForm, { props: { fields, submit } });

    await findInput(wrapper, "姓名").setValue("张三");
    await findInput(wrapper, "邮箱").setValue("z@example.com");
    const button = wrapper.findAll("button").find((b) => b.text() === "提交");
    await button!.trigger("click");

    await vi.waitFor(() => expect(submit).toHaveBeenCalledTimes(1));
    expect(submit).toHaveBeenCalledWith(expect.objectContaining({ name: "张三", email: "z@example.com" }));
    await vi.waitFor(() => expect((findInput(wrapper, "姓名").element as HTMLInputElement).value).toBe(""));
  });

  it("submit 抛 ApiError 呈现全局错误态（code/message/traceId）", async () => {
    const submit = vi.fn().mockRejectedValueOnce(new ApiError(1001, "参数校验失败", "tid-456", 400));
    const wrapper = mount(ProForm, { props: { fields, submit } });

    await findInput(wrapper, "姓名").setValue("张三");
    await findInput(wrapper, "邮箱").setValue("z@example.com");
    const button = wrapper.findAll("button").find((b) => b.text() === "提交");
    await button!.trigger("click");

    await vi.waitFor(() => expect(wrapper.text()).toContain("提交失败（code 1001）"));
    expect(wrapper.text()).toContain("参数校验失败 · traceId tid-456");
  });
});
