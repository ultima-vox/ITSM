interface Slice {
  value: number;
  color: string;
}

interface DonutChartProps {
  slices: Slice[];
  centerValue: string | number;
  centerLabel: string;
}

export function DonutChart({ slices, centerValue, centerLabel }: DonutChartProps) {
  const total = slices.reduce((s, x) => s + x.value, 0) || 1;
  let acc = 0;
  const stops = slices
    .map((slice) => {
      const start = (acc / total) * 100;
      acc += slice.value;
      const end = (acc / total) * 100;
      return `${slice.color} ${start}% ${end}%`;
    })
    .join(', ');

  return (
    <div
      className="donut"
      style={{ background: `conic-gradient(${stops})` }}
      role="img"
      aria-label={`${centerValue} ${centerLabel}`}
    >
      <div>
        <b>{centerValue}</b>
        <span>{centerLabel}</span>
      </div>
    </div>
  );
}
