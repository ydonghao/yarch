/**
 * ProForm：声明式查询/编辑表单（ElementPlus Form 薄语义层）。
 * submit 抛 ApiError → 全局错误态（message + traceId 报障凭证）；字段级校验仍走 ElementPlus rules。
 */
import { ElAlert, ElButton, ElForm, ElFormItem, ElInput, ElOption, ElSelect } from "element-plus";
import type { FormInstance, FormRules } from "element-plus";
import { defineComponent, h, reactive, ref, shallowRef } from "vue";
import type { PropType, VNodeChild } from "vue";

import { ApiError } from "@yarch/contract";

export type ProFieldWidget = "input" | "password" | "textarea" | "select";

export interface ProFieldOption {
  label: string;
  value: string | number;
}

export interface ProField {
  /** 字段名（提交键） */
  name: string;
  label: string;
  widget?: ProFieldWidget;
  placeholder?: string;
  required?: boolean;
  options?: ProFieldOption[];
  /** 额外 ElementPlus rules（叠加在 required 之上） */
  rules?: Array<Record<string, unknown>>;
}

export interface ProFormProps {
  fields: ProField[];
  /** 提交处理：抛 ApiError 即呈现错误态（不抛出视为成功并重置表单） */
  submit: (values: Record<string, unknown>) => Promise<void>;
  submitText?: string;
  /** 受控外层内容（如说明文案） */
  extra?: () => VNodeChild;
}

function toApiError(e: unknown): ApiError {
  if (e instanceof ApiError) return e;
  return new ApiError(-1, e instanceof Error ? e.message : String(e));
}

export const ProForm = defineComponent({
  name: "YarchProForm",
  props: {
    fields: { type: Array as PropType<ProField[]>, required: true },
    submit: { type: Function as PropType<ProFormProps["submit"]>, required: true },
    submitText: { type: String, default: "提交" },
    extra: { type: Function as PropType<ProFormProps["extra"]> },
  },
  setup(props) {
    const formRef = ref<FormInstance>();
    const model = reactive<Record<string, unknown>>({});
    const loading = ref(false);
    const error = shallowRef<ApiError | null>(null);

    const rules: FormRules = {};
    for (const f of props.fields) {
      rules[f.name] = [
        ...(f.required ? [{ required: true, message: `${f.label} 不得为空`, trigger: "blur" as const }] : []),
        ...((f.rules ?? []) as Array<Record<string, unknown>>),
      ];
    }

    const handleSubmit = async () => {
      try {
        await formRef.value?.validate();
      } catch {
        return; // 字段级校验失败：提示已由 FormItem 呈现
      }
      loading.value = true;
      error.value = null;
      try {
        await props.submit({ ...model });
        formRef.value?.resetFields();
      } catch (e: unknown) {
        error.value = toApiError(e);
      } finally {
        loading.value = false;
      }
    };

    const bind = (name: string) => ({
      modelValue: (model[name] ?? "") as string,
      "onUpdate:modelValue": (v: string) => (model[name] = v),
    });

    return () => {
      const children: VNodeChild[] = [];

      if (props.extra) children.push(h("div", [props.extra()]));

      if (error.value) {
        const e = error.value;
        children.push(
          h(ElFormItem, null, () =>
            h(ElAlert, { type: "error", showIcon: true, closable: false }, () => [
              `提交失败（code ${e.code}）——${e.message}`,
              e.traceId ? ` · traceId ${e.traceId}` : "",
            ]),
          ),
        );
      }

      for (const f of props.fields) {
        let widget: VNodeChild;
        if (f.widget === "select") {
          widget = h(
            ElSelect,
            { ...bind(f.name), placeholder: f.placeholder },
            () => (f.options ?? []).map((o) => h(ElOption, { key: String(o.value), label: o.label, value: o.value })),
          );
        } else if (f.widget === "textarea") {
          widget = h(ElInput, { ...bind(f.name), type: "textarea", rows: 3, placeholder: f.placeholder });
        } else {
          widget = h(ElInput, {
            ...bind(f.name),
            type: f.widget === "password" ? "password" : "text",
            showPassword: f.widget === "password" || undefined,
            placeholder: f.placeholder,
          });
        }
        children.push(h(ElFormItem, { label: f.label, prop: f.name }, () => widget));
      }

      children.push(
        h(ElButton, { type: "primary", loading: loading.value, onClick: () => void handleSubmit() }, () => props.submitText),
      );

      return h(ElForm, { model, rules, ref: formRef, labelPosition: "top" }, () => children);
    };
  },
});
