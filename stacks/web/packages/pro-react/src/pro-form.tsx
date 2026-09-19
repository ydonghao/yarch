/**
 * ProForm：声明式查询/编辑表单（antd Form 薄语义层）。
 * submit 抛 ApiError → 全局错误态（message + traceId 报障凭证）；字段级校验仍走 antd rules。
 */
import { Alert, Button, Form, Input, Select } from "antd";
import { useState } from "react";
import type { ReactNode } from "react";

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
  /** 额外 antd rules（叠加在 required 之上） */
  rules?: Array<Record<string, unknown>>;
}

export interface ProFormProps {
  fields: ProField[];
  /** 提交处理：抛 ApiError 即呈现错误态（不抛出视为成功并重置表单） */
  submit: (values: Record<string, unknown>) => Promise<void>;
  submitText?: string;
  /** 受控外层内容（如说明文案） */
  extra?: ReactNode;
}

export function ProForm(props: ProFormProps) {
  const { fields, submit, submitText = "提交", extra } = props;
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  return (
    <Form
      form={form}
      layout="vertical"
      onFinish={async (values: Record<string, unknown>) => {
        setLoading(true);
        setError(null);
        try {
          await submit(values);
          form.resetFields();
        } catch (e: unknown) {
          setError(e instanceof ApiError ? e : new ApiError(-1, String(e)));
        } finally {
          setLoading(false);
        }
      }}
    >
      {extra}
      {error && (
        <Form.Item>
          <Alert
            type="error"
            showIcon
            message={`提交失败（code ${error.code}）`}
            description={`${error.message}${error.traceId ? ` · traceId ${error.traceId}` : ""}`}
          />
        </Form.Item>
      )}
      {fields.map((f) => (
        <Form.Item
          key={f.name}
          name={f.name}
          label={f.label}
          rules={[
            ...(f.required ? [{ required: true, message: `${f.label} 不得为空` }] : []),
            ...(f.rules ?? []),
          ]}
        >
          {f.widget === "select" ? (
            <Select placeholder={f.placeholder} options={f.options} />
          ) : f.widget === "textarea" ? (
            <Input.TextArea placeholder={f.placeholder} rows={3} />
          ) : (
            <Input type={f.widget === "password" ? "password" : undefined} placeholder={f.placeholder} />
          )}
        </Form.Item>
      ))}
      <Button type="primary" htmlType="submit" loading={loading}>
        {submitText}
      </Button>
    </Form>
  );
}
