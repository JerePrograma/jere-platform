import type { PropsWithChildren } from "react";

export const PLATFORM_LABEL = "Jere Platform";

export function formatProductLabel(product: string): string {
  return `${PLATFORM_LABEL} · ${product.trim()}`;
}

type PlatformCardProps = PropsWithChildren<{
  title: string;
}>;

export function PlatformCard({ title, children }: PlatformCardProps) {
  return (
    <section className="platform-card" aria-labelledby="platform-card-title">
      <h2 id="platform-card-title">{title}</h2>
      <div>{children}</div>
    </section>
  );
}
