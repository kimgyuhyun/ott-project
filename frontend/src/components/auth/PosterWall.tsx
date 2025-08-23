export default function PosterWall() {
  return (
    <div
      aria-hidden
      className="absolute inset-0 -z-10 grid grid-cols-6 gap-4 p-6 opacity-30 [filter:blur(2px)]"
    >
      {Array.from({ length: 24 }).map((_, i) => (
        <div key={i} className="h-32 rounded-lg bg-white/10" />
      ))}
      <div className="absolute inset-0 bg-black/40" />
    </div>
  );
}


