import type { ReactNode } from "react";

export function PageContainer({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div>
      <h2>{title}</h2>
      {children}
    </div>
  );
}
